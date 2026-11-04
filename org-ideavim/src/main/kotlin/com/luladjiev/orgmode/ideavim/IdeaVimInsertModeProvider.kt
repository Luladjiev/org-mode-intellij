package com.luladjiev.orgmode.ideavim

import com.intellij.openapi.editor.Editor
import com.luladjiev.orgmode.editor.OrgInsertModeProvider
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.newapi.vim
import com.maddyhome.idea.vim.state.mode.Mode

/** Keeps TAB inserting text while IdeaVim is in Insert or Replace mode. */
class IdeaVimInsertModeProvider : OrgInsertModeProvider {
    override fun isInsertMode(editor: Editor): Boolean = IdeaVimCompat.call(false) {
        if (!VimPlugin.isEnabled()) return@call false
        val mode = editor.vim.mode
        mode is Mode.INSERT || mode is Mode.REPLACE
    }
}
