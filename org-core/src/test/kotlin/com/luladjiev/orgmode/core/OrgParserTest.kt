package com.luladjiev.orgmode.core

import com.luladjiev.orgmode.core.parser.OrgElementType
import com.luladjiev.orgmode.core.parser.OrgElementType.DONE_KEYWORD
import com.luladjiev.orgmode.core.parser.OrgElementType.HEADING
import com.luladjiev.orgmode.core.parser.OrgElementType.LINK
import com.luladjiev.orgmode.core.parser.OrgElementType.PARAGRAPH
import com.luladjiev.orgmode.core.parser.OrgElementType.PRIORITY
import com.luladjiev.orgmode.core.parser.OrgElementType.SECTION
import com.luladjiev.orgmode.core.parser.OrgElementType.SUBTREE
import com.luladjiev.orgmode.core.parser.OrgElementType.TITLE
import com.luladjiev.orgmode.core.parser.OrgElementType.TODO_KEYWORD
import com.luladjiev.orgmode.core.parser.SyntaxElement
import com.luladjiev.orgmode.core.parser.SyntaxTree
import com.luladjiev.orgmode.core.parser.TodoSequence
import com.luladjiev.orgmode.core.parser.TodoSequences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.random.Random

class OrgParserTest {
    @Test
    fun `subtree spans its heading, section, children and trailing blank lines`() {
        val text = "* A\nbody\n** B\n\n* C\n"
        val root = SyntaxTree.parse(text)
        val (a, c) = root.elements(SUBTREE)
        assertEquals("* A\nbody\n** B\n\n", text.of(a))
        assertEquals("* C\n", text.of(c))
        assertEquals("* A", text.of(a.elements(HEADING).single()))
        assertEquals("body\n", text.of(a.elements(SECTION).single()))
        assertEquals("** B\n\n", text.of(a.elements(SUBTREE).single()))
    }

    @Test
    fun `heading parts`() {
        val text = "** DONE [#B] Write [[x][tests]] :a:\n"
        val heading = SyntaxTree.parse(text).elements(SUBTREE).single().elements(HEADING).single()
        assertEquals("DONE", text.of(heading.elements(DONE_KEYWORD).single()))
        assertEquals("[#B]", text.of(heading.elements(PRIORITY).single()))
        val title = heading.elements(TITLE).single()
        assertEquals("Write [[x][tests]]", text.of(title))
        assertEquals("[[x][tests]]", text.of(title.elements(LINK).single()))
    }

    /** The Org Syntax spec puts the priority right after the keyword; Emacs 9.8 also accepts it later. */
    @Test
    fun `priority after a non-keyword word is title text`() {
        val text = "* Foo [#A] bar\n"
        val heading = SyntaxTree.parse(text).elements(SUBTREE).single().elements(HEADING).single()
        assertEquals(emptyList<SyntaxElement>(), heading.elements(PRIORITY))
        assertEquals("Foo [#A] bar", text.of(heading.elements(TITLE).single()))
    }

    /** Emacs ends a paragraph at a lone `*` line (its list-item separator); the spec does not, and neither do we. */
    @Test
    fun `lone star line continues a paragraph`() {
        val text = "a\n*\nb\n"
        assertEquals(1, SyntaxTree.parse(text).elements(SECTION).single().elements(PARAGRAPH).size)
    }

    @Test
    fun `todo keywords come from the document's sequences`() {
        val text = "#+TODO: NEXT | DONE\n* TODO a\n* NEXT b\n"
        val (a, b) = SyntaxTree.parse(text).elements(SUBTREE).map { it.elements(HEADING).single() }
        assertEquals(emptyList<SyntaxElement>(), a.elements(TODO_KEYWORD))
        assertEquals("NEXT", text.of(b.elements(TODO_KEYWORD).single()))
    }

    @Test
    fun `todo sequences`() {
        val parsed = TodoSequences.parse("#+todo: A(a) B | C(c@/!)\n  #+TYP_TODO: X Y\n#+SEQ_TODO:\n")
        assertEquals(listOf(TodoSequence(listOf("A", "B"), listOf("C")), TodoSequence(listOf("X"), listOf("Y"))), parsed.sequences)
        assertEquals(TodoSequences.Kind.DONE, parsed.classify("Y"))
        assertNull(parsed.classify("TODO"))
        assertEquals(TodoSequences.DEFAULT.sequences, TodoSequences.parse("* TODO x\n").sequences)
        // Declarations inside verbatim blocks are examples, not settings; an unterminated block is plain text.
        assertEquals(
            TodoSequences.DEFAULT.sequences,
            TodoSequences.parse("#+BEGIN_SRC org\n#+TODO: A | B\n#+END_SRC\n#+begin_example\n#+SEQ_TODO: C\n#+end_example\n").sequences,
        )
        assertEquals(
            listOf(TodoSequence(listOf("A"), listOf("B"))),
            TodoSequences.parse("#+BEGIN_QUOTE\n#+TODO: A | B\n#+END_QUOTE\n").sequences,
        )
        assertEquals(listOf(TodoSequence(listOf("A"), listOf("B"))), TodoSequences.parse("#+BEGIN_SRC\n#+TODO: A | B\n").sequences)
    }

    @Test
    fun `windows line endings`() {
        val text = "* A\r\n- item\r\n* B\r\n"
        val root = SyntaxTree.parse(text)
        ParserFixtureTest.assertLossless(text, root)
        assertEquals(listOf("* A\r\n- item\r\n", "* B\r\n"), root.elements(SUBTREE).map { text.of(it) })
    }

    @Test
    fun `empty and blank documents`() {
        for (text in listOf("", "\n", "  \n\n", "*", "* ", "-", "#+BEGIN_SRC", ":END:", "|", "[[")) {
            ParserFixtureTest.assertLossless(text, SyntaxTree.parse(text))
        }
    }

    /** Malformed input never fails and never loses text. */
    @Test
    fun `random mutations of the fixtures parse losslessly`() {
        val random = Random(42)
        val corpus = Fixtures.parserFixtures().map(Fixtures::read)
        val alphabet = "*#+:|-[]<>=~/_ \t\nabTODONE0123:END:#+BEGIN_SRC#+END_QUOTE"
        repeat(500) {
            val chars = StringBuilder(corpus.random(random))
            repeat(random.nextInt(1, 20)) {
                val at = random.nextInt(chars.length + 1)
                when (random.nextInt(3)) {
                    0 -> chars.insert(at, alphabet[random.nextInt(alphabet.length)])
                    1 -> if (at < chars.length) chars.deleteCharAt(at)
                    else -> {
                        val from = random.nextInt(chars.length)
                        chars.insert(at, chars.substring(from, (from + random.nextInt(40)).coerceAtMost(chars.length)))
                    }
                }
            }
            val text = chars.toString()
            ParserFixtureTest.assertLossless(text, SyntaxTree.parse(text))
        }
    }

    private fun SyntaxElement.elements(type: OrgElementType) =
        children.filterIsInstance<SyntaxElement>().filter { it.type == type }

    private fun String.of(element: SyntaxElement) = substring(element.startOffset, element.endOffset)
}
