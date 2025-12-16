package com.goncalossilva.useanybrowser

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class UseAnyBrowserPluginTest {
    @BeforeTest
    fun publishToMavenLocal() {
        GradleRunner.create()
            .withProjectDir(File("."))
            .withArguments("publishToMavenLocal")
            .forwardOutput()
            .build()
    }

    @Test
    fun `browser tests pass with useAnyBrowser`() {
        val projectDir = File("build/test-project").apply {
            deleteRecursively()
            mkdirs()
        }

        File("src/test/resources/test-project").copyRecursively(projectDir)

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("jsBrowserTest", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":jsBrowserTest")?.outcome)
    }
}
