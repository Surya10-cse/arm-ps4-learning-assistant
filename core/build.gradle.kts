plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.register<JavaExec>("exportCatalogue") {
    classpath=sourceSets.main.get().runtimeClasspath
    mainClass.set("ai.adapt.core.ExportCatalogueKt")
    args(rootProject.file("submission/catalogue.v2.json").absolutePath)
}
