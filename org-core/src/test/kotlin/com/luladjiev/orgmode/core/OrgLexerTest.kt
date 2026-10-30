package com.luladjiev.orgmode.core

import com.luladjiev.orgmode.core.lexer.OrgLexer
import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_BEGIN
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_CONTENT
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_END
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_PARAMETERS
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BOLD
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BULLET
import com.luladjiev.orgmode.core.lexer.OrgTokenType.CHECKBOX
import com.luladjiev.orgmode.core.lexer.OrgTokenType.CODE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.ITALIC
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_BRACKET
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_DESCRIPTION
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_PATH
import com.luladjiev.orgmode.core.lexer.OrgTokenType.NEWLINE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PLAIN_LINK
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PLANNING_KEYWORD
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PRIORITY_COOKIE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.STARS
import com.luladjiev.orgmode.core.lexer.OrgTokenType.STRIKE_THROUGH
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TAGS
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TEXT
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TIMESTAMP
import com.luladjiev.orgmode.core.lexer.OrgTokenType.UNDERLINE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.VERBATIM
import com.luladjiev.orgmode.core.lexer.OrgTokenType.WHITESPACE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

class OrgLexerTest {
    @Test
    fun `heading line`() {
        assertTokens(
            "*** TODO [#A] Title text :a:b:",
            STARS to "***", WHITESPACE to " ", TEXT to "TODO", WHITESPACE to " ", PRIORITY_COOKIE to "[#A]",
            WHITESPACE to " ", TEXT to "Title", WHITESPACE to " ", TEXT to "text", WHITESPACE to " ", TAGS to ":a:b:",
        )
    }

    @Test
    fun `priority cookie only within the first two words of a heading`() {
        assertEquals(listOf(TEXT, TEXT, TEXT), types("* a b [#A]").filter { it != WHITESPACE && it != STARS })
        assertEquals(listOf(TEXT, TEXT), types("[#A] x").filter { it != WHITESPACE })
    }

    @Test
    fun `emphasis follows pre and post rules`() {
        assertEquals(
            listOf(BOLD, ITALIC, UNDERLINE, STRIKE_THROUGH, VERBATIM, CODE),
            types("*b* /i/ _u_ +s+ =v= ~c~").filter { it != WHITESPACE },
        )
        assertTokens("(*b*),", TEXT to "(", BOLD to "*b*", TEXT to "),")
        assertTokens("a*b*", TEXT to "a*b*")
        assertTokens("* b*", STARS to "*", WHITESPACE to " ", TEXT to "b*")
        assertTokens("x *b *", TEXT to "x", WHITESPACE to " ", TEXT to "*b", WHITESPACE to " ", TEXT to "*")
        assertTokens("x *a*b*", TEXT to "x", WHITESPACE to " ", BOLD to "*a*b*")
        assertTokens("=a=b c", TEXT to "=a=b", WHITESPACE to " ", TEXT to "c")
    }

    @Test
    fun `emphasis does not span lines`() {
        assertEquals(listOf(TEXT, NEWLINE, TEXT), types("*a\nb*"))
    }

    @Test
    fun links() {
        assertTokens(
            "[[https://orgmode.org][Org mode]]",
            LINK_BRACKET to "[[", LINK_PATH to "https://orgmode.org", LINK_BRACKET to "][",
            LINK_DESCRIPTION to "Org mode", LINK_BRACKET to "]]",
        )
        assertTokens("[[file:a.org]]", LINK_BRACKET to "[[", LINK_PATH to "file:a.org", LINK_BRACKET to "]]")
        assertTokens("see https://example.com/x.", TEXT to "see", WHITESPACE to " ", PLAIN_LINK to "https://example.com/x", TEXT to ".")
        assertTokens("xhttps://a", TEXT to "xhttps://a")
        assertTokens("[[unclosed", TEXT to "[[unclosed")
    }

    @Test
    fun timestamps() {
        assertTokens("<2026-10-28 Wed>", TIMESTAMP to "<2026-10-28 Wed>")
        assertTokens("[2026-10-28 Wed 10:00]", TIMESTAMP to "[2026-10-28 Wed 10:00]")
        assertTokens("<2026-10-28>--<2026-10-30>", TIMESTAMP to "<2026-10-28>--<2026-10-30>")
        assertTokens("<2026-10-28 Wed]", TEXT to "<2026-10-28", WHITESPACE to " ", TEXT to "Wed]")
    }

    @Test
    fun `planning line`() {
        assertTokens(
            "SCHEDULED: <2026-10-28 Wed> DEADLINE: <2026-10-30 Fri>",
            PLANNING_KEYWORD to "SCHEDULED:", WHITESPACE to " ", TIMESTAMP to "<2026-10-28 Wed>", WHITESPACE to " ",
            PLANNING_KEYWORD to "DEADLINE:", WHITESPACE to " ", TIMESTAMP to "<2026-10-30 Fri>",
        )
    }

    @Test
    fun `list items and checkboxes`() {
        assertTokens("- [X] done", BULLET to "-", WHITESPACE to " ", CHECKBOX to "[X]", WHITESPACE to " ", TEXT to "done")
        assertTokens("12) [-]", BULLET to "12)", WHITESPACE to " ", CHECKBOX to "[-]")
        assertTokens("- [x] lowercase", BULLET to "-", WHITESPACE to " ", TEXT to "[x]", WHITESPACE to " ", TEXT to "lowercase")
        assertTokens("-not a bullet", TEXT to "-not")
        assertTokens("  * star", WHITESPACE to "  ", BULLET to "*", WHITESPACE to " ", TEXT to "star")
    }

    @Test
    fun `verbatim block is lexed only with its end line`() {
        assertTokens(
            "#+BEGIN_SRC sh\n*not bold*\n#+END_SRC",
            BLOCK_BEGIN to "#+BEGIN_SRC", WHITESPACE to " ", BLOCK_PARAMETERS to "sh", NEWLINE to "\n",
            BLOCK_CONTENT to "*not bold*", NEWLINE to "\n", BLOCK_END to "#+END_SRC",
        )
        assertEquals(TEXT, types("#+BEGIN_SRC sh\n*bold*\n").first())
        assertEquals(listOf(TEXT, WHITESPACE, TEXT, NEWLINE, STARS), types("#+BEGIN_SRC sh\n* H\n#+END_SRC").take(5))
    }

    @Test
    fun `windows line endings`() {
        assertTokens("* A\r\nb\r\n", STARS to "*", WHITESPACE to " ", TEXT to "A", NEWLINE to "\r\n", TEXT to "b", NEWLINE to "\r\n")
    }

    /** An editor highlighter restarts at any token in the initial state and must reproduce the same tokens. */
    @TestFactory
    fun `restarting at initial-state tokens matches a full run`(): List<DynamicTest> =
        Fixtures.parserFixtures().map { path ->
            dynamicTest(path.fileName.toString()) {
                val text = Fixtures.read(path)
                val full = lex(text, 0, OrgLexer.STATE_INITIAL)
                for ((i, token) in full.withIndex()) {
                    if (token.state != OrgLexer.STATE_INITIAL) continue
                    assertEquals(full.drop(i), lex(text, token.start, OrgLexer.STATE_INITIAL), "restart at ${token.start}")
                }
                // Resuming mid-line re-lexes the line; inside a verbatim block that is not supported (see OrgLexer).
                val lineStarts = full.filter { it.state == OrgLexer.STATE_INITIAL }.map { it.start }.toSet()
                val inside = full.withIndex().filter {
                    it.value.state == OrgLexer.STATE_INSIDE && lineStartOf(text, it.value.start) in lineStarts
                }
                for ((i, token) in inside) {
                    assertEquals(full.drop(i), lex(text, token.start, OrgLexer.STATE_INSIDE), "resume at ${token.start}")
                }
            }
        }

    private data class Token(val type: OrgTokenType, val start: Int, val end: Int, val state: Int)

    private fun lex(text: String, start: Int, state: Int): List<Token> {
        val lexer = OrgLexer()
        lexer.start(text, start, text.length, state)
        return buildList {
            while (true) {
                val type = lexer.tokenType ?: break
                add(Token(type, lexer.tokenStart, lexer.tokenEnd, lexer.state))
                lexer.advance()
            }
        }
    }

    private fun lineStartOf(text: String, offset: Int): Int {
        var i = offset
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }

    private fun types(text: String) = lex(text, 0, 0).map { it.type }

    private fun assertTokens(text: String, vararg expected: Pair<OrgTokenType, String>) {
        val actual = lex(text, 0, 0).map { it.type to text.substring(it.start, it.end) }
        assertEquals(expected.toList(), actual.take(expected.size), text)
    }
}
