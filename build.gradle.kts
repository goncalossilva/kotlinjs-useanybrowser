import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.20"

    id("com.github.gmazzo.buildconfig") version "4.2.0"

    id("java-gradle-plugin")
    id("maven-publish")
    id("signing")
    id("com.gradle.plugin-publish") version "1.2.1"

    id("io.gitlab.arturbosch.detekt") version "1.23.3"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    compileOnly(kotlin("stdlib"))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(8)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
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
    // Use `signingKey` and `signingPassword` properties to sign artifacts, if provided.
    // Otherwise, default to `signing.keyId`, `signing.password` and `signing.secretKeyRingFile`.
    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKey != null && signingPassword != null) {
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

gradle.taskGraph.whenReady {
    val from = File("${rootProject.rootDir}/config/detekt/pre-commit")
    val to = File("${rootProject.rootDir}/.git/hooks/pre-commit")
    from.copyTo(to, overwrite = true)
    to.setExecutable(true)
}
