import com.google.common.collect.EvictingQueue
import com.google.common.collect.HashMultimap
import com.google.common.collect.ImmutableMultimap
import com.google.common.collect.Multimaps
import com.google.common.util.concurrent.AtomicLongMap
import org.apache.commons.vfs2.VFS
import java.io.File
import java.io.IOException
import java.io.ObjectOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

val invertedIndex = Multimaps.synchronizedMultimap(HashMultimap.create<String, String>())
val phraseCounts = AtomicLongMap.create<String>()
val linkTexts = ConcurrentHashMap.newKeySet<String>()

fun String.archive() = substringAfter(archivesAbs).drop(1).substringBefore("/")

fun String.targetDoc(): String? =
    try {
        VFS.getManager().resolveFile(this)?.asHtmlDoc()?.text()
    } catch (ex: Exception) {
        null
    }

val WORDS_CTX_LEN = 6

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
        if (word == query || query.isNullOrBlank())
            concordances.add(leadingTerms.joinToString(" ") + " $word " + trailingTerms.joinToString(" "))

        // Backfill leading words
        leadingTerms.add(word)

        concordances
    }
}

class LinkWithCandidates(val link: Link, var countSearchCandidates: List<Pair<String, List<String>>>) {
    override fun toString() =
        link.toString() + "\t" + countSearchCandidates.joinToString("\t") {
            it.first + "\t" + it.second.joinToString(" … ")
        }
}

var links: List<Link>? = null

fun buildIndex(file: String) {
    links = File(file).readLines().drop(1).take(100).parallelStream()
        .map { Link(it) }
        .collect(Collectors.toList())

    links!!.map { linkTexts.add(it.linkText); listOf(it.sourceUri, it.targetUri) }
        .flatten().parallelStream().collect(Collectors.groupingByConcurrent<String, String> { it.archive() }).entries
        .parallelStream().forEach {
            it.value.forEach { document ->
                val docText = document.targetDoc() ?: ""
                Regex("\\s$VALID_PHRASE\\s").findAll(docText).forEach { phrase ->
                    val wholePhrase = phrase.value.drop(1).dropLast(1)
                    wholePhrase.split(Regex("[^A-Za-z]")).forEach { phraseFragment ->
                        if (MIN_ALPHANUMERICS < phraseFragment.length) invertedIndex.put(phraseFragment, document);
                        phraseCounts.incrementAndGet("$phraseFragment@$document")
                    }
                    if (linkTexts.contains(wholePhrase)) {
                        invertedIndex.put(wholePhrase, document)
                        phraseCounts.incrementAndGet("$wholePhrase@$document")
                    }
                }
            }
        }

    println("Inverted index contains: ${invertedIndex.size()} elements")
    println("Top words index contains:")
    val entries: MutableSet<MutableMap.MutableEntry<String, MutableCollection<String>>> = invertedIndex.asMap().entries
    entries.sortedBy { -it.value.size }.take(50).forEach { println(it.key.padEnd(20, ' ') + " : " + it.value.size) }
    println("Phrase-document count contains: ${phraseCounts.size()} elements")
}

fun String.topDocsMatchingQuery() = invertedIndex[this].sortedBy { -phraseCounts["$this@$it"] }

fun main(args: Array<String>) {
    val linksFile = args[0]
    buildIndex(linksFile)
    serializeIndex(File("${linksFile.substringBeforeLast(".")}_index_${System.currentTimeMillis()}.idx"))
    serializePhraseCounts(File("${linksFile.substringBeforeLast(".")}_phrases_${System.currentTimeMillis()}.idx"))

    println("$LINK_CSV_HEADER\t" + (0..10).joinToString("\t") { "cdoc_$it\tcdoc_context_$it" })

    links!!.forEach { link ->
        LinkWithCandidates(link, link.linkText.topDocsMatchingQuery().map {
            Pair(it, it.targetDoc()?.extractConcordances(link.linkText) ?: listOf())
        }).let { println(it) }
    }
}

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