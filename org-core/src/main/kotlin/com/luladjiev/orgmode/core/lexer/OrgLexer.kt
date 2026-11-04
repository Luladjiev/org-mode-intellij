package com.luladjiev.orgmode.core.lexer

import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_BEGIN
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_CONTENT
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_END
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BLOCK_PARAMETERS
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BOLD
import com.luladjiev.orgmode.core.lexer.OrgTokenType.BULLET
import com.luladjiev.orgmode.core.lexer.OrgTokenType.CHECKBOX
import com.luladjiev.orgmode.core.lexer.OrgTokenType.CODE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.COMMENT
import com.luladjiev.orgmode.core.lexer.OrgTokenType.DRAWER_END
import com.luladjiev.orgmode.core.lexer.OrgTokenType.DRAWER_NAME
import com.luladjiev.orgmode.core.lexer.OrgTokenType.FIXED_WIDTH
import com.luladjiev.orgmode.core.lexer.OrgTokenType.HORIZONTAL_RULE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.ITALIC
import com.luladjiev.orgmode.core.lexer.OrgTokenType.KEYWORD_NAME
import com.luladjiev.orgmode.core.lexer.OrgTokenType.KEYWORD_VALUE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_BRACKET
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_DESCRIPTION
import com.luladjiev.orgmode.core.lexer.OrgTokenType.LINK_PATH
import com.luladjiev.orgmode.core.lexer.OrgTokenType.NEWLINE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PLAIN_LINK
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PLANNING_KEYWORD
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PRIORITY_COOKIE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PROPERTY_NAME
import com.luladjiev.orgmode.core.lexer.OrgTokenType.PROPERTY_VALUE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.STARS
import com.luladjiev.orgmode.core.lexer.OrgTokenType.STRIKE_THROUGH
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TABLE_BAR
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TABLE_RULE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TAGS
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TEXT
import com.luladjiev.orgmode.core.lexer.OrgTokenType.TIMESTAMP
import com.luladjiev.orgmode.core.lexer.OrgTokenType.UNDERLINE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.VERBATIM
import com.luladjiev.orgmode.core.lexer.OrgTokenType.WHITESPACE

/**
 * Line-oriented Org lexer with the restartable shape of an IntelliJ lexer, so `org-intellij` can adapt it directly.
 *
 * Tokens are produced one chunk at a time: a single line, or a whole verbatim block from `#+BEGIN_SRC` through its
 * `#+END_SRC`. The first token of a chunk reports [STATE_INITIAL] and every other token [STATE_INSIDE], so an editor
 * highlighter only restarts lexing at line starts outside verbatim blocks.
 *
 * Inline markup is matched within a single line. Whether `#+BEGIN_SRC` opens a block depends on a matching end line
 * further down, so when the end line of an unterminated block is typed, the highlighter (which restarts at the edit)
 * only picks it up once lexing restarts above the block; the parser always re-lexes from the start and is unaffected.
 */
class OrgLexer {
    var buffer: CharSequence = ""
        private set
    var bufferEnd = 0
        private set

    private var types = arrayOfNulls<OrgTokenType>(INITIAL_CAPACITY)
    private var starts = IntArray(INITIAL_CAPACITY)
    private var ends = IntArray(INITIAL_CAPACITY)
    private var count = 0
    private var index = 0
    private var chunkEnd = 0

    // Where a search for a closing `]]` or emphasis marker failed in the current [lexInline] run: none follows.
    private var noLinkEndFrom = 0
    private val noEmphasisEndFrom = IntArray(EMPHASIS_MARKERS.length)

    val tokenType: OrgTokenType? get() = if (index < count) types[index] else null
    val tokenStart: Int get() = if (index < count) starts[index] else bufferEnd
    val tokenEnd: Int get() = if (index < count) ends[index] else bufferEnd
    val state: Int get() = if (index == 0) STATE_INITIAL else STATE_INSIDE

    fun start(
        buffer: CharSequence,
        startOffset: Int = 0,
        endOffset: Int = buffer.length,
        initialState: Int = STATE_INITIAL,
    ) {
        this.buffer = buffer
        bufferEnd = endOffset
        if (initialState == STATE_INITIAL) {
            lexChunk(startOffset)
        } else {
            // Resuming inside a chunk: re-lex from its line start and skip to the requested token.
            lexChunk(lineStartBefore(startOffset))
            while (tokenType != null && tokenStart < startOffset) advance()
        }
    }

    fun advance() {
        index++
        if (index >= count && chunkEnd < bufferEnd) lexChunk(chunkEnd)
    }

    private fun lexChunk(from: Int) {
        count = 0
        index = 0
        if (from >= bufferEnd) {
            chunkEnd = bufferEnd
            return
        }
        val end = lineEnd(from)
        chunkEnd = when {
            isHeading(from, end) -> {
                lexHeading(from, end)
                emitNewline(end)
            }

            else -> lexVerbatimBlock(from, end) ?: run {
                lexBodyLine(from, end)
                emitNewline(end)
            }
        }
    }

    // Headings

    private fun isHeading(from: Int, end: Int): Boolean {
        var i = from
        while (i < end && buffer[i] == '*') i++
        // Org requires a space (not a tab) after the stars; a bare `*` line is a paragraph.
        return i > from && i < end && buffer[i] == ' '
    }

    private fun lexHeading(from: Int, end: Int) {
        var i = from
        while (buffer[i] == '*') i++
        emit(STARS, from, i)
        val titleStart = skipBlanks(i, end)
        emit(WHITESPACE, i, titleStart)
        val contentEnd = trimEnd(titleStart, end)
        val tagsStart = tagsStart(titleStart, contentEnd)
        val titleEnd = if (tagsStart >= 0) trimEnd(titleStart, tagsStart) else contentEnd
        lexInline(titleStart, titleEnd, from, heading = true)
        if (tagsStart >= 0) {
            emit(WHITESPACE, titleEnd, tagsStart)
            emit(TAGS, tagsStart, contentEnd)
        }
        emit(WHITESPACE, contentEnd, end)
    }

    /** Start of a trailing `:tag1:tag2:` group preceded by whitespace, or -1. */
    private fun tagsStart(titleStart: Int, contentEnd: Int): Int {
        if (contentEnd - titleStart < 3 || buffer[contentEnd - 1] != ':') return -1
        var colon = contentEnd - 1
        while (true) {
            var k = colon
            while (k - 1 >= titleStart && isTagChar(buffer[k - 1])) k--
            if (k == colon || k - 1 < titleStart || buffer[k - 1] != ':') break
            colon = k - 1
        }
        return if (colon < contentEnd - 1 && isBlank(buffer[colon - 1])) colon else -1
    }

    // Body lines

    private fun lexBodyLine(from: Int, end: Int) {
        val p = skipBlanks(from, end)
        emit(WHITESPACE, from, p)
        if (p == end) return
        val handled = when (buffer[p]) {
            '#' -> lexHashLine(p, end)
            ':' -> lexColonLine(p, end)
            '|' -> lexTableRow(p, end, from)
            '-' -> lexHorizontalRule(p, end) || lexItem(p, end, from)
            '+' -> lexItem(p, end, from)
            '*' -> p > from && lexItem(p, end, from)
            in '0'..'9' -> lexItem(p, end, from)
            'S', 'D', 'C' -> lexPlanning(p, end, from)
            else -> false
        }
        if (!handled) lexInline(p, end, from)
    }

    private fun lexHashLine(p: Int, end: Int): Boolean {
        if (p + 1 == end || buffer[p + 1] == ' ') {
            emit(COMMENT, p, end)
            return true
        }
        if (buffer[p + 1] != '+') return false
        if (matchesIgnoreCase(p, end, BEGIN_PREFIX)) {
            val nameStart = p + BEGIN_PREFIX.length
            val nameEnd = nonBlankEnd(nameStart, end)
            // A verbatim block that reached this point has no end line: it is plain text.
            if (nameEnd == nameStart || isVerbatimBlock(buffer.subSequence(nameStart, nameEnd))) return false
            lexBlockBegin(p, nameEnd, end)
            return true
        }
        if (matchesIgnoreCase(p, end, END_PREFIX)) {
            val nameEnd = nonBlankEnd(p + END_PREFIX.length, end)
            if (nameEnd > p + END_PREFIX.length && skipBlanks(nameEnd, end) == end) {
                emit(BLOCK_END, p, nameEnd)
                emit(WHITESPACE, nameEnd, end)
                return true
            }
        }
        // `#+KEY: value`, KEY being the shortest non-blank run before a colon.
        var i = p + 2
        while (i < end && !isBlank(buffer[i]) && buffer[i] != ':') i++
        if (i > p + 2 && i < end && buffer[i] == ':') {
            emit(KEYWORD_NAME, p, i + 1)
            emitValue(i + 1, end, KEYWORD_VALUE)
            return true
        }
        return false
    }

    private fun lexBlockBegin(p: Int, nameEnd: Int, end: Int) {
        emit(BLOCK_BEGIN, p, nameEnd)
        emitValue(nameEnd, end, BLOCK_PARAMETERS)
    }

    /** Lexes a whole verbatim block as one chunk and returns its end, or null if [from] does not open one. */
    private fun lexVerbatimBlock(from: Int, end: Int): Int? {
        val p = skipBlanks(from, end)
        if (!matchesIgnoreCase(p, end, BEGIN_PREFIX)) return null
        val nameStart = p + BEGIN_PREFIX.length
        val nameEnd = nonBlankEnd(nameStart, end)
        if (nameEnd == nameStart || !isVerbatimBlock(buffer.subSequence(nameStart, nameEnd))) return null
        val nameLength = nameEnd - nameStart

        var endLine = nextLineStart(end)
        while (true) {
            if (endLine >= bufferEnd) return null
            val lineEnd = lineEnd(endLine)
            if (isHeading(endLine, lineEnd)) return null
            if (isBlockEndLine(endLine, lineEnd, nameStart, nameLength)) break
            endLine = nextLineStart(lineEnd)
        }

        emit(WHITESPACE, from, p)
        lexBlockBegin(p, nameEnd, end)
        var line = emitNewline(end)
        while (line < endLine) {
            val lineEnd = lineEnd(line)
            emit(BLOCK_CONTENT, line, lineEnd)
            line = emitNewline(lineEnd)
        }
        val lineEnd = lineEnd(endLine)
        val q = skipBlanks(endLine, lineEnd)
        val q2 = q + END_PREFIX.length + nameLength
        emit(WHITESPACE, endLine, q)
        emit(BLOCK_END, q, q2)
        emit(WHITESPACE, q2, lineEnd)
        return emitNewline(lineEnd)
    }

    private fun isBlockEndLine(line: Int, lineEnd: Int, nameStart: Int, nameLength: Int): Boolean {
        val q = skipBlanks(line, lineEnd)
        if (!matchesIgnoreCase(q, lineEnd, END_PREFIX)) return false
        val n = q + END_PREFIX.length
        if (n + nameLength > lineEnd) return false
        for (k in 0 until nameLength) {
            if (!buffer[n + k].equals(buffer[nameStart + k], ignoreCase = true)) return false
        }
        return skipBlanks(n + nameLength, lineEnd) == lineEnd
    }

    private fun lexColonLine(p: Int, end: Int): Boolean {
        if (p + 1 == end || buffer[p + 1] == ' ') {
            emit(FIXED_WIDTH, p, end)
            return true
        }
        val nameEnd = nonBlankEnd(p, end)
        if (nameEnd - p < 3 || buffer[nameEnd - 1] != ':') return false
        if (skipBlanks(nameEnd, end) == end) {
            val type = when {
                !isDrawerName(p + 1, nameEnd - 1) -> PROPERTY_NAME
                nameEnd - p == 5 && matchesIgnoreCase(p, end, ":END:") -> DRAWER_END
                else -> DRAWER_NAME
            }
            emit(type, p, nameEnd)
            emit(WHITESPACE, nameEnd, end)
            return true
        }
        emit(PROPERTY_NAME, p, nameEnd)
        emitValue(nameEnd, end, PROPERTY_VALUE)
        return true
    }

    private fun lexTableRow(p: Int, end: Int, lineStart: Int): Boolean {
        if (p + 1 < end && buffer[p + 1] == '-') {
            val ruleEnd = trimEnd(p, end)
            emit(TABLE_RULE, p, ruleEnd)
            emit(WHITESPACE, ruleEnd, end)
            return true
        }
        var bar = p
        while (bar < end) {
            emit(TABLE_BAR, bar, bar + 1)
            var next = bar + 1
            while (next < end && buffer[next] != '|') next++
            lexInline(bar + 1, next, lineStart)
            bar = next
        }
        return true
    }

    private fun lexHorizontalRule(p: Int, end: Int): Boolean {
        var i = p
        while (i < end && buffer[i] == '-') i++
        if (i - p < 5 || skipBlanks(i, end) != end) return false
        emit(HORIZONTAL_RULE, p, i)
        emit(WHITESPACE, i, end)
        return true
    }

    private fun lexItem(p: Int, end: Int, lineStart: Int): Boolean {
        var bulletEnd = p + 1
        if (buffer[p] in '0'..'9') {
            while (bulletEnd < end && buffer[bulletEnd] in '0'..'9') bulletEnd++
            if (bulletEnd == end || (buffer[bulletEnd] != '.' && buffer[bulletEnd] != ')')) return false
            bulletEnd++
        }
        if (bulletEnd < end && !isBlank(buffer[bulletEnd])) return false
        emit(BULLET, p, bulletEnd)
        var i = skipBlanks(bulletEnd, end)
        emit(WHITESPACE, bulletEnd, i)
        if (i + 3 <= end && buffer[i] == '[' && buffer[i + 1] in CHECKBOX_STATES && buffer[i + 2] == ']' &&
            (i + 3 == end || isBlank(buffer[i + 3]))
        ) {
            emit(CHECKBOX, i, i + 3)
            val contentStart = skipBlanks(i + 3, end)
            emit(WHITESPACE, i + 3, contentStart)
            i = contentStart
        }
        lexInline(i, end, lineStart)
        return true
    }

    private fun lexPlanning(p: Int, end: Int, lineStart: Int): Boolean {
        if (PLANNING_KEYWORDS.none { matches(p, it) }) return false
        lexInline(p, end, lineStart, planning = true)
        return true
    }

    // Inline markup

    private fun lexInline(from: Int, to: Int, lineStart: Int, heading: Boolean = false, planning: Boolean = false) {
        // Openers are tried left to right and a closer does not depend on its opener, so once a search for one fails,
        // it fails for every later opener too. Remembering that keeps a line full of unclosed openers linear.
        noLinkEndFrom = to
        noEmphasisEndFrom.fill(to)
        var i = from
        var textStart = -1
        var words = 0
        while (i < to) {
            val c = buffer[i]
            if (isBlank(c)) {
                if (textStart >= 0) {
                    emit(TEXT, textStart, i)
                    textStart = -1
                    words++
                }
                val blankEnd = skipBlanks(i, to)
                emit(WHITESPACE, i, blankEnd)
                i = blankEnd
                continue
            }
            val atTokenStart = textStart < 0
            var type: OrgTokenType? = null
            var end = -1
            val linkEnd = if (c == '[') linkEnd(i, to) else -1
            if (linkEnd < 0) {
                if (c == '<' || c == '[') {
                    end = timestampEnd(i, to)
                    if (end > 0) type = TIMESTAMP
                }
                if (type == null && heading && atTokenStart && words <= 1 && c == '[') {
                    end = priorityEnd(i, to)
                    if (end > 0) type = PRIORITY_COOKIE
                }
                if (type == null && planning && atTokenStart) {
                    end = PLANNING_KEYWORDS.firstOrNull { matches(i, it) }?.let { i + it.length } ?: -1
                    if (end > 0) type = PLANNING_KEYWORD
                }
                if (type == null) {
                    val emphasis = emphasisType(c)
                    if (emphasis != null) {
                        end = emphasisEnd(i, to, lineStart)
                        if (end > 0) type = emphasis
                    }
                }
                if (type == null && (i == 0 || !buffer[i - 1].isLetterOrDigit())) {
                    end = plainLinkEnd(i, to)
                    if (end > 0) type = PLAIN_LINK
                }
                if (type == null) {
                    if (textStart < 0) textStart = i
                    i++
                    continue
                }
            }
            if (textStart >= 0) {
                emit(TEXT, textStart, i)
                textStart = -1
            }
            if (linkEnd > 0) {
                emitLink(i, linkEnd)
                i = linkEnd
            } else {
                emit(type!!, i, end)
                i = end
            }
            words++
        }
        if (textStart >= 0) emit(TEXT, textStart, to)
    }

    /** End of `[[path]]` or `[[path][description]]` starting at [i], or -1. */
    private fun linkEnd(i: Int, to: Int): Int {
        if (i + 1 >= to || buffer[i + 1] != '[') return -1
        val pathEnd = linkPathEnd(i, to)
        if (pathEnd == i + 2 || pathEnd >= to || buffer[pathEnd] != ']' || pathEnd + 1 >= to) return -1
        if (buffer[pathEnd + 1] == ']') return pathEnd + 2
        if (buffer[pathEnd + 1] != '[' || pathEnd + 3 >= noLinkEndFrom) return -1
        var j = pathEnd + 3
        while (j + 1 < to && !(buffer[j] == ']' && buffer[j + 1] == ']')) j++
        if (j + 1 < to) return j + 2
        noLinkEndFrom = pathEnd + 3
        return -1
    }

    private fun linkPathEnd(i: Int, to: Int): Int {
        var j = i + 2
        while (j < to && buffer[j] != ']' && buffer[j] != '[') j++
        return j
    }

    private fun emitLink(i: Int, end: Int) {
        val pathEnd = linkPathEnd(i, end)
        emit(LINK_BRACKET, i, i + 2)
        emit(LINK_PATH, i + 2, pathEnd)
        if (pathEnd + 2 == end) {
            emit(LINK_BRACKET, pathEnd, end)
        } else {
            emit(LINK_BRACKET, pathEnd, pathEnd + 2)
            emit(LINK_DESCRIPTION, pathEnd + 2, end - 2)
            emit(LINK_BRACKET, end - 2, end)
        }
    }

    /** End of `<2026-10-28 Wed>` or `[2026-10-28 Wed]`, optionally a `--` range, starting at [i], or -1. */
    private fun timestampEnd(i: Int, to: Int): Int {
        val open = buffer[i]
        val close = if (open == '<') '>' else ']'
        if (i + 12 > to) return -1
        for (k in 1..10) {
            val ch = buffer[i + k]
            val ok = if (k == 5 || k == 8) ch == '-' else ch in '0'..'9'
            if (!ok) return -1
        }
        if (buffer[i + 11] != ' ' && buffer[i + 11] != close) return -1
        var j = i + 11
        while (j < to && buffer[j] != close) {
            if (buffer[j] in "<>[]") return -1
            j++
        }
        if (j >= to) return -1
        val end = j + 1
        if (end + 2 < to && buffer[end] == '-' && buffer[end + 1] == '-' && buffer[end + 2] == open) {
            val rangeEnd = timestampEnd(end + 2, to)
            if (rangeEnd > 0) return rangeEnd
        }
        return end
    }

    private fun priorityEnd(i: Int, to: Int): Int {
        if (i + 3 >= to || buffer[i + 1] != '#') return -1
        var j = i + 2
        while (j < to && (buffer[j] in 'A'..'Z' || buffer[j] in '0'..'9')) j++
        return if (j > i + 2 && j < to && buffer[j] == ']') j + 1 else -1
    }

    /** End of an emphasis span opened at [i], following Org's pre/post character rules, or -1. */
    private fun emphasisEnd(i: Int, to: Int, lineStart: Int): Int {
        val marker = buffer[i]
        if (i > lineStart) {
            val pre = buffer[i - 1]
            if (!isBlank(pre) && pre !in EMPHASIS_PRE) return -1
        }
        val m = EMPHASIS_MARKERS.indexOf(marker)
        if (i + 2 >= noEmphasisEndFrom[m] || isBlank(buffer[i + 1])) return -1
        for (j in i + 2 until to) {
            if (buffer[j] != marker || isBlank(buffer[j - 1])) continue
            val post = if (j + 1 < bufferEnd) buffer[j + 1] else '\n'
            if (post == '\n' || post == '\r' || isBlank(post) || post in EMPHASIS_POST) return j + 1
        }
        noEmphasisEndFrom[m] = i + 2
        return -1
    }

    private fun plainLinkEnd(i: Int, to: Int): Int {
        for (scheme in PLAIN_LINK_SCHEMES) {
            if (!matches(i, scheme)) continue
            val pathStart = i + scheme.length
            var j = pathStart
            while (j < to && !isBlank(buffer[j]) && buffer[j] !in "()<>[]") j++
            // Like Org, a plain link does not end in punctuation other than `/`.
            while (j > pathStart && buffer[j - 1] != '/' && isAsciiPunctuation(buffer[j - 1])) j--
            return if (j > pathStart) j else -1
        }
        return -1
    }

    // Helpers

    private fun emitValue(from: Int, end: Int, type: OrgTokenType) {
        val valueStart = skipBlanks(from, end)
        val valueEnd = trimEnd(valueStart, end)
        emit(WHITESPACE, from, valueStart)
        emit(type, valueStart, valueEnd)
        emit(WHITESPACE, valueEnd, end)
    }

    private fun emitNewline(end: Int): Int {
        if (end >= bufferEnd) return end
        val next = nextLineStart(end)
        emit(NEWLINE, end, next)
        return next
    }

    private fun emit(type: OrgTokenType, start: Int, end: Int) {
        if (start >= end) return
        if (count == types.size) {
            val size = count * 2
            types = types.copyOf(size)
            starts = starts.copyOf(size)
            ends = ends.copyOf(size)
        }
        types[count] = type
        starts[count] = start
        ends[count] = end
        count++
    }

    private fun lineEnd(from: Int): Int {
        var i = from
        while (i < bufferEnd && buffer[i] != '\n' && buffer[i] != '\r') i++
        return i
    }

    private fun nextLineStart(lineEnd: Int): Int = when {
        lineEnd >= bufferEnd -> bufferEnd
        buffer[lineEnd] == '\r' && lineEnd + 1 < bufferEnd && buffer[lineEnd + 1] == '\n' -> lineEnd + 2
        else -> lineEnd + 1
    }

    private fun lineStartBefore(offset: Int): Int {
        var i = offset
        while (i > 0 && buffer[i - 1] != '\n' && buffer[i - 1] != '\r') i--
        return i
    }

    private fun skipBlanks(from: Int, end: Int): Int {
        var i = from
        while (i < end && isBlank(buffer[i])) i++
        return i
    }

    private fun nonBlankEnd(from: Int, end: Int): Int {
        var i = from
        while (i < end && !isBlank(buffer[i])) i++
        return i
    }

    private fun trimEnd(from: Int, end: Int): Int {
        var i = end
        while (i > from && isBlank(buffer[i - 1])) i--
        return i
    }

    private fun matches(at: Int, s: String): Boolean =
        at + s.length <= bufferEnd && buffer.regionMatches(at, s, 0, s.length)

    private fun matchesIgnoreCase(at: Int, end: Int, s: String): Boolean =
        at + s.length <= end && buffer.regionMatches(at, s, 0, s.length, ignoreCase = true)

    private fun isDrawerName(from: Int, to: Int): Boolean =
        (from until to).all { buffer[it].isLetterOrDigit() || buffer[it] == '_' || buffer[it] == '-' }

    companion object {
        const val STATE_INITIAL = 0
        const val STATE_INSIDE = 1

        private const val INITIAL_CAPACITY = 64
        private const val BEGIN_PREFIX = "#+BEGIN_"
        private const val END_PREFIX = "#+END_"
        private const val CHECKBOX_STATES = " X-"
        private const val EMPHASIS_MARKERS = "*/_+=~"
        private const val EMPHASIS_PRE = "-({'\""
        private const val EMPHASIS_POST = "-.,;:!?')}[\"\\"
        private val PLANNING_KEYWORDS = listOf("SCHEDULED:", "DEADLINE:", "CLOSED:")
        private val PLAIN_LINK_SCHEMES = listOf("https:", "http:", "ftp:", "mailto:", "file:", "doi:")
        private val VERBATIM_BLOCKS = setOf("src", "example", "export", "comment", "verse")

        /** Blocks whose body is not parsed as Org: `#+BEGIN_SRC`, `EXAMPLE`, `EXPORT`, `COMMENT`, `VERSE`. */
        fun isVerbatimBlock(name: CharSequence) = name.toString().lowercase() in VERBATIM_BLOCKS

        private fun isBlank(c: Char) = c == ' ' || c == '\t'

        private fun isTagChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '@' || c == '#' || c == '%'

        private fun isAsciiPunctuation(c: Char) =
            c in '!'..'/' || c in ':'..'@' || c in '['..'`' || c in '{'..'~'

        private fun emphasisType(c: Char) = when (c) {
            '*' -> BOLD
            '/' -> ITALIC
            '_' -> UNDERLINE
            '+' -> STRIKE_THROUGH
            '=' -> VERBATIM
            '~' -> CODE
            else -> null
        }
    }
}
