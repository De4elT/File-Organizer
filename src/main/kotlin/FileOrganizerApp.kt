import javafx.application.Application
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.CheckBoxTableCell
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/* ---------------- UTILITIES ---------------- */
private val df = DecimalFormat("#,##0.##")
private val timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
fun fmtTime(epochMs: Long) = timeFmt.format(Instant.ofEpochMilli(epochMs))
fun fileCreation(f: File): Long = try { Files.readAttributes(f.toPath(), BasicFileAttributes::class.java).creationTime().toMillis() } catch(_:Exception){ f.lastModified() }
fun sizeStr(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1_048_576 -> "${df.format(b/1024.0)} KB"
    b < 1_073_741_824 -> "${df.format(b/1_048_576.0)} MB"
    else -> "${df.format(b/1_073_741_824.0)} GB"
}

/* ---------------- APP ---------------- */
class FileOrganizerApp : Application() {
    data class FileItem(val file: File, val selected: SimpleBooleanProperty) {
        val name get() = file.name
        val size get() = file.length()
        val modified get() = fmtTime(file.lastModified())
        val created get() = fmtTime(fileCreation(file))
    }

    private val items = FXCollections.observableArrayList<FileItem>()
    private val table = TableView<FileItem>()
    private val summaryLbl = Label("No files selected")
    private val keepCheck = CheckBox("Keep original files (copy instead of move)")
    private val organizeBtn = Button("Organize selected")

    override fun start(stage: Stage) {
        val chooseBtn = Button("Choose folder").apply {
            setOnAction { DirectoryChooser().apply { title = "Choose folder to organize" }.showDialog(stage)?.let { loadFiles(it) } }
        }
        organizeBtn.isDisable = true
        organizeBtn.setOnAction {
            val selected = items.filter { it.selected.get() }.map(FileItem::file)
            val log = organize(selected, keepCheck.isSelected)
            Alert(Alert.AlertType.INFORMATION, log.joinToString("\n"), ButtonType.OK).showAndWait()
        }
        buildColumns()
        table.items = items; table.isEditable = true
        items.addListener(ListChangeListener { refreshSummary() })

        val root = VBox(10.0, chooseBtn, table, keepCheck, summaryLbl, organizeBtn).apply { padding = Insets(15.0) }
        stage.scene = Scene(root, 900.0, 650.0).also { it.stylesheets += darkCss() }
        stage.title = "File Organizer"; stage.show()
    }

    private fun buildColumns() {
        val headerCB = CheckBox().apply { setOnAction { items.forEach { it.selected.set(isSelected) } } }
        val selCol = TableColumn<FileItem, Boolean>("✔").apply {
            graphic = headerCB; setCellValueFactory { it.value.selected }; cellFactory = CheckBoxTableCell.forTableColumn(this); prefWidth = 50.0; isEditable = true
        }
        val nameCol = TableColumn<FileItem, String>("File name").apply { setCellValueFactory { javafx.beans.property.SimpleStringProperty(it.value.name) }; prefWidth = 300.0 }
        val sizeCol = TableColumn<FileItem, String>("Size").apply { setCellValueFactory { javafx.beans.property.SimpleStringProperty(sizeStr(it.value.size)) }; prefWidth = 110.0 }
        val createdCol = TableColumn<FileItem, String>("Created").apply { setCellValueFactory { javafx.beans.property.SimpleStringProperty(it.value.created) }; prefWidth = 170.0 }
        val modCol = TableColumn<FileItem, String>("Last modified").apply { setCellValueFactory { javafx.beans.property.SimpleStringProperty(it.value.modified) }; prefWidth = 170.0 }
        table.columns.setAll(selCol, nameCol, sizeCol, createdCol, modCol)
        selCol.setCellFactory { CheckBoxTableCell<FileItem, Boolean>().apply { setSelectedStateCallback { idx -> items[idx].selected } } }
    }

    private fun loadFiles(dir: File) {
        val processed = processedAlready(dir)
        items.setAll(dir.listFiles()?.filter(File::isFile)?.map { f -> FileItem(f, SimpleBooleanProperty(!processed.contains(f.name))).also { it.selected.addListener { _,_,_ -> refreshSummary() } } } ?: emptyList())
        organizeBtn.isDisable = items.isEmpty(); refreshSummary()
    }
    private fun processedAlready(base: File): Set<String> {
        val d = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val rep = File(base, "$d/organizer_report-$d.txt")
        return if (rep.exists()) rep.readLines().mapNotNull { it.substringAfter(": ").substringBefore(" →","") }.toSet() else emptySet()
    }
    private fun refreshSummary() { val sel = items.filter { it.selected.get() }; summaryLbl.text = "Selected: ${sel.size}, ${sizeStr(sel.sumOf { it.size })}" }

    private fun organize(files: List<File>, keep: Boolean): List<String> {
        if (files.isEmpty()) return listOf("No files.")
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        val destDir = File(files.first().parent, today)
        val cats = mapOf(
            "documents/pdf" to listOf("pdf","doc","docx","txt"),
            "images/gimp"   to listOf("xcf"),
            "images/photos" to listOf("jpg","jpeg","png","webp"),
            "presentations/ppt" to listOf("ppt","pptx","odp"),
            "archives/all"  to listOf("zip","rar","7z","tar","gz"),
            "exe"           to listOf("exe","msi")
        )
        val log = mutableListOf<String>()
        for (f in files) {
            val rel = cats.entries.find { f.extension.lowercase() in it.value }?.key ?: "others"
            val tgtDir = File(destDir, rel).apply { mkdirs() }
            val tgt = File(tgtDir, f.name)
            try {
                if (keep) Files.copy(f.toPath(), tgt.toPath(), StandardCopyOption.REPLACE_EXISTING) else Files.move(f.toPath(), tgt.toPath(), StandardCopyOption.REPLACE_EXISTING)
                log += "✔ ${if (keep) "Copied" else "Moved"}: ${f.name} → ${tgt.path}"
            } catch (e: Exception) { log += "✘ Error: ${f.name} – ${e.message}" }
        }
        File(destDir, "organizer_report-$today.txt").appendText("\n" + log.joinToString("\n"))
        return log
    }

    private fun darkCss() = "data:text/css," + """
        root { -fx-background:#1e1e1e; }
        label, table-view, table-row-cell, table-column-header, check-box, button { -fx-text-fill:#d4d4d4; -fx-font-family:'Segoe UI',sans-serif; }
        table-view, table-row-cell { -fx-control-inner-background:#252526; -fx-background:#1e1e1e; }
        table-column-header { -fx-background-color:#1e1e1e; }
        button { -fx-background-color:#0e639c; -fx-text-fill:white; }
    """.trimIndent().replace("\n","%0A")
}

fun main() = Application.launch(FileOrganizerApp::class.java)
