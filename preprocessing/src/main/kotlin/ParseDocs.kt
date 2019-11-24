import org.apache.commons.vfs2.FileObject
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field.Store.YES
import org.apache.lucene.document.TextField
import org.apache.lucene.index.*
import org.apache.lucene.index.IndexWriterConfig.OpenMode.CREATE
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermStatistics
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.store.MMapDirectory
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.system.measureTimeMillis

data class Doc(
    val uri: String,
    val title: String,
    val contents: String
) {
    override fun toString() = "$uri, $title, $contents"
}

val analyzer = StandardAnalyzer()
val index = MMapDirectory(File("index").toPath())
val config = IndexWriterConfig(analyzer).apply { setOpenMode(CREATE) }

fun FileObject.asHtmlDoc(uri: String = "") =
    try {
        Parser.parse(content.getString(UTF_8), uri)
    } catch (e: Exception) {
        null
    }

fun parseDocs() =
    File(archivesDir).listFiles()!!.asList()
        .parallelStream()
        .map { it.getHtmlFiles()?.map { file -> file.asHtmlDoc("${file.url}")?.let { jDocToDoc(it) } } }

fun main() {
    indexDocs()

    println("Query took: " + measureTimeMillis { query("test") } + " ms")
}

private fun indexDocs() {
    var t = 0
    val iw = IndexWriter(index, config)
    val startTime = System.currentTimeMillis()

    parseDocs().forEach { docStream ->
        if(System.currentTimeMillis() - startTime > 84600000) return@forEach

        docStream?.forEach { doc ->
            if(System.currentTimeMillis() - startTime > 84600000) return@forEach
            doc?.run {
                iw.addDoc(this); t += 1;
                if (t % 1000 == 0) println("Indexed $uri")
            }
        }
    }

    iw.close()
}

private fun jDocToDoc(it: org.jsoup.nodes.Document): Doc {
    val uri = it.baseUri()
    val title = try {
        it.title().run { if (isBlank()) "EMPTY_TITLE" else this }
    } catch (e: Exception) {
        "EMPTY_TITLE"
    }

    val contents = it.text()
    return Doc(uri, title, contents)
}

private fun org.jsoup.nodes.Document.parseFragment(fragment: String): String? =
    select("[id='$fragment']")?.firstOrNull()?.text()

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

private fun IndexWriter.addDoc(d: Doc) =
    addDocument(Document().apply {
        add(TextField("title", d.title, YES))
        add(TextField("uri", d.uri, YES))
        add(TextField("contents", d.contents, YES))
    })