package com.luladjiev.orgmode.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.lexer.OrgTokenType.*
import com.luladjiev.orgmode.lang.OrgLexerAdapter
import com.luladjiev.orgmode.psi.OrgTokenElementType

/**
 * Lexer-based highlighting. Heading levels, TODO Keywords and priorities depend on the parser (TODO Sequences,
 * keyword position), so [OrgAnnotator] colors those.
 */
class OrgSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer() = OrgLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack((tokenType as? OrgTokenElementType)?.let { KEYS[it.type] })

    private companion object {
        val KEYS: Map<OrgTokenType, TextAttributesKey> = mapOf(
            TAGS to OrgHighlighterColors.TAGS,
            KEYWORD_NAME to OrgHighlighterColors.DIRECTIVE,
            KEYWORD_VALUE to OrgHighlighterColors.DIRECTIVE_VALUE,
            BLOCK_BEGIN to OrgHighlighterColors.BLOCK_DELIMITER,
            BLOCK_PARAMETERS to OrgHighlighterColors.BLOCK_DELIMITER,
            BLOCK_END to OrgHighlighterColors.BLOCK_DELIMITER,
            BLOCK_CONTENT to OrgHighlighterColors.BLOCK_CONTENT,
            DRAWER_NAME to OrgHighlighterColors.DRAWER,
            DRAWER_END to OrgHighlighterColors.DRAWER,
            PROPERTY_NAME to OrgHighlighterColors.PROPERTY_NAME,
            PROPERTY_VALUE to OrgHighlighterColors.PROPERTY_VALUE,
            PLANNING_KEYWORD to OrgHighlighterColors.PLANNING_KEYWORD,
            TIMESTAMP to OrgHighlighterColors.TIMESTAMP,
            COMMENT to OrgHighlighterColors.COMMENT,
            FIXED_WIDTH to OrgHighlighterColors.FIXED_WIDTH,
            HORIZONTAL_RULE to OrgHighlighterColors.HORIZONTAL_RULE,
            BULLET to OrgHighlighterColors.BULLET,
            CHECKBOX to OrgHighlighterColors.CHECKBOX,
            TABLE_BAR to OrgHighlighterColors.TABLE,
            TABLE_RULE to OrgHighlighterColors.TABLE,
            LINK_BRACKET to OrgHighlighterColors.LINK,
            LINK_PATH to OrgHighlighterColors.LINK,
            LINK_DESCRIPTION to OrgHighlighterColors.LINK,
            PLAIN_LINK to OrgHighlighterColors.LINK,
            BOLD to OrgHighlighterColors.BOLD,
            ITALIC to OrgHighlighterColors.ITALIC,
            UNDERLINE to OrgHighlighterColors.UNDERLINE,
            STRIKE_THROUGH to OrgHighlighterColors.STRIKE_THROUGH,
            VERBATIM to OrgHighlighterColors.VERBATIM,
            CODE to OrgHighlighterColors.CODE,
        )
    }
}

class OrgSyntaxHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {
    override fun createHighlighter() = OrgSyntaxHighlighter()
}
