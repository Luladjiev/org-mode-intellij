package com.luladjiev.orgmode

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.luladjiev.orgmode.settings.OrgSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/** Structural edits through the registered actions and editor hooks, including Undo. */
class OrgEditActionTest : BasePlatformTestCase() {
    /** The `org-core` golden files (`EditFixtureTest`), run through the action named after each directory. */
    fun testGoldenFixturesThroughActions() {
        val root = Path.of(System.getProperty("org.mode.fixtures")).resolve("edit")
        val cases = root.listDirectoryEntries().sorted().flatMap { dir ->
            dir.listDirectoryEntries("*.org").filterNot { it.name.endsWith(".expected.org") }.sorted()
        }
        assertTrue(cases.size > 10)
        for ((i, input) in cases.withIndex()) {
            val actionId = "org.mode." + input.parent.name.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
            val expected = input.resolveSibling(input.name.removeSuffix(".org") + ".expected.org")
            // A fresh file per case, so Undo only ever sees this case's command.
            myFixture.configureByText("case$i.org", input.readText())
            val before = myFixture.editor.document.text
            myFixture.performEditorAction(actionId)
            try {
                myFixture.checkResult(Files.readString(expected))
            } catch (e: Throwable) {
                throw AssertionError("$actionId on ${input.parent.name}/${input.name}", e)
            }
            undo()
            assertEquals("Undo of $actionId on ${input.name}", before, myFixture.editor.document.text)
        }
    }

    fun testActionsAreRegisteredInTheCodeMenu() {
        val ids = listOf(
            "PromoteHeading", "PromoteSubtree", "DemoteHeading", "DemoteSubtree", "CycleLevel", "MoveSubtreeUp", "MoveSubtreeDown",
            "CycleTodo", "ToggleCheckbox", "CycleVisibility", "CycleGlobalVisibility", "NextHeading",
            "PreviousHeading", "NextSibling", "PreviousSibling", "ParentHeading",
        ).map { "org.mode.$it" }
        val manager = ActionManager.getInstance()
        val group = manager.getAction("org.mode.Menu") as DefaultActionGroup
        assertEquals(ids.sorted(), group.childActionsOrStubs.mapNotNull { manager.getId(it) }.sorted())
        val codeMenu = manager.getAction("CodeMenu") as DefaultActionGroup
        assertTrue(codeMenu.childActionsOrStubs.any { manager.getId(it) == "org.mode.Menu" })
    }

    fun testMoveStatementMovesTheSubtreeFromItsHeadingLine() {
        myFixture.configureByText("a.org", "* A\na\n* <caret>B\nb\n")
        myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
        myFixture.checkResult("* <caret>B\nb\n* A\na\n")
        myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
        myFixture.checkResult("* A\na\n* <caret>B\nb\n")
    }

    fun testMoveStatementMovesLinesOutsideHeadings() {
        myFixture.configureByText("a.org", "* A\nfirst\nsec<caret>ond\n")
        myFixture.performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
        myFixture.checkResult("* A\nsec<caret>ond\nfirst\n")
    }

    fun testMoveKeepsFoldedSubtreesFolded() {
        myFixture.configureByText("a.org", "* A\n** A1\nbody\n* <caret>B\nb\n")
        myFixture.buildFolding()
        myFixture.collapse("\n** A1\nbody")
        myFixture.performEditorAction("org.mode.MoveSubtreeUp")
        myFixture.checkResult("* <caret>B\nb\n* A\n** A1\nbody\n")
        assertEquals(listOf("\n** A1\nbody"), myFixture.collapsedTexts())
    }

    /** Blank lines ending the file stay outside a fold, so a Subtree moved away from the end folds them again. */
    fun testMoveKeepsTheLastSubtreeFolded() {
        myFixture.configureByText("a.org", "* A\na\n\n* <caret>B\nb\n\n")
        myFixture.buildFolding()
        myFixture.collapse("\nb")
        myFixture.performEditorAction("org.mode.MoveSubtreeUp")
        myFixture.checkResult("* <caret>B\nb\n\n* A\na\n\n")
        assertEquals(listOf("\nb\n"), myFixture.collapsedTexts())
    }

    /** As Emacs: TAB at the end of an empty Heading cycles its level, in Insert mode and with TAB cycling off too. */
    fun testTabCyclesTheLevelOfAnEmptyHeading() {
        val settings = OrgSettings.getInstance()
        settings.tabCyclesVisibility = false
        try {
            // Blank after the caret, which IdeaVim's Normal mode keeps on the line.
            myFixture.configureByText("a.org", "* A\n** B\n** TODO <caret> \n")
            for (stars in listOf("***", "*", "**")) {
                myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)
                myFixture.checkResult("* A\n** B\n$stars TODO <caret> \n")
            }
            undo()
            myFixture.checkResult("* A\n** B\n* TODO <caret> \n")
        } finally {
            settings.tabCyclesVisibility = true
        }
    }

    fun testRefusalsLeaveTheDocumentAlone() {
        for ((text, action) in listOf(
            "* To<caret>p\n" to "org.mode.PromoteHeading",
            "* A\n** <caret>B\n" to "org.mode.MoveSubtreeUp",
            "* <caret>A\n* B\n" to "org.mode.MoveSubtreeUp",
            "Bef<caret>ore\n* A\n" to "org.mode.CycleTodo",
            "- no b<caret>ox\n" to "org.mode.ToggleCheckbox",
            "* A\n** <caret>B\n" to "org.mode.CycleLevel",
        )) {
            myFixture.configureByText("a.org", text)
            myFixture.performEditorAction(action)
            myFixture.checkResult(text)
        }
    }

    fun testHierarchyCommandsRefuseMultipleCarets() {
        val text = "* A\n** <caret>B\n** <caret>C\n"
        myFixture.configureByText("a.org", text)
        myFixture.performEditorAction("org.mode.DemoteSubtree")
        myFixture.checkResult(text)
    }

    fun testActionsAreHiddenOutsideOrgFiles() {
        myFixture.configureByText("a.txt", "* A\n")
        val presentation = myFixture.testAction(ActionManager.getInstance().getAction("org.mode.CycleTodo"))
        assertFalse(presentation.isEnabledAndVisible)
        myFixture.checkResult("* A\n")
    }

    fun testNavigation() {
        myFixture.configureByText("a.org", "Int<caret>ro\n* A\nbody\n** A1\n** A2\n*** A2a\n* B\n")
        val text = myFixture.editor.document.text
        fun go(action: String, expected: String) {
            myFixture.performEditorAction("org.mode.$action")
            assertEquals(action, text.indexOf(expected), myFixture.caretOffset)
        }
        go("NextHeading", "* A")
        go("NextSibling", "* B")
        go("PreviousSibling", "* A")
        myFixture.editor.caretModel.moveToOffset(text.indexOf("A2a") + 1)
        go("ParentHeading", "** A2")
        go("PreviousSibling", "** A1")
        go("PreviousHeading", "* A")
        // A folded Subtree's Headings are skipped.
        myFixture.buildFolding()
        myFixture.collapse("\nbody\n** A1\n** A2\n*** A2a")
        go("NextHeading", "* B")
        go("PreviousHeading", "* A")
        // Before the first Heading, Next Sibling goes to it.
        myFixture.editor.caretModel.moveToOffset(0)
        go("NextSibling", "* A")
    }

    private fun undo() {
        val fileEditor = TextEditorProvider.getInstance().getTextEditor(myFixture.editor)
        UndoManager.getInstance(project).undo(fileEditor)
    }
}
