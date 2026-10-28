package com.luladjiev.orgmode.highlighting

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.luladjiev.orgmode.OrgBundle
import com.luladjiev.orgmode.lang.OrgFileType

class OrgColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "Org"

    override fun getIcon() = OrgFileType.ICON

    override fun getHighlighter() = OrgSyntaxHighlighter()

    override fun getDemoText() = DEMO

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = TAGS

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    companion object {
        internal val TAGS: Map<String, TextAttributesKey> =
            OrgHighlighterColors.HEADING_LEVELS.withIndex().associate { (i, key) -> "h${i + 1}" to key } + mapOf(
                "todo" to OrgHighlighterColors.TODO_KEYWORD,
                "done" to OrgHighlighterColors.DONE_KEYWORD,
                "donetitle" to OrgHighlighterColors.DONE_TITLE,
                "priority" to OrgHighlighterColors.PRIORITY,
            )

        private val DESCRIPTORS: Array<AttributesDescriptor> by lazy {
            (OrgHighlighterColors.HEADING_LEVELS.withIndex().map { (i, key) ->
                AttributesDescriptor(OrgBundle.message("color.settings.heading.level", i + 1), key)
            } + listOf(
                "todo.keyword" to OrgHighlighterColors.TODO_KEYWORD,
                "done.keyword" to OrgHighlighterColors.DONE_KEYWORD,
                "done.title" to OrgHighlighterColors.DONE_TITLE,
                "priority" to OrgHighlighterColors.PRIORITY,
                "tags" to OrgHighlighterColors.TAGS,
                "planning" to OrgHighlighterColors.PLANNING_KEYWORD,
                "timestamp" to OrgHighlighterColors.TIMESTAMP,
                "drawer" to OrgHighlighterColors.DRAWER,
                "property.name" to OrgHighlighterColors.PROPERTY_NAME,
                "property.value" to OrgHighlighterColors.PROPERTY_VALUE,
                "directive" to OrgHighlighterColors.DIRECTIVE,
                "directive.value" to OrgHighlighterColors.DIRECTIVE_VALUE,
                "block.delimiter" to OrgHighlighterColors.BLOCK_DELIMITER,
                "block.content" to OrgHighlighterColors.BLOCK_CONTENT,
                "comment" to OrgHighlighterColors.COMMENT,
                "fixed.width" to OrgHighlighterColors.FIXED_WIDTH,
                "horizontal.rule" to OrgHighlighterColors.HORIZONTAL_RULE,
                "bullet" to OrgHighlighterColors.BULLET,
                "checkbox" to OrgHighlighterColors.CHECKBOX,
                "table" to OrgHighlighterColors.TABLE,
                "link" to OrgHighlighterColors.LINK,
                "bold" to OrgHighlighterColors.BOLD,
                "italic" to OrgHighlighterColors.ITALIC,
                "underline" to OrgHighlighterColors.UNDERLINE,
                "strike.through" to OrgHighlighterColors.STRIKE_THROUGH,
                "verbatim" to OrgHighlighterColors.VERBATIM,
                "code" to OrgHighlighterColors.CODE,
            ).map { (name, key) -> AttributesDescriptor(OrgBundle.message("color.settings.$name"), key) })
                .toTypedArray()
        }

        private val DEMO = """
            #+TITLE: Org Mode
            #+TODO: TODO NEXT | DONE

            <h1>*</h1> <todo>NEXT</todo> <priority>[#A]</priority> <h1>Write the parser</h1> :work:
            SCHEDULED: <2026-10-28 Wed>
            :PROPERTIES:
            :EFFORT:   2:00
            :END:
            Text with *bold*, /italic/, _underline_, +strike-through+, =verbatim= and ~code~,
            a [[https://orgmode.org][link]] and an inactive timestamp [2026-10-27 Tue].
            <h2>**</h2> <done>DONE</done> <donetitle>Lists</donetitle>
            - [X] Checked
            - [ ] Unchecked
            # A comment
            : Fixed-width text
            -----
            <h3>***</h3> <h3>Level 3</h3>
            <h4>****</h4> <h4>Level 4</h4>
            <h5>*****</h5> <h5>Level 5</h5>
            <h6>******</h6> <h6>Level 6</h6>
            <h7>*******</h7> <h7>Level 7</h7>
            <h8>********</h8> <h8>Level 8</h8>
            | Name | Value |
            |------+-------|
            | a    |     1 |

            #+BEGIN_SRC kotlin
            fun main() = println("plain body")
            #+END_SRC
        """.trimIndent()
    }
}
