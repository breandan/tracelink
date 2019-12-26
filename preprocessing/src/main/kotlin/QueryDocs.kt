import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.index.TermStates
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermStatistics
import org.apache.lucene.search.TopScoreDocCollector
import kotlin.system.measureTimeMillis

fun main() {
    println("Query took: " + measureTimeMillis { query("test") } + " ms")
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
