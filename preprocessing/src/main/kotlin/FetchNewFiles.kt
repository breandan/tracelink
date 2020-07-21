import java.net.URL

fun main() {
  val jdk8members = mutableSetOf<String>()
  val jdk14members = mutableSetOf<String>()

  for (i in 1..27) {
    val jdk8URL = "https://docs.oracle.com/javase/8/docs/api/index-files/index-$i.html"
    val jdk14URL = "https://download.java.net/java/GA/jdk14/docs/api/index-files/index-$i.html"
    val jdk8page = fetchPage(jdk8URL)
    val jdk14page = fetchPage(jdk14URL)
    jdk8members.addAll(extractMembers(jdk8page))
    jdk14members.addAll(extractMembers(jdk14page))
  }
  println((jdk14members - jdk8members).joinToString("\n"))
}

private fun fetchPage(url: String): String =
  try{ URL(url).readText() } catch(e: Exception) { fetchPage(url) }

private fun extractMembers(page: String) =
  Regex("span class=\"memberNameLink\".*?href=\"../(.*?)\\.html#(.*?)\">(.*?)</a>")
  .findAll(page).map { htmlDecode(it.groupValues[1].replace("/", ".") + "." + it.groupValues[3]) }

private fun htmlDecode(string: String) =
  string.replace("&lt;", "<").replace("&gt;", ">")