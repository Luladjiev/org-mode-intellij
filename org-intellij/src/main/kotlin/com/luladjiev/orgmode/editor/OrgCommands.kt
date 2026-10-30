package com.luladjiev.orgmode.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.luladjiev.orgmode.OrgBundle
import com.luladjiev.orgmode.core.edit.EditResult
import com.luladjiev.orgmode.core.edit.OrgSnapshot
import com.luladjiev.orgmode.core.edit.StructureEdits
import com.luladjiev.orgmode.folding.OrgVisibility
import com.luladjiev.orgmode.psi.OrgFile
import com.luladjiev.orgmode.psi.OrgHeading
import com.luladjiev.orgmode.psi.OrgSubtree
import org.jetbrains.annotations.Nls
import kotlin.math.abs

/**
 * Runs `org-core` edits in the editor: parse a snapshot, compute the edits there, apply them as one
 * undoable write command, and keep folded regions folded.
 */
object OrgCommands {
    fun orgFile(editor: Editor): OrgFile? {
        val project = editor.project ?: return null
        return PsiDocumentManager.getInstance(project).getPsiFile(editor.document) as? OrgFile
    }

    fun snapshot(editor: Editor) = OrgSnapshot.parse(editor.document.immutableCharSequence)

    /** Whether an offset lies outside collapsed folds; Heading navigation skips the Headings inside them. */
    fun isVisible(editor: Editor): (Int) -> Boolean = { !editor.foldingModel.isOffsetCollapsed(it) }

    /** The Subtree whose Heading is on the caret's line. Commits the document first. */
    fun subtreeOnCaretLine(editor: Editor, file: OrgFile): OrgSubtree? {
        val document = editor.document
        PsiDocumentManager.getInstance(file.project).commitDocument(document)
        val lineStart = document.getLineStartOffset(document.getLineNumber(editor.caretModel.offset))
        val heading = file.findElementAt(lineStart)?.parent as? OrgHeading ?: return null
        return heading.parent as? OrgSubtree
    }

    /** The Subtree containing the caret, wherever it is in it. Commits the document first. */
    fun subtreeAtCaret(editor: Editor, file: OrgFile): OrgSubtree? {
        PsiDocumentManager.getInstance(file.project).commitDocument(editor.document)
        val offset = editor.caretModel.offset
        val leaf = file.findElementAt(offset) ?: file.findElementAt(offset - 1)
        return PsiTreeUtil.getParentOfType(leaf, OrgSubtree::class.java, false)
    }

    /**
     * Whether a single caret, with no selection or completion popup, is at the end of an empty Heading, where TAB
     * cycles the level (Emacs `org-cycle`).
     */
    fun isAtEndOfEmptyHeading(editor: Editor): Boolean {
        val caret = editor.caretModel
        if (caret.caretCount > 1 || editor.selectionModel.hasSelection() || LookupManager.getActiveLookup(editor) != null) {
            return false
        }
        // Only a line starting with a star and blank after the caret is worth parsing the document for.
        val document = editor.document
        val line = document.getLineNumber(caret.offset)
        val text = document.immutableCharSequence
        return text.getOrNull(document.getLineStartOffset(line)) == '*' &&
            text.subSequence(caret.offset, document.getLineEndOffset(line)).isBlank() &&
            snapshot(editor).isAtEndOfEmptyHeading(caret.offset)
    }

    /** Cycle Level at the caret, as one undoable command. */
    fun cycleLevel(editor: Editor, file: OrgFile) {
        val result = StructureEdits.cycleLevel(snapshot(editor), editor.caretModel.offset)
        apply(editor, file, OrgBundle.message("action.org.mode.CycleLevel.text"), result, moveCaret = true)
    }

    fun hint(editor: Editor, @Nls message: String) = HintManager.getInstance().showErrorHint(editor, message)

    /** Hierarchy commands are disabled with several carets; returns false after showing a hint. */
    fun checkSingleCaret(editor: Editor): Boolean {
        if (editor.caretModel.caretCount == 1) return true
        hint(editor, OrgBundle.message("hint.multiple.carets"))
        return false
    }

    /**
     * Applies [result] as one command named [name]. With [moveCaret] the caret follows its text, also when a
     * Subtree move carries it elsewhere. Regions that were folded inside the edited range are folded again.
     */
    fun apply(editor: Editor, file: OrgFile, @Nls name: String, result: EditResult, moveCaret: Boolean) {
        val applied = when (result) {
            is EditResult.Refused -> {
                hint(editor, OrgBundle.message("refusal.${result.refusal.name}"))
                return
            }

            is EditResult.Applied -> result
        }
        if (applied.edits.isEmpty()) return
        val document = editor.document
        val folding = editor.foldingModel
        val from = applied.edits.minOf { it.start }
        val to = applied.edits.maxOf { it.end }
        val folded = folding.allFoldRegions
            .filter { !it.isExpanded && it.startOffset >= from && it.endOffset <= to }
            .map { it.startOffset to it.endOffset }
        val caret = editor.caretModel.offset
        WriteCommandAction.writeCommandAction(file.project, file).withName(name).run<RuntimeException> {
            for (edit in applied.edits.sortedByDescending { it.start }) {
                document.replaceString(edit.start, edit.end, edit.replacement)
            }
            if (moveCaret) editor.caretModel.moveToOffset(applied.caret ?: applied.mapOffset(caret))
        }
        if (folded.isNotEmpty()) {
            OrgVisibility.updateRegions(editor)
            // Matched by start: a Subtree's region ends elsewhere when the move takes it to or from the end
            // of the file, whose blank lines never fold. A Subtree and its Section share a start; the closer
            // end tells them apart.
            val regions = folding.allFoldRegions.groupBy { it.startOffset }
            folding.runBatchFoldingOperation {
                for ((start, end) in folded) {
                    val mappedEnd = applied.mapOffset(end)
                    regions[applied.mapOffset(start)]?.minByOrNull { abs(it.endOffset - mappedEnd) }?.isExpanded = false
                }
            }
        }
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }
}
