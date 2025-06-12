package com.goncalossilva.useanybrowser

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Delete
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJsCompilation
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsSubTargetContainerDsl
import org.jetbrains.kotlin.gradle.targets.js.testing.karma.KotlinKarma

/**
 * Use the first available browser to run tests.
 *
 * Prefers headless variants and chooses Chrome when that option is available.
 */
fun KotlinKarma.useAnyBrowser() {
    // List all browsers so that the Kotlin plugin downloads their runners and captures failures.
    // "karma-detect-browsers" figures out which browser to use, depending on what's installed.
    useChromeHeadless()
    useChromeCanaryHeadless()
    useChromiumHeadless()
    useFirefoxHeadless()
    useFirefoxAuroraHeadless()
    useFirefoxDeveloperHeadless()
    useFirefoxNightlyHeadless()
    useOpera()
    useSafari()
    useIe()

    // Depend on "karma-detect-browsers" for browser selection.
    compilation.dependencies {
        implementation(npm("karma-detect-browsers", "^2.3"))
    }
}

class UseAnyBrowserPlugin : KotlinCompilerPluginSupportPlugin {
    override fun getCompilerPluginId() = BuildConfig.PLUGIN_ID

    override fun getPluginArtifact() = SubpluginArtifact(
        groupId = BuildConfig.GROUP_ID,
        artifactId = BuildConfig.ARTIFACT_ID,
        version = BuildConfig.VERSION
    )

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>) =
        kotlinCompilation is KotlinJsCompilation &&
            kotlinCompilation.target.isBrowserConfigured &&
            kotlinCompilation.compilationName == KotlinCompilation.TEST_COMPILATION_NAME

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        fun getTaskName(prefix: String, vararg qualifiers: String) =
            "$prefix${qualifiers.joinToString("") { it.replaceFirstChar(Char::titlecase) }}"

        val compilation = kotlinCompilation as KotlinJsCompilation
        val target = compilation.target
        val taskName = getTaskName("selectBrowser", target.targetName)
        val project = target.project
        val tasks = project.tasks
        val confFile = project.projectDir
            .resolve("karma.config.d")
            .apply { mkdirs() }
            .resolve("select-browser.js")

        val selectBrowserTask = tasks.register(taskName) { task ->
            @Suppress("ObjectLiteralToLambda")
            task.doLast(object : Action<Task> {
                override fun execute(_task: Task) {
                    // Create karma configuration file in the expected location, deleting when done.
                    confFile.printWriter().use { confWriter ->
                        @Suppress("MaxLineLength")
                        confWriter.println(
                            """
                            |config.frameworks.push("detectBrowsers");
                            |config.set({
                            |    browsers: [],
                            |    detectBrowsers: {
                            |        enabled: true,
                            |        usePhantomJS: false,
                            |        preferHeadless: true,
                            |        postDetection: function(browsers) {
                            |            browsers = browsers.filter((browser) => browser.includes("Headless")) || browsers;
                            |            browsers = browsers.filter((browser) => browser.includes("Chrom")) || browsers;
                            |            browsers = browsers.slice(0, 1);
                            |            return browsers;
                            |        }
                            |    }
                            |});
                            |config.plugins.push("karma-detect-browsers");
                            """.trimMargin()
                        )
                    }
                }
            })
            task.mustRunAfter(compilation.processResourcesTaskName)
        }
        tasks.named(compilation.compileKotlinTaskName).configure {
            it.dependsOn(selectBrowserTask)
        }

        val cleanupConfFileTask = tasks.register("${taskName}Cleanup", Delete::class.java) {
            it.delete = setOf(confFile)
        }
        tasks.named(getTaskName(compilation.target.name, "browser", compilation.name)) {
            it.finalizedBy(cleanupConfFileTask)
        }

        return project.provider { emptyList() }
    }
}
