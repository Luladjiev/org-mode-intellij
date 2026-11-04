package com.luladjiev.orgmode.core.edit

import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.parser.OrgElementType
import com.luladjiev.orgmode.core.parser.SyntaxElement
import com.luladjiev.orgmode.core.parser.SyntaxToken
import com.luladjiev.orgmode.core.parser.SyntaxTree
import com.luladjiev.orgmode.core.parser.TodoSequences

/**
 * A parsed text snapshot that structural edits and navigation work on: the syntax tree plus the outline of its
 * Headings. Edits are computed against it and applied by the caller.
 */
class OrgSnapshot private constructor(val text: CharSequence, val root: SyntaxElement) {
    val todoSequences: TodoSequences = TodoSequences.parse(text)

    /** Every Heading in document order. */
    val headings: List<OutlineHeading>

    /** The level-1 (or shallowest) Headings, which have no parent. */
    val roots: List<OutlineHeading>

    init {
        val all = ArrayList<OutlineHeading>()
        fun collect(element: SyntaxElement, parent: OutlineHeading?, into: MutableList<OutlineHeading>) {
            for (child in element.children) {
                if (child !is SyntaxElement || child.type != OrgElementType.SUBTREE) continue
                val heading = OutlineHeading(child, parent, all.size)
                all += heading
                into += heading
                collect(child, heading, heading.mutableChildren)
            }
        }
        val roots = ArrayList<OutlineHeading>()
        collect(root, null, roots)
        headings = all
        this.roots = roots
    }

    /** The Heading of the innermost Subtree containing [offset]; the end of the text belongs to the last one. */
    fun headingAt(offset: Int): OutlineHeading? {
        var level: List<OutlineHeading> = roots
        var found: OutlineHeading? = null
        while (true) {
            val next = level.firstOrNull { offset >= it.start && (offset < it.end || offset == text.length && it.end == offset) }
                ?: return found
            found = next
            level = next.children
        }
    }

    /** The first Heading after [offset] for which [isVisible] holds (Emacs `outline-next-visible-heading`). */
    fun nextHeading(offset: Int, isVisible: (Int) -> Boolean): OutlineHeading? =
        headings.firstOrNull { it.start > offset && isVisible(it.start) }

    /** The last Heading starting before the line of [offset] for which [isVisible] holds. */
    fun previousHeading(offset: Int, isVisible: (Int) -> Boolean): OutlineHeading? {
        val lineStart = lineStart(offset)
        return headings.lastOrNull { it.start < lineStart && isVisible(it.start) }
    }

    /** The Heading whose line contains [offset], if that line is a Heading line. */
    fun headingOnLine(offset: Int): OutlineHeading? = headingAt(offset)?.takeIf { it.start == lineStart(offset) }

    /**
     * Whether only blanks follow [offset] on a Heading line holding nothing but stars and maybe a TODO Keyword
     * (Emacs `org-point-at-end-of-empty-headline`, where TAB changes the level of a new Heading).
     */
    fun isAtEndOfEmptyHeading(offset: Int): Boolean {
        val heading = headingOnLine(offset) ?: return false
        if ((offset until heading.heading.endOffset).any { text[it] != ' ' && text[it] != '\t' }) return false
        val content = heading.heading.children.drop(1).filterNot { it is SyntaxToken && it.type == OrgTokenType.WHITESPACE }
        val keyword = heading.keyword ?: return content.isEmpty()
        // A keyword with nothing after it is the title in Emacs: `* TODO` is not empty, `* TODO ` is.
        return content == listOf(keyword) && heading.whitespaceAfter(keyword) != null
    }

    /** The line break ending the line that contains [offset], or `"\n"` when that line has none. */
    fun lineBreakAt(offset: Int): String {
        var p = offset
        while (p < text.length && text[p] != '\n') p++
        return if (p > 0 && p < text.length && text[p - 1] == '\r') "\r\n" else "\n"
    }

    private fun lineStart(offset: Int): Int {
        var p = offset.coerceAtMost(text.length)
        while (p > 0 && text[p - 1] != '\n') p--
        return p
    }

    /** One Heading and the extent of its Subtree. */
    inner class OutlineHeading internal constructor(
        /** The Subtree element; the Heading line is its first child. */
        val element: SyntaxElement,
        val parent: OutlineHeading?,
        private val index: Int,
    ) {
        /** The HEADING element: the Heading line without its line break. */
        val heading: SyntaxElement = element.children.first() as SyntaxElement

        /** Offset of the first star, at the start of the line. */
        val start: Int get() = element.startOffset

        /** End of the Subtree: its Section, descendants and trailing blank lines. */
        val end: Int get() = element.endOffset

        val level: Int = heading.children.first().let { it.endOffset - it.startOffset }

        internal val mutableChildren = ArrayList<OutlineHeading>()

        val children: List<OutlineHeading> get() = mutableChildren

        private val siblings: List<OutlineHeading> get() = parent?.children ?: roots

        /** The adjacent Heading with the same parent and level, if any; levels never increase along siblings. */
        val previousSibling: OutlineHeading?
            get() = siblings.getOrNull(siblings.indexOf(this) - 1)?.takeIf { it.level == level }

        val nextSibling: OutlineHeading?
            get() = siblings.getOrNull(siblings.indexOf(this) + 1)?.takeIf { it.level == level }

        /** This Heading and all Headings of its Subtree, in document order. */
        val subtreeHeadings: List<OutlineHeading>
            get() = headings.subList(index, headings.size).takeWhile { it.start < end }

        /** The TODO_KEYWORD or DONE_KEYWORD element of the Heading. */
        val keyword: SyntaxElement?
            get() = heading.children.firstOrNull {
                it is SyntaxElement && (it.type == OrgElementType.TODO_KEYWORD || it.type == OrgElementType.DONE_KEYWORD)
            } as SyntaxElement?

        /** Where a TODO Keyword goes: after the stars and the whitespace that follows them. */
        val keywordOffset: Int
            get() = heading.children.getOrNull(1)
                ?.takeIf { it is SyntaxToken && it.type == OrgTokenType.WHITESPACE }?.endOffset
                ?: heading.endOffset

        /** The whitespace token right after [node] in the Heading line, if any. */
        internal fun whitespaceAfter(node: SyntaxElement): SyntaxToken? {
            val children = heading.children
            val next = children.getOrNull(children.indexOf(node) + 1)
            return next.takeIf { it is SyntaxToken && it.type == OrgTokenType.WHITESPACE } as SyntaxToken?
        }

        override fun toString() = "Heading[$start,$end) level $level"
    }

    companion object {
        fun parse(text: CharSequence) = OrgSnapshot(text, SyntaxTree.parse(text))
    }
}
