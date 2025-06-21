// ---------------------  File Organizer  -------------------------------
// Kotlin + JavaFX — single-file app
//   • Dark theme (inline CSS)           • Editable config file
//   • Quick-access: Documents / Desktop / Downloads
//   • Optional custom destination folder
//   • Custom dark title-bar (StageStyle.UNDECORATED)
//   • Recursive load  +  dynamic filters (name LIKE, size, date)
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
import java.util.stream.Collectors
import javafx.geometry.Pos
import java.nio.file.Paths
import java.nio.file.LinkOption


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

/* ---------- match helpers ---------- */
private fun sqlLike(text: String, pattern: String): Boolean {
    val regex = Regex(
        pattern.trim()
            .replace(".", "\\.")
            .replace("%", ".*")
            .replace("_", "."),
        RegexOption.IGNORE_CASE
    )
    return regex.matches(text)
}
private fun parseSize(raw: String): Long? {
    if (raw.isBlank()) return null
    val m = Regex("""(\d[\d\s]*)\s*(B|KB|MB|GB)?""", RegexOption.IGNORE_CASE)
        .matchEntire(raw.trim()) ?: return null
    val num = m.groupValues[1].replace(" ", "").toLong()
    val mul = when (m.groupValues[2].uppercase()) {
        "KB" -> 1_024L
        "MB" -> 1_048_576L
        "GB" -> 1_073_741_824L
        else -> 1L
    }
    return num * mul
}

private fun toBytes(valueTxt: String, unit: String): Long? {
    val num = valueTxt.trim().toLongOrNull() ?: return null
    val mul = when (unit) {
        "KB" -> 1_024L
        "MB" -> 1_048_576L
        "GB" -> 1_073_741_824L
        else -> 1L                     // "B"
    }
    return num * mul
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
        // --- Ręczny zapis configa z komentarzami ---
        val exampleComment = """
            # File Organizer config
            #
            # Dodaj własne kategorie, np:
            # images/gimp=psd,xcf
            # documents/word=doc,docx
            #
            # Format: folder/nazwa=rozszerzenie1,rozszerzenie2,...
            #
            # Wszystkie nieprzypisane trafią do "others"
            #
        """.trimIndent()

        val props = defaultConfig()
        FileWriter(f).use { w ->
            w.write(exampleComment + "\n")
            props.store(w, "File-Organizer default")
        }
        props
    }
}

private fun saveCfg(p: Properties) =
    FileWriter(CFG).use { w -> p.store(w, "File-Organizer") }

/* -------------------  APPLICATION  ------------------- */
class FileOrganizerApp : Application() {
    private var baseSearchPath: File? = null




    /* ---- row model ---- */
    data class Row(val file: File, val sel: SimpleBooleanProperty = SimpleBooleanProperty(true)) {
        val name     get() = file.name
        val size     get() = sizeStr(file.length())
        val created  get() = fmt(createdMs(file))
        val modified get() = fmt(file.lastModified())
        var relPath: String = ""
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
    private val lblLocation = Label("No folder selected").apply { style = "-fx-text-fill:#7d7d7d;" }

    private val chkKeep     = CheckBox("Keep original (copy)")
    private val chkSkipExe  = CheckBox("Skip .exe / .lnk")
    private val chkCustom   = CheckBox("Use custom destination")
    private val txtCustom   = TextField().apply { promptText = "Destination path…" }
    private val btnOrganize = Button("Organize selected")

    /* ---------- start ---------- */
    /* ---------- start ---------- */
    override fun start(primary: Stage) {

        primary.initStyle(StageStyle.UNDECORATED)

        // odtwórz ustawienia z cfg
        chkKeep.isSelected    = cfg.getProperty("keepOriginal",         "false").toBoolean()
        chkSkipExe.isSelected = cfg.getProperty("skipExe",              "true").toBoolean()
        chkCustom.isSelected  = cfg.getProperty("useCustomDestination", "false").toBoolean()
        txtCustom.text        = cfg.getProperty("customDestination",    "")

        // UI
        val quickBar  = buildQuickBar(primary)   // górny pasek
        val filterBar = buildFilterBar()
        buildTable()

        val optsBox   = VBox(6.0, chkKeep, chkSkipExe, chkCustom, txtCustom)
        val centerBox = VBox(10.0, table, optsBox)

        val bottomBox = VBox(
            10.0,
            filterBar,
            HBox(10.0, lblSummary, btnOrganize).apply {
                alignment = Pos.CENTER_LEFT
                VBox.setMargin(this, Insets(0.0, 0.0, 0.0, 4.0))
            }
        )

        btnOrganize.isDisable = true
        btnOrganize.setOnAction { doOrganize() }
        rows.addListener(ListChangeListener { refreshSummary() })

        val root = BorderPane().apply {
            top    = VBox(buildTitleBar(primary), quickBar)   // <- wraca!
            center = centerBox
            bottom = bottomBox
        }

        primary.scene = Scene(root, 960.0, 720.0).apply {
            fill = Color.web("#1e1e1e")
            stylesheets += darkCss()
        }
        primary.title = "File Organizer"
        primary.show()
    }



    private fun buildQuickBar(stage: Stage): HBox {

        val home = System.getProperty("user.home")
        val user = System.getProperty("user.name")
        fun f(p: String) = File(p)
        fun dirs(list: List<String>) = list.map(::f).filter { it.isDirectory }

        // ❶ domyślny „Documents” z HOME
        val defaultDocs = Paths.get(System.getProperty("user.home"), "Documents").toFile()

// ❷ ścieżka, którą zwraca Windows Shell (USERPROFILE\Documents)
        val shellDocs   = File(System.getenv("USERPROFILE") + "\\Documents")

// ❸ (opcjonalnie) ręczne warianty OneDrive - jeśli ich potrzebujesz
        val oneDriveDocs = listOf(
            File(System.getenv("USERPROFILE") + "\\OneDrive\\Documents"),
            File(System.getenv("USERPROFILE") + "\\OneDrive - Personal\\Documents"),
            File(System.getenv("USERPROFILE") + "\\OneDrive - Osobiste\\Dokumenty")
        )

// ostateczna lista:
        val docs = (listOf(defaultDocs, shellDocs) + oneDriveDocs)
            .filter { it.isDirectory }

        val desk = dirs(listOf(
            "$home/Desktop","$home/Pulpit",
            "$home/OneDrive/Desktop","$home/OneDrive/Pulpit",
            "C:/Users/$user/OneDrive/Pulpit"
        ))
        val down = f("$home/Downloads").takeIf { it.isDirectory }

        fun loadPreset(dirs: List<File>, name: String) = loadFiles(dirs, name)

        val choose = Button("Choose").apply {
            setOnAction {
                DirectoryChooser().showDialog(stage)?.let { dir ->
                    loadFiles(listOf(dir), dir.absolutePath)
                }
            }
        }
        val btnDocs = Button("Documents").apply {
            isDisable = docs.isEmpty()
            setOnAction { loadPreset(docs, "Documents") }
        }
        val btnDown = Button("Downloads").apply {
            isDisable = down == null
            setOnAction { loadPreset(listOf(down!!), "Downloads") }
        }
        val btnDesk = Button("Desktop").apply {
            isDisable = desk.isEmpty()
            setOnAction {
                chkSkipExe.isSelected = true
                loadPreset(desk, "Desktop")
            }
        }

        val spacer = Region()
        return HBox(10.0, choose, btnDocs, btnDown, btnDesk, spacer, lblLocation).apply {
            HBox.setHgrow(spacer, Priority.ALWAYS)
            padding = Insets(6.0, 15.0, 6.0, 15.0)
        }
    }

    private fun buildTitleBar(stage: Stage): HBox {

        fun makeDraggable(node: Region) {
            var dx = 0.0; var dy = 0.0
            node.addEventFilter(MouseEvent.MOUSE_PRESSED) { dx = it.sceneX; dy = it.sceneY }
            node.addEventFilter(MouseEvent.MOUSE_DRAGGED) { stage.x = it.screenX - dx; stage.y = it.screenY - dy }
        }

        val icon = javaClass.getResource("/icon16.png")
            ?.let { ImageView(Image(it.toString())).apply { fitWidth = 16.0; fitHeight = 16.0 } }
            ?: Region()

        val titleLbl = Label("File Organizer").apply {
            style = "-fx-text-fill:#d4d4d4; -fx-font-size:13;"
        }

        fun sysBtn(c: String, red: Boolean = false, act: () -> Unit) =
            Button(c).apply {
                prefWidth = 44.0; minHeight = 28.0; isFocusTraversable = false
                style = """
                -fx-background-color: transparent;
                -fx-text-fill: ${if (red) "#ff4e4e" else "#d4d4d4"};
                -fx-font-size: 15;
            """.trimIndent()
                setOnAction { act() }
            }

        val dragArea = HBox(6.0, icon, titleLbl).apply { padding = Insets(4.0, 0.0, 4.0, 8.0) }

        return HBox(
            dragArea,
            Region(),
            sysBtn("–")             { stage.isIconified  = true },
            sysBtn("❐")             { stage.isMaximized  = !stage.isMaximized },
            sysBtn("×", red = true) { stage.close() }
        ).apply {
            style = "-fx-background-color:#1e1e1e; -fx-border-color:#3d3d3d;"
            minHeight = 32.0
            HBox.setHgrow(children[1], Priority.ALWAYS)
            makeDraggable(this)
        }
    }






    /* ---------- Organize button ---------- */
    private fun doOrganize() {
        val sel = table.items.filter { it.sel.get() }.map { it.file }

        val customDst = if (chkCustom.isSelected && txtCustom.text.isNotBlank()) File(txtCustom.text) else null
        val log       = organize(sel, chkKeep.isSelected, chkSkipExe.isSelected, customDst)

        Alert(Alert.AlertType.INFORMATION, log.joinToString("\n"), ButtonType.OK).showAndWait()

        cfg.setProperty("keepOriginal", chkKeep.isSelected.toString())
        cfg.setProperty("skipExe", chkSkipExe.isSelected.toString())
        cfg.setProperty("useCustomDestination", chkCustom.isSelected.toString())
        cfg.setProperty("customDestination", txtCustom.text)
        saveCfg(cfg)
    }

    private fun buildFilterBar(): HBox {

        /* --- pola nazwy --- */
        val txtName = TextField().apply {
            promptText = "name LIKE %pattern%"
            prefWidth  = 160.0
        }

        /* --- pola rozmiaru --- */
        fun sizeInput(mark: String): Pair<TextField, ComboBox<String>> {
            val txt = TextField().apply {
                prefWidth = 80.0
                promptText = mark          //  "≥ size"  •  "≤ size"
            }
            val cmb = ComboBox(
                FXCollections.observableArrayList("B", "KB", "MB", "GB")
            ).apply { value = "KB"; prefWidth = 65.0 }
            return txt to cmb
        }
        val (txtSizeMin, cmbMin) = sizeInput("≥ size")
        val (txtSizeMax, cmbMax) = sizeInput("≤ size")

        /* --- pola daty --- */
        val dpFrom = DatePicker().apply { promptText = "date from" }
        val dpTo   = DatePicker().apply { promptText = "to" }

        /* --- przycisk --- */
        val btnFilter = Button("Filter").apply {
            setOnAction {
                applyFilter(
                    pattern  = txtName.text,
                    sizeMin  = toBytes(txtSizeMin.text, cmbMin.value),
                    sizeMax  = toBytes(txtSizeMax.text, cmbMax.value),
                    dateFrom = dpFrom.value,
                    dateTo   = dpTo.value
                )
            }
        }

        return HBox(
            10.0,
            txtName,
            txtSizeMin, cmbMin,
            Label("≤"),          // mały separator wizualny
            txtSizeMax, cmbMax,
            dpFrom, dpTo,
            btnFilter
        ).apply { padding = Insets(6.0, 15.0, 6.0, 15.0) }
    }

    /* ---------- table ---------- */
    private fun buildTable() {
        table.isEditable = true

        val master = CheckBox().apply {
            setOnAction {
                val select = isSelected
                rows.forEach { it.sel.set(select) }
            }
        }

        val colSel = TableColumn<Row, Boolean>("✔").apply {
            prefWidth = 50.0
            setCellValueFactory { it.value.sel }
            cellFactory = CheckBoxTableCell.forTableColumn { rows[it].sel }
            isEditable = true
            graphic = master

            // Synchronizacja stanu mastera (jeśli odklikniesz coś ręcznie, master odznaczy się)
            rows.addListener(ListChangeListener {
                master.isSelected = rows.all { it.sel.get() }
            })
            // Jeszcze reakcja na zmianę jednego ptaszka (gdy user klika)
            rows.forEach { row ->
                row.sel.addListener { _, _, _ ->
                    master.isSelected = rows.all { it.sel.get() }
                }
            }
        }

        fun col(t: String, w: Double, p: (Row) -> String) = TableColumn<Row, String>(t).apply {
            prefWidth = w
            setCellValueFactory { javafx.beans.property.SimpleStringProperty(p(it.value)) }
        }

        table.columns.setAll(
            colSel,
            col("File name", 260.0) { it.name },
            col("Path", 180.0) { it.relPath },
            col("Size", 110.0) { it.size },
            col("Created", 160.0) { it.created },
            col("Modified", 160.0) { it.modified }
        )
        table.items = rows
    }




    private fun loadFiles(dirs: List<File>, displayName: String) {

        val collected = mutableListOf<File>()
        var deniedCnt = 0
        baseSearchPath = dirs.firstOrNull()

        for (root in dirs) {
            if (!root.canRead()) {       // brak praw już do katalogu startowego
                deniedCnt++; continue
            }

            try {
                Files.walk(root.toPath())        // bez dodatkowych opcji
                    .use { stream ->
                        stream.forEach { p ->
                            try {
                                if (Files.isRegularFile(p)) {      // tylko pliki
                                    collected += p.toFile()
                                }
                            } catch (_: AccessDeniedException) {
                                deniedCnt++
                            } catch (_: Exception) {
                                // inne incydentalne błędy
                            }
                        }
                    }
            } catch (_: AccessDeniedException) {   // gdy cały podkatalog jest chroniony
                deniedCnt++
            } catch (_: Exception) {
                /* ignorujemy pozostałe wyjątki na katalogu głównym */
            }
        }

        // aktualizacja UI
        rows.setAll(collected.map { file ->
            Row(file).apply {
                relPath = baseSearchPath?.toPath()?.relativize(file.toPath()).toString()
            }
        })

        lblLocation.text      = displayName
        btnOrganize.isDisable = rows.isEmpty()
        refreshSummary()

        if (deniedCnt > 0) {
            Alert(
                Alert.AlertType.INFORMATION,
                "Pominięto $deniedCnt elementów bez uprawnień.",
                ButtonType.OK
            ).showAndWait()
        }
    }






    private fun refreshSummary() {
        lblSummary.text = "Selected: ${rows.count { it.sel.get() }}"
    }

    /* ---------- dynamic filter ---------- */
    private fun applyFilter(
        pattern : String,
        sizeMin : Long?,
        sizeMax : Long?,
        dateFrom: LocalDate?,
        dateTo  : LocalDate?
    ) {
        val filtered = rows.filter { r ->
            if (pattern.isNotBlank() && !sqlLike(r.file.name, pattern)) false
            else {
                val len = r.file.length()
                val okSize = (sizeMin == null || len >= sizeMin) &&
                        (sizeMax == null || len <= sizeMax)
                val d = Instant.ofEpochMilli(r.file.lastModified())
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                val okDate = (dateFrom == null || !d.isBefore(dateFrom)) &&
                        (dateTo   == null || !d.isAfter(dateTo))
                okSize && okDate
            }
        }
        table.items.setAll(filtered)
        lblSummary.text = "Filtered: ${filtered.size} / ${rows.size}"
    }

    /* ---------- organize core ---------- */
    private fun organize(
        files: List<File>,
        keep: Boolean,
        skipExe: Boolean,
        customDest: File?
    ): List<String> {
        if (files.isEmpty()) return listOf("No files selected")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val base  = customDest ?: File(files.first().parentFile, today)
        val log   = mutableListOf<String>()

        // --- podsumowanie ---
        var moved = 0
        var copied = 0
        var skipped = 0
        var errors = 0

        for (f in files) {
            if (skipExe && (f.extension.equals("exe",true)||f.extension.equals("lnk",true))) {
                log += "⏭ Skipped: ${f.name}"
                skipped++
                continue
            }
            val rel = categories.entries.firstOrNull { f.extension.lowercase() in it.value }?.key ?: "others"
            val tgt = File(File(base, rel).apply{mkdirs()}, f.name)
            try {
                if (keep) {
                    Files.copy(f.toPath(), tgt.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    log += "✔ Copied: ${f.name} → ${tgt.path}"
                    copied++
                } else {
                    Files.move(f.toPath(), tgt.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    log += "✔ Moved: ${f.name} → ${tgt.path}"
                    moved++
                }
            } catch (e: Exception) {
                log += "✘ Error moving ${f.name}: ${e.message}"
                errors++
            }
        }
        File(base, "organizer_report-$today.txt").appendText(log.joinToString("\n", prefix = "\n"))

        val summary = """
        Finished!
        Moved: $moved
        Copied: $copied
        Skipped: $skipped
        Errors: $errors
    """.trimIndent()

        return listOf(summary)
    }

    /* ---------- inline dark css ---------- */
    /** Ciemny arkusz stylów osadzony jako data-URI */
    private fun darkCss(): String {
        val css = """
    /* ------------ podstawowa paleta ------------ */
    .root { -fx-background-color:#1e1e1e; }

    .label, .check-box, .button, .table-row-cell,
    .table-column-header, .text-field {
        -fx-text-fill:#d4d4d4;
        -fx-font-family:"Segoe UI",sans-serif;
    }

    /* ------------ Buttons ------------ */
    .button            { -fx-background-color:#0e639c; -fx-background-radius:3; }
    .button:hover      { -fx-background-color:#1174b5; }
    .button:focused    { -fx-focus-color:transparent; -fx-faint-focus-color:transparent; }

    /* ------------ TextField ------------ */
    .text-field {
        -fx-background-color:#2b2b2b;
        -fx-text-fill:#d4d4d4;
        -fx-prompt-text-fill:#7d7d7d;
        -fx-highlight-fill:#094771;
        -fx-border-color:#3d3d3d;
        -fx-background-radius:3;
    }

    /* ------------ CheckBox ------------ */
    .check-box > .box              { -fx-background-color:#2b2b2b; -fx-border-color:#5a5a5a; }
    .check-box:selected > .box     { -fx-background-color:#0e639c; }

    /* ------------ TableView ------------ */
    .table-view, .table-row-cell {
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

    /* ------------ ScrollBars ------------ */
    .scroll-bar > .track                    { -fx-background-color:#1e1e1e; }
    .scroll-bar > .increment-button,
    .scroll-bar > .decrement-button         { -fx-background-color:#1e1e1e; }
    .scroll-bar > .thumb                    { -fx-background-color:#3d3d3d; }
    .scroll-bar > .thumb:hover              { -fx-background-color:#5a5a5a; }

    /* ------------ DatePicker – ciemny popup ------------ */
    .date-picker-popup,
    .date-picker-popup > .month-year-pane,
    .date-picker-popup > .calendar-grid {
        -fx-background-color:#252526;
    }
    .date-picker-popup > .month-year-pane > .spinner ,
    .date-picker-popup > .calendar-grid > .day-name-cell ,
    .date-picker-popup > .calendar-grid > .day-cell {
        -fx-background-color:#252526;
        -fx-text-fill:#d4d4d4;
    }
    .date-picker-popup .spinner .arrow-button  { -fx-background-color:#1e1e1e; }
    .date-picker-popup .spinner .arrow         { -fx-background-color:transparent; }
    .date-picker-popup .day-cell:hover         { -fx-background-color:#333333; }
    .date-picker-popup .day-cell:focused       { -fx-background-color:#094771; }
    .date-picker-popup .today                  { -fx-border-color:#0e639c; }
    """.trimIndent()

        /* zamiana na data:URI */
        return "data:text/css," +
                css.replace(" ", "%20")
                    .replace("\n", "%0A")
    }

}

/* ---------- entry point ---------- */
fun main() = Application.launch(FileOrganizerApp::class.java)
