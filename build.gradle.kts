import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jsoup:jsoup:1.12.1")
    implementation("com.google.guava:guava:28.1-jre")
    implementation("org.apache.commons:commons-compress:1.19")
    implementation("org.apache.commons:commons-vfs2:2.4.1")
    implementation("org.apache.lucene:lucene-core:8.2.0")
    implementation("org.apache.lucene:lucene-queryparser:8.2.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

configure<ApplicationPluginConvention> {
    mainClassName = "ParseLinksKt"
}