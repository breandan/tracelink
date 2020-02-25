import astminer.ast.DotAstStorage
import astminer.common.model.LabeledPathContexts
import astminer.examples.forFilesWithSuffix
import astminer.parse.antlr.java.JavaMethodSplitter
import astminer.parse.antlr.java.JavaParser
import astminer.paths.CsvPathStorage
import astminer.paths.PathMiner
import astminer.paths.PathRetrievalSettings
import astminer.paths.toPathContext
import java.io.File

fun main(args: Array<String>) {
    val dir = "${args.first()}/preprocessed"
    val csvStorage = CsvPathStorage(dir)
    val dotStorage = DotAstStorage()
    File(args.first()).forFilesWithSuffix(".java") { file ->
        val miner = PathMiner(PathRetrievalSettings(5, 5))
        val parser = JavaParser()
        val root = parser.parse(file.inputStream())
        val paths = miner.retrievePaths(root ?: return@forFilesWithSuffix)
        JavaMethodSplitter().splitIntoMethods(root).forEach {
            println(it.name())
            println(it.returnType())
            println(it.enclosingElementName())
            it.methodParameters.forEach { parameters ->
                println("${parameters.name()} ${parameters.returnType()}")
            }
        }

        dotStorage.store(root, root.getTypeLabel())
        csvStorage.store(LabeledPathContexts(file.path, paths.map { toPathContext(it) }))
    }
    dotStorage.save(dir)
    csvStorage.save()
}