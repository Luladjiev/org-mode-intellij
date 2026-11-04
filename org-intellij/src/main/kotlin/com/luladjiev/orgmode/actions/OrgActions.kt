package com.luladjiev.orgmode.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.DumbAwareAction
import com.luladjiev.orgmode.core.edit.CheckboxEdits
import com.luladjiev.orgmode.core.edit.EditResult
import com.luladjiev.orgmode.core.edit.HeadingMotion
import com.luladjiev.orgmode.core.edit.OrgSnapshot
import com.luladjiev.orgmode.core.edit.StructureEdits
import com.luladjiev.orgmode.core.edit.TodoEdits
import com.luladjiev.orgmode.editor.OrgCommands
import com.luladjiev.orgmode.folding.OrgVisibility
import com.luladjiev.orgmode.psi.OrgFile

// Action IDs are `org.mode.<Verb><Object>` and never change once released; see plugin.xml.

private fun AnActionEvent.orgEditor(): Pair<Editor, OrgFile>? {
    val editor = getData(CommonDataKeys.EDITOR) ?: return null
    val file = getData(CommonDataKeys.PSI_FILE) as? OrgFile ?: return null
    return editor to file
}

/** The Code | Org Mode menu, shown only for Org editors. */
class OrgActionGroup : DefaultActionGroup() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.orgEditor() != null
    }
}

/** An action available only in Org editors. */
abstract class OrgEditorAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.orgEditor() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val (editor, file) = e.orgEditor() ?: return
        perform(editor, file)
    }

    protected abstract fun perform(editor: Editor, file: OrgFile)

    protected val name: String get() = templatePresentation.text
}

/** Promotion, demotion, moves and new Headings: one caret only, acting on the Subtree around it. */
abstract class StructureAction(private val edit: (OrgSnapshot, Int) -> EditResult) : OrgEditorAction() {
    override fun perform(editor: Editor, file: OrgFile) {
        if (!OrgCommands.checkSingleCaret(editor)) return
        OrgCommands.apply(editor, file, name, edit(OrgCommands.snapshot(editor), editor.caretModel.offset), moveCaret = true)
    }
}

class PromoteHeadingAction : StructureAction(StructureEdits::promoteHeading)

class PromoteSubtreeAction : StructureAction(StructureEdits::promoteSubtree)

class DemoteHeadingAction : StructureAction(StructureEdits::demoteHeading)

class DemoteSubtreeAction : StructureAction(StructureEdits::demoteSubtree)

class CycleLevelAction : StructureAction(StructureEdits::cycleLevel)

class MoveSubtreeUpAction : StructureAction(StructureEdits::moveSubtreeUp)

class MoveSubtreeDownAction : StructureAction(StructureEdits::moveSubtreeDown)

class InsertHeadingAfterSubtreeAction : StructureAction({ snapshot, offset ->
    StructureEdits.insertHeadingAfterSubtree(snapshot, offset)
})

class InsertTodoHeadingAfterSubtreeAction : StructureAction({ snapshot, offset ->
    StructureEdits.insertHeadingAfterSubtree(snapshot, offset, todo = true)
})

/** Applies once per distinct Heading or checkbox under any caret, as one undo step. */
abstract class AllCaretsAction(private val edit: (OrgSnapshot, List<Int>) -> EditResult) : OrgEditorAction() {
    override fun perform(editor: Editor, file: OrgFile) {
        val offsets = editor.caretModel.allCarets.map { it.offset }
        OrgCommands.apply(editor, file, name, edit(OrgCommands.snapshot(editor), offsets), moveCaret = false)
    }
}

class CycleTodoAction : AllCaretsAction(TodoEdits::cycle)

class ToggleCheckboxAction : AllCaretsAction(CheckboxEdits::toggle)

/** Cycles the Subtree around the caret, like TAB on its Heading but from anywhere and regardless of settings. */
class CycleVisibilityAction : OrgEditorAction() {
    override fun perform(editor: Editor, file: OrgFile) {
        val subtree = OrgCommands.subtreeAtCaret(editor, file) ?: return
        OrgVisibility.updateRegions(editor)
        OrgVisibility.cycle(editor, subtree)
    }
}

class CycleGlobalVisibilityAction : OrgEditorAction() {
    override fun perform(editor: Editor, file: OrgFile) {
        OrgVisibility.updateRegions(editor)
        OrgVisibility.cycleGlobal(editor, file)
    }
}

/** Moves each caret to the first column of a Heading (Emacs `C-c C-n/p/f/b/u`); no target, no move. */
abstract class NavigationAction(private val motion: HeadingMotion) : OrgEditorAction() {
    override fun perform(editor: Editor, file: OrgFile) {
        val snapshot = OrgCommands.snapshot(editor)
        val isVisible = OrgCommands.isVisible(editor)
        editor.caretModel.runForEachCaret { caret ->
            motion.target(snapshot, caret.offset, isVisible)?.let {
                caret.removeSelection()
                caret.moveToOffset(it.start)
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }
}

class NextHeadingAction : NavigationAction(HeadingMotion.NEXT_HEADING)

class PreviousHeadingAction : NavigationAction(HeadingMotion.PREVIOUS_HEADING)

class NextSiblingAction : NavigationAction(HeadingMotion.NEXT_SIBLING)

class PreviousSiblingAction : NavigationAction(HeadingMotion.PREVIOUS_SIBLING)

class ParentHeadingAction : NavigationAction(HeadingMotion.PARENT_HEADING)
