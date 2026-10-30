package com.luladjiev.orgmode.core.edit

import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.parser.OrgElementType
import com.luladjiev.orgmode.core.parser.SyntaxElement
import com.luladjiev.orgmode.core.parser.SyntaxToken

/** Checkbox toggling. */
object CheckboxEdits {
    /**
     * Toggles the checkbox of each list item whose first line holds an offset: `[ ]` and `[-]` become `[X]`, `[X]`
     * and `[x]` become `[ ]`. Lowercase `[x]` is not a checkbox to Org, but it is what people type, so it unchecks
     * too. Each checkbox changes once however many offsets hit it; parent statistics are left alone until v0.2.
     */
    fun toggle(snapshot: OrgSnapshot, offsets: Collection<Int>): EditResult {
        val text = snapshot.text
        val checkboxes = offsets.mapNotNull { checkboxAt(snapshot, it) }.distinct()
        if (checkboxes.isEmpty()) return EditResult.Refused(Refusal.NO_CHECKBOX)
        return EditResult.Applied(checkboxes.map { start ->
            val checked = text[start + 1] == 'X' || text[start + 1] == 'x'
            TextEdit(start, start + 3, if (checked) "[ ]" else "[X]")
        })
    }

    /** Offset of the `[` of the checkbox on the first line of the item at [offset]. */
    private fun checkboxAt(snapshot: OrgSnapshot, offset: Int): Int? {
        val text = snapshot.text
        val item = itemOnLine(snapshot.root, offset, text) ?: return null
        val children = item.children
        val bullet = children.indexOfFirst { it is SyntaxToken && it.type == OrgTokenType.BULLET }
        val after = children.getOrNull(bullet + 1)?.takeIf { it is SyntaxToken && it.type == OrgTokenType.WHITESPACE }
            ?: return null
        val box = after.endOffset
        val token = children.getOrNull(bullet + 2)
        if (token is SyntaxToken && token.type == OrgTokenType.CHECKBOX) return box
        val lowercase = text.startsWith("[x]", box) &&
            (box + 3 == text.length || text[box + 3].let { it == ' ' || it == '\t' || it == '\n' || it == '\r' })
        return box.takeIf { lowercase }
    }

    /** The list item whose first line (bullet line) contains [offset]. */
    private fun itemOnLine(element: SyntaxElement, offset: Int, text: CharSequence): SyntaxElement? {
        for (child in element.children) {
            if (child !is SyntaxElement || offset < child.startOffset || offset > child.endOffset) continue
            if (child.type == OrgElementType.ITEM && offset <= lineEnd(text, child.startOffset)) return child
            itemOnLine(child, offset, text)?.let { return it }
        }
        return null
    }

    private fun lineEnd(text: CharSequence, offset: Int): Int {
        var p = offset
        while (p < text.length && text[p] != '\n' && text[p] != '\r') p++
        return p
    }
}
