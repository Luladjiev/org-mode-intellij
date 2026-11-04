package com.luladjiev.orgmode.core

import com.luladjiev.orgmode.core.parser.SyntaxElement
import com.luladjiev.orgmode.core.parser.SyntaxNode
import com.luladjiev.orgmode.core.parser.SyntaxToken
import com.luladjiev.orgmode.core.parser.SyntaxTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/** Compares our tree with Emacs `org-element` on each fixture, and checks the tree covers the text exactly. */
class ParserFixtureTest {
    @TestFactory
    fun `matches org-element`(): List<DynamicTest> = Fixtures.parserFixtures().map { path ->
        dynamicTest(path.fileName.toString()) {
            val text = Fixtures.read(path)
            assertEquals(Fixtures.expectedTree(path).trimEnd(), ReducedTree(text).render(SyntaxTree.parse(text)).trimEnd())
        }
    }

    @TestFactory
    fun `tree covers the text without gaps`(): List<DynamicTest> = Fixtures.parserFixtures().map { path ->
        dynamicTest(path.fileName.toString()) {
            val text = Fixtures.read(path)
            assertLossless(text, SyntaxTree.parse(text))
        }
    }

    companion object {
        /** Leaves are contiguous, non-empty and cover [text]; elements span exactly their children. */
        fun assertLossless(text: CharSequence, root: SyntaxElement) {
            var offset = 0
            fun visit(node: SyntaxNode) {
                when (node) {
                    is SyntaxToken -> {
                        assertEquals(offset, node.startOffset, "gap or overlap before $node")
                        check(node.endOffset > node.startOffset) { "empty token $node" }
                        offset = node.endOffset
                    }

                    is SyntaxElement -> {
                        assertEquals(offset, node.startOffset, "element start $node")
                        check(node === root || node.children.isNotEmpty()) { "empty element $node" }
                        node.children.forEach(::visit)
                        assertEquals(offset, node.endOffset, "element end $node")
                    }
                }
            }
            visit(root)
            assertEquals(text.length, offset)
        }
    }
}
