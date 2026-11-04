package com.luladjiev.orgmode.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.parser.OrgElementType
import com.luladjiev.orgmode.core.parser.OrgParser
import com.luladjiev.orgmode.core.parser.TreeBuilder
import com.luladjiev.orgmode.psi.OrgCompositeElementType
import com.luladjiev.orgmode.psi.OrgElement
import com.luladjiev.orgmode.psi.OrgFile
import com.luladjiev.orgmode.psi.OrgHeading
import com.luladjiev.orgmode.psi.OrgSubtree
import com.luladjiev.orgmode.psi.OrgTokenElementType
import com.luladjiev.orgmode.psi.OrgTypes

/**
 * Builds PSI with the `org-core` grammar (docs/decisions/0001). No token is whitespace or comment to `PsiBuilder`:
 * the grammar is line-based and needs to see every token, including indentation and newlines.
 */
class OrgParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = OrgLexerAdapter()

    override fun createParser(project: Project?) = PsiParser { root, builder ->
        val file = builder.mark()
        OrgParser.parse(PsiTreeBuilder(builder))
        file.done(root)
        builder.treeBuilt
    }

    override fun getFileNodeType() = OrgTypes.FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.EMPTY

    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun createElement(node: ASTNode): PsiElement = when ((node.elementType as OrgCompositeElementType).type) {
        OrgElementType.SUBTREE -> OrgSubtree(node)
        OrgElementType.HEADING -> OrgHeading(node)
        else -> OrgElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider) = OrgFile(viewProvider)
}

private class PsiTreeBuilder(private val builder: PsiBuilder) : TreeBuilder {
    override val text: CharSequence get() = builder.originalText

    override fun lookAhead(steps: Int): OrgTokenType? = (builder.rawLookup(steps) as OrgTokenElementType?)?.type

    override fun lookAheadStart(steps: Int) = builder.rawTokenTypeStart(steps)

    override fun advance() = builder.advanceLexer()

    override fun mark(): TreeBuilder.Marker = Marker(builder.mark())

    private class Marker(private val marker: PsiBuilder.Marker) : TreeBuilder.Marker {
        override fun done(type: OrgElementType) = marker.done(OrgTypes.element(type))

        override fun drop() = marker.drop()
    }
}
