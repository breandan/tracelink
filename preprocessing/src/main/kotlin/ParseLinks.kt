import me.xdrop.fuzzywuzzy.FuzzySearch
import org.apache.commons.vfs2.AllFileSelector
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import kotlin.streams.asStream

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
    val query: String,         // Anchor text of link itself
    val context: String,       // Surrounding text on the same line
    val fuzzyHits: String,     // Hits and surrounding context in target doc
    val fromUri: String,       // Original document location
    val toUri: String,         // Target document location
    val linkFragment: String   // Link fragment (indicating subsection)
) {
    constructor(
        line: String, parsed: Array<String> = line.split("$dlm, $dlm")
            .map { it.trim().replace(dlm, "") }.toTypedArray()
    ) : this(parsed[0], parsed[1], parsed[2], parsed[3].toFullPath(), parsed[4].toFullPath(), parsed[5])

    fun String.compact(prefixLength: Int = archivesAbs.length + 11) = substring(prefixLength)

    override fun toString(): String =
        "$dlm${query}$dlm, $dlm${context}$dlm, $dlm${fuzzyHits}$dlm, $dlm${fromUri.compact()}$dlm, $dlm${toUri.compact()}$dlm, $dlm${linkFragment}$dlm"

    override fun hashCode() = (query + context + fuzzyHits + toUri + linkFragment).hashCode()
}

val archivesDir: String = "archives/" // Parent directory (assumed to contain `.tgz` files)
val archivesAbs: String = File(archivesDir).absolutePath

/**
 * Extracts documents from archives in parallel and prints the links in CSV format.
 */

fun printLinks() {
    println("link_text, context, target_context, source_document, target_document, link_fragment")
    File(archivesDir).listFiles()?.toList()?.parallelStream()
        ?.forEach { archive ->
            try {
                fetchLinks(archive)?.forEach { htmlLinkStream ->
                    htmlLinkStream.forEach { linkStream ->
                        linkStream.forEach { link ->
                            val hash = link.hashCode()
                            if (link != null && hash !in previouslySeen) {
                                previouslySeen.add(hash)
                                println(link)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
//                System.err.println("Error reading $archive")
//                e.printStackTrace()
            }
            System.err.println("Finished reading $archive")
        }
}

private fun FileObject.getLinksInFile() =
    content.inputStream.bufferedReader(UTF_8).lines().map { line -> line.getAllLinks(relativeTo = this) }

/**
 * Streams the contents of HTML files and returns all links contained within each document.
 */

private fun fetchLinks(archive: File) =
    archive.getHtmlFiles()?.map { it.getLinksInFile() }

fun File.getHtmlFiles() =
    try {
        VFS.getManager().resolveFile("tgz:${absolutePath}")
            .findFiles(AllFileSelector()).asList().stream()
            .filter { it.name.extension.toString().let { ext -> ext == "htm" || ext == "html" } }
    } catch (ex: Exception) {
        null
    }

//                                      LINK URI       FRAGMENT              ANCHOR TEXT
val linkRegex = Regex("<a[^<>]*href=\"([^<>#:?\"]*?)(#[^<>#:?\"]*)?\"[^<>]*>([!-;?-~]{6,})</a>")
val asciiRegex = Regex("[ -~]*")
val window = 3
val minCtx = 5
val previouslySeen = ConcurrentHashMap.newKeySet<Int>()

/**
 * Returns all HTML links within a string whose anchor text is shorter than the string
 * by a fixed margin. This catches links which have surrounding context in documentation
 * containing a mixture of natural language and source code. Links are resolved relative
 * to a document path, and validated so that all links returned point to a valid URL.
 */

private fun String.getAllLinks(relativeTo: FileObject): Stream<Link> =
    linkRegex.findAll(this).asStream().map { result ->
        result.destructured.let { regexGroups ->
            try {
                val targetUri = regexGroups.component1().let {
                    // Support self-links to the same page where link occurs
                    if (it.isEmpty()) relativeTo.name.baseName else it
                }
                val resolvedLink = relativeTo.parent.resolveFile(targetUri)
                val linkText = Parser.parse(regexGroups.component3(), "")!!.text()!!
                val context = Parser.parse(this, "")!!.text()!!
                val fragment = regexGroups.component2()
                val indexOfLinkText = context.indexOf(linkText)
                val startIdx = (indexOfLinkText - window / 2).coerceAtLeast(0)
                val endIdx = (indexOfLinkText + linkText.length + window / 2).coerceAtMost(context.length)
                val preText = context.substring(startIdx, indexOfLinkText)
                val subText = context.substring(indexOfLinkText + linkText.length, endIdx)
                val targetText = resolvedLink.asHtmlDoc(resolvedLink.url.path)?.text() ?: ""
//                            .also {
//                            println("${regexGroups.component1()}, ${regexGroups.component2()}, ${regexGroups.component3()}, ${relativeTo} ... ${this@getAllLinks}")
//                            println("${regexGroups.component1()} has files in dir: ${resolvedLink.getChildren().map {
//                        it.getName().baseName
//                    }.joinToString(", ")} ") }

                if (targetText.isNotEmpty() && context.length > (linkText.length + minCtx) && context.matches(asciiRegex)) {
                    val lines = targetText.split("\n")
                    val hitList = lines.filterForQuery(linkText)
                    val matches = hitList.joinToString(" … ")
                    if (matches.isNotEmpty())
                        Link(
                            fromUri = relativeTo.toString(),
                            toUri = resolvedLink.toString(),
                            linkFragment = fragment,
                            query = linkText,
                            context = "$preText <<LNK>> $subText",
                            fuzzyHits = matches
                        ) else null
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

fun List<String>.filterForQuery(query: String): List<String> =
    FuzzySearch.extractTop(query, this, 30, 60).map { it.string }

fun main() {
    printLinks()
}