import org.apache.commons.vfs2.AllFileSelector
import org.apache.commons.vfs2.VFS
import org.jsoup.Jsoup
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import kotlin.streams.asSequence

data class Link(val from: String, val to: String, val text: String) {
    override fun toString(): String = "(${from.substring(59).take(20)}..${from.takeLast(20)}, ${to.substring(59).take(20)}..${to.takeLast(20)}, $text)"
}

//val outputFile = File("results${System.currentTimeMillis()}.csv").also { it.createNewFile(); it.appendText("text, source, target\n") }
//val regex = Regex("<a.*href=\"([^:]*?[A-Za-z._-]*\\.html?)\"[^<>]*>(.+?)</a>")
val regex = Regex("(.{0,100})<a.*href=\"([^<>:]*?)\"[^<>]*>([!-;?-~]{5,})</a>(.{0,100})")
val archivesDir: String = "/home/breandan/PycharmProjects/zealds/archives/"

fun extractLinks() =
    File(archivesDir).listFiles()?.toList()?.parallelStream()?.map { file ->
        VFS.getManager().resolveFile("tgz:${file.absolutePath}")
            .findFiles(AllFileSelector())
            .asSequence()
            .filter { it.name.toString().let { name -> ".html" in name || ".htm" in name } }
            .map { it.content.inputStream.bufferedReader(UTF_8).lineSequence().map { line -> Pair(it, line) } }
            .flatten()
            .map { (file, line) ->
                regex.findAll(line).mapIndexedNotNull { idx, result ->
                    result.destructured.run {
                        try {
                            Link(
                                from = file.toString(),
                                to = file.resolveFile(component2()).toString(),
                                text = component3().let { if (it.contains("<")) Jsoup.parse(it).text() else it })
                            //.also { if(idx % 10 == 0) println("$it") }
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.flatten().forEach { println("\"${it.text}\", \"${it.from.compact()}\", \"${it.to.compact()}\"") }
    }?.forEach {  }

fun String.compact(prefixLength: Int = archivesDir.length + 11) = substring(prefixLength).run { substringBefore(".tgz") + "::" + substringAfter("Contents/Resources/Documents/") }

fun main() {
    println("text, source, target")
    extractLinks()
}