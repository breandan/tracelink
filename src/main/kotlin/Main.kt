import org.apache.commons.vfs2.AllFileSelector
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.NameScope
import org.apache.commons.vfs2.VFS
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.streams.asSequence

data class Link(val from: String, val to: String, val text: String) {
    constructor(
        line: String, parsed: Array<String> = line.split(",")
            .map { it.trim().replace("\"", "") }.toTypedArray()
    ) : this(parsed[1].toLongPath(), parsed[2].toLongPath(), parsed[0])

    override fun toString(): String =
        "(${from.substring(59).take(20)}..${from.takeLast(20)}, " +
                "${to.substring(59).take(20)}..${to.takeLast(20)}, $text)"
}

val regex = Regex("(.{0,100})<a.*href=\"([^<>:]*?)\"[^<>]*>([!-;?-~]{5,})</a>(.{0,100})")
val archivesDir: String = "/home/breandan/PycharmProjects/zealds/archives/"

fun extractLinks() {
    println("text, source, target")
    File(archivesDir).listFiles()?.toList()?.parallelStream()?.map { file ->
        VFS.getManager().resolveFile("tgz:${file.absolutePath}")
            .findFiles(AllFileSelector())
            .asSequence()
            .filter { it.name.toString().let { name -> ".html" in name || ".htm" in name } }
            .map { it.getFilexLine() }
            .flatten()
            .map { (file, line) -> getAllLinks(line, file) }
            .flatten()
            .forEach { println("\"${it.text}\", \"${it.from.compact()}\", \"${it.to.compact()}\"") }
    }?.forEach { }
}

private fun getAllLinks(line: String, relativeToFile: FileObject) =
    regex.findAll(line).mapIndexedNotNull { idx, result ->
        result.destructured.run {
            try {
                val resolvedLink = relativeToFile.parent.resolveFile(component2())
                if (!resolvedLink.exists()) null else
                    Link(from = relativeToFile.toString(),
                        to = relativeToFile.parent.resolveFile(component2()).also { it.exists() }.toString(),
                        text = component3().let { if (it.contains("<")) Jsoup.parse(it).text() else it })
            } catch (e: Exception) {
                null
            }
        }
    }

private fun FileObject.allText() =
    Parser.parse(content.inputStream.bufferedReader(UTF_8).lines().asSequence().joinToString(""), "").text()

private fun FileObject.getFilexLine() =
    content.inputStream.bufferedReader(UTF_8).lineSequence().map { line -> Pair(this, line) }

fun String.compact(prefixLength: Int = archivesDir.length + 11) =
    substring(prefixLength).run { substringBefore(".tgz") + "::" + substringAfter("Contents/Resources/Documents/") }

// Needed because we botched the path serialization on the first pass (should be fixed now)
val baseNameToDocset = mutableMapOf<String, String>()

fun String.toLongPath() = "tgz:file://$archivesDir${split("::").let {
    val docset = baseNameToDocset.getOrPut(it.first()) {
        VFS.getManager().resolveFile("tgz:file://$archivesDir" + it.first() + ".tgz")
            .children.first { it.name.extension == "docset" }.name.baseName
    }
    it.first() + ".tgz!/" +
            if (it.last().contains(".tgz!/")) it.last().substringAfter(".tgz!/")
            else ("$docset/Contents/Resources/Documents/" + it.last())
}
}"

fun File.readLinks() = readLines().drop(3).parallelStream().map { Link(it) }

private fun Link.readDestination() =
    try {
        VFS.getManager().resolveFile(to.substringBeforeLast("%")).allText()
    } catch (e: Exception) {
        val path = to.substringAfterLast("/").substringBeforeLast("%")
        val t = VFS.getManager().resolveFile(from.substringBeforeLast("/"))
        try {
            val alternateDest = t.resolveFile(path, NameScope.CHILD)
            alternateDest.allText()
        } catch (e: Exception) { null }
    }

fun main() {
//    extractLinks()
    File("results.csv").readLinks().map { it.readDestination() }.forEach { it?.run { println(this) } }
}