package com.luladjiev.orgmode.core.edit

import com.luladjiev.orgmode.core.edit.OrgSnapshot.OutlineHeading

/** Heading navigation (Emacs `C-c C-n/p/f/b/u`), shared by the IDE actions and the IdeaVim adapter. */
enum class HeadingMotion(
    private val step: (snapshot: OrgSnapshot, offset: Int, isVisible: (Int) -> Boolean) -> OutlineHeading?,
) {
    NEXT_HEADING({ s, offset, visible -> s.nextHeading(offset, visible) }),
    PREVIOUS_HEADING({ s, offset, visible -> s.previousHeading(offset, visible) }),
    NEXT_SIBLING({ s, offset, _ -> s.headingAt(offset)?.nextSibling }),
    PREVIOUS_SIBLING({ s, offset, _ -> s.headingAt(offset)?.previousSibling }),
    PARENT_HEADING({ s, offset, _ -> s.headingAt(offset)?.parent }),
    ;

    /**
     * The Heading reached by [count] moves from [offset], stopping early at the last move possible (like Emacs with a
     * prefix argument); null when not even one move is. Next/previous skip Headings for which [isVisible] fails.
     */
    fun target(snapshot: OrgSnapshot, offset: Int, isVisible: (Int) -> Boolean, count: Int = 1): OutlineHeading? {
        // Before the first Heading, Emacs `org-forward-heading-same-level` goes to it, whatever the count.
        if (this == NEXT_SIBLING && snapshot.headingAt(offset) == null) {
            return snapshot.headings.firstOrNull { it.start > offset }
        }
        var found: OutlineHeading? = null
        repeat(count) {
            found = step(snapshot, found?.start ?: offset, isVisible) ?: return found
        }
        return found
    }
}
