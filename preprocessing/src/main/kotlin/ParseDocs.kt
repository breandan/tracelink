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
import java.util.stream.Stream
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
    File(archivesDir).listFiles()!!.sortedBy { -it.length() }.parallelStream()
        .map { archive ->
            archive.getHtmlFiles()?.map { file ->
                file.asHtmlDoc("${file.url}")?.let {
                    convertJsoupDocToDocTrace(it)
                }
            }
        }

fun main() {
    indexDocs()
}

private fun indexDocs() {
    var t = 0
    val iw = IndexWriter(index, config)
    val startTime = System.currentTimeMillis()

    parseDocs().forEach { docStream: Stream<Doc?>? ->
        if (timeLimitExceeded(startTime)) return@forEach

        docStream?.forEach { doc: Doc? ->
            if (timeLimitExceeded(startTime)) return@forEach
            doc?.run {
                iw.addDoc(this); t += 1;
                if (t % 1000 == 0) System.err.println("Indexed $uri")
            }
        }
    }

    iw.close()
}

val timeLimit = 84000000
// Needed if running on a time-sharing system to flush the existing contents in a timely manner..
private fun timeLimitExceeded(startTime: Long) = System.currentTimeMillis() - startTime > timeLimit

private fun convertJsoupDocToDocTrace(it: org.jsoup.nodes.Document): Doc {
    val uri = it.baseUri()
    val title = try {
        it.title()
    } catch (e: Exception) {
        ""
    }

    val contents = it.text()
    return Doc(uri, title, contents)
}

private fun org.jsoup.nodes.Document.parseFragment(fragment: String): String? =
    select("[id='$fragment']")?.firstOrNull()?.text()


private fun IndexWriter.addDoc(d: Doc) =
    addDocument(Document().apply {
        add(TextField("title", d.title, YES))
        add(TextField("uri", d.uri, YES))
        add(TextField("contents", d.contents, YES))
    })