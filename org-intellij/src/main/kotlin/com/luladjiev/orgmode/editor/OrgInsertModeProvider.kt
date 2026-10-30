package com.luladjiev.orgmode.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Tells whether a modal editing plugin has the editor in Insert mode, where TAB must keep inserting text.
 * `org-ideavim` implements it for IdeaVim, so this module never references IdeaVim classes.
 */
interface OrgInsertModeProvider {
    fun isInsertMode(editor: Editor): Boolean

    companion object {
        val EP_NAME = ExtensionPointName<OrgInsertModeProvider>("com.luladjiev.org-mode.insertModeProvider")

        fun isInsertMode(editor: Editor) = EP_NAME.extensionList.any { it.isInsertMode(editor) }
    }
}
