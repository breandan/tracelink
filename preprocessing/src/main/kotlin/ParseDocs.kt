import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field.Store.YES
import org.apache.lucene.document.TextField
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.store.MMapDirectory
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets
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

private fun FileObject.asHtmlDoc(uri: String) =
    try {
        Parser.parse(content.getString(StandardCharsets.UTF_8), uri)
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }

private fun Link.readDestination() =
    VFS.getManager().resolveFile(to).asHtmlDoc("$to$linkFragment")

fun parseDocs() =
    File(archivesDir).listFiles().asList().take(3)
        .parallelStream()
        .map { it.getHtmlFiles() }
        .map { it.map { jDocToDoc(it.asHtmlDoc("${it.url}")) } }

val docPath = "../links_with_context.csv"

fun main() {
    indexDocs()

    println("Query took: " + measureTimeMillis { query("BUFFER_FILE_EXTENSION") } + " ms")
}

private fun indexDocs() {
    val iw = IndexWriter(index, config)
    parseDocs().forEach { it.forEach { iw.addDoc(it); println("Indexed: ${it.uri}") } }
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
    val q = QueryParser("title", analyzer).parse(querystr)

    // search
    val hitsPerPage = 10
    val reader = DirectoryReader.open(index)
    val searcher = IndexSearcher(reader)
    val cs = searcher.collectionStatistics("contents")
    val term = Term("contents", "browser")
    val ts = searcher.termStatistics(term, TermStates.build(searcher.topReaderContext, term, true))

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
}

private fun IndexWriter.addDoc(d: Doc) =
    addDocument(Document().apply {
        add(TextField("title", d.title, YES))
        add(TextField("uri", d.uri, YES))
        add(TextField("contents", d.contents, YES))
    })