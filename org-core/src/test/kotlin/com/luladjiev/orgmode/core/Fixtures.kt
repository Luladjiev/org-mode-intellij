package com.luladjiev.orgmode.core

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/** The shared `test-fixtures/org` corpus; Gradle passes its location. */
object Fixtures {
    val root: Path = Path.of(
        checkNotNull(System.getProperty("org.mode.fixtures")) { "Run through Gradle: org.mode.fixtures is not set" },
    )

    /** Parser fixtures, each an `.org` file with the reduced org-element tree in a sibling `.tree` file. */
    fun parserFixtures(): List<Path> = root.resolve("parser").listDirectoryEntries("*.org").sorted()

    /** Structural edit inputs, `edit/OPERATION/NAME.org`, each with a sibling `NAME.expected.org`. */
    fun editFixtures(): List<Path> = root.resolve("edit").listDirectoryEntries().sorted().flatMap { operation ->
        operation.listDirectoryEntries("*.org").filterNot { it.fileName.toString().endsWith(".expected.org") }.sorted()
    }

    fun expectedEdit(input: Path): Path = input.resolveSibling(input.fileName.toString().removeSuffix(".org") + ".expected.org")

    fun read(path: Path): String = path.readText()

    /** The expected tree, without the generated header comment. */
    fun expectedTree(org: Path): String {
        val tree = org.resolveSibling(org.fileName.toString().removeSuffix(".org") + ".tree")
        check(Files.exists(tree)) { "Missing $tree: run `emacs --batch -l scripts/gen-fixtures.el`" }
        return tree.readText().lines().filterNot { it.startsWith("#") }.joinToString("\n")
    }
}
