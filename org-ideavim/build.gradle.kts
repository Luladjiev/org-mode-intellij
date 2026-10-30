import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform.module")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(project(":org-intellij"))
    compileOnly(project(":org-core"))
    testImplementation(libs.junit)

    intellijPlatform {
        intellijIdea(providers.gradleProperty("platformVersion"))
        // Compile-only: IdeaVim stays an optional runtime dependency of the plugin.
        plugin("IdeaVIM", providers.gradleProperty("ideaVimVersion").get())
        testFramework(TestFrameworkType.Platform)
    }
}
