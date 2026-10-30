package com.luladjiev.orgmode.core

import com.luladjiev.orgmode.core.edit.CheckboxEdits
import com.luladjiev.orgmode.core.edit.EditResult
import com.luladjiev.orgmode.core.edit.HeadingMotion
import com.luladjiev.orgmode.core.edit.OrgSnapshot
import com.luladjiev.orgmode.core.edit.Refusal
import com.luladjiev.orgmode.core.edit.StructureEdits
import com.luladjiev.orgmode.core.edit.TodoEdits
import com.luladjiev.orgmode.core.edit.applyTo
import com.luladjiev.orgmode.core.parser.TodoSequences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OrgEditTest {
    @Test
    fun `promoting a level-1 heading is refused`() {
        assertRefused(Refusal.TOP_LEVEL, StructureEdits.promoteHeading(snapshot("* A\n** B\n"), 1))
        assertRefused(Refusal.TOP_LEVEL, StructureEdits.promoteSubtree(snapshot("* A\n** B\n"), 1))
    }

    @Test
    fun `structural edits need a heading`() {
        val text = "Preamble\n* A\n"
        assertRefused(Refusal.NO_HEADING, StructureEdits.demoteHeading(snapshot(text), 3))
        assertRefused(Refusal.NO_HEADING, StructureEdits.moveSubtreeDown(snapshot(text), 3))
        assertRefused(Refusal.NO_HEADING, TodoEdits.cycle(snapshot(text), listOf(3)))
        assertRefused(Refusal.NO_CHECKBOX, CheckboxEdits.toggle(snapshot("- a\n- [ ] b\n  more\n"), listOf(1, 16)))
    }

    @Test
    fun `moves stay among same-level siblings`() {
        val text = "* P\n*** B\n** C\n** D\n* Q\n"
        val s = snapshot(text)
        assertRefused(Refusal.MOVE_PAST_PARENT, StructureEdits.moveSubtreeDown(s, text.indexOf("B")))
        assertRefused(Refusal.MOVE_PAST_PARENT, StructureEdits.moveSubtreeUp(s, text.indexOf("C")))
        assertRefused(Refusal.MOVE_PAST_PARENT, StructureEdits.moveSubtreeDown(s, text.indexOf("D")))
        assertRefused(Refusal.MOVE_PAST_PARENT, StructureEdits.moveSubtreeUp(s, text.indexOf("P")))
        assertEquals("* P\n*** B\n** D\n** C\n* Q\n", applied(StructureEdits.moveSubtreeDown(s, text.indexOf("C")), text))
        assertEquals("* Q\n* P\n*** B\n** C\n** D\n", applied(StructureEdits.moveSubtreeUp(s, text.indexOf("Q")), text))
    }

    @Test
    fun `moves keep windows line endings`() {
        val text = "* A\r\na\r\n* B\r\nb"
        assertEquals("* B\r\nb\r\n* A\r\na\r\n", applied(StructureEdits.moveSubtreeUp(snapshot(text), text.length), text))
    }

    @Test
    fun `heading edits touch only the stars`() {
        val text = "** A\r\n*** B\r\n"
        val result = assertInstanceOf(EditResult.Applied::class.java, StructureEdits.demoteSubtree(snapshot(text), 0))
        assertEquals(listOf(0 to 0, 6 to 6), result.edits.map { it.start to it.end }.sortedBy { it.first })
        assertEquals("*** A\r\n**** B\r\n", result.edits.applyTo(text))
    }

    @Test
    fun `todo cycling on a bare heading`() {
        assertEquals("* TODO ", applied(TodoEdits.cycle(snapshot("* "), listOf(2)), "* "))
        assertEquals("* \n", applied(TodoEdits.cycle(snapshot("* DONE\n"), listOf(3)), "* DONE\n"))
    }

    @Test
    fun `todo sequence order`() {
        val sequences = TodoSequences.parse("#+TODO: A B | C D\n#+TODO: X | Y\n")
        assertEquals(listOf("A", "B", "C", "D"), generateSequence(sequences.next(null)) { sequences.next(it) }.toList())
        assertEquals(null, sequences.next("Y"))
        assertEquals("Y", sequences.next("X"))
        assertEquals(null, sequences.next("unknown"))
    }

    @Test
    fun `next and previous visible headings`() {
        val text = "intro\n* A\nbody\n** B\n*** C\n* D\n"
        val s = snapshot(text)
        val hidden = setOf(text.indexOf("** B"), text.indexOf("*** C"))
        assertEquals(text.indexOf("* A"), s.nextHeading(0) { true }?.start)
        assertEquals(text.indexOf("** B"), s.nextHeading(text.indexOf("A")) { true }?.start)
        assertEquals(text.indexOf("* D"), s.nextHeading(text.indexOf("A")) { it !in hidden }?.start)
        assertNull(s.nextHeading(text.indexOf("D")) { true })
        assertEquals(text.indexOf("* A"), s.previousHeading(text.indexOf("body")) { true }?.start)
        assertEquals(text.indexOf("* A"), s.previousHeading(text.indexOf("* D")) { it !in hidden }?.start)
        assertEquals(text.indexOf("*** C"), s.previousHeading(text.indexOf("D")) { true }?.start)
        assertNull(s.previousHeading(text.indexOf("A")) { true })
    }

    @Test
    fun `siblings and parents`() {
        val text = "* P\n*** B\n** C\nbody\n*** C1\n** D\n* Q\n"
        val s = snapshot(text)
        assertNull(s.headingAt(text.indexOf("B"))?.nextSibling)
        assertEquals(text.indexOf("** D"), s.headingAt(text.indexOf("body"))?.nextSibling?.start)
        assertEquals(text.indexOf("** C"), s.headingAt(text.indexOf("D"))?.previousSibling?.start)
        assertEquals(text.indexOf("** C"), s.headingAt(text.indexOf("C1"))?.parent?.start)
        assertEquals(text.indexOf("* Q"), s.headingAt(0)?.nextSibling?.start)
        assertNull(s.headingAt(0)?.parent)
        assertEquals(s.headingAt(text.indexOf("Q")), s.headingAt(text.length))
    }

    @Test
    fun `counted heading motions stop at the last possible move`() {
        val text = "* A\n** B\n*** C\nbody\n** D\n** E\n* F\n"
        val s = snapshot(text)
        val visible = { _: Int -> true }
        fun target(motion: HeadingMotion, from: String, count: Int) =
            motion.target(s, text.indexOf(from), visible, count)?.start

        assertEquals(text.indexOf("*** C"), target(HeadingMotion.NEXT_HEADING, "A", 2))
        assertEquals(text.indexOf("* F"), target(HeadingMotion.NEXT_HEADING, "A", 99))
        assertEquals(text.indexOf("** B"), target(HeadingMotion.PREVIOUS_HEADING, "body", 2))
        assertEquals(text.indexOf("** E"), target(HeadingMotion.NEXT_SIBLING, "B", 2))
        assertEquals(text.indexOf("** B"), target(HeadingMotion.PREVIOUS_SIBLING, "E", 5))
        assertEquals(text.indexOf("* A"), target(HeadingMotion.PARENT_HEADING, "body", 2))
        assertNull(target(HeadingMotion.NEXT_SIBLING, "F", 1))
        assertNull(target(HeadingMotion.PARENT_HEADING, "A", 3))
    }

    @Test
    fun `next sibling goes to the first Heading from before it, whatever the count`() {
        val text = "Intro\n** A\n*** B\n** C\n* D\n"
        val s = snapshot(text)
        fun target(count: Int) = HeadingMotion.NEXT_SIBLING.target(s, text.indexOf("tro"), { true }, count)?.start

        assertEquals(text.indexOf("** A"), target(1))
        assertEquals(text.indexOf("** A"), target(2))
        assertNull(HeadingMotion.NEXT_SIBLING.target(snapshot("Intro\n"), 0, { true }))
    }

    /** Cases checked against Emacs `org-point-at-end-of-empty-headline` (Org 9.8.7). */
    @Test
    fun `end of an empty Heading`() {
        fun atEnd(line: String, offset: Int = line.length) =
            snapshot("* A\n$line\nbody\n").isAtEndOfEmptyHeading("* A\n".length + offset)

        assertTrue(atEnd("* "))
        assertTrue(atEnd("*  "))
        assertTrue(atEnd("** TODO "))
        assertTrue(atEnd("* TODO   ", offset = 7))
        assertTrue(atEnd("* ", offset = 1))
        assertFalse(atEnd("* TODO"))
        assertFalse(atEnd("* B"))
        assertFalse(atEnd("* [#A] "))
        assertFalse(atEnd("* :tag:"))
        assertFalse(atEnd("* TODO ", offset = 3))
        assertFalse(atEnd("text "))
    }

    @Test
    fun `cycling an empty Heading's level comes back round`() {
        fun cycle(text: String) = applied(StructureEdits.cycleLevel(snapshot(text), text.lastIndexOf(' ') + 1), text)

        val levels = generateSequence("* A\n*** B\n*** ") { cycle(it) }.take(5).map { it.substringAfterLast('\n') }
        assertEquals(listOf("*** ", "**** ", "** ", "* ", "*** "), levels.toList())
        assertRefused(Refusal.NOT_AT_EMPTY_HEADING, StructureEdits.cycleLevel(snapshot("* A\n** B"), 9))
    }

    @Test
    fun `new Headings keep windows line endings`() {
        val text = "* A\r\nbody\r\n\r\n* B\r\n"
        val result = assertInstanceOf(EditResult.Applied::class.java, StructureEdits.insertHeadingAfterSubtree(snapshot(text), 1))
        assertEquals("* A\r\nbody\r\n\r\n* \r\n\r\n* B\r\n", result.edits.applyTo(text))
        assertEquals("* A\r\nbody\r\n\r\n* ".length, result.caret)
    }

    private fun snapshot(text: String) = OrgSnapshot.parse(text)

    private fun applied(result: EditResult, text: String) =
        assertInstanceOf(EditResult.Applied::class.java, result).edits.applyTo(text)

    private fun assertRefused(refusal: Refusal, result: EditResult) =
        assertEquals(EditResult.Refused(refusal), result)
}
