import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.7.10"
  application
  id("com.github.ben-manes.versions") version "0.42.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  maven(url = "https://dl.bintray.com/egor-bogomolov/astminer/")
  maven("https://jitpack.io")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
  implementation("org.jsoup:jsoup:1.15.2")
  implementation("me.xdrop:fuzzywuzzy:1.4.0")
  implementation("com.google.guava:guava:31.1-jre")
  implementation("org.apache.commons:commons-compress:1.21")
  implementation("org.apache.commons:commons-vfs2:2.9.0")
  implementation("org.apache.lucene:lucene-core:9.2.0")
  implementation("org.apache.lucene:lucene-queryparser:9.2.0")
  implementation("org.apache.lucene:lucene-analyzers-common:8.11.2")
  implementation("io.github.vovak.astminer:astminer:0.6")
//  implementation("edu.stanford.nlp:stanford-corenlp:3.9.2")
//  implementation("edu.stanford.nlp:stanford-corenlp:3.9.2:models")
//  implementation("edu.stanford.nlp:stanford-corenlp:3.9.2:models-english")

  implementation("com.github.breandan:progex:master-SNAPSHOT")
  implementation("com.github.ghaffarian:nanologger:master-SNAPSHOT")
  implementation("com.github.ghaffarian:graphs:master-SNAPSHOT")
  implementation("com.github.breandan:kaliningraph:0.0.2")
  implementation("com.github.ISCAS-PMC:roll-library:-SNAPSHOT")
}

tasks {
  listOf("ParseDocs", "ParseLinks", "ParseQueries", "FetchNewFiles",
      "QueryDocs", "ParseJava", "ParseEnglish", "HelloProgex", "HelloKaliningraph").forEach {
    register(it, JavaExec::class) {
      findProperty("retty")?.let { args = listOf("1") }
      findProperty("rocess")?.let { args = listOf(it.toString()) }
      main = "${it}Kt"
      classpath = sourceSets["main"].runtimeClasspath
    }
  }

  withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
  }
}

configure<ApplicationPluginConvention> {
  mainClassName = "ParseLinksKt"
}