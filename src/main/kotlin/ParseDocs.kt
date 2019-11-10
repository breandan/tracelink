import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field.Store.YES
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TopScoreDocCollector
import org.apache.lucene.store.Directory
import org.apache.lucene.store.RAMDirectory
import java.io.FileReader
import java.nio.file.Paths
import kotlin.streams.asSequence

data class Doc(
    val uri: String,
    val title: String,
    val contents: String
) {
    override fun toString() = "$uri, $title, $contents"
}

val analyzer = StandardAnalyzer()
val index = RAMDirectory() // FSDirectory.open(Paths.get("test"))
val config = IndexWriterConfig(analyzer)


fun main() {
    IndexWriter(index, config).use { w ->
        parseLinks("links_with_context.csv")
            .map { jDocToDoc(it!!) }
            .asSequence().take(10)
            .forEach { addDoc(w, it); println("Indexed: ${it.contents}") }
    }

    query("DataBuffer")
}

private fun jDocToDoc(it: org.jsoup.nodes.Document): Doc {
    val uri = it.baseUri()
    val title = try {
        it.title().run { if (isBlank()) "EMPTY_TITLE" else this }
    } catch (e: Exception) {
        "EMPTY_TITLE"
    }
// if (uri.contains("#")) { it.parseFragment(uri.substringAfterLast("#")) ?: it.wholeText() }  else it.wholeText()
    val contents = it.wholeText()
    return Doc(uri, title, contents)
}

private fun org.jsoup.nodes.Document.parseFragment(fragment: String): String? =
    select("[id='$fragment']")?.firstOrNull()?.wholeText()

fun query(querystr: String = "test") {
    // the "title" arg specifies the default field to use
    // when no field is explicitly specified in the query.
    val q = QueryParser("title", analyzer).parse(querystr)

    // 3. search
    val hitsPerPage = 10
    val reader = DirectoryReader.open(index)
    val searcher = IndexSearcher(reader)
    val collector = TopScoreDocCollector.create(hitsPerPage, 10)
    searcher.search(q, collector)
    val hits = collector.topDocs().scoreDocs

    // 4. display results
    println("Found " + hits.size + " hits.")
    for (i in hits.indices) {
        val docId = hits[i].doc
        val d = searcher.doc(docId)
        println((i + 1).toString() + ". " + d.get("title") + "\t" + d.get("uri"))
    }

    // reader can only be closed when there
    // is no need to access the documents any more.
    reader.close()
}

private fun addDoc(w: IndexWriter, d: Doc) =
    w.addDocument(Document().apply {
        add(TextField("title", d.title, YES))
        add(TextField("uri", d.uri, YES))
        add(TextField("contents", d.contents, YES))
    })

class LuceneFileSearch(val indexDirectory: Directory, val analyzer: StandardAnalyzer) {
    fun addFileToIndex(filepath: String) {
        val file = Paths.get(javaClass.classLoader.getResource(filepath)!!.toURI()).toFile()
        val indexWriterConfig = IndexWriterConfig(analyzer)
        val indexWriter = IndexWriter(indexDirectory, indexWriterConfig)
        val document = Document()

        val fileReader = FileReader(file)
        document.add(TextField("contents", fileReader))
        document.add(StringField("path", file.path, YES))
        document.add(StringField("filename", file.name, YES))

        indexWriter.addDocument(document)

        indexWriter.close()
    }

    fun searchFiles(inField: String, queryString: String): List<Document> {
        val query = QueryParser(inField, analyzer).parse(queryString)
        val indexReader = DirectoryReader.open(indexDirectory)
        val searcher = IndexSearcher(indexReader)
        return searcher.search(query, 10).scoreDocs.map { searcher.doc(it.doc) }
    }
}