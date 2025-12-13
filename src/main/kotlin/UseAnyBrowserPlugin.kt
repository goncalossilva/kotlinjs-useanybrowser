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
    // "karma-detect-browsers" will figure out which browser to use, depending on what's installed.
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
}

@Suppress("MaxLineLength")
private val SELECT_BROWSER_KARMA_CONFIG: String = """
    |config.frameworks = config.frameworks || [];
    |if (config.frameworks.indexOf("detectBrowsers") === -1) {
    |    config.frameworks.push("detectBrowsers");
    |}
    |
    |config.plugins = config.plugins || [];
    |if (config.plugins.indexOf("karma-detect-browsers") === -1) {
    |    config.plugins.push("karma-detect-browsers");
    |}
    |
    |var IS_LINUX = process.platform === "linux";
    |if (IS_LINUX) {
    |    config.customLaunchers = config.customLaunchers || {};
    |    // Some Linux CI/container environments can't use Chromium's sandbox ("No usable sandbox!" error).
    |    // Provide `--no-sandbox` variants and use them on Linux.
    |    config.customLaunchers.ChromeHeadlessNoSandbox = { base: "ChromeHeadless", flags: ["--no-sandbox"] };
    |    config.customLaunchers.ChromeCanaryHeadlessNoSandbox = { base: "ChromeCanaryHeadless", flags: ["--no-sandbox"] };
    |    config.customLaunchers.ChromiumHeadlessNoSandbox = { base: "ChromiumHeadless", flags: ["--no-sandbox"] };
    |}
    |
    |config.set({
    |    browsers: [],
    |    detectBrowsers: {
    |        enabled: true,
    |        usePhantomJS: false,
    |        preferHeadless: true,
    |        postDetection: function(browsers) {
    |            var candidates = browsers.filter(function (browser) {
    |                return browser.indexOf("Headless") !== -1;
    |            });
    |            if (!candidates.length) candidates = browsers;
    |
    |            var chrom = candidates.filter(function (browser) {
    |                return browser.indexOf("Chrom") !== -1;
    |            });
    |            if (chrom.length) candidates = chrom;
    |
    |            candidates = candidates.slice(0, 1);
    |
    |            if (IS_LINUX) {
    |                candidates = candidates.map(function (browser) {
    |                    if (browser === "ChromeHeadless") return "ChromeHeadlessNoSandbox";
    |                    if (browser === "ChromeCanaryHeadless") return "ChromeCanaryHeadlessNoSandbox";
    |                    if (browser === "ChromiumHeadless") return "ChromiumHeadlessNoSandbox";
    |                    return browser;
    |                });
    |            }
    |
    |            return candidates;
    |        }
    |    }
    |});
    |""".trimMargin()

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
        compilation.dependencies {
            implementation(npm("karma-detect-browsers", "^2.3"))
        }

        val target = compilation.target
        val taskName = getTaskName("selectBrowser", target.targetName)
        val project = target.project
        val tasks = project.tasks
        val confFile = project.projectDir
            .resolve("karma.config.d")
            .apply { mkdirs() }
            // Avoid cleanup races when multiple browser targets exist in the same project (e.g., js/wasmJs).
            .resolve("useanybrowser-${target.targetName}.js")

        val selectBrowserTask = tasks.register(taskName) { task ->
            @Suppress("ObjectLiteralToLambda")
            task.doLast(object : Action<Task> {
                override fun execute(_task: Task) {
                    // Create karma configuration file in the expected location, deleting when done.
                    confFile.printWriter().use { confWriter ->
                        confWriter.print(SELECT_BROWSER_KARMA_CONFIG)
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
