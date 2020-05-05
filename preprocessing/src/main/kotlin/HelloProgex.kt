import ghaffarian.progex.CLI

fun main() {
  val args = arrayOf("-cfg", "-lang", "java", "-format", "dot", "/home/breandan/IdeaProjects/progex")
  CLI().parse(args).execute()
}