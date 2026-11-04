package com.luladjiev.orgmode.core.edit

/** TODO Keyword cycling. */
object TodoEdits {
    /**
     * Moves each targeted Heading to the next keyword of its TODO Sequence ([com.luladjiev.orgmode.core.parser.TodoSequences.next]).
     * Several offsets in one Subtree change its Heading once. Only the keyword changes: spacing, priority, title and
     * tags stay as written, and no `CLOSED:` stamp is added in v0.1.
     */
    fun cycle(snapshot: OrgSnapshot, offsets: Collection<Int>): EditResult {
        val headings = offsets.mapNotNull(snapshot::headingAt).distinct()
        if (headings.isEmpty()) return EditResult.Refused(Refusal.NO_HEADING)
        return EditResult.Applied(headings.mapNotNull { heading ->
            val keyword = heading.keyword
            val current = keyword?.let { snapshot.text.substring(it.startOffset, it.endOffset) }
            val next = snapshot.todoSequences.next(current)
            when {
                keyword == null -> next?.let { TextEdit(heading.keywordOffset, heading.keywordOffset, "$it ") }
                next == null -> TextEdit(keyword.startOffset, heading.whitespaceAfter(keyword)?.endOffset ?: keyword.endOffset, "")
                else -> TextEdit(keyword.startOffset, keyword.endOffset, next)
            }
        })
    }
}
