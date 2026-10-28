package com.luladjiev.orgmode.highlighting

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.parser.OrgElementType
import com.luladjiev.orgmode.psi.OrgCompositeElementType
import com.luladjiev.orgmode.psi.OrgHeading
import com.luladjiev.orgmode.psi.OrgTokenElementType

/**
 * Colors the parts of a Heading that only the parser can classify: level, TODO Keyword and priority. The title of
 * a done Heading is dimmed instead of taking the level color.
 */
class OrgAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is OrgHeading) return
        val level = OrgHighlighterColors.headingLevel(element.level)
        val done = element.todoKeyword?.type == OrgElementType.DONE_KEYWORD
        var child = element.node.firstChildNode
        while (child != null) {
            val key = when (val type = child.elementType) {
                is OrgTokenElementType -> if (type.type == OrgTokenType.STARS) level else null
                is OrgCompositeElementType -> when (type.type) {
                    OrgElementType.TITLE -> if (done) OrgHighlighterColors.DONE_TITLE else level
                    OrgElementType.TODO_KEYWORD -> OrgHighlighterColors.TODO_KEYWORD
                    OrgElementType.DONE_KEYWORD -> OrgHighlighterColors.DONE_KEYWORD
                    OrgElementType.PRIORITY -> OrgHighlighterColors.PRIORITY
                    else -> null
                }

                else -> null
            }
            if (key != null) highlight(holder, child.textRange, key)
            child = child.treeNext
        }
    }

    private fun highlight(holder: AnnotationHolder, range: TextRange, key: TextAttributesKey) {
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range).textAttributes(key).create()
    }
}
