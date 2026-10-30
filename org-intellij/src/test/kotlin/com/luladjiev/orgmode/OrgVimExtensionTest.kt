package com.luladjiev.orgmode

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.maddyhome.idea.vim.KeyHandler
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.api.setToggleOption
import com.maddyhome.idea.vim.api.unsetToggleOption
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.key.MappingOwner
import com.maddyhome.idea.vim.key.ToKeysMappingInfo
import com.maddyhome.idea.vim.newapi.vim
import com.maddyhome.idea.vim.options.OptionAccessScope
import com.maddyhome.idea.vim.options.ToggleOption

/** IdeaVim integration: runs with IdeaVim installed via `testPlugin`, excluded from `testWithoutIdeaVim`. */
class OrgVimExtensionTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        if (VimPlugin.isNotEnabled()) VimPlugin.setEnabled(true)
    }

    override fun tearDown() {
        try {
            // IdeaVim's mode outlives the editor; leave Normal mode so later tests start from it.
            if (myFixture.editor != null) type("<Esc>")
            injector.keyGroup.removeKeyMapping(MappingOwner.IdeaVim.Other)
            setIntegration(false)
        } finally {
            super.tearDown()
        }
    }

    fun testExtensionIsRegisteredAndOffByDefault() {
        assertNotNull(orgOption())
        assertFalse(hasPlugMapping())
    }

    fun testEnablingAddsPlugMappingsAndDisablingRemovesThem() {
        setIntegration(true)
        assertTrue(hasPlugMapping())

        setIntegration(false)
        assertFalse(hasPlugMapping())
    }

    /** Users choose every key: enabling maps none to the `<Plug>(org-…)` commands. */
    fun testEnablingMapsNoKeys() {
        setIntegration(true)
        val plug = injector.parser.parseKeys("<Plug>(org-")
        for (mode in listOf(MappingMode.NORMAL, MappingMode.VISUAL)) {
            val mappings = injector.keyGroup.getKeyMapping(mode)
            val toOrg = mappings.getAll(emptyList()).map { it.getPath() }
                .filter { (mappings[it] as? ToKeysMappingInfo)?.toKeys?.take(plug.size) == plug }.toList()
            assertEquals(emptyList<Any>(), toOrg)
        }
    }

    fun testMappingRunsActionInOrgFile() {
        setIntegration(true)
        userMap("<leader>ot", "<Plug>(org-cycle-todo)")
        myFixture.configureByText("notes.org", "* Heading")

        type("<leader>ot")

        assertEquals("* TODO Heading", myFixture.editor.document.text)
    }

    fun testMappingReplaysKeysOutsideOrgFiles() {
        setIntegration(true)
        userMap("<leader>ot", "<Plug>(org-cycle-todo)")
        myFixture.configureByText("notes.txt", "abc")

        type("<leader>ot")

        // Unmapped `\ot`: `\` does nothing, `o` opens a line below, `t` is typed, exactly as in plain Vim.
        assertEquals("abc\nt", myFixture.editor.document.text)
    }

    /** `<Plug>(org-cycle-global-visibility)` cycles the whole document from anywhere, like Shift+TAB. */
    fun testMappingCyclesGlobalVisibility() {
        setIntegration(true)
        userMap("<leader>oZ", "<Plug>(org-cycle-global-visibility)")
        myFixture.configureByText("notes.org", "* A\nb<caret>ody\n** A1\na1\n* B\nb\n")
        myFixture.buildFolding()

        type("<leader>oZ")
        assertEquals(listOf("\nb", "\nbody\n** A1\na1"), myFixture.collapsedTexts().sorted())
        type("<leader>oZ")
        assertEquals(listOf("\na1", "\nb", "\nbody"), myFixture.collapsedTexts().sorted())
    }

    /** TAB on a Heading inserts text in Insert mode and cycles visibility otherwise. */
    fun testTabCyclesOnlyOutsideInsertMode() {
        myFixture.configureByText("notes.org", "* A<caret>\nbody\n")
        myFixture.buildFolding()

        type("A")
        try {
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)
            assertEquals(emptyList<String>(), myFixture.collapsedTexts())
            assertFalse(myFixture.editor.document.text == "* A\nbody\n")
        } finally {
            type("<Esc>")
        }
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)
        assertEquals(listOf("\nbody"), myFixture.collapsedTexts())
    }

    /** Mapped Tab cycles the Heading under the caret, Shift-Tab the whole document. */
    fun testMappedTabAndShiftTabCycleVisibility() {
        setIntegration(true)
        userMap("<Tab>", "<Plug>(org-cycle)")
        userMap("<S-Tab>", "<Plug>(org-global-cycle)")
        myFixture.configureByText("notes.org", "* <caret>A\na\n** A1\na1\n* B\nb\n")
        myFixture.buildFolding()

        type("<Tab>")
        assertEquals(listOf("\na\n** A1\na1"), myFixture.collapsedTexts())
        type("<Tab>")
        assertEquals(listOf("\na1"), myFixture.collapsedTexts())

        type("j<S-Tab>")
        assertEquals(listOf("\na\n** A1\na1", "\nb"), myFixture.collapsedTexts().sorted())
        type("<S-Tab>")
        assertEquals(listOf("\na", "\na1", "\nb"), myFixture.collapsedTexts().sorted())
        assertEquals("* A\na\n** A1\na1\n* B\nb\n", myFixture.editor.document.text)
    }

    /** Off a Heading line, and outside Org files, the mapped keys do what plain Vim does. */
    fun testMappedTabFallsThroughElsewhere() {
        setIntegration(true)
        userMap("<Tab>", "<Plug>(org-cycle)")
        userMap("<S-Tab>", "<Plug>(org-global-cycle)")
        myFixture.configureByText("notes.org", "* A\nb<caret>ody\n")
        myFixture.buildFolding()
        type("<Tab>")
        assertEquals(emptyList<String>(), myFixture.collapsedTexts())

        myFixture.configureByText("notes.txt", "a<caret>bc\n")
        type("<Tab><S-Tab>")
        assertEquals("abc\n", myFixture.editor.document.text)
    }

    /** As Emacs TAB, mapped `<Plug>(org-cycle)` cycles the level at the end of an empty Heading, then visibility. */
    fun testMappedTabCyclesTheLevelOfAnEmptyHeading() {
        setIntegration(true)
        userMap("<Tab>", "<Plug>(org-cycle)")
        // Normal mode keeps the caret on the trailing blank.
        myFixture.configureByText("notes.org", "* A\n** B\nbody\n** <caret> \n")
        for (stars in listOf("***", "*", "**")) {
            type("<Tab>")
            assertEquals("* A\n** B\nbody\n$stars  \n", myFixture.editor.document.text)
        }
        myFixture.editor.caretModel.moveToOffset(myFixture.editor.document.text.indexOf("B"))
        myFixture.buildFolding()
        type("<Tab>")
        assertEquals(listOf("\nbody"), myFixture.collapsedTexts())
    }

    /**
     * As Emacs M-→/M-←, in Insert mode Demote/Promote Heading act anywhere on a Heading line; elsewhere, and outside
     * Org files, the mapped key is typed as usual. Mapped to Tab and Shift+Tab, this gives Doom Emacs's keys.
     */
    fun testInsertModePromoteAndDemoteActOnHeadingLines() {
        setIntegration(true)
        userMap("<Tab>", "<Plug>(org-demote-heading)", MappingMode.I)
        userMap("<S-Tab>", "<Plug>(org-promote-heading)", MappingMode.I)
        myFixture.configureByText("notes.org", "* A\n* <caret>x\nbody\n")

        type("A<Tab>")
        assertEquals("* A\n** x\nbody\n", myFixture.editor.document.text)
        type("<S-Tab>")
        assertEquals("* A\n* x\nbody\n", myFixture.editor.document.text)
        type("<Esc>j0i<Tab><Esc>")
        assertTextMatches("\\* A\n\\* x\n[ \t]+body\n")

        myFixture.configureByText("notes.txt", "<caret>a\n")
        type("A<Tab><Esc>")
        assertTextMatches("a[ \t]+\n")
    }

    /** Emacs style: Tab cycles the level at the end of an empty Heading, and types anywhere else. */
    fun testInsertModeCycleLevel() {
        setIntegration(true)
        userMap("<Tab>", "<Plug>(org-cycle-level)", MappingMode.I)
        myFixture.configureByText("notes.org", "* A\n** B\n** <caret>x\n")

        type("A<BS>")
        for (stars in listOf("***", "*", "**")) {
            type("<Tab>")
            assertEquals("* A\n** B\n$stars \n", myFixture.editor.document.text)
        }
        type("x<Tab><Esc>")
        assertTextMatches("\\* A\n\\*\\* B\n\\*\\* x[ \t]+\n")
    }

    /** As Doom Emacs's `C-RET`: a Heading after the Subtree, typed into in Insert mode, from Normal or Insert mode. */
    fun testInsertHeadingAfterSubtreeEntersInsertMode() {
        setIntegration(true)
        userMap("<C-CR>", "<Plug>(org-insert-heading-after-subtree)")
        userMap("<C-CR>", "<Plug>(org-insert-todo-heading-after-subtree)", MappingMode.I)
        myFixture.configureByText("notes.org", "* A\n** B\nb<caret>ody\n*** C\n** D\n")

        type("<C-CR>x")
        assertEquals("* A\n** B\nbody\n*** C\n** x\n** D\n", myFixture.editor.document.text)
        type("<C-CR>y<Esc>")
        assertEquals("* A\n** B\nbody\n*** C\n** x\n** TODO y\n** D\n", myFixture.editor.document.text)
    }

    /** The motions follow Emacs `C-c C-n/p/f/b/u`, take a count and stop at the last possible move. */
    fun testNavigationMappingsMoveBetweenHeadings() {
        setIntegration(true)
        mapNavigation()
        val text = "* A\nbo<caret>dy\n** B\n** C\n* D\n"
        myFixture.configureByText("notes.org", text)
        val clean = text.replace("<caret>", "")

        type("<leader>on")
        assertCaretAt(clean.indexOf("** B"))
        type("<leader>of")
        assertCaretAt(clean.indexOf("** C"))
        type("<leader>ob")
        assertCaretAt(clean.indexOf("** B"))
        type("<leader>ou")
        assertCaretAt(clean.indexOf("* A"))
        type("2<leader>on")
        assertCaretAt(clean.indexOf("** C"))
        type("<leader>op")
        assertCaretAt(clean.indexOf("** B"))
        type("9<leader>of")
        assertCaretAt(clean.indexOf("** C"))
        type("<leader>of")
        assertCaretAt(clean.indexOf("** C"))
        assertEquals(clean, myFixture.editor.document.text)
    }

    /** Before the first Heading, the next-Heading and next-sibling motions both go to it, as in Emacs. */
    fun testNavigationFromBeforeTheFirstHeading() {
        setIntegration(true)
        mapNavigation()
        val text = "In<caret>tro\n** A\n*** B\n** C\n"
        myFixture.configureByText("notes.org", text)
        val clean = text.replace("<caret>", "")

        type("<leader>on")
        assertCaretAt(clean.indexOf("** A"))
        myFixture.editor.caretModel.moveToOffset(1)
        type("2<leader>of")
        assertCaretAt(clean.indexOf("** A"))
    }

    /** Like Vim's `[[` and `]]`, a Heading move is a jump that `<C-o>` returns from. */
    fun testNavigationJoinsJumpList() {
        setIntegration(true)
        mapNavigation()
        myFixture.configureByText("notes.org", "* <caret>A\na\n* B\nb\n* C\n")
        injector.jumpService.resetJumps()

        type("2<leader>on")
        assertCaretAt(myFixture.editor.document.text.indexOf("* C"))
        type("<C-o>")
        assertCaretAt(2)
    }

    /** In Visual mode a Heading motion extends the selection, inclusive of the Heading's first star. */
    fun testMotionExtendsVisualSelection() {
        setIntegration(true)
        mapNavigation(MappingMode.X)
        myFixture.configureByText("notes.org", "* <caret>A\nbody\n** B\n* C\n")

        type("v<leader>on")
        assertEquals("A\nbody\n*", myFixture.editor.selectionModel.selectedText)
        type("<leader>ou")
        assertEquals("* A", myFixture.editor.selectionModel.selectedText)
    }

    /** Motions are Normal and Visual only; IdeaVim would give an operator only a characterwise range. */
    fun testNoOperatorPendingMappings() {
        setIntegration(true)
        val mappings = injector.keyGroup.getKeyMapping(MappingMode.OP_PENDING)
        assertNull(mappings[injector.parser.parseKeys("<Plug>(org-next-heading)")])
    }

    /** Outside Org files a motion replays the keys mapped to it in the current mode, here a Visual-only mapping. */
    fun testVisualMappingReplaysItsKeysElsewhere() {
        userMap("J", "<Plug>(org-next-heading)", MappingMode.X)
        setIntegration(true)
        myFixture.configureByText("notes.txt", "<caret>a\nb\nc\n")

        type("VjJ")
        assertEquals("a b\nc\n", myFixture.editor.document.text)
    }

    /** A mapping to a motion moves in Org files and, outside them, replays its own keys with their count. */
    fun testUserMappedNavigationReplaysItsKeysElsewhere() {
        userMap("J", "<Plug>(org-next-heading)")
        setIntegration(true)

        myFixture.configureByText("notes.org", "* <caret>A\na\nb\n* B\n")
        type("J")
        assertCaretAt(myFixture.editor.document.text.indexOf("* B"))

        myFixture.configureByText("notes.txt", "<caret>a\nb\nc\nd\n")
        type("3J")
        assertEquals("a b c\nd\n", myFixture.editor.document.text)
    }

    private fun assertTextMatches(regex: String) =
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.matches(Regex(regex)))

    private fun assertCaretAt(offset: Int) = assertEquals(offset, myFixture.editor.caretModel.offset)

    private fun userMap(from: String, to: String, modes: Set<MappingMode> = MappingMode.N) = injector.keyGroup.putKeyMapping(
        modes, injector.parser.parseKeys(from), MappingOwner.IdeaVim.Other, injector.parser.parseKeys(to), true,
    )

    private fun orgOption() = injector.optionGroup.getOption("orgmode") as? ToggleOption

    private fun setIntegration(enabled: Boolean) {
        val option = orgOption() ?: return
        val scope = OptionAccessScope.GLOBAL(null)
        if (enabled) injector.optionGroup.setToggleOption(option, scope)
        else injector.optionGroup.unsetToggleOption(option, scope)
    }

    /** `<leader>o` + the letters of Emacs `C-c C-n/p/f/b/u`, as a user might map the motions. */
    private fun mapNavigation(modes: Set<MappingMode> = MappingMode.N) {
        val motions = listOf(
            "n" to "next-heading", "p" to "previous-heading", "f" to "next-sibling", "b" to "previous-sibling",
            "u" to "parent-heading",
        )
        for ((key, plug) in motions) userMap("<leader>o$key", "<Plug>(org-$plug)", modes)
    }

    private fun hasPlugMapping() =
        injector.keyGroup.getKeyMapping(MappingMode.NORMAL)[injector.parser.parseKeys("<Plug>(org-cycle-todo)")] != null

    private fun type(keys: String) {
        val editor = myFixture.editor
        val keyHandler = KeyHandler.getInstance()
        val context = injector.executionContextManager.getEditorExecutionContext(editor.vim)
        ApplicationManager.getApplication().invokeAndWait {
            injector.actionExecutor.executeCommand(editor.vim, {
                injector.parser.parseKeys(keys).forEach { keyHandler.handleKey(editor.vim, it, context, keyHandler.keyHandlerState) }
            }, null, null)
        }
    }
}
