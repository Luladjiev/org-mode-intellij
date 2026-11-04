package com.luladjiev.orgmode.core.parser

import com.luladjiev.orgmode.core.lexer.OrgLexer
import com.luladjiev.orgmode.core.lexer.OrgTokenType

/** A node of a plain syntax tree: the same shape IntelliJ's PSI gets from [OrgParser], without the platform. */
sealed interface SyntaxNode {
    val startOffset: Int
    val endOffset: Int
}

class SyntaxToken(val type: OrgTokenType, override val startOffset: Int, override val endOffset: Int) : SyntaxNode {
    override fun toString() = "$type[$startOffset,$endOffset)"
}

class SyntaxElement(
    val type: OrgElementType,
    override val startOffset: Int,
    override val endOffset: Int,
    val children: List<SyntaxNode>,
) : SyntaxNode {
    override fun toString() = "$type[$startOffset,$endOffset)"
}

/** Parses text snapshots outside IntelliJ, for tests and structural edits. */
object SyntaxTree {
    fun parse(text: CharSequence): SyntaxElement {
        val builder = PlainTreeBuilder(text)
        OrgParser.parse(builder)
        return builder.build()
    }
}

private class PlainTreeBuilder(override val text: CharSequence) : TreeBuilder {
    private val types = ArrayList<OrgTokenType>()
    private val starts = ArrayList<Int>()
    private var current = 0
    private val markers = ArrayList<PlainMarker>()

    init {
        val lexer = OrgLexer()
        lexer.start(text)
        while (true) {
            val type = lexer.tokenType ?: break
            types += type
            starts += lexer.tokenStart
            lexer.advance()
        }
    }

    override fun lookAhead(steps: Int): OrgTokenType? = types.getOrNull(current + steps)

    override fun lookAheadStart(steps: Int): Int = starts.getOrNull(current + steps) ?: text.length

    override fun advance() {
        if (current < types.size) current++
    }

    override fun mark(): TreeBuilder.Marker = PlainMarker(current).also { markers += it }

    private inner class PlainMarker(val startToken: Int) : TreeBuilder.Marker {
        var endToken = -1
        var type: OrgElementType? = null
        var dropped = false

        override fun done(type: OrgElementType) {
            check(this.type == null && !dropped) { "Marker already closed" }
            this.type = type
            endToken = current
        }

        override fun drop() {
            check(type == null) { "Marker already done" }
            dropped = true
        }
    }

    private class Frame(val type: OrgElementType, val startToken: Int, val endToken: Int) {
        val children = ArrayList<SyntaxNode>()
    }

    /** Assembles the tree; markers are in creation order, so their start tokens never decrease. */
    fun build(): SyntaxElement {
        val count = types.size
        val stack = ArrayDeque<Frame>()
        stack.addLast(Frame(OrgElementType.DOCUMENT, 0, count))
        val done = markers.filter { !it.dropped }
        check(done.all { it.type != null }) { "Unclosed marker" }
        var next = 0
        for (token in 0..count) {
            while (next < done.size && done[next].startToken == token) {
                val marker = done[next++]
                closeFrames(stack, token)
                stack.addLast(Frame(marker.type!!, marker.startToken, marker.endToken))
            }
            closeFrames(stack, token)
            if (token < count) {
                stack.last().children += SyntaxToken(types[token], starts[token], offsetOf(token + 1))
            }
        }
        check(stack.size == 1) { "Markers are not properly nested" }
        return stack.last().toElement()
    }

    private fun closeFrames(stack: ArrayDeque<Frame>, token: Int) {
        while (stack.size > 1 && stack.last().endToken == token) {
            val frame = stack.removeLast()
            stack.last().children += frame.toElement()
        }
    }

    private fun Frame.toElement() = SyntaxElement(type, offsetOf(startToken), offsetOf(endToken), children)

    private fun offsetOf(token: Int) = starts.getOrNull(token) ?: text.length
}
