// ---------------------  File Organizer  -------------------------------
// Kotlin + JavaFX — single-file app
//   • Dark theme (inline CSS)           • Editable config file
//   • Quick-access: Documents / Desktop / Downloads
//   • Optional custom destination folder
//   • Custom dark title-bar (StageStyle.UNDECORATED)
// ----------------------------------------------------------------------
// Config file (generated at first run): file-organizer.cfg
// ----------------------------------------------------------------------

import javafx.application.Application
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.CheckBoxTableCell
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.stage.StageStyle
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/* ---------- helpers ---------- */
private val df      = DecimalFormat("#,##0.##")
private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
private fun fmt(ms: Long) = timeFmt.format(Instant.ofEpochMilli(ms))
private fun createdMs(f: File) =
    try { Files.readAttributes(f.toPath(), BasicFileAttributes::class.java).creationTime().toMillis() }
    catch (_: Exception) { f.lastModified() }
private fun sizeStr(b: Long) = when {
    b < 1024          -> "$b B"
    b < 1_048_576     -> "${df.format(b / 1024.0)} KB"
    b < 1_073_741_824 -> "${df.format(b / 1_048_576.0)} MB"
    else              -> "${df.format(b / 1_073_741_824.0)} GB"
}

/* ---------- config ---------- */
private const val CFG = "file-organizer.cfg"
private fun defaultConfig(): Properties = Properties().apply {
    setProperty("keepOriginal", "false")
    setProperty("skipExe", "true")
    setProperty("useCustomDestination", "false")
    setProperty("customDestination", "")
    setProperty("documents/pdf",      "pdf,doc,docx,txt")
    setProperty("images/photos",      "jpg,jpeg,png,webp")
    setProperty("presentations/ppt",  "ppt,pptx,odp")
    setProperty("archives/all",       "zip,rar,7z,tar,gz")
    setProperty("exe",                "exe,msi")
}
private fun loadCfg(): Properties {
    val f = File(CFG)
    return if (f.exists()) {
        Properties().apply { FileReader(f).use(this::load) }
    } else {
        val p = defaultConfig()
        FileWriter(f).use { writer -> p.store(writer, "File-Organizer default") }
        p
    }
}
private fun saveCfg(p: Properties) =
    FileWriter(CFG).use { writer -> p.store(writer, "File-Organizer") }
/* -------------------  APPLICATION  ------------------- */
class FileOrganizerApp : Application() {

    /* ---- row model ---- */
    data class Row(val file: File, val sel: SimpleBooleanProperty = SimpleBooleanProperty(true)) {
        val name     get() = file.name
        val size     get() = sizeStr(file.length())
        val created  get() = fmt(createdMs(file))
        val modified get() = fmt(file.lastModified())
    }

    private val cfg = loadCfg()
    private val categories = cfg.stringPropertyNames()
        .filter { it.contains('/') }
        .associateWith { cfg.getProperty(it).split(',').map(String::trim).filter(String::isNotEmpty) }
        .toMutableMap()

    /* ---- UI controls ---- */
    private val rows        = FXCollections.observableArrayList<Row>()
    private val table       = TableView<Row>()
    private val lblSummary  = Label("No files loaded")
    private val chkKeep     = CheckBox("Keep original (copy)")
    private val chkSkipExe  = CheckBox("Skip .exe / .lnk")
    private val chkCustom   = CheckBox("Use custom destination")
    private val txtCustom   = TextField().apply { promptText = "Destination path…" }
    private val btnOrganize = Button("Organize selected")

    /* ---------- start ---------- */
    /* ---------- start ---------- */
    override fun start(primary: Stage) {

        /* 1️⃣  własna ramka – trzeba zadeklarować **PRZED** show()  */
        primary.initStyle(StageStyle.UNDECORATED)

        /* --- opcje z cfg --- */
        chkKeep.isSelected    = cfg.getProperty("keepOriginal",         "false").toBoolean()
        chkSkipExe.isSelected = cfg.getProperty("skipExe",              "true").toBoolean()
        chkCustom.isSelected  = cfg.getProperty("useCustomDestination", "false").toBoolean()
        txtCustom.text        = cfg.getProperty("customDestination",    "")

        /* --- centralna część UI --- */
        val bar      = buildQuickBar(primary)
        buildTable()

        val opts     = VBox(6.0, chkKeep, chkSkipExe, chkCustom, txtCustom)
        val content  = VBox(10.0, table, opts, lblSummary, btnOrganize)
        VBox.setMargin(lblSummary, Insets(6.0, 0.0, 0.0, 0.0))

        btnOrganize.isDisable = true
        btnOrganize.setOnAction { doOrganize() }
        rows.addListener(ListChangeListener { refreshSummary() })

        /* --- własna, ciemna belka tytułu --- */
        val titleBar = buildTitleBar(primary)

        /* --- BorderPane: belka u góry + reszta --- */
        val rootPane = BorderPane().apply {
            top    = titleBar
            center = VBox(10.0, bar, content)   // ← NIC nie nadpisujemy
        }

        val scene = Scene(rootPane, 960.0, 720.0).apply {
            fill        = Color.web("#1e1e1e")
            stylesheets += darkCss()             // jeden arkusz, inline
        }

        primary.scene = scene
        primary.title = "File Organizer"        // potrzebne dla Alt-Tab/ikony
        primary.show()
    }


    /* ---------- custom dark title-bar ---------- */
    /* ---------- custom dark title-bar + drag helper ---------- */
    private fun buildTitleBar(stage: Stage): HBox {

        /* uniwersalna funkcja drag-and-move */
        fun makeDraggable(node: Region) {
            var dx = .0; var dy = .0
            node.addEventFilter(MouseEvent.MOUSE_PRESSED) {
                dx = it.sceneX; dy = it.sceneY
            }
            node.addEventFilter(MouseEvent.MOUSE_DRAGGED) {
                stage.x = it.screenX - dx
                stage.y = it.screenY - dy
            }
        }

        /* opcjonalna ikona 16×16 z resources */
        val iconView = javaClass.getResource("/icon16.png")
            ?.let { ImageView(Image(it.toString())) }
            ?: Region()                                // pusta gdy brak pliku
        if (iconView is ImageView) {
            iconView.fitWidth  = 16.0
            iconView.fitHeight = 16.0
        }

        val title = Label("File Organizer").apply {
            style = "-fx-text-fill:#d4d4d4; -fx-font-size:13;"
        }

        /* system buttons – trochę większy font + minHeight */
        fun sysBtn(ch: String, onClick: () -> Unit, red: Boolean = false) =
            Button(ch).apply {
                style = """
                -fx-background-color: transparent;
                -fx-text-fill: ${if (red) "#ff4e4e" else "#d4d4d4"};
                -fx-font-size: 15;
            """.trimIndent()
                prefWidth  = 44.0
                minHeight  = 28.0
                isFocusTraversable = false
                setOnAction { onClick() }
            }

        val btnMin = sysBtn("–", { stage.isIconified = true })
        val btnMax = sysBtn("❐", { stage.isMaximized = !stage.isMaximized })
        val btnCls = sysBtn("×", { stage.close() }, true)


        /* lewa część belki (ikona + tytuł) */
        val dragArea = HBox(6.0, iconView, title).apply {
            padding = Insets(4.0, 0.0, 4.0, 8.0)
        }

        /* cała belka */
        return HBox(dragArea, Region(), btnMin, btnMax, btnCls).apply {
            style     = "-fx-background-color:#1e1e1e; -fx-border-color:#3d3d3d;"
            minHeight = 32.0
            HBox.setHgrow(children[1], Priority.ALWAYS)   // filler

            /* przeciąganie działa na całą belkę  */
            makeDraggable(this)
        }
    }


    /* ---------- Organize button ---------- */
    private fun doOrganize() {
        val selected   = rows.filter { it.sel.get() }.map { it.file }
        val customDest = if (chkCustom.isSelected && txtCustom.text.isNotBlank()) File(txtCustom.text) else null

        val log = organize(selected, chkKeep.isSelected, chkSkipExe.isSelected, customDest)

        Alert(Alert.AlertType.INFORMATION, log.joinToString("\n"), ButtonType.OK).showAndWait()

        cfg.setProperty("keepOriginal",         chkKeep.isSelected.toString())
        cfg.setProperty("skipExe",              chkSkipExe.isSelected.toString())
        cfg.setProperty("useCustomDestination", chkCustom.isSelected.toString())
        cfg.setProperty("customDestination",    txtCustom.text)
        saveCfg(cfg)
    }

    /* ---------- table ---------- */
    private fun buildTable() {
        table.isEditable = true                         // ① tabela edytowalna

        val colSel = TableColumn<Row, Boolean>("✔").apply {
            prefWidth = 50.0
            // ② właściwość do odczytu
            setCellValueFactory { it.value.sel }
            // ③ właściwość do zapisu (lambda ze wskaźnikiem wiersza)
            cellFactory = CheckBoxTableCell.forTableColumn { rowIndex ->
                rows[rowIndex].sel                      // ← zwracamy właściwość wiersza
            }
            isEditable = true                           // ④ kolumna edytowalna
        }

        fun col(t: String, w: Double, p: (Row) -> String) =
            TableColumn<Row, String>(t).apply {
                prefWidth = w
                setCellValueFactory { javafx.beans.property.SimpleStringProperty(p(it.value)) }
            }

        table.columns.setAll(
            colSel,
            col("File name", 260.0) { it.name },
            col("Size",      110.0) { it.size },
            col("Created",   160.0) { it.created },
            col("Modified",  160.0) { it.modified }
        )
        table.items = rows
    }


    /* ---------- quick buttons ---------- */
    private fun buildQuickBar(stage: Stage): HBox {
        val home = System.getProperty("user.home"); val user = System.getProperty("user.name")
        fun f(path: String) = File(path)
        fun dirs(pl: List<String>) = pl.map(::f).filter { it.exists() && it.isDirectory }

        val docs = dirs(listOf(
            "$home/Documents", "$home/Dokumenty",
            "$home/OneDrive/Documents", "$home/OneDrive/Dokumenty",
            "C:/Users/$user/Documents", "C:/Users/$user/Dokumenty",
            "C:/Users/$user/OneDrive/Documents", "C:/Users/$user/OneDrive/Dokumenty"
        ))
        val desk = dirs(listOf(
            "$home/Desktop", "$home/Pulpit",
            "$home/OneDrive/Desktop", "$home/OneDrive/Pulpit",
            "C:/Users/$user/OneDrive/Pulpit"
        ))
        val down = f("$home/Downloads").takeIf { it.exists() && it.isDirectory }

        val choose = Button("Choose").apply { setOnAction { DirectoryChooser().showDialog(stage)?.let { loadFiles(listOf(it)) } } }
        val bDocs  = Button("Documents").apply { isDisable = docs.isEmpty(); setOnAction { loadFiles(docs) } }
        val bDown  = Button("Downloads").apply { isDisable = down == null; setOnAction { loadFiles(listOf(down!!)) } }
        val bDesk  = Button("Desktop").apply { isDisable = desk.isEmpty(); setOnAction { chkSkipExe.isSelected = true; loadFiles(desk) } }
        return HBox(10.0, choose, bDocs, bDown, bDesk)
    }

    /* ---------- load files ---------- */
    private fun loadFiles(dirs: List<File>) {
        val files = dirs.flatMap { it.listFiles()?.toList() ?: emptyList() }.filter { it.isFile }
        rows.setAll(files.map { Row(it) })
        btnOrganize.isDisable = rows.isEmpty(); refreshSummary()
    }

    private fun refreshSummary() {
        lblSummary.text = "Selected: ${rows.count { it.sel.get() }}"
    }

    /* ---------- organize core ---------- */
    private fun organize(files: List<File>, keep: Boolean, skipExe: Boolean, customDest: File?): List<String> {
        if (files.isEmpty()) return listOf("No files selected")

        val today   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val baseDir = customDest ?: File(files.first().parentFile, today)
        val log     = mutableListOf<String>()

        for (f in files) {
            if (skipExe && (f.extension.equals("exe", true) || f.extension.equals("lnk", true))) {
                log += "⏭ Skipped: ${f.name}"; continue
            }

            val relCat = categories.entries.firstOrNull { f.extension.lowercase() in it.value }?.key ?: "others"
            val tgtDir = File(baseDir, relCat).apply { mkdirs() }
            val tgt    = File(tgtDir, f.name)

            try {
                if (keep) Files.copy(f.toPath(), tgt.toPath(), StandardCopyOption.REPLACE_EXISTING)
                else      Files.move(f.toPath(), tgt.toPath(), StandardCopyOption.REPLACE_EXISTING)
                log += "✔ ${if (keep) "Copied" else "Moved"}: ${f.name} → ${tgt.path}"
            } catch (e: Exception) {
                log += "✘ Error moving ${f.name}: ${e.message}"
            }
        }
        File(baseDir, "organizer_report-$today.txt").appendText(log.joinToString("\n", prefix = "\n"))
        return log
    }

    /* ---------- inline dark css ---------- */
    private fun darkCss(): String {
        val css = """
        .root { -fx-background-color:#1e1e1e; }

        .label, .check-box, .button, .table-row-cell,
        .table-column-header, .text-field {
            -fx-text-fill:#d4d4d4;
            -fx-font-family:"Segoe UI",sans-serif;
        }

        .button { -fx-background-color:#0e639c; -fx-background-radius:3; }
        .button:hover { -fx-background-color:#1174b5; }
        .button:focused { -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }

        .text-field {
            -fx-background-color:#2b2b2b;
            -fx-text-fill:#d4d4d4;
            -fx-prompt-text-fill:#7d7d7d;
            -fx-highlight-fill:#094771;
            -fx-border-color:#3d3d3d;
            -fx-background-radius:3;
        }

        .check-box > .box              { -fx-background-color:#2b2b2b; -fx-border-color:#5a5a5a; }
        .check-box:selected > .box     { -fx-background-color:#0e639c; }

        .table-view,
        .table-row-cell {
            -fx-control-inner-background:#252526;
            -fx-background-color:#1e1e1e;
            -fx-table-cell-border-color:#3d3d3d;
        }
        .table-row-cell:filled:hover         { -fx-background-color:#333333; }
        .table-row-cell:selected,
        .table-row-cell:selected:hover       { -fx-background-color:#094771; }

        .table-view .column-header,
        .table-view .column-header-background,
        .table-view .filler {
            -fx-background-color:#1e1e1e;
            -fx-border-color:#3d3d3d transparent #3d3d3d transparent;
        }

        .virtual-flow > .corner { -fx-background-color:#1e1e1e; }

        .scroll-bar > .track          { -fx-background-color:#1e1e1e; }
        .scroll-bar > .increment-button,
        .scroll-bar > .decrement-button { -fx-background-color:#1e1e1e; }
        .scroll-bar > .thumb          { -fx-background-color:#3d3d3d; }
        .scroll-bar > .thumb:hover    { -fx-background-color:#5a5a5a; }
        """.trimIndent()

        return "data:text/css," + css.replace(" ", "%20").replace("\n", "%0A")
    }
}

/* ---------- entry point ---------- */
fun main() = Application.launch(FileOrganizerApp::class.java)
