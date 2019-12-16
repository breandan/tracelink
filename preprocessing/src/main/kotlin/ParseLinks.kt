import com.google.common.collect.EvictingQueue
import org.apache.commons.vfs2.AllFileSelector
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import org.jsoup.nodes.Document
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Stream

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
    val sourceHitCount: Int,         // Number of occurrences of the link text in the source document
    val targetHitCount: Int,         // Number of occurrences of the link text in the target document
    val sourceTitle: String,         // Title of the source document
    val targetTitle: String,         // Title of the target document
    val sourceContext: List<String>, // Context within the same source document
    val targetContext: List<String>, // Hits and surrounding context in target doc
    val sourceUri: String,           // Original document location
    val targetUri: String,           // Target document location
    val targetFragment: String       // Link fragment (indicating subsection)
) {
    constructor(
        line: String, parsed: Array<String> = line.split("\t").map { it.trim() }.toTypedArray()
    ) : this(
        linkText = parsed[0].normalize(),
        sourceHitCount = Integer.valueOf(parsed[1]),
        targetHitCount = Integer.valueOf(parsed[2]),
        sourceTitle = parsed[3].trim(),
        targetTitle = parsed[4].trim(),
        sourceContext = parsed[5].split(Regex(" <<[A-Z]+>> ")),
        targetContext =parsed[6].split(" … ").map { it.trim() },
        sourceUri = parsed[7].toFullPath(),
        targetUri = parsed[8].toFullPath(),
        targetFragment = parsed[9]
    )

    val targetArchive: String = targetUri.archiveName()

    fun pretty(): String = linkText.noTabs().prettyText() + "\t" +
            sourceHitCount.toString().padStart(2, ' ') + "\t" +
            targetHitCount.toString().padStart(4, ' ').padEnd(6, ' ') + "\t" +
            sourceTitle.noTabs().prettyTitle() + "\t" +
            targetTitle.noTabs().prettyTitle() + "\t" +
            sourceContext.joinToString(" … ") { prettyHit(it.replace("…", "...")) }.padEnd(2000, ' ') + "\t" +
            targetContext.joinToString(" … ") { prettyHit(it.replace("…", "...")) }.padEnd(2000, ' ') + "\t" +
            sourceUri.compact() + "\t" +
            targetUri.compact() + "\t" +
            targetFragment

    override fun toString(): String =
        if (PRETTY_PRINT) { pretty() } else {
            linkText.noTabs() + "\t" +
                    sourceHitCount.toString() + "\t" +
                    targetHitCount.toString() + "\t" +
                    sourceTitle.noTabs() + "\t" +
                    targetTitle.noTabs() + "\t" +
                    sourceContext.joinToString(" … ") { it.replace("…", "...").noTabs() } + "\t" +
                    targetContext.joinToString(" … ") { it.replace("…", "...").noTabs() } + "\t" +
                    sourceUri.compact() + "\t" +
                    targetUri.compact() + "\t" +
                    targetFragment
        }

    override fun hashCode() = (linkText + sourceUri).hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Link

        if (linkText != other.linkText) return false
        if (sourceContext != other.sourceContext) return false
        if (targetContext != other.targetContext) return false
        if (targetUri != other.targetUri) return false
        if (targetFragment != other.targetFragment) return false

        return true
    }

    // No need to trim since all link texts are regex-matched to be less than MAX_LTEXT_LEN
    private fun String.prettyText() = padEnd(MAX_LTEXT_LEN, ' ')

    private fun String.prettyTitle() =
        let { if (MAX_TITLE_LEN < it.length) it.take(MAX_TITLE_LEN) else it.padEnd(MAX_TITLE_LEN, ' ') }

    private fun String.prettyPretext() =
        let { if (MAX_CONTS_LEN < length) it.takeLast(MAX_CONTS_LEN) else it.padStart(MAX_CONTS_LEN, ' ') }

    private fun String.prettySubtext() =
        let { if (MAX_CONTS_LEN < length) it.take(MAX_CONTS_LEN) else it.padEnd(MAX_CONTS_LEN, ' ') }

    private fun prettyHit(it: String) =
        it.split(" <<LTX>> ").let { it.first().prettyPretext() + " <<LTX>> " + it.last().prettySubtext() }
}

fun String.compact(prefixLength: Int = archivesAbs.length) = substring(prefixLength)

val MAX_LTEXT_LEN = 50
val MAX_TITLE_LEN = 100
val MAX_CONTS_LEN = 120
val MIN_KWIC_HITS = 2
val MIN_KWIC_LEN = 8

var PRETTY_PRINT = false

fun String.toFullPath() = "$archivesAbs${this.substringBeforeLast("%")}"

fun String.noTabs() = this.replace("\t", "  ").replace("\"", "'")

val archivesDir: String = "python" // Parent directory (assumed to contain `.tgz` files)
val archivesAbs: String = "tgz:file://" + File(archivesDir).absolutePath
fun String.archiveName() = substringAfter(archivesAbs).drop(1).substringBefore("/")

/**
 * Extracts documents from archives in parallel and prints the links in CSV format.
 */

val LINK_CSV_HEADER = "link_text\t" +
        "source_hit_count\t" +
        "target_hit_count\t" +
        "source_title\t" +
        "target_title\t" +
        "source_context\t" +
        "target_context\t" +
        "source_document\t" +
        "target_document\t" +
        "link_fragment"

fun printLinks() {
    println(LINK_CSV_HEADER)

    // Be careful to catch exceptions in each substream so we do not crash or skip results prematurely.
    File(archivesDir).listFiles()?.toList()?.parallelStream()
        ?.forEach { archive ->
            try {
                fetchLinks(archive)?.forEach { htmlLinkStream: Stream<Link?>? ->
                    try {
                        htmlLinkStream?.forEach { link: Link? ->
                            if (link != null) {
                                    println(link)
                            }
                        }
                    } catch (e: Exception) {
//                                System.err.println("Error reading stream $e")
                        e.printStackTrace()
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

private fun fetchLinks(archive: File): Stream<Stream<Link?>?>? =
    archive.getHtmlFiles()?.map { file ->
        file.asHtmlDoc()?.getAllLinks(relativeTo = file)
    }

fun File.getHtmlFiles(): Stream<FileObject>? =
    try {
        VFS.getManager().resolveFile("tgz:${absolutePath}")
            .findFiles(AllFileSelector()).asList().stream()
            .filter { it.name.extension.toString().let { ext -> ext == "htm" || ext == "html" } }
    } catch (ex: Exception) {
        null
    }

val MIN_ALPHANUMERICS = 5
//                          ALLOW BALANCED PUNCTUATION UP TO ONE LEVEL OF NESTING ONLY
val BALANCED_BRACKETS = "((\\([^\\(\\)]\\))|(\\[[^\\[\\]]\\])|(\\{[^\\{\\}]\\})|(<[^<>]>)|(\"[^\"]\")|('[^']'))*"
//                   ANYTHING BUT: '"()[]{}<>                            ANYTHING BUT: '"()[]{}<>
val TEXT_OR_CODE = "[^'\"\\s\\(\\)\\{\\}\\[\\]<>]*[a-zA-Z._:&@#\\*~]{$MIN_ALPHANUMERICS,}[^'\"\\s\\(\\)\\{\\}\\[\\]<>]*"
val VALID_PHRASE = "$TEXT_OR_CODE$BALANCED_BRACKETS($TEXT_OR_CODE)*"
//                                      LINK URI       FRAGMENT               ANCHOR TEXT
val LINK_REGEX = Regex("<a[^<>]*href=\"([^<>#:?\"]*?)(#[^<>#:?\"]*)?\"[^<>]*>($VALID_PHRASE)</a>")

val asciiRegex = Regex("[ -~]*")
val previouslySeen = ConcurrentHashMap.newKeySet<Int>()

/**
 * Returns all HTML links within a string whose anchor text is shorter than the string
 * by a fixed margin. This catches links which have surrounding context in documentation
 * containing a mixture of natural language and source code. Links are resolved relative
 * to a document path, and validated so that all links returned point to a valid URL.
 */

private fun Document.getAllLinks(relativeTo: FileObject): Stream<Link?> =
    select("a[href]").stream().map { linkTag ->
        try {
            if (!linkTag.outerHtml().matches(LINK_REGEX) || MAX_LTEXT_LEN < linkTag.text().length) return@map null
            val linkText = linkTag.text().normalize()
            val hash = (linkText + relativeTo.toString()).hashCode()
            if (hash in previouslySeen) return@map null else previouslySeen.add(hash)
            val sourceDocHits = search(linkText).toList()
            val targetUri = linkTag.attr("href")
            val resolvedLink = relativeTo.parent.resolveFile(targetUri)
            val targetDoc = resolvedLink.asHtmlDoc(resolvedLink.url.path)
            val targetFragment = if (targetUri.contains("#")) targetUri.substringAfterLast("#") else ""
            val targetDocHits = targetDoc?.search(linkText, targetFragment)?.toList() ?: emptyList()

            val sourceTitle = title()
            val targetTitle = targetDoc?.title() ?: ""
            if (!(sourceTitle + targetTitle).matches(asciiRegex)) return@map null

            if (MIN_KWIC_HITS < sourceDocHits.size && MIN_KWIC_HITS < targetDocHits.size) {
                Link(
                    linkText = linkText,
                    sourceHitCount = sourceDocHits.size,
                    targetHitCount = targetDocHits.size,
                    sourceTitle = title(),
                    targetTitle = targetDoc?.title() ?: "",
                    sourceContext = sourceDocHits,
                    targetContext = targetDocHits,
                    sourceUri = relativeTo.toString(),
                    targetUri = resolvedLink.toString().substringBeforeLast("#"),
                    targetFragment = targetFragment
                )
            } else null
        } catch (ex: Exception) {
            null
        }
    }

fun String.normalize() = replace(Regex("\\s\\s+"), " ").trim()

fun Document.search(query: String, fragment: String = "", bufferLen: Int = MAX_CONTS_LEN) =
    (if (fragment.isNotEmpty()) extractFragmentText(fragment) else body().text()).normalize().let { docText ->
        Regex(Regex.escape(query)).findAll(docText).map { matchResult ->
            docText.substring(
                (matchResult.range.first - bufferLen).coerceAtLeast(0), matchResult.range.first
            ) + " <<LTX>> " +
                    docText.substring(
                        matchResult.range.last + 1, ((matchResult.range.last + bufferLen)
                            .coerceAtMost(docText.length))
                    )
        }.filter { it.matches(asciiRegex) }
//                && previouslySeen.run {
//                val hash = (query + it + this@search).hashCode()
//                val cont = !contains(hash)
//                add(hash)
//                !cont
//            }
//        }
    }.take(99)

private fun Document.extractFragmentText(fragment: String): String =
    select(fragment)?.first()?.parents()
        ?.firstOrNull { it.siblingElements().isNotEmpty() }
        ?.nextElementSiblings()
        ?.takeWhile { !it.children().hasAttr("id") }
        ?.joinToString("") { it.text() } ?: body().text()

fun main(args: Array<String>) {
    if (args.isNotEmpty()) PRETTY_PRINT = true
    printLinks()
    System.err.println("FINISHED")
}