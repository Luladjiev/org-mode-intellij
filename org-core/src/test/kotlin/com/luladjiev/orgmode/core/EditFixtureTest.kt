package com.luladjiev.orgmode.core

import com.luladjiev.orgmode.core.edit.CheckboxEdits
import com.luladjiev.orgmode.core.edit.EditResult
import com.luladjiev.orgmode.core.edit.OrgSnapshot
import com.luladjiev.orgmode.core.edit.StructureEdits
import com.luladjiev.orgmode.core.edit.TodoEdits
import com.luladjiev.orgmode.core.edit.applyTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Golden tests for structural transformations: `edit/OPERATION/NAME.org` with `<caret>` markers, and the
 * expected result in `NAME.expected.org`, carets included. Comparing whole texts asserts that everything the
 * operation does not target is preserved exactly. `OrgEditActionTest` runs the same files through the IDE actions.
 */
class EditFixtureTest {
    @TestFactory
    fun `golden edits`(): List<DynamicTest> = Fixtures.editFixtures().map { path ->
        val operation = path.parent.fileName.toString()
        dynamicTest("$operation/${path.fileName}") {
            val input = Carets.parse(Fixtures.read(path))
            val expected = Carets.parse(Fixtures.read(Fixtures.expectedEdit(path)))
            val result = assertInstanceOf(EditResult.Applied::class.java, edit(operation, OrgSnapshot.parse(input.text), input.offsets))
            assertEquals(expected.text, result.edits.applyTo(input.text))
            assertEquals(expected.offsets, input.offsets.map(result::mapOffset), "carets")
        }
    }

    private fun edit(operation: String, snapshot: OrgSnapshot, carets: List<Int>): EditResult = when (operation) {
        "promote-heading" -> StructureEdits.promoteHeading(snapshot, carets.single())
        "demote-heading" -> StructureEdits.demoteHeading(snapshot, carets.single())
        "promote-subtree" -> StructureEdits.promoteSubtree(snapshot, carets.single())
        "demote-subtree" -> StructureEdits.demoteSubtree(snapshot, carets.single())
        "move-subtree-up" -> StructureEdits.moveSubtreeUp(snapshot, carets.single())
        "move-subtree-down" -> StructureEdits.moveSubtreeDown(snapshot, carets.single())
        "cycle-level" -> StructureEdits.cycleLevel(snapshot, carets.single())
        "cycle-todo" -> TodoEdits.cycle(snapshot, carets)
        "toggle-checkbox" -> CheckboxEdits.toggle(snapshot, carets)
        else -> error("Unknown operation directory $operation")
    }
}

/** Text with IntelliJ-style `<caret>` markers removed, and the offsets they marked. */
data class Carets(val text: String, val offsets: List<Int>) {
    companion object {
        private const val MARKER = "<caret>"

        fun parse(marked: String): Carets {
            val offsets = mutableListOf<Int>()
            val text = StringBuilder()
            var from = 0
            while (true) {
                val at = marked.indexOf(MARKER, from)
                if (at < 0) break
                text.append(marked, from, at)
                offsets += text.length
                from = at + MARKER.length
            }
            text.append(marked, from, marked.length)
            return Carets(text.toString(), offsets)
        }
    }
}
