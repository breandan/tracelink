import org.apache.commons.vfs2.AllFileSelector
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.streams.asSequence

val dlm = "\"\"\""

data class Link(
    val text: String,
    val context: String,
    val from: String,
    val to: String,
    val linkFragment: String
) {
    constructor(
        line: String, parsed: Array<String> = line.split("$dlm, $dlm")
            .map { it.trim().replace(dlm, "") }.toTypedArray()
    ) : this(parsed[0], parsed[1], parsed[2].toFullPath(), parsed[3].toFullPath(), parsed[4])

    override fun toString(): String =
        "$dlm${text}$dlm, $dlm${context}$dlm, $dlm${from.compact()}$dlm, $dlm${to.compact()}$dlm, $dlm${linkFragment}$dlm"
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

val linkRegex = Regex("<a[^<>]*href=\"([^<>#:]*?)(#[^\"]*)?\"[^<>]*>([!-;?-~]{6,})</a>")
val asciiRegex = Regex("[ -~]*")

private fun getLinksInLine(line: String, relativeToFile: FileObject) =
    linkRegex.findAll(line).mapIndexedNotNull { idx, result ->
        result.destructured.run {
            try {
                val resolvedLink = relativeToFile.parent.resolveFile(component1())
                val linkText = Parser.parse(component3(), "")!!.wholeText()!!
                val context = Parser.parse(line, "")!!.wholeText()!!
                val fragment = component2()
                val indexOfLinkText = context.indexOf(linkText)
                val preText = context.substring((indexOfLinkText - 120).coerceAtLeast(0), indexOfLinkText)
                val subText = context.substring(indexOfLinkText + linkText.length, (indexOfLinkText + linkText.length + 120).coerceAtMost(context.length))
                if (resolvedLink.exists() && context.length > (linkText.length + 8) && context.matches(asciiRegex))
                    Link(from = relativeToFile.toString(),
                        to = resolvedLink.toString(),
                        linkFragment = fragment,
                        text = linkText,
                        context = "$preText <<LNK>> $subText"
                    )
                else null
            } catch (e: Exception) {
                null
            }
        }
    }

private fun FileObject.parseHtml(uri: String) =
    Parser.parse(content.inputStream.bufferedReader(UTF_8).lines().asSequence().joinToString(""), uri)

private fun FileObject.getFilexLine() =
    content.inputStream.bufferedReader(UTF_8).lineSequence().map { line -> Pair(this, line) }

fun String.compact(prefixLength: Int = archivesDir.length + 11) = substring(prefixLength)

fun String.toFullPath() = "tgz:file://$archivesDir${this.substringBeforeLast("%")}"

fun File.readLinks() = readLines().drop(5).parallelStream().map { Link(it) }

private fun Link.readDestination() =
    VFS.getManager().resolveFile(to).parseHtml("$to$linkFragment")

fun parseLinks(file: String) = File(file).readLinks()
    .map { try { it.readDestination() } catch (ex: Exception) { null } }
    .filter { it != null }

fun main() {
    printLinks()
}