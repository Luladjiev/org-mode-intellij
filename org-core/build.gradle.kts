// Pure Kotlin: must not depend on the IntelliJ Platform or IdeaVim.
plugins {
    id("org.jetbrains.kotlin.jvm")
    // The generated 1 MB document, shared with the JMH benchmarks and org-intellij's performance test.
    `java-test-fixtures`
    id("me.champeau.jmh")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The IDE provides the Kotlin stdlib at runtime; don't package a second copy into the plugin.
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("stdlib"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testFixturesCompileOnly(kotlin("stdlib"))
    jmh(kotlin("stdlib"))
}

// `./gradlew :org-core:jmh`, results in build/results/jmh/results.txt. Not part of `check`.
jmh {
    jmhVersion = "1.37"
    fork = 1
    warmupIterations = 3
    warmup = "2s"
    iterations = 5
    timeOnIteration = "2s"
    resultFormat = "TEXT"
}

// Keeps the benchmarks compiling without running them.
tasks.check {
    dependsOn(tasks.named("jmhClasses"))
}

val fixtures = rootProject.layout.projectDirectory.dir("test-fixtures/org")

tasks.test {
    useJUnitPlatform()
    inputs.dir(fixtures).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("org.mode.fixtures", fixtures.asFile.absolutePath)
}
