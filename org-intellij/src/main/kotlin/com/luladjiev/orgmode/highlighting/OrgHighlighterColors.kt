package com.luladjiev.orgmode.highlighting

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

/**
 * Org text attributes. Every key except [UNDERLINE] falls back to a key that color schemes define, so
 * Org is colored in any scheme. `colorSchemes/` sets TODO Keywords and emphasis for the bundled Default and Darcula
 * schemes, which JetBrains' other schemes inherit; schemes from theme plugins (Catppuccin) don't inherit them and use
 * the fallbacks.
 */
object OrgHighlighterColors {
    /**
     * Heading levels 1–8; deeper levels cycle, as in Emacs. Each falls back to a language color that the bundled
     * Light, Dark, Classic Light and Darcula schemes and common theme plugins all set, so no level renders as plain
     * text (Emacs' choices, such as variable or type names, are uncolored in some of them; instance fields are plain
     * in Catppuccin).
     */
    val HEADING_LEVELS: List<TextAttributesKey> = listOf(
        DefaultLanguageHighlighterColors.FUNCTION_DECLARATION,
        DefaultLanguageHighlighterColors.STATIC_FIELD,
        DefaultLanguageHighlighterColors.KEYWORD,
        DefaultLanguageHighlighterColors.STRING,
        DefaultLanguageHighlighterColors.METADATA,
        DefaultLanguageHighlighterColors.NUMBER,
        DefaultLanguageHighlighterColors.CONSTANT,
        DefaultLanguageHighlighterColors.LINE_COMMENT,
    ).mapIndexed { i, fallback -> createTextAttributesKey("ORG_HEADING_${i + 1}", fallback) }

    val TODO_KEYWORD = createTextAttributesKey("ORG_TODO_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

    /** Strings are green in most schemes, like Emacs' `org-done`. */
    val DONE_KEYWORD = createTextAttributesKey("ORG_DONE_KEYWORD", DefaultLanguageHighlighterColors.STRING)

    /** Title of a Heading with a done keyword, dimmed like Emacs' `org-headline-done`. */
    val DONE_TITLE = createTextAttributesKey("ORG_DONE_TITLE", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val PRIORITY = createTextAttributesKey("ORG_PRIORITY", DefaultLanguageHighlighterColors.NUMBER)
    val TAGS = createTextAttributesKey("ORG_TAGS", DefaultLanguageHighlighterColors.METADATA)
    val PLANNING_KEYWORD = createTextAttributesKey("ORG_PLANNING_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
    val TIMESTAMP = createTextAttributesKey("ORG_TIMESTAMP", DefaultLanguageHighlighterColors.CONSTANT)
    val DRAWER = createTextAttributesKey("ORG_DRAWER", DefaultLanguageHighlighterColors.DOC_COMMENT_TAG)
    val PROPERTY_NAME = createTextAttributesKey("ORG_PROPERTY_NAME", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
    val PROPERTY_VALUE = createTextAttributesKey("ORG_PROPERTY_VALUE", DefaultLanguageHighlighterColors.STRING)
    val DIRECTIVE = createTextAttributesKey("ORG_DIRECTIVE", DefaultLanguageHighlighterColors.METADATA)
    val DIRECTIVE_VALUE = createTextAttributesKey("ORG_DIRECTIVE_VALUE", DefaultLanguageHighlighterColors.STRING)
    val BLOCK_DELIMITER = createTextAttributesKey("ORG_BLOCK_DELIMITER", DefaultLanguageHighlighterColors.METADATA)
    val BLOCK_CONTENT = createTextAttributesKey("ORG_BLOCK_CONTENT", HighlighterColors.TEXT)
    val COMMENT = createTextAttributesKey("ORG_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val FIXED_WIDTH = createTextAttributesKey("ORG_FIXED_WIDTH", DefaultLanguageHighlighterColors.STRING)
    val HORIZONTAL_RULE = createTextAttributesKey("ORG_HORIZONTAL_RULE", DefaultLanguageHighlighterColors.LINE_COMMENT)
    val BULLET = createTextAttributesKey("ORG_BULLET", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val CHECKBOX = createTextAttributesKey("ORG_CHECKBOX", DefaultLanguageHighlighterColors.KEYWORD)
    val TABLE = createTextAttributesKey("ORG_TABLE", DefaultLanguageHighlighterColors.OPERATION_SIGN)
    val LINK = createTextAttributesKey("ORG_LINK", CodeInsightColors.HYPERLINK_ATTRIBUTES)

    // Emphasis falls back to Markdown's, which themes style; referenced by name, so Markdown stays optional.
    val BOLD = createTextAttributesKey("ORG_BOLD", TextAttributesKey.find("MARKDOWN_BOLD"))
    val ITALIC = createTextAttributesKey("ORG_ITALIC", TextAttributesKey.find("MARKDOWN_ITALIC"))

    /** No fallback: no common key is only underlined. */
    val UNDERLINE = createTextAttributesKey("ORG_UNDERLINE")
    val STRIKE_THROUGH = createTextAttributesKey("ORG_STRIKE_THROUGH", TextAttributesKey.find("MARKDOWN_STRIKE_THROUGH"))
    val VERBATIM = createTextAttributesKey("ORG_VERBATIM", DefaultLanguageHighlighterColors.STRING)
    val CODE = createTextAttributesKey("ORG_CODE", DefaultLanguageHighlighterColors.STRING)

    fun headingLevel(level: Int): TextAttributesKey = HEADING_LEVELS[(level - 1) % HEADING_LEVELS.size]
}
