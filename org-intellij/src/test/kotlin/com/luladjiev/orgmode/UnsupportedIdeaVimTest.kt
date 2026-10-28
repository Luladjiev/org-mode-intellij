package com.luladjiev.orgmode

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.VersionComparatorUtil
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.api.setToggleOption
import com.maddyhome.idea.vim.api.unsetToggleOption
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.options.OptionAccessScope
import com.maddyhome.idea.vim.options.ToggleOption

/**
 * With an IdeaVim older than the supported minimum (`-PtestIdeaVimVersion=2.27.2`), `set orgmode` registers no
 * `<Plug>` commands and Org editing keeps working. Does nothing with a supported IdeaVim, which
 * `OrgVimExtensionTest` covers; excluded from `testWithoutIdeaVim`.
 */
class UnsupportedIdeaVimTest : BasePlatformTestCase() {
    fun testIntegrationStaysOffAndOrgEditingWorks() {
        if (VersionComparatorUtil.compare(VimPlugin.getVersion(), "2.28.0") >= 0) return
        if (VimPlugin.isNotEnabled()) VimPlugin.setEnabled(true)
        val option = injector.optionGroup.getOption("orgmode") as ToggleOption
        val scope = OptionAccessScope.GLOBAL(null)
        injector.optionGroup.setToggleOption(option, scope)
        try {
            assertNull(injector.keyGroup.getKeyMapping(MappingMode.NORMAL)[injector.parser.parseKeys("<Plug>(org-cycle-todo)")])

            myFixture.configureByText("notes.org", "* A<caret>\nbody\n")
            myFixture.buildFolding()
            // TAB asks IdeaVim for its mode before cycling.
            myFixture.performEditorAction(IdeActions.ACTION_EDITOR_TAB)
            assertEquals(listOf("\nbody"), myFixture.collapsedTexts())
            myFixture.performEditorAction("org.mode.CycleTodo")
            assertEquals("* TODO A\nbody\n", myFixture.editor.document.text)
        } finally {
            injector.optionGroup.unsetToggleOption(option, scope)
        }
    }
}
