package com.luladjiev.orgmode.folding

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.luladjiev.orgmode.core.parser.OrgElementType
import com.luladjiev.orgmode.lang.OrgFileType
import com.luladjiev.orgmode.psi.OrgElement
import com.luladjiev.orgmode.psi.OrgSubtree
import com.luladjiev.orgmode.settings.OrgSettings
import kotlin.math.abs

/**
 * Folding through IntelliJ's native regions, so Collapse/Expand (All) work as usual. Each Subtree
 * folds below its Heading; a Subtree with children also folds its Section alone, which lets visibility cycling
 * show child Headings without body text. Drawers and blocks fold below their first line. Nothing is collapsed by
 * default: the initial state is IntelliJ's remembered one.
 */
class OrgFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val text = document.immutableCharSequence
        val descriptors = ArrayList<FoldingDescriptor>()
        for (element in PsiTreeUtil.findChildrenOfType(root, OrgElement::class.java)) {
            when (element.type) {
                OrgElementType.SUBTREE -> {
                    val subtree = element as OrgSubtree
                    OrgFoldRanges.subtree(subtree, text)?.let { descriptors += FoldingDescriptor(subtree.node, it) }
                    // A Section of only blank lines has no element of its own, so its region hangs off the Heading.
                    val section = subtree.section ?: subtree.heading
                    OrgFoldRanges.section(subtree, text)?.let { descriptors += FoldingDescriptor(section.node, it) }
                }

                OrgElementType.DRAWER, OrgElementType.PROPERTY_DRAWER, OrgElementType.BLOCK ->
                    OrgFoldRanges.belowFirstLine(element.textRange, text)?.let { descriptors += FoldingDescriptor(element.node, it) }

                else -> {}
            }
        }
        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode) = "..."

    override fun isCollapsedByDefault(node: ASTNode) = false

    companion object {
        /**
         * Rebuilds the regions of every open Org editor after a setting that shapes them changed. The platform drops
         * its cached regions for the editor and recomputes them in the next highlighting pass.
         */
        fun refreshOpenEditors() {
            for (editor in EditorFactory.getInstance().allEditors) {
                val project = editor.project ?: continue
                if (editor.virtualFile?.fileType != OrgFileType) continue
                CodeFoldingManager.getInstance(project).scheduleAsyncFoldingUpdate(editor)
            }
        }
    }
}

/** The ranges [OrgFoldingBuilder] folds, shared with visibility cycling so it can find the regions again. */
object OrgFoldRanges {
    /** Everything below the Heading line, up to the next Heading. */
    fun subtree(subtree: OrgSubtree, text: CharSequence): TextRange? =
        beforeHeading(subtree.heading.textRange.endOffset, subtree.textRange.endOffset, text)

    /**
     * Below the Heading line up to the first child Heading, only when the Subtree has children (otherwise it equals
     * [subtree]). It may hold only blank lines, which the contents state hides as Emacs does.
     */
    fun section(subtree: OrgSubtree, text: CharSequence): TextRange? {
        val firstChild = subtree.childSubtrees.firstOrNull() ?: return null
        return beforeHeading(subtree.heading.textRange.endOffset, firstChild.textRange.startOffset, text)
    }

    /** Only blank lines below the Heading line: Emacs's empty entry, which TAB shows but never folds. */
    fun isBlank(subtree: OrgSubtree, text: CharSequence): Boolean =
        subtree.heading.textRange.endOffset.let { trimmedEnd(it, subtree.textRange.endOffset, text) == it }

    fun belowFirstLine(range: TextRange, text: CharSequence): TextRange? {
        var lineEnd = range.startOffset
        while (lineEnd < range.endOffset && text[lineEnd] != '\n' && text[lineEnd] != '\r') lineEnd++
        val end = trimmedEnd(lineEnd, range.endOffset, text)
        return if (end > lineEnd) TextRange(lineEnd, end) else null
    }

    /**
     * `[start, heading)` up to the line break before the Heading at [heading], so the blank lines before it fold
     * too, except those Emacs keeps visible (`org-cycle-separator-lines`, [OrgSettings.separatorLines]). Null when
     * nothing is left to fold.
     */
    private fun beforeHeading(start: Int, heading: Int, text: CharSequence): TextRange? {
        val contentEnd = trimmedEnd(start, heading, text)
        // Blank lines that end the file always show.
        if (heading == text.length) return if (contentEnd > start) TextRange(start, contentEnd) else null
        // One line break ends the last line of content, and one more follows each blank line.
        var lastBreak = -1
        var previousBreak = -1
        var blankLines = -1
        for (i in contentEnd until heading) {
            if (text[i] != '\n') continue
            previousBreak = lastBreak
            lastBreak = i
            blankLines++
        }
        val separatorLines = OrgSettings.getInstance().separatorLines
        val end = when {
            lastBreak < 0 -> contentEnd
            separatorLines == 0 || blankLines < abs(separatorLines) -> lastBreak
            separatorLines > 0 -> previousBreak // the last blank line stays visible
            else -> contentEnd
        }
        return if (end > start) TextRange(start, end) else null
    }

    private fun trimmedEnd(start: Int, end: Int, text: CharSequence): Int {
        var trimmed = end
        while (trimmed > start && text[trimmed - 1].isWhitespace()) trimmed--
        return trimmed
    }
}
