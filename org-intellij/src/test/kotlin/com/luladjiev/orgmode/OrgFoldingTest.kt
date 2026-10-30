package com.luladjiev.orgmode

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.luladjiev.orgmode.folding.OrgVisibility
import com.luladjiev.orgmode.settings.OrgSettings

/** Folding regions and visibility cycling. */
class OrgFoldingTest : BasePlatformTestCase() {
    fun testRegions() {
        myFixture.configureByText(
            "a.org",
            "* A\nbody\n** B\nb\n\n* C\n:PROPERTIES:\n:ID: x\n:END:\n#+BEGIN_SRC sh\necho\n#+END_SRC\n* Empty\n",
        )
        myFixture.buildFolding()
        assertEquals(
            listOf(
                "\nbody\n** B\nb\n", // A's Subtree, with its one trailing blank line
                "\nbody", // A's Section, because A has children
                "\nb\n",
                "\n:PROPERTIES:\n:ID: x\n:END:\n#+BEGIN_SRC sh\necho\n#+END_SRC",
                "\n:ID: x\n:END:",
                "\necho\n#+END_SRC",
            ).sorted(),
            myFixture.regionTexts().sorted(),
        )
    }

    /** As Emacs `org-cycle-separator-lines`: N blank lines before the next Heading keep one visible, fewer none. */
    fun testFoldedSubtreesHideBlankLinesBeforeTheNextHeading() {
        val text = "* A\na\n\n* B\nb\n\n\n* C\nc\n\n\n  \n* D\nd\n\n"
        for ((lines, expected) in listOf(
            2 to "* A...\n* B...\n\n* C...\n  \n* D...\n\n",
            1 to "* A...\n\n* B...\n\n* C...\n  \n* D...\n\n",
            3 to "* A...\n* B...\n* C...\n  \n* D...\n\n",
            0 to "* A...\n* B...\n* C...\n* D...\n\n",
            // Negative: all of them, once there are at least -N. Blank lines that end the file always show.
            -2 to "* A...\n* B...\n\n\n* C...\n\n\n  \n* D...\n\n",
        )) {
            withSeparatorLines(lines) {
                myFixture.configureByText("separator$lines.org", text)
                myFixture.buildFolding()
                myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS)
                assertEquals("separator lines $lines", expected, myFixture.foldedText())
            }
        }
    }

    /** The contents state folds Sections too, and the blank lines before child Headings with them. */
    fun testContentsStateHidesBlankLinesBeforeChildHeadings() {
        myFixture.configureByText("a.org", "* <caret>A\nbody\n\n** B\nb\n\n** C\nc\n")
        myFixture.buildFolding()
        shiftTab()
        assertEquals("* A...\n", myFixture.foldedText())
        shiftTab()
        assertEquals("* A...\n** B...\n** C...\n", myFixture.foldedText())
    }

    /** As Emacs: a Section of only blank lines folds in the contents state but shows in the children state. */
    fun testBlankSectionsFoldBeforeChildHeadings() {
        myFixture.configureByText("a.org", "* <caret>Bugs\n\n** A\na\n** B\nb\n* C\n\n\n** D\nd\n")
        myFixture.buildFolding()
        shiftTab()
        shiftTab()
        assertEquals("* Bugs...\n** A...\n** B...\n* C...\n\n** D...\n", myFixture.foldedText())
        shiftTab()
        shiftTab()
        tab()
        assertEquals("* Bugs\n\n** A...\n** B...\n* C...\n", myFixture.foldedText())
    }

    /** As Emacs: a Heading with only blank lines below folds in the overview, and TAB only shows it. */
    fun testBlankEntries() {
        myFixture.configureByText("a.org", "* <caret>A\n\n\n* B\n\n* C\nc\n")
        myFixture.buildFolding()
        shiftTab()
        assertEquals("* A...\n\n* B...\n* C...\n", myFixture.foldedText())
        tab()
        assertEquals("* A\n\n\n* B...\n* C...\n", myFixture.foldedText())
        tab()
        assertEquals("* A\n\n\n* B...\n* C...\n", myFixture.foldedText())
    }

    /**
     * The gutter shows the arrow of the smaller region that starts on a line: on a Heading with children, the
     * Section's. Clicking it folds the whole Subtree.
     */
    fun testGutterArrowFoldsTheSubtree() {
        myFixture.configureByText("a.org", "* A\n\n** B\nb\n* C\n")
        myFixture.buildFolding()
        val regions = myFixture.editor.foldingModel.allFoldRegions
        val section = regions.single { myFixture.editor.document.getText(it.textRange) == "\n" }
        assertTrue(OrgVisibility.gutterClicked(myFixture.editor, section))
        assertEquals(listOf("\n\n** B\nb"), myFixture.collapsedTexts())
        // Any other arrow is the platform's.
        assertFalse(OrgVisibility.gutterClicked(myFixture.editor, section))
        assertFalse(OrgVisibility.gutterClicked(myFixture.editor, regions.single { it.endOffset - it.startOffset == 2 }))
        assertEquals(listOf("\n\n** B\nb"), myFixture.collapsedTexts())
    }

    fun testChangingSeparatorLinesUpdatesTheRegions() {
        myFixture.configureByText("a.org", "* A\na\n\n* B\n")
        myFixture.buildFolding()
        assertEquals(listOf("\na\n"), myFixture.regionTexts())
        withSeparatorLines(1) {
            // Open Org editors pick up the new setting in the next highlighting pass.
            myFixture.doHighlighting()
            assertEquals(listOf("\na"), myFixture.regionTexts())
        }
    }

    fun testTabCyclesTheHeadingOnTheCaretLine() {
        myFixture.configureByText("a.org", "* <caret>A\nbody\n** B\nb\n** C\nc\n")
        myFixture.buildFolding()
        tab()
        assertEquals(listOf("\nbody\n** B\nb\n** C\nc"), myFixture.collapsedTexts())
        tab()
        assertEquals(listOf("\nb", "\nc"), myFixture.collapsedTexts())
        tab()
        assertEquals(emptyList<String>(), myFixture.collapsedTexts())
        tab()
        assertEquals(listOf("\nbody\n** B\nb\n** C\nc"), myFixture.collapsedTexts())
        myFixture.checkResult("* <caret>A\nbody\n** B\nb\n** C\nc\n")
    }

    fun testTabKeepsItsUsualMeaningElsewhere() {
        for (text in listOf("* A\nbo<caret>dy\n", "<selection>* A</selection>\nbody\n", "* <caret>A\n** B<caret>\n")) {
            myFixture.configureByText("a.org", text)
            val before = myFixture.editor.document.text
            tab()
            assertFalse(text, before == myFixture.editor.document.text)
        }
    }

    fun testTabCyclingCanBeDisabled() {
        val settings = OrgSettings.getInstance()
        settings.tabCyclesVisibility = false
        try {
            myFixture.configureByText("a.org", "* <caret>A\nbody\n")
            myFixture.buildFolding()
            tab()
            assertEquals(emptyList<String>(), myFixture.collapsedTexts())
            assertFalse(myFixture.editor.document.text == "* A\nbody\n")
        } finally {
            settings.tabCyclesVisibility = true
        }
    }

    fun testShiftTabCyclesTheDocument() {
        myFixture.configureByText("a.org", "* <caret>A\na\n** A1\na1\n* B\nb\n")
        myFixture.buildFolding()
        shiftTab()
        assertEquals(listOf("\na\n** A1\na1", "\nb"), myFixture.collapsedTexts().sorted())
        shiftTab()
        assertEquals(listOf("\na", "\na1", "\nb"), myFixture.collapsedTexts().sorted())
        shiftTab()
        assertEquals(emptyList<String>(), myFixture.collapsedTexts())
        shiftTab()
        assertEquals(listOf("\na\n** A1\na1", "\nb"), myFixture.collapsedTexts().sorted())
    }

    fun testCycleVisibilityActionWorksFromTheBody() {
        myFixture.configureByText("a.org", "* A\nbo<caret>dy\n")
        myFixture.buildFolding()
        myFixture.performEditorAction("org.mode.CycleVisibility")
        assertEquals(listOf("\nbody"), myFixture.collapsedTexts())
    }

    fun testNativeCollapseAndExpandAll() {
        myFixture.configureByText("a.org", "* <caret>A\na\n** A1\na1\n")
        myFixture.buildFolding()
        myFixture.performEditorAction(IdeActions.ACTION_COLLAPSE_ALL_REGIONS)
        assertEquals(myFixture.regionTexts(), myFixture.collapsedTexts())
        myFixture.performEditorAction(IdeActions.ACTION_EXPAND_ALL_REGIONS)
        assertEquals(emptyList<String>(), myFixture.collapsedTexts())
    }

    private fun withSeparatorLines(lines: Int, block: () -> Unit) {
        val settings = OrgSettings.getInstance()
        val saved = settings.separatorLines
        settings.separatorLines = lines
        try {
            block()
        } finally {
            settings.separatorLines = saved
        }
    }

    private fun tab() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)

    private fun shiftTab() = myFixture.performEditorAction(IdeActions.ACTION_EDITOR_UNINDENT_SELECTION)
}
