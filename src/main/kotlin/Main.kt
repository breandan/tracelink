import org.apache.commons.vfs2.AllFileSelector
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.NameScope
import org.apache.commons.vfs2.VFS
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.streams.asSequence

val dlm = "\"\"\""

data class Link(val text: String, val context: String, val from: String, val to: String) {
    constructor(
        line: String, parsed: Array<String> = line.split(",")
            .map { it.trim().replace(dlm, "") }.toTypedArray()
    ) : this(parsed[0], parsed[1], parsed[2], parsed[3])

    override fun toString(): String =
        "$dlm${text}$dlm, $dlm${context}$dlm, $dlm${from.compact()}$dlm, $dlm${to.compact()}$dlm"
}

val archivesDir: String = "/home/breandan/PycharmProjects/zealds/archives/"

fun printLinks() {
    println("link_text, context, source_document, target_document")
    File(archivesDir).listFiles()?.toList()?.parallelStream()?.forEach { file ->
        try {
            fetchLinks(file).forEach { link -> println(link) }
        } catch (e: Exception) {
            System.err.println("Error reading $file")
            e.printStackTrace()
        }
    }
}

private fun fetchLinks(file: File) =
    VFS.getManager().resolveFile("tgz:${file.absolutePath}")
        .findFiles(AllFileSelector())
        .asSequence()
        .filter { it.name.extension.toString().let { ext -> ext == "htm" || ext == "html" } }
        .map { it.getFilexLine() }
        .flatten()
        .map { (file, line) -> getLinksInLine(line, file) }
        .flatten()

val linkRegex = Regex("<a[^<>]*href=\"([^<>:]*?)\"[^<>]*>([!-;?-~]{6,})</a>")
val asciiRegex = Regex("[ -~]*")

private fun getLinksInLine(line: String, relativeToFile: FileObject) =
    linkRegex.findAll(line).mapIndexedNotNull { idx, result ->
        result.destructured.run {
            try {
                val resolvedLink = relativeToFile.parent.resolveFile(component1())
                val linkText = Parser.parse(component2(), "")!!.text()!!
                val context = Parser.parse(line, "")!!.text()!!
                val indexOfLinkText = context.indexOf(linkText)
                val preText = context.substring((indexOfLinkText - 120).coerceAtLeast(0), indexOfLinkText)
                val subText = context.substring(indexOfLinkText + linkText.length, (indexOfLinkText + linkText.length + 120).coerceAtMost(context.length))
                if (resolvedLink.exists() && context.length > (linkText.length + 8) && context.matches(asciiRegex))
                    Link(from = relativeToFile.toString(),
                        to = resolvedLink.toString(),
                        text = linkText,
                        context = "$preText <<LNK>> $subText"
                    )
                else null
            } catch (e: Exception) {
                System.err.println("Error parsing link in line: $line")
                System.err.println("${Parser.parse(line, "")!!.text()!!}")
                e.printStackTrace()
                null
            }
        }
    }

private fun FileObject.allText() =
    Parser.parse(content.inputStream.bufferedReader(UTF_8).lines().asSequence().joinToString(""), "").text()

private fun FileObject.getFilexLine() =
    content.inputStream.bufferedReader(UTF_8).lineSequence().map { line -> Pair(this, line) }

fun String.compact(prefixLength: Int = archivesDir.length + 11) = substring(prefixLength)

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
    printLinks()
//    File("results.csv").readLinks().map { it.readDestination() }.forEach { it?.run { println(this) } }
}