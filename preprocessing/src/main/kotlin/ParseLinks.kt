import me.xdrop.fuzzywuzzy.FuzzySearch
import org.apache.commons.vfs2.AllFileSelector
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream
import kotlin.math.abs
import kotlin.streams.asStream

/**
 * Intended to be called from the command line, e.g. `./gradlew run > file.csv`
 */

/**
 * Class representing an HTML link which the document's author saw fit to reference
 * another page in the same doc set. The following features are the minimal set of
 * features which are needed to perform link prediction.
 */

data class Link(
    val linkText: String,            // Anchor text of link itself
    val sourceTitle: String,         // Title of the source document
    val targetTitle: String,         // Title of the target document
    val sourcePretext: String,       // Preceeding text on the same line
    val sourceSubtext: String,       // Proceeding text on the same line
    val targetContext: List<String>, // Hits and surrounding context in target doc
    val sourceUri: String,           // Original document location
    val targetUri: String,           // Target document location
    val targetFragment: String       // Link fragment (indicating subsection)
) {
    constructor(
        line: String, parsed: Array<String> = line.split("\t")
            .map { it.trim() }.toTypedArray()
    ) : this(
        parsed[0].normalize(),
        parsed[1].trim(),
        parsed[2].trim(),
        parsed[3].split(" <<LNK>> ").first().trim(),
        parsed[3].split(" <<LNK>> ").last().trim(),
        parsed[4].split(" … ").map { it.trim() },
        parsed[5].toFullPath(),
        parsed[6].toFullPath(),
        parsed[7]
    )

    private fun String.compact(prefixLength: Int = archivesAbs.length + 11) = substring(prefixLength)

    override fun toString(): String =
        if (PRETTY_PRINT) {
            linkText.noTabs().prettyText() + "\t" +
                    sourceTitle.noTabs().prettyTitle() + "\t" +
                    targetTitle.noTabs().prettyTitle() + "\t" +
                    sourcePretext.noTabs().prettyPretext() + " <<LNK>> " + sourceSubtext.noTabs().prettySubtext() + "\t" +
                    targetContext.joinToString(" … ") { prettyHit(it.replace("…", "...")) } + "\t" +
                    sourceUri.compact() + "\t" +
                    targetUri.compact() + "\t" +
                    targetFragment
        } else {
            linkText.noTabs() + "\t" +
                    sourceTitle.noTabs() + "\t" +
                    targetTitle.noTabs() + "\t" +
                    sourcePretext.noTabs() + " <<LNK>> " + sourceSubtext.noTabs() + "\t" +
                    targetContext.joinToString(" … ") { it.replace("…", "...").noTabs() } + "\t" +
                    sourceUri.compact() + "\t" +
                    targetUri.compact() + "\t" +
                    targetFragment
        }

    override fun hashCode() =
        (linkText + sourcePretext + sourceSubtext + targetContext + targetUri + targetFragment).hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Link

        if (linkText != other.linkText) return false
        if (sourcePretext != other.sourcePretext) return false
        if (sourceSubtext != other.sourceSubtext) return false
        if (targetContext != other.targetContext) return false
        if (targetUri != other.targetUri) return false
        if (targetFragment != other.targetFragment) return false

        return true
    }

    // No need to trim since all link texts are regex-matched to be less than MAX_LTEXT_LEN
    private fun String.prettyText() = padEnd(MAX_LTEXT_LEN, ' ')

    private fun String.prettyTitle() =
        let { if (MAX_TITLE_LEN < it.length) it.substring(0, MAX_TITLE_LEN) else it.padEnd(MAX_TITLE_LEN, ' ') }

    private fun String.prettyPretext() =
        let { if (MAX_CONTS_LEN < length) it.takeLast(MAX_CONTS_LEN) else it.padStart(MAX_CONTS_LEN, ' ') }

    private fun String.prettySubtext() =
        let { if (MAX_CONTS_LEN < length) it.substring(0, MAX_CONTS_LEN) else it.padEnd(MAX_CONTS_LEN, ' ') }

    private fun prettyHit(it: String) =
        it.split(" <<HIT>> ").let { it.first().trim().prettyPretext() + " <<HIT>> " + it.last().trim().prettySubtext() }
}

val MAX_LTEXT_LEN = 50
val MAX_TITLE_LEN = 100
val MAX_CONTS_LEN = 120
var PRETTY_PRINT = false

fun String.toFullPath() = "tgz:file://$archivesDir${this.substringBeforeLast("%")}"

fun String.noTabs() = this.replace("\t", "  ")

val archivesDir: String = "python/" // Parent directory (assumed to contain `.tgz` files)
val archivesAbs: String = File(archivesDir).absolutePath

/**
 * Extracts documents from archives in parallel and prints the links in CSV format.
 */

fun printLinks() {
    println(
        "link_text\t" +
                "source_title\t" +
                "target_title\t" +
                "source_context\t" +
                "target_context\t" +
                "source_document\t" +
                "target_document\t" +
                "link_fragment"
    )

    File(archivesDir).listFiles()?.toList()?.parallelStream()
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
 * Streams the contents of HTML files and returns all links contained within each
 * document. Assumes that links and surrounding context are grouped on a per-line
 * basis. Usually works for links that are embedded in natural language, but will
 * not catch links whose surrounding context spans more than a single line.
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
val linkRegex = Regex("<a[^<>]*href=\"([^<>#:?\"]*?)(#[^<>#:?\"]*)?\"[^<>]*>([!-;?-~]{6,$MAX_LTEXT_LEN})</a>")
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
                val linkText = Parser.parse(regexGroups.component3(), "").text().normalize()
                val context = Parser.parse(this, "").text().normalize()
                if (context.length > (linkText.length + minCtx) && context.matches(asciiRegex)) {
                    val targetFragment = regexGroups.component2().trim()
                    val targetDoc = resolvedLink.asHtmlDoc(resolvedLink.url.path)
                    val targetDocText = targetDoc?.search(linkText, targetFragment, false)!!.toList()

                    if (targetDocText.isNotEmpty()) {
                        // Finds middlemost hit in line to maximize surrounding context
                        val middleIndex = Regex(linkText).findAll(context)
                            .minBy { abs(it.range.first - context.length / 2) }!!.range.first
                        val startIdx = (middleIndex - window / 2).coerceAtLeast(0)
                        val endIdx = (middleIndex + linkText.length + window / 2).coerceAtMost(context.length)
                        val preText = context.substring(startIdx, middleIndex)
                        val subText = context.substring(middleIndex + linkText.length, endIdx).padEnd(window / 2, ' ')
                        val targetDocTitle = targetDoc.title() ?: ""
                        val sourceDocTitle = relativeTo.asHtmlDoc()?.title() ?: ""

                        Link(
                            sourceUri = relativeTo.toString(),
                            targetUri = resolvedLink.toString(),
                            targetFragment = targetFragment,
                            linkText = linkText,
                            sourcePretext = preText,
                            sourceSubtext = subText,
                            targetContext = targetDocText,
                            targetTitle = targetDocTitle,
                            sourceTitle = sourceDocTitle
                        )
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

fun String.normalize() = replace(Regex("\\s\\s+"), " ").trim()

fun Document.search(query: String, fragment: String, isLiteral: Boolean, bufferLen: Int = window): Sequence<String> =
    (if (fragment.isNotEmpty()) extractFragmentText(fragment) else body().text()).normalize()
        .split("\n").run {
            if (isLiteral) extractLiteralHits(query, bufferLen)
            else FuzzySearch.extractTop(query, this, 30, 60).map { it.string.trim() }.asSequence()
        }

fun List<String>.extractLiteralHits(query: String, bufferLen: Int): Sequence<String> = map { line ->
    Regex(Regex.escape(query)).findAll(line).map { mr ->
        (line.substring((mr.range.first - bufferLen).coerceAtLeast(0)) +
                " <<HIT>> " +
                line.substring((mr.range.last + bufferLen).coerceAtMost(line.length)))
    }
}.asSequence().flatten().take(30)

private fun Document.extractFragmentText(fragment: String): String =
    select(fragment)?.first()?.parents()
        ?.firstOrNull { it.siblingElements().isNotEmpty() }
        ?.nextElementSiblings()
        ?.takeWhile { !it.children().hasAttr("id") }
        ?.joinToString("") { it.text() } ?: body().text()

fun main(args: Array<String>) {
    if(args.isNotEmpty()) PRETTY_PRINT = true
    printLinks()
}