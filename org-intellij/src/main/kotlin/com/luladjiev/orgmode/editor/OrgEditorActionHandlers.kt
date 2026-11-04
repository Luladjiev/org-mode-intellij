package com.luladjiev.orgmode.editor

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.luladjiev.orgmode.OrgBundle
import com.luladjiev.orgmode.core.edit.EditResult
import com.luladjiev.orgmode.core.edit.OrgSnapshot
import com.luladjiev.orgmode.core.edit.StructureEdits
import com.luladjiev.orgmode.folding.OrgVisibility
import com.luladjiev.orgmode.lang.OrgFileType
import com.luladjiev.orgmode.psi.OrgFile
import com.luladjiev.orgmode.psi.OrgSubtree
import com.luladjiev.orgmode.settings.OrgSettings
import org.jetbrains.annotations.PropertyKey

/**
 * Wraps a platform editor action: in an Org editor where [orgTarget] finds something to act on, runs [perform];
 * everywhere else, the platform's own handler runs unchanged.
 */
abstract class OrgEditorActionHandler<T : Any>(private val original: EditorActionHandler) : EditorActionHandler() {
    protected abstract fun orgTarget(editor: Editor, file: OrgFile): T?

    protected abstract fun perform(editor: Editor, file: OrgFile, target: T)

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?) =
        editor.virtualFile?.fileType == OrgFileType || original.isEnabled(editor, caret, dataContext)

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val file = if (editor.virtualFile?.fileType == OrgFileType) OrgCommands.orgFile(editor) else null
        val target = file?.let { orgTarget(editor, it) }
        if (target != null) perform(editor, file, target) else original.execute(editor, caret, dataContext)
    }
}

/**
 * TAB cycles a Heading's visibility only when the caret is on its line, with no selection, no completion popup and
 * no modal editor in Insert mode, and only while enabled in settings; otherwise TAB is untouched.
 */
private fun cyclingSubtree(editor: Editor, file: OrgFile): OrgSubtree? {
    if (!OrgSettings.getInstance().tabCyclesVisibility) return null
    if (editor.caretModel.caretCount > 1 || editor.selectionModel.hasSelection()) return null
    if (LookupManager.getActiveLookup(editor) != null || OrgInsertModeProvider.isInsertMode(editor)) return null
    return OrgCommands.subtreeOnCaretLine(editor, file)
}

/**
 * TAB at the end of an empty Heading cycles its level, also in Insert mode and with visibility cycling off, as Emacs
 * TAB on a new Heading does; on other Heading lines, folded → children → subtree.
 */
class OrgTabHandler(original: EditorActionHandler) : OrgEditorActionHandler<() -> Unit>(original) {
    override fun orgTarget(editor: Editor, file: OrgFile): (() -> Unit)? {
        if (OrgCommands.isAtEndOfEmptyHeading(editor)) return { OrgCommands.cycleLevel(editor, file) }
        val subtree = cyclingSubtree(editor, file) ?: return null
        return {
            OrgVisibility.updateRegions(editor)
            OrgVisibility.cycle(editor, subtree)
        }
    }

    override fun perform(editor: Editor, file: OrgFile, target: () -> Unit) = target()
}

/** Shift+TAB on a Heading line: overview → contents → show all, under the same conditions as TAB. */
class OrgShiftTabHandler(original: EditorActionHandler) : OrgEditorActionHandler<OrgSubtree>(original) {
    override fun orgTarget(editor: Editor, file: OrgFile) = cyclingSubtree(editor, file)

    override fun perform(editor: Editor, file: OrgFile, target: OrgSubtree) {
        OrgVisibility.updateRegions(editor)
        OrgVisibility.cycleGlobal(editor, file)
    }
}

/**
 * Move Statement Up/Down on a Heading line moves its Subtree among its siblings; on other lines the
 * platform moves lines as usual.
 */
abstract class OrgMoveStatementHandler(
    original: EditorActionHandler,
    private val move: (OrgSnapshot, Int) -> EditResult,
    @PropertyKey(resourceBundle = "messages.OrgBundle") private val nameKey: String,
) : OrgEditorActionHandler<OrgSubtree>(original) {
    override fun orgTarget(editor: Editor, file: OrgFile) = OrgCommands.subtreeOnCaretLine(editor, file)

    override fun perform(editor: Editor, file: OrgFile, target: OrgSubtree) {
        if (!OrgCommands.checkSingleCaret(editor)) return
        val result = move(OrgCommands.snapshot(editor), editor.caretModel.offset)
        OrgCommands.apply(editor, file, OrgBundle.message(nameKey), result, moveCaret = true)
    }
}

class OrgMoveStatementUpHandler(original: EditorActionHandler) :
    OrgMoveStatementHandler(original, StructureEdits::moveSubtreeUp, "action.org.mode.MoveSubtreeUp.text")

class OrgMoveStatementDownHandler(original: EditorActionHandler) :
    OrgMoveStatementHandler(original, StructureEdits::moveSubtreeDown, "action.org.mode.MoveSubtreeDown.text")
