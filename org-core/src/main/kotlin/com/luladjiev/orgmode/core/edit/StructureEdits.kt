package com.luladjiev.orgmode.core.edit

import com.luladjiev.orgmode.core.edit.OrgSnapshot.OutlineHeading

/**
 * Promotion, demotion and Subtree moves. Each operation targets the Heading of the
 * Subtree containing the offset, so it works from anywhere in the Subtree, and changes only the stars it must.
 */
object StructureEdits {
    /** Removes one star from the Heading; its children keep their level (Emacs `org-do-promote`). */
    fun promoteHeading(snapshot: OrgSnapshot, offset: Int): EditResult =
        promote(snapshot, offset) { listOf(it) }

    /** Adds one star to the Heading; its children keep their level (Emacs `org-do-demote`). */
    fun demoteHeading(snapshot: OrgSnapshot, offset: Int): EditResult =
        demote(snapshot, offset) { listOf(it) }

    /** Removes one star from every Heading of the Subtree (Emacs `org-promote-subtree`). */
    fun promoteSubtree(snapshot: OrgSnapshot, offset: Int): EditResult =
        promote(snapshot, offset) { it.subtreeHeadings }

    /** Adds one star to every Heading of the Subtree (Emacs `org-demote-subtree`). */
    fun demoteSubtree(snapshot: OrgSnapshot, offset: Int): EditResult =
        demote(snapshot, offset) { it.subtreeHeadings }

    /**
     * At the end of an empty Heading, moves it to the next level of a cycle relative to the Heading before it:
     * child, then up through the ancestors' levels, then back to sibling (Emacs `org-cycle-level`). Only the
     * Heading's stars change.
     */
    fun cycleLevel(snapshot: OrgSnapshot, offset: Int): EditResult {
        val heading = snapshot.headingOnLine(offset)?.takeIf { snapshot.isAtEndOfEmptyHeading(offset) }
            ?: return EditResult.Refused(Refusal.NOT_AT_EMPTY_HEADING)
        val level = heading.level
        val previous = snapshot.headings.getOrNull(snapshot.headings.indexOf(heading) - 1)?.level ?: 0
        val target = when {
            previous == 0 -> 1
            previous == level -> level + 1
            previous == 1 -> 1
            level == 1 -> previous
            level < previous -> level - 1
            else -> previous - 1
        }
        val start = heading.start
        val edit = if (target > level) {
            TextEdit(start, start, "*".repeat(target - level))
        } else {
            TextEdit(start, start + level - target, "")
        }
        return EditResult.Applied(listOf(edit))
    }

    /** Swaps the Subtree with its previous same-level sibling. */
    fun moveSubtreeUp(snapshot: OrgSnapshot, offset: Int): EditResult {
        val heading = snapshot.headingAt(offset) ?: return EditResult.Refused(Refusal.NO_HEADING)
        val previous = heading.previousSibling ?: return EditResult.Refused(Refusal.MOVE_PAST_PARENT)
        return swap(snapshot, previous, heading)
    }

    /** Swaps the Subtree with its next same-level sibling. */
    fun moveSubtreeDown(snapshot: OrgSnapshot, offset: Int): EditResult {
        val heading = snapshot.headingAt(offset) ?: return EditResult.Refused(Refusal.NO_HEADING)
        val next = heading.nextSibling ?: return EditResult.Refused(Refusal.MOVE_PAST_PARENT)
        return swap(snapshot, heading, next)
    }

    private fun promote(snapshot: OrgSnapshot, offset: Int, targets: (OutlineHeading) -> List<OutlineHeading>): EditResult {
        val heading = snapshot.headingAt(offset) ?: return EditResult.Refused(Refusal.NO_HEADING)
        if (heading.level == 1) return EditResult.Refused(Refusal.TOP_LEVEL)
        return EditResult.Applied(targets(heading).map { TextEdit(it.start, it.start + 1, "") })
    }

    private fun demote(snapshot: OrgSnapshot, offset: Int, targets: (OutlineHeading) -> List<OutlineHeading>): EditResult {
        val heading = snapshot.headingAt(offset) ?: return EditResult.Refused(Refusal.NO_HEADING)
        return EditResult.Applied(targets(heading).map { TextEdit(it.start, it.start, "*") })
    }

    /**
     * Moves [second] in front of [first], its adjacent sibling, by re-inserting it there; [first] is not rewritten.
     * Trailing blank lines travel with their Subtree. A final Subtree without a line break gets one, so it does not
     * merge with the Heading that now follows it.
     */
    private fun swap(snapshot: OrgSnapshot, first: OutlineHeading, second: OutlineHeading): EditResult {
        val text = snapshot.text
        val a = first.start
        val b = second.start
        val c = second.end
        val missingBreak = c == text.length && c > b && text[c - 1] != '\n'
        val moved = text.substring(b, c) + if (missingBreak) snapshot.lineBreakAt(a) else ""
        val edits = listOf(TextEdit(a, a, moved), TextEdit(b, c, ""))
        return EditResult.Applied(edits) { offset ->
            when {
                offset in a until b -> offset + moved.length
                offset in b until c || offset == c && missingBreak -> offset - (b - a)
                else -> null
            }
        }
    }
}
