import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun main() {
    println("Podaj ścieżkę do pierwotnego folderu (np. C:\\Users\\Dominik\\Downloads):")
    val srcPath = readLine()?.trim()?.ifEmpty { null }
        ?: return println("❌  Nie podano ścieżki – kończę.")

    // domyślnie dzisiejsza data; jeśli chcesz ręcznie – wpisz ją zamiast
    val dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    val rootDir   = File(srcPath)
    val datedDir  = File(rootDir, dateFolder)

    if (!datedDir.exists() || !datedDir.isDirectory) {
        return println("❌  Folder z datą \"$dateFolder\" nie istnieje w $srcPath.")
    }

    val logs = mutableListOf<String>()

    datedDir.walkBottomUp()               // najpierw głębokie pliki, potem puste katalogi
        .forEach { f ->
            if (f.isFile) {
                val target = File(rootDir, f.name)
                try {
                    Files.move(f.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING)
                    logs += "✔  ${f.path}  →  ${target.path}"
                } catch (e: Exception) {
                    logs += "✘  ${f.path}  –  ${e.message}"
                }
            } else if (f.isDirectory && f.listFiles()?.isEmpty() == true) {
                // usuń puste katalogi
                f.delete()
            }
        }

    println("\n===== RAPORT COFANIA =====")
    logs.forEach(::println)
    println("Gotowe – przywrócono ${logs.count { it.startsWith("✔") }} plików.")
}
