import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"

    id("com.github.gmazzo.buildconfig") version "6.0.6"

    id("java-gradle-plugin")
    id("maven-publish")
    id("signing")
    id("com.gradle.plugin-publish") version "2.0.0"

    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(kotlin("stdlib"))

    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

val artifactId: String by project
val pluginId = "$group.$artifactId"

ext["gradle.publish.key"] = property("gradlePublishKey") as String
ext["gradle.publish.secret"] = property("gradlePublishSecret") as String

buildConfig {
    packageName.set(pluginId)
    buildConfigField("String", "GROUP_ID", "\"$group\"")
    buildConfigField("String", "ARTIFACT_ID", "\"$artifactId\"")
    buildConfigField("String", "PLUGIN_ID", "\"$pluginId\"")
    buildConfigField("String", "VERSION", "\"$version\"")
    useKotlinOutput { internalVisibility = true }
}

gradlePlugin {
    val publicationUrl: String by project
    val publicationScmUrl: String by project

    website.set(publicationUrl)
    vcsUrl.set(publicationScmUrl)

    val resources by plugins.creating {
        val publicationDisplayName: String by project
        val publicationDescription: String by project
        val publicationTags: String by project

        id = pluginId
        implementationClass = "com.goncalossilva.useanybrowser.UseAnyBrowserPlugin"
        displayName = publicationDisplayName
        description = publicationDescription
        tags.set(publicationTags.split(','))
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    isRequired = signingKey != null && signingPassword != null
    if (isRequired) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications)
}

tasks.named("publish") {
    dependsOn("publishPlugins")
}

detekt {
    config.setFrom("config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

// Install git hooks automatically.
gradle.taskGraph.whenReady {
    val from = rootProject.file("config/detekt/pre-commit")
    val hooksDir = runCatching {
        val process = ProcessBuilder("git", "rev-parse", "--git-path", "hooks")
            .directory(rootProject.rootDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        val exitCode = process.waitFor()
        output.takeIf { exitCode == 0 && it.isNotBlank() }?.let(rootProject::file)
    }.getOrNull() ?: return@whenReady
    val to = hooksDir.resolve("pre-commit")
    to.parentFile.mkdirs()
    from.copyTo(to, overwrite = true)
    to.setExecutable(true)
}
