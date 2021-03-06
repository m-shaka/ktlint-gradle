package org.jlleitschuh.gradle.ktlint

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KtlintPluginTest : AbstractPluginTest() {

    @Before
    fun setupBuild() {
        projectRoot.apply {
            buildFile().writeText("""
                ${buildscriptBlockWithUnderTestPlugin()}

                ${pluginsBlockWithKotlinJvmPlugin()}

                apply plugin: "org.jlleitschuh.gradle.ktlint"

                repositories {
                    gradlePluginPortal()
                }

                ktlint.reporters = ["PLAIN", "CHECKSTYLE"]
            """.trimIndent())
        }
    }

    @Test
    fun `fails on versions older than 0_10`() {
        projectRoot.buildFile().appendText("""

            ktlint.version = "0.9.0"
        """.trimIndent())

        projectRoot.withCleanSources()

        buildAndFail("ktlintCheck").apply {
            assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.FAILED))
            assertThat(output, containsString("Ktlint versions less than 0.10.0 are not supported. Detected Ktlint version: 0.9.0."))
        }
    }

    @Test
    fun `should fail check on failing sources`() {
        projectRoot.withFailingSources()

        buildAndFail("ktlintCheck").apply {
            assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.FAILED))
            assertThat(output, containsString("Unnecessary space(s)"))
            assertReportCreated(ReporterType.PLAIN)
            assertReportCreated(ReporterType.CHECKSTYLE)
            assertReportNotCreated(ReporterType.JSON)
        }
    }

    @Test
    fun `creates multiple reports`() {
        projectRoot.withFailingSources()

        projectRoot.buildFile().appendText("""

            ktlint.reporters = ["PLAIN_GROUP_BY_FILE", "CHECKSTYLE", "JSON"]
        """.trimIndent())

        buildAndFail("ktlintCheck").apply {
            assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.FAILED))
            assertThat(output, containsString("Unnecessary space(s)"))
            assertReportCreated(ReporterType.PLAIN_GROUP_BY_FILE)
            assertReportCreated(ReporterType.CHECKSTYLE)
            assertReportCreated(ReporterType.JSON)
        }
    }

    @Test
    fun `is out of date when different report is enabled`() {
        projectRoot.withCleanSources()

        projectRoot.buildFile().appendText("""

            ktlint.reporters = ["JSON", property("reportType")]
        """.trimIndent())

        build("ktlintCheck", "-PreportType=PLAIN").apply {
            assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.SUCCESS))
            assertReportCreated(ReporterType.PLAIN)
            assertReportCreated(ReporterType.JSON)
        }

        build("ktlintCheck", "-PreportType=PLAIN").apply {
            assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.UP_TO_DATE))
            assertReportCreated(ReporterType.PLAIN)
            assertReportCreated(ReporterType.JSON)
            assertReportNotCreated(ReporterType.CHECKSTYLE)
        }

        build("ktlintCheck", "-PreportType=PLAIN_GROUP_BY_FILE").apply {
            assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.SUCCESS))
            assertReportCreated(ReporterType.PLAIN_GROUP_BY_FILE)
            assertReportCreated(ReporterType.JSON)
        }

        build("ktlintCheck", "-PreportType=CHECKSTYLE").apply {
            assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.SUCCESS))
            assertReportCreated(ReporterType.JSON)
            assertReportCreated(ReporterType.CHECKSTYLE)
            // TODO: Stale reports are not cleaned up
            assertReportCreated(ReporterType.PLAIN)
        }
    }

    @Test
    fun `check task is relocatable`() {
        val originalLocation = temporaryFolder.root.resolve("original")
        val relocatedLocation = temporaryFolder.root.resolve("relocated")
        val localBuildCacheDirectory = temporaryFolder.root.resolve("build-cache")
        listOf(originalLocation, relocatedLocation).forEach {
            it.apply {
                withCleanSources()
                buildFile().writeText("""
                    ${buildscriptBlockWithUnderTestPlugin()}

                    ${pluginsBlockWithKotlinJvmPlugin()}

                    apply plugin: "org.jlleitschuh.gradle.ktlint"

                    repositories {
                        gradlePluginPortal()
                    }

                    ktlint.reporters = ["PLAIN", "CHECKSTYLE"]
                """.trimIndent())
                settingsFile().writeText("""
                    buildCache {
                        local {
                            directory = '${localBuildCacheDirectory.toURI()}'
                        }
                    }
                """.trimIndent())
            }
        }

        GradleRunner.create()
                .withProjectDir(originalLocation)
                .withArguments("ktlintCheck", "--build-cache")
                .forwardOutput()
                .build().apply {
                    assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.SUCCESS))
                }

        GradleRunner.create()
                .withProjectDir(relocatedLocation)
                .withArguments("ktlintCheck", "--build-cache")
                .forwardOutput()
                .build().apply {
                    assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.FROM_CACHE))
                }
    }

    private
    fun assertReportCreated(reportType: ReporterType) {
        assertTrue(reportLocation(reportType).isFile)
    }

    private
    fun assertReportNotCreated(reportType: ReporterType) {
        assertFalse(reportLocation(reportType).isFile)
    }

    private fun reportLocation(reportType: ReporterType) =
            projectRoot.resolve("build/reports/ktlint/ktlint-main.${reportType.fileExtension}")

    @Test
    fun `should succeed check on clean sources`() {

        projectRoot.withCleanSources()

        build("ktlintCheck").apply {
            assertThat(task(":ktlintMainCheck")!!.outcome, equalTo(TaskOutcome.SUCCESS))
        }
    }
}
