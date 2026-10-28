package com.luladjiev.orgmode.core

import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.parser.OrgElementType
import com.luladjiev.orgmode.core.parser.OrgElementType.AFFILIATED_KEYWORD
import com.luladjiev.orgmode.core.parser.OrgElementType.BLOCK
import com.luladjiev.orgmode.core.parser.OrgElementType.COMMENT
import com.luladjiev.orgmode.core.parser.OrgElementType.DOCUMENT
import com.luladjiev.orgmode.core.parser.OrgElementType.DONE_KEYWORD
import com.luladjiev.orgmode.core.parser.OrgElementType.DRAWER
import com.luladjiev.orgmode.core.parser.OrgElementType.FIXED_WIDTH
import com.luladjiev.orgmode.core.parser.OrgElementType.HEADING
import com.luladjiev.orgmode.core.parser.OrgElementType.HORIZONTAL_RULE
import com.luladjiev.orgmode.core.parser.OrgElementType.ITEM
import com.luladjiev.orgmode.core.parser.OrgElementType.KEYWORD
import com.luladjiev.orgmode.core.parser.OrgElementType.LINK
import com.luladjiev.orgmode.core.parser.OrgElementType.NODE_PROPERTY
import com.luladjiev.orgmode.core.parser.OrgElementType.PARAGRAPH
import com.luladjiev.orgmode.core.parser.OrgElementType.PLAIN_LIST
import com.luladjiev.orgmode.core.parser.OrgElementType.PLANNING
import com.luladjiev.orgmode.core.parser.OrgElementType.PRIORITY
import com.luladjiev.orgmode.core.parser.OrgElementType.PROPERTY_DRAWER
import com.luladjiev.orgmode.core.parser.OrgElementType.SECTION
import com.luladjiev.orgmode.core.parser.OrgElementType.SUBTREE
import com.luladjiev.orgmode.core.parser.OrgElementType.TABLE
import com.luladjiev.orgmode.core.parser.OrgElementType.TABLE_ROW
import com.luladjiev.orgmode.core.parser.OrgElementType.TITLE
import com.luladjiev.orgmode.core.parser.OrgElementType.TODO_KEYWORD
import com.luladjiev.orgmode.core.parser.SyntaxElement
import com.luladjiev.orgmode.core.parser.SyntaxNode
import com.luladjiev.orgmode.core.parser.SyntaxToken

/** Renders a tree in the reduced org-element format that `scripts/gen-fixtures.el` writes. */
class ReducedTree(private val text: CharSequence) {
    fun render(root: SyntaxElement): String = buildString { dump(root, 0) }

    private fun StringBuilder.dump(element: SyntaxElement, depth: Int) {
        val name = name(element) ?: return
        append("  ".repeat(depth)).append(name).append(props(element)).append('\n')
        if (element.type == TABLE) return
        element.children.filterIsInstance<SyntaxElement>().forEach { dump(it, depth + 1) }
    }

    private fun name(element: SyntaxElement): String? = when (element.type) {
        DOCUMENT -> "org-data"
        SUBTREE -> "headline"
        SECTION -> "section"
        PLANNING -> "planning"
        PROPERTY_DRAWER -> "property-drawer"
        NODE_PROPERTY -> "node-property"
        DRAWER -> "drawer"
        BLOCK -> when (blockName(element).lowercase()) {
            "src" -> "src-block"
            "example" -> "example-block"
            "export" -> "export-block"
            "comment" -> "comment-block"
            "verse" -> "verse-block"
            "quote" -> "quote-block"
            "center" -> "center-block"
            else -> "special-block"
        }

        KEYWORD -> "keyword"
        COMMENT -> "comment"
        FIXED_WIDTH -> "fixed-width"
        HORIZONTAL_RULE -> "horizontal-rule"
        TABLE -> "table"
        PLAIN_LIST -> "plain-list"
        ITEM -> "item"
        PARAGRAPH -> "paragraph"
        HEADING, TODO_KEYWORD, DONE_KEYWORD, PRIORITY, TITLE, AFFILIATED_KEYWORD, TABLE_ROW, LINK -> null
    }

    private fun props(element: SyntaxElement): String = when (element.type) {
        SUBTREE -> headingProps(element.children.first() as SyntaxElement)
        KEYWORD -> {
            val name = text(token(element, OrgTokenType.KEYWORD_NAME)!!)
            " key=${name.substring(2, name.length - 1).uppercase()} value=${quote(textOrEmpty(element, OrgTokenType.KEYWORD_VALUE))}"
        }

        NODE_PROPERTY -> {
            val name = text((token(element, OrgTokenType.PROPERTY_NAME) ?: token(element, OrgTokenType.DRAWER_NAME))!!)
            " key=${name.substring(1, name.length - 1)} value=${quote(textOrEmpty(element, OrgTokenType.PROPERTY_VALUE))}"
        }

        DRAWER -> " name=" + text(token(element, OrgTokenType.DRAWER_NAME)!!).trim(':')
        BLOCK -> {
            val name = blockName(element)
            when (name.lowercase()) {
                "src" -> textOrEmpty(element, OrgTokenType.BLOCK_PARAMETERS).substringBefore(' ')
                    .let { if (it.isEmpty()) "" else " language=$it" }

                "example", "export", "comment", "verse", "quote", "center" -> ""
                else -> " type=$name"
            }
        }

        PLAIN_LIST -> {
            val firstItem = element.children.first { it is SyntaxElement && it.type == ITEM } as SyntaxElement
            val bullet = text(token(firstItem, OrgTokenType.BULLET)!!)
            if (bullet[0].isDigit()) " type=ordered" else " type=unordered"
        }

        ITEM -> when (token(element, OrgTokenType.CHECKBOX)?.let(::text)) {
            null -> ""
            "[ ]" -> " checkbox=off"
            "[X]" -> " checkbox=on"
            else -> " checkbox=trans"
        }

        TABLE -> " rows=" + element.children.count { it is SyntaxElement && it.type == TABLE_ROW }
        else -> ""
    }

    private fun headingProps(heading: SyntaxElement): String = buildString {
        append(" level=").append(text(token(heading, OrgTokenType.STARS)!!).length)
        child(heading, TODO_KEYWORD)?.let { append(" todo=").append(text(it)).append("/todo") }
        child(heading, DONE_KEYWORD)?.let { append(" todo=").append(text(it)).append("/done") }
        child(heading, PRIORITY)?.let { append(" priority=").append(text(it)[2]) }
        token(heading, OrgTokenType.TAGS)?.let { append(" tags=").append(text(it).trim(':')) }
        append(" title=").append(quote(child(heading, TITLE)?.let(::text) ?: ""))
    }

    private fun blockName(block: SyntaxElement) = text(token(block, OrgTokenType.BLOCK_BEGIN)!!).substring(8)

    /** The first token of [type] in [element]'s own lines, not inside child elements. */
    private fun token(element: SyntaxElement, type: OrgTokenType): SyntaxToken? =
        element.children.firstOrNull { it is SyntaxToken && it.type == type } as SyntaxToken?

    private fun child(element: SyntaxElement, type: OrgElementType): SyntaxElement? =
        element.children.firstOrNull { it is SyntaxElement && it.type == type } as SyntaxElement?

    private fun textOrEmpty(element: SyntaxElement, type: OrgTokenType) = token(element, type)?.let(::text) ?: ""

    private fun text(node: SyntaxNode) = text.substring(node.startOffset, node.endOffset)

    /** Quotes like Emacs `prin1`: backslashes and double quotes are escaped. */
    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
