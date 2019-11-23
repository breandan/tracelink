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
        line: String, parsed: Array<String> = line.split("\t")
            .map { it.trim() }.toTypedArray()
    ) : this(parsed[0], parsed[1], parsed[2], parsed[3].toFullPath(), parsed[4].toFullPath(), parsed[5])

    private fun String.compact(prefixLength: Int = archivesAbs.length + 11) = substring(prefixLength)

    override fun toString(): String =
        "${query.noTabs()}\t${context.noTabs()}\t${fuzzyHits.noTabs()}\t${fromUri.compact()}\t${toUri.compact()}\t${linkFragment}"

    override fun hashCode() = (query + context + fuzzyHits + toUri + linkFragment).hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Link

        if (query != other.query) return false
        if (context != other.context) return false
        if (fuzzyHits != other.fuzzyHits) return false
        if (toUri != other.toUri) return false
        if (linkFragment != other.linkFragment) return false

        return true
    }
}

fun String.noTabs() = this.replace("\t", "  ")

val archivesDir: String = "archives/" // Parent directory (assumed to contain `.tgz` files)
val archivesAbs: String = File(archivesDir).absolutePath

/**
 * Extracts documents from archives in parallel and prints the links in CSV format.
 */

fun printLinks() {
    println("link_text\tcontext\ttarget_context\tsource_document\ttarget_document\tlink_fragment")
    File(archivesDir).listFiles()?.toList()?.sortedBy { it.name }?.parallelStream()
        ?.forEach { archive ->
            try {
                fetchLinks(archive)?.forEach { htmlLinkStream: Stream<Stream<Link?>?>? ->
                    try {
                        htmlLinkStream?.forEach { linkStream: Stream<Link?>? ->
                            try {
                                linkStream?.forEach { link: Link? ->
                                    if (link != null) {
                                        val hash = link.hashCode()
                                        if (hash !in previouslySeenLinks) {
                                            previouslySeenLinks.add(hash)
                                            println(link)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
//                                System.err.println("Error reading stream $e")
                            }
                        }
                    } catch (e: Exception) {
//                        System.err.println("Error reading linkStream $e")
                    }
                }
            } catch (e: Exception) {
//                System.err.println("Error reading $archive: $e")
            }
            System.err.println("Finished reading $archive")
        }
}

/**
 * Streams the contents of HTML files and returns all links contained within each document.
 */

private fun fetchLinks(archive: File): Stream<Stream<Stream<Link?>?>?>? =
    archive.getHtmlFiles()?.map { file ->
        file.content.inputStream.bufferedReader(UTF_8).lines().map { line ->
            line.getAllLinks(relativeTo = file)
        }
    }

fun File.getHtmlFiles(): Stream<FileObject>? =
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
val window = 240
val minCtx = 5
val previouslySeenLinks = ConcurrentHashMap.newKeySet<Int>()

/**
 * Returns all HTML links within a string whose anchor text is shorter than the string
 * by a fixed margin. This catches links which have surrounding context in documentation
 * containing a mixture of natural language and source code. Links are resolved relative
 * to a document path, and validated so that all links returned point to a valid URL.
 */

private fun String.getAllLinks(relativeTo: FileObject): Stream<Link?>? =
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
                if (context.length > (linkText.length + minCtx) && context.matches(asciiRegex)) {
                    val fragment = regexGroups.component2()
                    val indexOfLinkText = context.indexOf(linkText)
                    val startIdx = (indexOfLinkText - window / 2).coerceAtLeast(0)
                    val endIdx = (indexOfLinkText + linkText.length + window / 2).coerceAtMost(context.length)
                    val preText = context.substring(startIdx, indexOfLinkText)
                    val subText = context.substring(indexOfLinkText + linkText.length, endIdx)
                    val targetDocText = resolvedLink.asHtmlDoc(resolvedLink.url.path)?.text() ?: ""

                    if (targetDocText.isNotEmpty()) {
                        val matches = targetDocText.extractHits(linkText, true)
                        if (matches.isNotEmpty())
                            Link(
                                fromUri = relativeTo.toString(),
                                toUri = resolvedLink.toString(),
                                linkFragment = fragment,
                                query = linkText,
                                context = "$preText <<LNK>> $subText",
                                fuzzyHits = matches
                            )
                        else null
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

private fun String.extractHits(query: String, exact: Boolean, bufferLen: Int = window): String =
    split("\n").run {
        if (exact)
            map { line ->
                Regex(Regex.escape(query)).findAll(line).map { mr ->
                    line.substring(
                        (mr.range.first - bufferLen).coerceAtLeast(0),
                        (mr.range.last + bufferLen).coerceAtMost(line.length)
                    ).trim()
                }
            }.asSequence().flatten().take(30)
        else FuzzySearch.extractTop(query, this, 30, 60).map { it.string.trim() }.asSequence()
    }.joinToString(" … ")


fun main() = printLinks()