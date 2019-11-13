
import org.apache.commons.vfs2.AllFileSelector
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8

/**
 * Intended to be called from the command line, e.g. `./gradlew run > file.csv`
 */

val dlm = "\"\"\""

fun String.toFullPath() = "tgz:file://$archivesDir${this.substringBeforeLast("%")}"

/**
 * Class representing an HTML link which the document's author saw fit to reference
 * another page in the same doc set. The following features are the minimal set of
 * features which are needed to perform link prediction.
 */

data class Link(
    val text: String,        // Anchor text of link itself
    val context: String,     // Surrounding text on the same line
    val from: String,        // Original document location
    val to: String,          // Target document location
    val linkFragment: String // Link fragment (indicating subsection)
) {
    constructor(
        line: String, parsed: Array<String> = line.split("$dlm, $dlm")
            .map { it.trim().replace(dlm, "") }.toTypedArray()
    ) : this(parsed[0], parsed[1], parsed[2].toFullPath(), parsed[3].toFullPath(), parsed[4])

    fun String.compact(prefixLength: Int = archivesDir.length + 11) = substring(prefixLength)

    override fun toString(): String =
        "$dlm${text}$dlm, $dlm${context}$dlm, $dlm${from.compact()}$dlm, $dlm${to.compact()}$dlm, $dlm${linkFragment}$dlm"
}

val archivesDir: String = "archives/" // Parent directory (assumed to contain `.tgz` files)

/**
 * Extracts documents from archives in parallel and prints the links in CSV format.
 */

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

private fun FileObject.getFilexLine() =
    content.inputStream.bufferedReader(UTF_8).lineSequence().map { line -> Pair(this, line) }

/**
 * Streams the contents of HTML files and returns all links contained within each document.
 */

private fun fetchLinks(file: File) =
    VFS.getManager().resolveFile("tgz:${file.absolutePath}")
        .findFiles(AllFileSelector())
        .asSequence()
        .filter { it.name.extension.toString().let { ext -> ext == "htm" || ext == "html" } }
        .map { it.getFilexLine() }
        .flatten()
        .map { (file, line) -> line.getAllLinks(relativeTo = file) }
        .flatten()

//                                     LINK URI   FRAGMENT           ANCHOR TEXT
val linkRegex = Regex("<a[^<>]*href=\"([^<>#:]*?)(#[^\"]*)?\"[^<>]*>([!-;?-~]{6,})</a>")
val asciiRegex = Regex("[ -~]*")

/**
 * Returns all HTML links within a string whose anchor text is shorter than the string
 * by a fixed margin. This catches links which have surrounding context in documentation
 * containing a mixture of natural language and source code. Links are resolved relative
 * to a document path, and validated so that all links returned point to a valid URL.
 */

private fun String.getAllLinks(relativeTo: FileObject): Sequence<Link> =
    linkRegex.findAll(this).mapIndexedNotNull { idx, result ->
        result.destructured.let { regexGroups ->
            try {
                val resolvedLink = relativeTo.parent.resolveFile(regexGroups.component1())
                val linkText = Parser.parse(regexGroups.component3(), "")!!.wholeText()!!
                val context = Parser.parse(this, "")!!.wholeText()!!
                val fragment = regexGroups.component2()
                val indexOfLinkText = context.indexOf(linkText)
                val preText = context.substring((indexOfLinkText - 120).coerceAtLeast(0), indexOfLinkText)
                val subText = context.substring(indexOfLinkText + linkText.length, (indexOfLinkText + linkText.length + 120).coerceAtMost(context.length))
                if (resolvedLink.exists() && context.length > (linkText.length + 8) && context.matches(asciiRegex))
                    Link(from = relativeTo.toString(),
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

fun main() {
    printLinks()
}