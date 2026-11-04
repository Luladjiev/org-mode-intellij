package com.luladjiev.orgmode.core.parser

import com.luladjiev.orgmode.core.lexer.OrgLexer
import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_BEGIN
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_END
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BULLET
import com.luladjiev.orgmode.core.lexer.OrgTokenType.CHECKBOX
import com.luladjiev.orgmode.core.lexer.OrgTokenType.DRAWER_END
import com.luladjiev.orgmode.core.lexer.OrgTokenType.DRAWER_NAME
import com.luladjiev.orgmode.core.lexer.OrgTokenType.KEYWORD_NAME
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_BRACKET
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_DESCRIPTION
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_PATH
import com.luladjiev.orgmode.core.lexer.OrgTokenType.NEWLINE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PLANNING_KEYWORD
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PRIORITY_COOKIE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PROPERTY_NAME
import com.luladjiev.orgmode.core.lexer.OrgTokenType.STARS
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TABLE_BAR
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TABLE_RULE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TAGS
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TEXT
import com.luladjiev.orgmode.core.lexer.OrgTokenType.WHITESPACE

/**
 * The Org grammar (docs/decisions/0001). Follows the Org Syntax spec, with Emacs `org-element` deciding where the
 * spec is ambiguous (the fixtures in `test-fixtures/org/parser` pin those cases).
 *
 * The grammar never fails: text that is not a recognised element becomes a paragraph, and an unterminated block or
 * drawer is plain text, so malformed content only affects the lines up to the next Heading.
 */
class OrgParser private constructor(private val b: TreeBuilder) {
    private val todo = TodoSequences.parse(b.text)

    /** Lines that end an element's contents: a drawer's `:END:`, a block's `#+END_…`, an item's dedent. */
    private sealed class Container(val parent: Container?) {
        class Drawer(parent: Container?) : Container(parent)
        class GreaterBlock(parent: Container?, val name: String) : Container(parent)
        class Item(parent: Container?, val indent: Int) : Container(parent)
    }

    private fun parseDocument() {
        parseSection(afterHeading = false)
        while (type() != null) parseSubtree()
    }

    // Headings

    private fun parseSubtree() {
        val level = tokenLength(0)
        val subtree = b.mark()
        parseHeading()
        if (type() == NEWLINE) b.advance()
        parseSection(afterHeading = true)
        // A skipped level (`*` then `***`) nests under the nearest shallower Heading.
        while (type() == STARS && tokenLength(0) > level) parseSubtree()
        subtree.done(OrgElementType.SUBTREE)
    }

    private fun parseHeading() {
        val heading = b.mark()
        b.advance()
        skip(WHITESPACE)
        if (type() == TEXT && type(1).let { it == null || it == WHITESPACE || it == NEWLINE }) {
            val kind = todo.classify(tokenText(0))
            if (kind != null) {
                val keyword = b.mark()
                b.advance()
                keyword.done(if (kind == TodoSequences.Kind.DONE) OrgElementType.DONE_KEYWORD else OrgElementType.TODO_KEYWORD)
                skip(WHITESPACE)
            }
        }
        if (type() == PRIORITY_COOKIE) {
            val priority = b.mark()
            b.advance()
            priority.done(OrgElementType.PRIORITY)
            skip(WHITESPACE)
        }
        if (isTitleToken(0)) {
            val title = b.mark()
            while (isTitleToken(0)) advanceInline()
            title.done(OrgElementType.TITLE)
        }
        skip(WHITESPACE)
        skip(TAGS)
        skip(WHITESPACE)
        heading.done(OrgElementType.HEADING)
    }

    private fun isTitleToken(k: Int): Boolean = when (type(k)) {
        null, NEWLINE, TAGS -> false
        WHITESPACE -> type(k + 1).let { it != null && it != NEWLINE && it != TAGS }
        else -> true
    }

    // Sections and elements

    private fun parseSection(afterHeading: Boolean) {
        val section = b.mark()
        var hasElements = false
        if (afterHeading) {
            if (lineType(0) == PLANNING_KEYWORD) {
                parseLine(OrgElementType.PLANNING)
                hasElements = true
            }
            if (isPropertyDrawer()) {
                parsePropertyDrawer()
                hasElements = true
            }
        }
        if (parseElements(null)) hasElements = true
        if (hasElements) section.done(OrgElementType.SECTION) else section.drop()
    }

    /** Parses elements until a Heading, the end, or the end of [container]; returns whether it parsed any. */
    private fun parseElements(container: Container?): Boolean {
        var parsed = false
        while (true) {
            if (type() == null || type() == STARS) return parsed
            if (isBlankLine(0)) {
                if (!blankLinesBelongTo(container)) return parsed
                while (isBlankLine(0) && type() != null) skipLine()
                continue
            }
            if (ends(container, 0)) return parsed
            parseElement(container)
            parsed = true
        }
    }

    private fun parseElement(container: Container?) {
        when (lineType(0)) {
            BULLET -> parsePlainList(container)
            KEYWORD_NAME -> parseKeyword()
            BLOCK_BEGIN -> if (isBlock(0)) parseBlock(container) else parseParagraph(container)
            DRAWER_NAME -> if (isDrawer(0, container)) parseDrawer(container) else parseParagraph(container)
            OrgTokenType.COMMENT -> parseConsecutive(OrgElementType.COMMENT, OrgTokenType.COMMENT, container)
            OrgTokenType.FIXED_WIDTH -> parseConsecutive(OrgElementType.FIXED_WIDTH, OrgTokenType.FIXED_WIDTH, container)
            TABLE_BAR, TABLE_RULE -> parseTable(container)
            OrgTokenType.HORIZONTAL_RULE -> parseLine(OrgElementType.HORIZONTAL_RULE)
            else -> parseParagraph(container)
        }
    }

    /** Whether the line at [k] starts an element, which ends a paragraph. */
    private fun startsElement(k: Int, container: Container?): Boolean = when (lineType(k)) {
        BULLET, KEYWORD_NAME, OrgTokenType.COMMENT, OrgTokenType.FIXED_WIDTH, TABLE_BAR, TABLE_RULE,
        OrgTokenType.HORIZONTAL_RULE -> true

        BLOCK_BEGIN -> isBlock(k)
        DRAWER_NAME -> isDrawer(k, container)
        else -> false
    }

    private fun parseParagraph(container: Container?) {
        val paragraph = b.mark()
        skipLine()
        while (type() != null && type() != STARS && !isBlankLine(0) && !ends(container, 0) &&
            !startsElement(0, container)
        ) {
            skipLine()
        }
        paragraph.done(OrgElementType.PARAGRAPH)
    }

    private fun parseKeyword() {
        val name = tokenText(firstOnLine(0)).toString()
        val key = name.substring(2, name.length - 1).uppercase()
        val next = nextLine(0)
        val affiliated = (key in AFFILIATED_KEYWORDS || key.startsWith("ATTR_")) &&
            next >= 0 && type(next) != null && type(next) != STARS && !isBlankLine(next)
        parseLine(if (affiliated) OrgElementType.AFFILIATED_KEYWORD else OrgElementType.KEYWORD)
    }

    private fun parseConsecutive(element: OrgElementType, token: OrgTokenType, container: Container?) {
        val marker = b.mark()
        skipLine()
        while (lineType(0) == token && !ends(container, 0)) skipLine()
        marker.done(element)
    }

    private fun parseLine(element: OrgElementType) {
        val marker = b.mark()
        skipLine()
        marker.done(element)
    }

    // Drawers

    private fun isPropertyDrawer(): Boolean {
        if (lineType(0) != DRAWER_NAME || !tokenText(firstOnLine(0)).toString().equals(":PROPERTIES:", true)) {
            return false
        }
        var k = nextLine(0)
        while (k >= 0) {
            when (lineType(k)) {
                DRAWER_END -> return true
                PROPERTY_NAME, DRAWER_NAME -> k = nextLine(k)
                else -> return false
            }
        }
        return false
    }

    private fun parsePropertyDrawer() {
        val drawer = b.mark()
        skipLine()
        while (lineType(0) != DRAWER_END) parseLine(OrgElementType.NODE_PROPERTY)
        skipLine()
        drawer.done(OrgElementType.PROPERTY_DRAWER)
    }

    /** A `:NAME:` line opens a drawer when an `:END:` line follows before the next Heading; drawers do not nest. */
    private fun isDrawer(k: Int, container: Container?): Boolean {
        if (generateSequence(container) { it.parent }.any { it is Container.Drawer }) return false
        return anyLineBeforeHeading(k) { lineType(it) == DRAWER_END }
    }

    private fun parseDrawer(container: Container?) {
        val drawer = b.mark()
        skipLine()
        parseElements(Container.Drawer(container))
        if (lineType(0) == DRAWER_END) skipLine()
        drawer.done(OrgElementType.DRAWER)
    }

    // Blocks

    /**
     * The lexer only emits a verbatim [BLOCK_BEGIN] when its end line exists; other blocks hold elements and need
     * a matching `#+END_NAME` before the next Heading.
     */
    private fun isBlock(k: Int): Boolean {
        val name = blockName(firstOnLine(k))
        return OrgLexer.isVerbatimBlock(name) || anyLineBeforeHeading(k) { isBlockEnd(it, name) }
    }

    private fun isBlockEnd(k: Int, name: String): Boolean {
        val first = firstOnLine(k)
        return type(first) == BLOCK_END && tokenText(first).substring(END_PREFIX_LENGTH).equals(name, true)
    }

    private fun blockName(k: Int) = tokenText(k).substring(BEGIN_PREFIX_LENGTH)

    private fun parseBlock(container: Container?) {
        val block = b.mark()
        val name = blockName(firstOnLine(0))
        skipLine()
        if (OrgLexer.isVerbatimBlock(name)) {
            while (type() != null && lineType(0) != BLOCK_END) skipLine()
        } else {
            parseElements(Container.GreaterBlock(container, name))
        }
        if (isBlockEnd(0, name)) skipLine()
        block.done(OrgElementType.BLOCK)
    }

    // Tables

    private fun parseTable(container: Container?) {
        val table = b.mark()
        while (lineType(0).let { it == TABLE_BAR || it == TABLE_RULE } && !ends(container, 0)) {
            parseLine(OrgElementType.TABLE_ROW)
        }
        // `#+TBLFM:` lines belong to the table above them.
        while (lineType(0) == KEYWORD_NAME && tokenText(firstOnLine(0)).toString().equals("#+TBLFM:", true)) {
            skipLine()
        }
        table.done(OrgElementType.TABLE)
    }

    // Lists

    private fun parsePlainList(container: Container?) {
        val indent = indentation(0)
        val list = b.mark()
        while (lineType(0) == BULLET && indentation(0) == indent && !ends(container, 0)) parseItem(indent, container)
        list.done(OrgElementType.PLAIN_LIST)
    }

    private fun parseItem(indent: Int, container: Container?) {
        val item = b.mark()
        skip(WHITESPACE)
        b.advance()
        skip(WHITESPACE)
        if (type() == CHECKBOX) {
            b.advance()
            skip(WHITESPACE)
        }
        val itemContainer = Container.Item(container, indent)
        if (type() != null && type() != NEWLINE) parseParagraph(itemContainer) else skip(NEWLINE)
        parseElements(itemContainer)
        item.done(OrgElementType.ITEM)
    }

    /**
     * Blank lines stay inside a list item only when the item, or a sibling item, continues after them; two blank
     * lines end the whole list, as in Emacs.
     */
    private fun blankLinesBelongTo(container: Container?): Boolean {
        if (container !is Container.Item) return true
        var blanks = 0
        var k = 0
        while (k >= 0 && type(k) != null && isBlankLine(k)) {
            blanks++
            k = nextLine(k)
        }
        if (blanks >= 2 || k < 0 || type(k) == null || type(k) == STARS) return false
        val indent = indentation(k)
        return when {
            indent > container.indent -> !ends(container.parent, k)
            indent == container.indent -> lineType(k) == BULLET
            else -> false
        }
    }

    private fun ends(container: Container?, k: Int): Boolean {
        var c = container
        while (c != null) {
            val ended = when (c) {
                is Container.Drawer -> lineType(k) == DRAWER_END
                is Container.GreaterBlock -> isBlockEnd(k, c.name)
                is Container.Item -> !isBlankLine(k) && indentation(k) <= c.indent
            }
            if (ended) return true
            c = c.parent
        }
        return false
    }

    // Token helpers; `k` is a token offset from the current token, at a line start unless noted.

    private fun type(k: Int = 0): OrgTokenType? = b.lookAhead(k)

    private fun tokenText(k: Int): CharSequence = b.text.subSequence(b.lookAheadStart(k), b.lookAheadStart(k + 1))

    private fun tokenLength(k: Int) = b.lookAheadStart(k + 1) - b.lookAheadStart(k)

    private fun skip(type: OrgTokenType) {
        if (type() == type) b.advance()
    }

    /** Advances past the current token, grouping a bracket link into a [OrgElementType.LINK]. */
    private fun advanceInline() {
        if (type() != LINK_BRACKET) {
            b.advance()
            return
        }
        val link = b.mark()
        b.advance()
        while (type() == LINK_PATH || type() == LINK_DESCRIPTION || type() == LINK_BRACKET) {
            val closing = type() == LINK_BRACKET && tokenText(0).toString() == "]]"
            b.advance()
            if (closing) break
        }
        link.done(OrgElementType.LINK)
    }

    /** Advances past the rest of the current line, including its newline. */
    private fun skipLine() {
        while (type() != null && type() != NEWLINE) advanceInline()
        skip(NEWLINE)
    }

    private fun firstOnLine(k: Int) = if (type(k) == WHITESPACE) k + 1 else k

    /** Type of the first token after the line's indentation. */
    private fun lineType(k: Int): OrgTokenType? = type(firstOnLine(k))

    private fun isBlankLine(k: Int): Boolean = lineType(k).let { it == null || it == NEWLINE }

    /** Offset of the line after the one at [k], or -1 at the end. */
    private fun nextLine(k: Int): Int {
        var i = k
        while (true) {
            when (type(i)) {
                null -> return -1
                NEWLINE -> return i + 1
                else -> i++
            }
        }
    }

    /** Whether a line after the one at [k] and before the next Heading satisfies [predicate]. */
    private inline fun anyLineBeforeHeading(k: Int, predicate: (line: Int) -> Boolean): Boolean {
        var line = nextLine(k)
        while (line >= 0 && type(line) != null && type(line) != STARS) {
            if (predicate(line)) return true
            line = nextLine(line)
        }
        return false
    }

    /** Indentation width of the line at [k], with tabs stopping every 8 columns as in Emacs. */
    private fun indentation(k: Int): Int {
        if (type(k) != WHITESPACE) return 0
        var width = 0
        for (c in tokenText(k)) width = if (c == '\t') (width / TAB_WIDTH + 1) * TAB_WIDTH else width + 1
        return width
    }

    companion object {
        private const val TAB_WIDTH = 8
        private const val BEGIN_PREFIX_LENGTH = "#+BEGIN_".length
        private const val END_PREFIX_LENGTH = "#+END_".length

        private val AFFILIATED_KEYWORDS = setOf(
            "CAPTION", "DATA", "HEADER", "HEADERS", "LABEL", "NAME", "PLOT", "RESNAME", "RESULT", "RESULTS",
            "SOURCE", "SRCNAME", "TBLNAME",
        )

        /** Parses a whole document into [builder]; the caller wraps the result in its root element. */
        fun parse(builder: TreeBuilder) {
            OrgParser(builder).parseDocument()
            // The grammar consumes every token; this only guards the IntelliJ builder against a grammar bug.
            while (builder.lookAhead(0) != null) builder.advance()
        }
    }
}
