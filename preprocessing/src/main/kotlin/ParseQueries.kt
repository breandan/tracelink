import com.google.common.collect.EvictingQueue
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimaps
import com.google.common.util.concurrent.AtomicLongMap
import org.apache.commons.vfs2.VFS
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

fun main(args: Array<String>) {
    val linksFile = args[0]
    buildIndex(linksFile)

    storeIndex(linksFile)

    println("link_text\tsource_idx\ttarget_idx\t" + (0 until TOP_K_DOCS).joinToString("\t") {
        "top${it}_doc_uri\ttop${it}_doc_title\ttop${it}_doc_context" }
    )

    printLinksWithCandidates()

    System.err.println("Cached ${frequentQueryCache.size} queries with over $MIN_FREQ_TO_CACHE_QUERY links in dataset")
}

// Contains a list of words and the URIs of documents in which they can be found.
val invertedIndex = Multimaps.synchronizedMultimap(HashMultimap.create<String, String>())
val phraseCounts = AtomicLongMap.create<String>()
val anchorCounts = AtomicLongMap.create<String>()

fun String.archive() = substringAfter(archivesAbs).drop(1).substringBefore("/")

fun String.fetchTargetDoc(): Document? =
    try {
        VFS.getManager().resolveFile(this)?.asHtmlDoc()
    } catch (ex: Exception) {
        null
    }

val WORDS_CTX_LEN = 20

fun <T> Iterator<T>.takeOrEvictFrom(queue: EvictingQueue<T>) {
    if (hasNext()) queue.add(next()) else if (queue.isNotEmpty()) queue.remove()
}

/* Generates a concordance list for every word in a string, with the keyword located in the dead center of the list, or
 * surrounded by up to WORDS_CTX_LEN contiguous words on either side and truncated at the boundaries of the string. For
 * example, when invoked on "the quick brown fox jumped over the lazy dog", this method will produce the following list:
 *
 * {"<the> quick brown fox", "the <quick> brown fox jumped", "the quick <brown> fox jumped over", "the quick brown <fox>
 * jumped over the", "quick brown fox <jumped> over the lazy", "brown fox jumped <over> the lazy dog", "fox jumped over
 * <the> lazy dog", "jumped over the <lazy> dog", "over the lazy <dog>"}
 *
 * For more information about concordance/KWIC, refer to: https://en.wikipedia.org/wiki/Key_Word_in_Context
 */

fun String.extractConcordances(query: String? = null): List<String> {
    val words = split(Regex("\\s+"))

    val leadingTerms = EvictingQueue.create<String>(WORDS_CTX_LEN / 2)
    val trailingTerms = EvictingQueue.create<String>(WORDS_CTX_LEN / 2)
    val trailingIterator: Iterator<String> = words.iterator()

    // Preload trailing words
    repeat(WORDS_CTX_LEN / 2) { trailingIterator.takeOrEvictFrom(trailingTerms) }

    return words.fold(mutableListOf()) { concordances, word ->
        trailingIterator.takeOrEvictFrom(trailingTerms)
        if (word == query)
            concordances.add(leadingTerms.joinToString(" ") + " <<LTX>> " + trailingTerms.joinToString(" "))
        else if(query.isNullOrBlank())
            concordances.add(leadingTerms.joinToString(" ") + " $word " + trailingTerms.joinToString(" "))

        // Backfill leading words
        leadingTerms.add(word)

        concordances
    }
}

class CandidateDoc(val uri: String, val title: String, val context: List<String>)

class LinkWithCountSearchCandidates(val link: Link, var countSearchCandidates: List<CandidateDoc>) {
    fun contexts() = countSearchCandidates.joinToString("\t") {
        it.uri.compact() + "\t" + it.title + "\t" + it.context.joinToString(" … ").noTabs()
    }.let {
        val columns = it.count { char -> char == '\t' }
        if (columns <= TOP_K_DOCS*3) it + "".padEnd((TOP_K_DOCS*3 - columns - 1).coerceAtLeast(0), '\t') else it
    }

    override fun toString() = link.toString() + "\t" + contexts()
}

lateinit var links: List<Link>

fun buildIndex(file: String) {
    links = File(file).readLines().drop(1)
        .map { Link(it).apply { anchorCounts.incrementAndGet(anchorText) } }

    println("Read ${links.size} links from $file")

    links.map { listOf(it.sourceUri, it.targetUri) }.flatten().distinct()
        // Ensure we group by the archive filename so that threads do not deadlock on file IO
//        .parallelStream().collect(Collectors.groupingByConcurrent<String, String> { it.archive() }).entries
        .parallelStream().forEach { uri ->
//            entry.value.forEachIndexed { idx, uri ->
//                if (idx % 20 == 0)
//                    println("Parsed $idx links of ${entry.value.size} in archive ${entry.key.archiveName()}")
                val docText = uri.fetchTargetDoc()?.text() ?: ""
                Regex("\\s$VALID_PHRASE\\s").findAll(docText).forEach { phrase ->
                    val wholePhrase = phrase.value.drop(1).dropLast(1)
                    wholePhrase.split(Regex("[^A-Za-z]")).forEach { phraseFragment ->
                        if (MIN_ALPHANUMERICS < phraseFragment.length) invertedIndex.put(phraseFragment, uri);
                        phraseCounts.incrementAndGet("$phraseFragment@$uri")
                    }
                    if (anchorCounts.containsKey(wholePhrase)) {
                        invertedIndex.put(wholePhrase, uri)
                        phraseCounts.incrementAndGet("$wholePhrase@$uri")
                    }
                }
            }
//        }

    println("Inverted index contains: ${invertedIndex.size()} elements")
//    println("Top words index contains:")
//    val entries: MutableSet<MutableMap.MutableEntry<String, MutableCollection<String>>> = invertedIndex.asMap().entries
//    entries.sortedBy { -it.value.size }.take(50).forEach { println(it.key.padEnd(20, ' ') + " : " + it.value.size) }
    println("Phrase-document count contains: ${phraseCounts.size()} elements")
}

val TOP_K_DOCS = 20
fun String.getTopURIsMatchingQuery(): List<String> =
    invertedIndex[this].sortedBy { -phraseCounts["$this@$it"] }.take(TOP_K_DOCS)

private fun storeIndex(linksFile: String) {
    val indexFileName = "${linksFile.substringBeforeLast(".")}_index_${System.currentTimeMillis()}.idx"
    serializeIndex(File(indexFileName))
    println("Saved index to $indexFileName")
    val phraseFileName = "${linksFile.substringBeforeLast(".")}_phrases_${System.currentTimeMillis()}.idx"
    serializePhraseCounts(File(phraseFileName))
    println("Saved phrases to $phraseFileName")
}

private fun printLinksWithCandidates() =
    getLinksWithCandidates().forEach { it: LinkWithCountSearchCandidates ->
        val sourceIdx = it.countSearchCandidates.indexOfFirst { candidate -> candidate.uri == it.link.sourceUri }
        val targetIdx = it.countSearchCandidates.indexOfFirst { candidate -> candidate.uri == it.link.targetUri }
        val srcIdx = (sourceIdx.toString() + "/" + it.countSearchCandidates.size.toString()).padEnd(7)
        val tgtIdx = (targetIdx.toString() + "/" + it.countSearchCandidates.size.toString()).padEnd(7)
        if (0 <= targetIdx && 0 <= sourceIdx && sourceIdx != targetIdx)
            println(it.link.anchorText.padEnd(45) + "\t" + srcIdx + "\t" + tgtIdx + "\t" + it.contexts())
    }

private fun getLinksWithCandidates(): List<LinkWithCountSearchCandidates> =
    links!!.map { link -> LinkWithCountSearchCandidates(link, getCandidatesForQuery(link.anchorText)) }

val MIN_FREQ_TO_CACHE_QUERY = 2
val frequentQueryCache = ConcurrentHashMap<String, List<CandidateDoc>>()

private fun getCandidatesForQuery(query: String): List<CandidateDoc> =
    if (anchorCounts[query] < MIN_FREQ_TO_CACHE_QUERY) computeCandidates(query)
    else frequentQueryCache.computeIfAbsent(query) { computeCandidates(query) }

private fun computeCandidates(query: String): List<CandidateDoc> =
    query.getTopURIsMatchingQuery().mapNotNull { it.searchForText(query) }

fun String.searchForText(linkText: String): CandidateDoc? =
    fetchTargetDoc()?.let { CandidateDoc(this, it.title() ?: "", it.text()?.extractConcordances(linkText) ?: listOf()) }

fun serializeIndex(file: File) {
    try {
        file.outputStream().use {
            ObjectOutputStream(it).use {
                it.writeObject(ImmutableMultimap.copyOf(invertedIndex))
            }
        }

        System.err.println("Inverted index is saved in: ${file.absolutePath}")
    } catch (i: IOException) {
        i.printStackTrace()
    }
}

fun serializePhraseCounts(file: File) {
    try {
        file.outputStream().use {
            ObjectOutputStream(it).use {
                it.writeObject(phraseCounts)
            }
        }

        System.err.println("Phrase-document counts stored in: ${file.absolutePath}")
    } catch (i: IOException) {
        i.printStackTrace()
    }
}