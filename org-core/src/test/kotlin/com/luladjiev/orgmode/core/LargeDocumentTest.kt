package com.luladjiev.orgmode.core

import com.luladjiev.orgmode.core.edit.EditResult
import com.luladjiev.orgmode.core.edit.OrgSnapshot
import com.luladjiev.orgmode.core.edit.StructureEdits
import com.luladjiev.orgmode.core.parser.SyntaxTree
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Guards against accidental super-linear parsing on the 1 MB benchmark document. The limit is deliberately loose;
 * real numbers come from the JMH benchmarks.
 */
class LargeDocumentTest {
    @Test
    fun `parses a 1 MB document quickly`() {
        val text = LargeDocument.text
        SyntaxTree.parse(text)
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val start = System.nanoTime()
            val root = SyntaxTree.parse(text)
            println("Parsed ${text.length} chars in ${(System.nanoTime() - start) / 1_000_000} ms")
            ParserFixtureTest.assertLossless(text, root)
        }
    }

    @Test
    fun `moves a subtree in a 1 MB document quickly`() {
        val text = LargeDocument.text
        val offset = LargeDocument.lastTopLevelHeading(text)
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            val start = System.nanoTime()
            val result = StructureEdits.moveSubtreeUp(OrgSnapshot.parse(text), offset)
            println("Snapshot and Subtree move in ${(System.nanoTime() - start) / 1_000_000} ms")
            assertInstanceOf(EditResult.Applied::class.java, result)
        }
    }

    @Test
    fun `lexes long lines of unclosed markup quickly`() {
        // Every opener has no closer, so a lexer that searches the rest of the line from each one is quadratic.
        for (text in listOf("x " + "*a /a _a +a =a ~a ".repeat(15_000), "[[a][b ".repeat(30_000))) {
            assertTimeoutPreemptively(Duration.ofSeconds(5)) {
                val start = System.nanoTime()
                ParserFixtureTest.assertLossless(text, SyntaxTree.parse(text))
                println("Parsed ${text.length} chars of unclosed markup in ${(System.nanoTime() - start) / 1_000_000} ms")
            }
        }
    }
}
