package com.luladjiev.orgmode.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.parser.OrgElementType

open class OrgElement(node: ASTNode) : ASTWrapperPsiElement(node) {
    val type: OrgElementType get() = (node.elementType as OrgCompositeElementType).type

    protected fun child(type: OrgElementType): OrgElement? =
        children.firstOrNull { it is OrgElement && it.type == type } as OrgElement?

    protected fun token(type: OrgTokenType): PsiElement? =
        node.findChildByType(OrgTypes.token(type))?.psi
}

/** A Heading with its Section and descendant Subtrees. */
class OrgSubtree(node: ASTNode) : OrgElement(node) {
    val heading: OrgHeading get() = findNotNullChildByClass(OrgHeading::class.java)

    /** The Section below the Heading, absent when the Heading is directly followed by a child or nothing. */
    val section: OrgElement? get() = child(OrgElementType.SECTION)

    /** The direct child Subtrees. */
    val childSubtrees: List<OrgSubtree> get() = children.filterIsInstance<OrgSubtree>()
}

/** The Heading line. */
class OrgHeading(node: ASTNode) : OrgElement(node) {
    val level: Int get() = node.firstChildNode.textLength

    /** The TODO Keyword, if the first word belongs to the document's TODO Sequences. */
    val todoKeyword: OrgElement? get() = child(OrgElementType.TODO_KEYWORD) ?: child(OrgElementType.DONE_KEYWORD)

    val priority: OrgElement? get() = child(OrgElementType.PRIORITY)

    val title: String get() = child(OrgElementType.TITLE)?.text.orEmpty()

    val tags: List<String>
        get() = token(OrgTokenType.TAGS)?.text?.split(':')?.filter { it.isNotEmpty() }.orEmpty()
}
