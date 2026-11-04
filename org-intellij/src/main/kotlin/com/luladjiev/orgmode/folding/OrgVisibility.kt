package com.luladjiev.orgmode.folding

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.luladjiev.orgmode.psi.OrgFile
import com.luladjiev.orgmode.psi.OrgSubtree

/**
 * Emacs-style visibility cycling over the regions of [OrgFoldingBuilder]. The current
 * state is read from the regions rather than remembered, so cycling picks up after any manual folding.
 */
object OrgVisibility {
    /** One Heading: folded → children → subtree → folded. */
    fun cycle(editor: Editor, subtree: OrgSubtree) {
        val text = editor.document.immutableCharSequence
        val regions = Regions(editor)
        val own = regions.subtree(subtree, text) ?: return
        if (OrgFoldRanges.isBlank(subtree, text)) {
            editor.foldingModel.runBatchFoldingOperation { own.isExpanded = true }
            return
        }
        val withDescendants = PsiTreeUtil.findChildrenOfType(subtree, OrgSubtree::class.java) + subtree
        editor.foldingModel.runBatchFoldingOperation {
            when {
                // Folded → children: the Section and the child Headings, each child folded.
                !own.isExpanded -> {
                    own.isExpanded = true
                    regions.section(subtree, text)?.isExpanded = true
                    for (child in subtree.childSubtrees) regions.subtree(child, text)?.isExpanded = false
                }
                // Children (or anything partly folded) → subtree: everything below the Heading.
                withDescendants.any { regions.anyCollapsed(it, text) } ->
                    for (s in withDescendants) regions.expand(s, text)
                // Subtree → folded.
                else -> own.isExpanded = false
            }
        }
    }

    /** The whole document: overview (top-level Headings) → contents (all Headings) → show all → overview. */
    fun cycleGlobal(editor: Editor, file: OrgFile) {
        val text = editor.document.immutableCharSequence
        val regions = Regions(editor)
        val all = PsiTreeUtil.findChildrenOfType(file, OrgSubtree::class.java)
        val top = file.children.filterIsInstance<OrgSubtree>()
        val contents = all.all { regions.matchesContents(it, text) }
        val overview = top.all { regions.subtree(it, text)?.isExpanded != true }
        editor.foldingModel.runBatchFoldingOperation {
            when {
                contents -> for (s in all) regions.expand(s, text)
                overview -> for (s in all) regions.showContents(s, text)
                else -> {
                    for (s in all) regions.expand(s, text)
                    for (s in top) regions.subtree(s, text)?.isExpanded = false
                }
            }
        }
    }

    /**
     * A plain click on the gutter arrow of [region]. On a line where several regions start, the gutter shows the
     * smallest one's arrow: on a Heading with children, its Section's. Folds the largest expanded region starting
     * there instead, the Subtree, and returns false when that is [region] itself, so the platform handles the click.
     */
    fun gutterClicked(editor: Editor, region: FoldRegion): Boolean {
        if (!region.isExpanded) return false
        val outer = editor.foldingModel.allFoldRegions
            .filter { it.startOffset == region.startOffset && it.isExpanded }
            .maxBy { it.endOffset }
        if (outer === region) return false
        editor.foldingModel.runBatchFoldingOperation { outer.isExpanded = false }
        return true
    }

    /** Brings the editor's regions up to date with the document, so the lookups below find them. */
    fun updateRegions(editor: Editor) {
        val project = editor.project ?: return
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        CodeFoldingManager.getInstance(project).updateFoldRegions(editor)
    }

    private class Regions(private val editor: Editor) {
        fun subtree(subtree: OrgSubtree, text: CharSequence): FoldRegion? = region(OrgFoldRanges.subtree(subtree, text))

        fun section(subtree: OrgSubtree, text: CharSequence): FoldRegion? = region(OrgFoldRanges.section(subtree, text))

        private fun region(range: TextRange?): FoldRegion? =
            range?.let { editor.foldingModel.getFoldRegion(it.startOffset, it.endOffset) }

        fun anyCollapsed(subtree: OrgSubtree, text: CharSequence) =
            subtree(subtree, text)?.isExpanded == false || section(subtree, text)?.isExpanded == false

        fun expand(subtree: OrgSubtree, text: CharSequence) {
            subtree(subtree, text)?.isExpanded = true
            section(subtree, text)?.isExpanded = true
        }

        /** Contents state: a Heading with children shows them but folds its Section; a leaf folds entirely. */
        fun showContents(subtree: OrgSubtree, text: CharSequence) {
            val hasChildren = subtree.childSubtrees.isNotEmpty()
            subtree(subtree, text)?.isExpanded = hasChildren
            section(subtree, text)?.isExpanded = false
        }

        fun matchesContents(subtree: OrgSubtree, text: CharSequence): Boolean {
            val hasChildren = subtree.childSubtrees.isNotEmpty()
            return subtree(subtree, text).let { it == null || it.isExpanded == hasChildren } &&
                section(subtree, text).let { it == null || !it.isExpanded }
        }
    }
}
