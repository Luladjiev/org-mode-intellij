import org.jetbrains.changelog.Changelog
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

// The plugin project: owns plugin.xml and assembles the installable plugin.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(21)
}

// `-PtestIdeaVimVersion=2.28.0` runs the tests against another IdeaVim release while org-ideavim stays
// compiled against ideaVimVersion, which checks the jar users get against the IdeaVim they have.
val testIdeaVimVersion: String = providers.gradleProperty("testIdeaVimVersion")
    .orElse(providers.gradleProperty("ideaVimVersion")).get()

// The composed org-ideavim module passes its own IdeaVim on to the test sandbox, and the newer version would win.
// org-intellij never compiles against IdeaVim, so pinning it here only changes which IdeaVim the tests load.
if (providers.gradleProperty("testIdeaVimVersion").isPresent) {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.jetbrains.plugins" && requested.name == "IdeaVIM") useVersion(testIdeaVimVersion)
        }
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation(project(":org-core"))
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)
    testImplementation(testFixtures(project(":org-core")))
    testRuntimeOnly(project(":org-ideavim"))

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        testFramework(TestFrameworkType.Platform)
        testPlugin("IdeaVIM", testIdeaVimVersion)

        // Composed into the plugin jar but kept off org-intellij's compile classpath: org-ideavim depends on
        // org-intellij, never the reverse.
        pluginComposedModule(project(":org-ideavim"))
    }
}

intellijPlatform {
    projectName = rootProject.name

    pluginConfiguration {
        // The plugin page's "What's New": this version's CHANGELOG.md section, or Unreleased until it is released.
        val changelog = project.changelog
        changeNotes = providers.gradleProperty("version").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased()).withHeader(false).withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        // Public APIs only. Deprecated APIs are allowed when documented, so they don't fail the build, and
        // dynamic loading is not required.
        failureLevel = FailureLevel.ALL - FailureLevel.DEPRECATED_API_USAGES - FailureLevel.NOT_DYNAMIC
        ides {
            providers.gradleProperty("verifyPlatformVersions").get().split(',').forEach {
                create(IntelliJPlatformType.IntellijIdea, it.trim())
            }
        }
    }
}

changelog {
    path = rootProject.file("CHANGELOG.md").path
}

val fixtures = rootProject.layout.projectDirectory.dir("test-fixtures/org")

fun Test.useFixtures() {
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("org.mode.fixtures", fixtures.asFile.absolutePath)
}

tasks.test {
    useFixtures()
    systemProperty("org.mode.test.ideaVimVersion", testIdeaVimVersion)
}

// `test` runs with IdeaVim installed; this task reruns the IdeaVim-independent tests with it disabled.
val testWithoutIdeaVim = intellijPlatformTesting.testIde.register("testWithoutIdeaVim") {
    plugins {
        disablePlugin("IdeaVIM")
    }
    task {
        useFixtures()
        systemProperty("org.mode.test.ideaVimExpected", "false")
        filter {
            excludeTestsMatching("*OrgVimExtensionTest")
            excludeTestsMatching("*UnsupportedIdeaVimTest")
        }
    }
}

tasks.check {
    dependsOn(testWithoutIdeaVim)
}
