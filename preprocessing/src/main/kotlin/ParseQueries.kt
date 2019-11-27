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

val invertedIndex = Multimaps.synchronizedMultimap(HashMultimap.create<String, String>())
val phraseCounts = AtomicLongMap.create<String>()
val linkCounts = AtomicLongMap.create<String>()

fun String.archive() = substringAfter(archivesAbs).drop(1).substringBefore("/")

fun String.targetDoc(): Document? =
    try {
        VFS.getManager().resolveFile(this)?.asHtmlDoc() ?: null
    } catch (ex: Exception) {
        null
    }

val WORDS_CTX_LEN = 20

fun <T> Iterator<T>.takeOrEvictFrom(queue: EvictingQueue<T>) {
    if (hasNext()) queue.add(next()) else if (queue.isNotEmpty()) queue.remove()
}

/* Generates a concordance list for every word in a string, with the keyword located in the dead center of the list. For
 * example, when invoked on "the quick brown fox jumped over the lazy dog", this method will produce the following list:
 *
 * {"the quick brown fox", "the quick brown fox jumped", "the quick brown fox jumped over", "the quick brown fox jumped
 * over the", "quick brown fox jumped over the lazy", "brown fox jumped over the lazy dog", "fox jumped over the lazy
 * dog", "jumped over the lazy dog", "over the lazy dog"}.
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

class LinkWithCandidates(val link: Link, var countSearchCandidates: List<Triple<String, String, List<String>>>) {
    override fun toString() =
        link.toString() + "\t" + countSearchCandidates.joinToString("\t") {
            it.first.compact() + "\t" + it.second + "\t" + it.third.joinToString(" … ").noTabs()
        }.let {
            val occurrences = it.count { char -> char == '…' }
            if (occurrences <= TOP_K_DOCS) it.padEnd((TOP_K_DOCS - occurrences - 1).coerceAtLeast(0), '\t') else it
        }
}

var links: List<Link>? = null

fun buildIndex(file: String) {
    links = File(file).readLines().drop(1)
        .parallelStream()
        .map { Link(it).apply { linkCounts.incrementAndGet(linkText) } }
        .collect(Collectors.toList())

    links!!.map { listOf(it.sourceUri, it.targetUri) }.flatten().distinct()
        .parallelStream().collect(Collectors.groupingByConcurrent<String, String> { it.archive() }).entries
        .parallelStream().forEach { entry ->
            entry.value.forEachIndexed { idx, document ->
                if (idx % 20 == 0)
                    System.err.println("Parsed $idx links of ${entry.value.size} in archive ${entry.key.archiveName()}")
                val docText = document.targetDoc()?.text() ?: ""
                Regex("\\s$VALID_PHRASE\\s").findAll(docText).forEach { phrase ->
                    val wholePhrase = phrase.value.drop(1).dropLast(1)
                    wholePhrase.split(Regex("[^A-Za-z]")).forEach { phraseFragment ->
                        if (MIN_ALPHANUMERICS < phraseFragment.length) invertedIndex.put(phraseFragment, document);
                        phraseCounts.incrementAndGet("$phraseFragment@$document")
                    }
                    if (linkCounts.containsKey(wholePhrase)) {
                        invertedIndex.put(wholePhrase, document)
                        phraseCounts.incrementAndGet("$wholePhrase@$document")
                    }
                }
            }
        }

//    println("Inverted index contains: ${invertedIndex.size()} elements")
//    println("Top words index contains:")
//    val entries: MutableSet<MutableMap.MutableEntry<String, MutableCollection<String>>> = invertedIndex.asMap().entries
//    entries.sortedBy { -it.value.size }.take(50).forEach { println(it.key.padEnd(20, ' ') + " : " + it.value.size) }
//    println("Phrase-document count contains: ${phraseCounts.size()} elements")
}

val TOP_K_DOCS = 20
fun String.topURIsMatchingQuery() = invertedIndex[this].sortedBy { -phraseCounts["$this@$it"] }.take(TOP_K_DOCS)

fun main(args: Array<String>) {
    val linksFile = args[0]
    buildIndex(linksFile)
    System.err.println("Read ${links?.size} links from $linksFile")
    serializeIndex(File("${linksFile.substringBeforeLast(".")}_index_${System.currentTimeMillis()}.idx"))
    serializePhraseCounts(File("${linksFile.substringBeforeLast(".")}_phrases_${System.currentTimeMillis()}.idx"))
    println("gt_rank\t$LINK_CSV_HEADER\t" + (0 until TOP_K_DOCS).joinToString("\t") {
        "top${it}_doc_uri\ttop${it}_doc_title\ttop${it}_doc_context" }
    )
    val linksWithTopKDocs = getLinksWithTopKCandidates()
    val topKStrings = linksWithTopKDocs.mapNotNull {
        val trueIdx = it.countSearchCandidates.indexOfFirst { candidate -> candidate.first == it.link.targetUri }
        if (0 <= trueIdx) trueIdx.toString().padEnd(4) + "\t" + it
        else { System.err.println("Something is wrong with $it"); null }// TODO: Why does this happen?
    }
    topKStrings.forEach { println(it) }
    System.err.println("Wrote ${linksWithTopKDocs.size} links with accompanying count-search results")
    println(
        "Average index position of ground target in top K docs sorted by count: " +
                "${topKStrings.map { it.substringBefore('\t').trim().toDouble() }.sum() / topKStrings.size.toDouble()}"
    )
    System.err.println("Cached ${frequentQueryCache.size} queries with over $MIN_FREQ_TO_CACHE_QUERY links in dataset")
}

private fun getLinksWithTopKCandidates(): List<LinkWithCandidates> =
    links!!.map { link -> LinkWithCandidates(link, getCandidatesForQuery(link.linkText)) }

val MIN_FREQ_TO_CACHE_QUERY = 3
val frequentQueryCache = ConcurrentHashMap<String, List<Triple<String, String, List<String>>>>()

private fun getCandidatesForQuery(query: String) =
    if (linkCounts[query] < MIN_FREQ_TO_CACHE_QUERY) computeCandidates(query)
    else frequentQueryCache.computeIfAbsent(query) { computeCandidates(query) }

private fun computeCandidates(query: String) =
    query.topURIsMatchingQuery().mapNotNull { it.searchForText(query) }

private fun String.searchForText(linkText: String): Triple<String, String, List<String>>? =
    targetDoc()?.let { Triple(this, it.title() ?: "", it.text()?.extractConcordances(linkText) ?: listOf()) }

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