import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field.Store.YES
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.store.MMapDirectory
import java.io.File
import kotlin.streams.asSequence
import kotlin.system.measureTimeMillis

data class Doc(
    val uri: String,
    val title: String,
    val contents: String
) {
    override fun toString() = "$uri, $title, $contents"
}

val analyzer = StandardAnalyzer()
val index = MMapDirectory(File("test").toPath())
val config = IndexWriterConfig(analyzer)

fun main() {
    indexDocs("links_with_context.csv")

    println("Query took: " + measureTimeMillis { query("DataBuffer") } + " ms")
}

private fun indexDocs(csv: String) {
    IndexWriter(index, config).use { w ->
        parseLinks(csv).map { jDocToDoc(it!!) }
            .asSequence().take(10)
            .forEach { w.addDoc(it); println("Indexed: ${it.contents}") }
    }
}

private fun jDocToDoc(it: org.jsoup.nodes.Document): Doc {
    val uri = it.baseUri()
    val title = try {
        it.title().run { if (isBlank()) "EMPTY_TITLE" else this }
    } catch (e: Exception) {
        "EMPTY_TITLE"
    }

    val contents = it.wholeText()
    return Doc(uri, title, contents)
}

private fun org.jsoup.nodes.Document.parseFragment(fragment: String): String? =
    select("[id='$fragment']")?.firstOrNull()?.wholeText()

fun query(querystr: String = "test") {
    // the "title" arg specifies the default field to use
    // when no field is explicitly specified in the query.
    val q = QueryParser("title", analyzer).parse(querystr)

    // search
    val hitsPerPage = 10
    DirectoryReader.open(index).use { reader ->
        val searcher = IndexSearcher(reader)
        val collector = TopScoreDocCollector.create(hitsPerPage, 10)
        searcher.search(q, collector)
        val hits = collector.topDocs().scoreDocs

        // display results
        println("Found ${hits.size} hits.")
        for (i in hits.indices) {
            val docId = hits[i].doc
            val d = searcher.doc(docId)
            println((i + 1).toString() + ". " + d.get("title") + "\t" + d.get("uri"))
        }
    }
}

private fun IndexWriter.addDoc(d: Doc) =
    addDocument(Document().apply {
        add(TextField("title", d.title, YES))
        add(TextField("uri", d.uri, YES))
        add(TextField("contents", d.contents, YES))
    })