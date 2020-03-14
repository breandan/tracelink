import com.google.common.collect.Multimap
import com.google.common.util.concurrent.AtomicLongMap
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.index.TermStates
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermStatistics
import org.apache.lucene.search.TopScoreDocCollector
import java.io.File
import java.io.IOException
import java.io.ObjectInputStream
import kotlin.system.measureTimeMillis

fun main() {
  deserialize()
  runQueryBenchmark()
}

lateinit var queryIndex: Multimap<String, String>
lateinit var counts: AtomicLongMap<String>

private fun deserialize() {
  deserializeIndex<Multimap<String, String>>(getLatest("index")) { queryIndex = it }
  deserializeIndex<AtomicLongMap<String>>(getLatest("phrases")) { counts = it }
}

private fun runQuery(query: String, take: Int = 20) =
  queryIndex[query].sortedBy { -counts["$query@$it"] }
    .mapNotNull { it.searchForText(query) }.take(take).mapIndexed { i, candidateDoc ->
      "${i + 1}.) ${candidateDoc.title} - ${candidateDoc.context.joinToString("...")}"
    }

fun getLatest(indexFileKeyword: String) =
  File(".").listFiles().sortedBy { it.name }
    .filter { indexFileKeyword in it.name }
    .last { it.extension == "idx" }

inline fun <reified T> deserializeIndex(file: File, t: (T) -> Unit) {
  try {
    file.inputStream().use { ObjectInputStream(it).use { t(it.readObject() as T) } }
    System.err.println("Index loaded from: ${file.absolutePath}")
  } catch (i: IOException) {
    i.printStackTrace()
  }
}

private fun runQueryBenchmark() {
  val total = 100
  val time = queryIndex.keySet().take(total).sumByDouble { query ->
    measureTimeMillis {
      println("QUERY: $query")
      runQuery(query, 3).forEach { println(it) }

      println()
    }.toDouble()
  }

  println("Average query time: ${time / total.toDouble()}ms")
}

fun query(querystr: String = "test") {
  // the "title" arg specifies the default field to use
  // when no field is explicitly specified in the query.
  val q = MultiFieldQueryParser(arrayOf("title", "contents", "uri"), analyzer).parse(querystr)

  // search
  val hitsPerPage = 9
  val reader = DirectoryReader.open(index)
  val searcher = IndexSearcher(reader)

  val collector = TopScoreDocCollector.create(hitsPerPage, 10)
  searcher.search(q, collector)
  val hits = collector.topDocs().scoreDocs

  // display results
  println("Found ${hits.size} hits.")
  hits.forEachIndexed { i, scoreDoc ->
    val docId = scoreDoc.doc
    val d = searcher.doc(docId)
    println((i + 1).toString() + ". " + d.get("title") + "\t" + d.get("uri"))
  }
  reader.close()
}

private fun IndexSearcher.getStats(term: String): TermStatistics? {
  val cs = collectionStatistics("contents")
  val tm = Term("contents", term)
  return termStatistics(tm, TermStates.build(topReaderContext, tm, true))
}
