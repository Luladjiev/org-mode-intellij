package com.luladjiev.orgmode

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.luladjiev.orgmode.highlighting.OrgColorSettingsPage
import com.luladjiev.orgmode.highlighting.OrgHighlighterColors
import java.awt.Font

class OrgHighlightingTest : BasePlatformTestCase() {
    fun testHeadingsAreColoredByLevelKeywordAndPriority() {
        myFixture.configureByText("a.org", "* NEXT Task\n** TODO [#A] Sub task\n*** DONE Old :x:\n**** Foo [#A] bar\n")
        assertEquals(listOf("ORG_HEADING_2"), annotatedKeys("Sub task"))
        assertEquals(listOf("ORG_HEADING_2"), annotatedKeys("**"))
        assertEquals(listOf("ORG_TODO_KEYWORD"), annotatedKeys("TODO"))
        assertEquals(listOf("ORG_DONE_KEYWORD"), annotatedKeys("DONE"))
        assertEquals(listOf("ORG_DONE_TITLE"), annotatedKeys("Old"))
        assertEquals(listOf("ORG_HEADING_3"), annotatedKeys("***"))
        assertEquals(listOf("ORG_PRIORITY"), annotatedKeys("[#A]"))
        // Not a keyword under the default sequence, and a priority cookie that is not in a priority position.
        assertEquals(listOf("ORG_HEADING_1"), annotatedKeys("NEXT Task"))
        assertEquals(listOf("ORG_HEADING_4"), annotatedKeys("Foo [#A] bar"))
    }

    fun testEditingTodoDeclarationRehighlightsKeywords() {
        myFixture.configureByText("a.org", "#+TODO: TODO | DONE\n* NEXT Task\n")
        assertEquals(emptyList<String>(), annotatedKeys("NEXT", after = "* "))
        val document = myFixture.editor.document
        WriteCommandAction.runWriteCommandAction(project) { document.replaceString(8, 12, "NEXT") }
        assertEquals(listOf("ORG_TODO_KEYWORD"), annotatedKeys("NEXT", after = "* "))
    }

    fun testLexerHighlighting() {
        myFixture.configureByText(
            "a.org",
            "Text *bold* and [[x][link]]\n#+BEGIN_SRC sh\n*not bold*\n#+END_SRC\n- [ ] item\n",
        )
        assertEquals(listOf("ORG_BOLD"), lexerKeys("*bold*"))
        assertEquals(listOf("ORG_LINK"), lexerKeys("link"))
        assertEquals(listOf("ORG_BLOCK_DELIMITER"), lexerKeys("#+BEGIN_SRC"))
        assertEquals(listOf("ORG_BLOCK_CONTENT"), lexerKeys("*not bold*"))
        assertEquals(listOf("ORG_CHECKBOX"), lexerKeys("[ ]"))
    }

    /** Deleting a block's end line turns its body back into Org text in the editor highlighter. */
    fun testEditorHighlighterFollowsBlockEdits() {
        myFixture.configureByText("a.org", "#+BEGIN_SRC sh\n*not bold*\n#+END_SRC\n")
        val document = myFixture.editor.document
        val end = document.text.indexOf("#+END_SRC")
        WriteCommandAction.runWriteCommandAction(project) { document.deleteString(end, end + "#+END_SRC".length) }
        assertEquals(listOf("ORG_BOLD"), lexerKeys("*not bold*"))
    }

    fun testSchemeDefaults() {
        val scheme = EditorColorsManager.getInstance().getScheme(EditorColorsManager.DEFAULT_SCHEME_NAME)!!
        val todo = scheme.getAttributes(OrgHighlighterColors.TODO_KEYWORD)
        assertNotNull(todo.foregroundColor)
        assertEquals(Font.BOLD, todo.fontType)
        assertEquals(Font.BOLD, scheme.getAttributes(OrgHighlighterColors.BOLD).fontType)
        assertEquals(Font.ITALIC, scheme.getAttributes(OrgHighlighterColors.ITALIC).fontType)
        assertEquals(EffectType.LINE_UNDERSCORE, scheme.getAttributes(OrgHighlighterColors.UNDERLINE).effectType)
        assertEquals(EffectType.STRIKEOUT, scheme.getAttributes(OrgHighlighterColors.STRIKE_THROUGH).effectType)
    }

    /** Every Heading level must stand out from body text in the bundled schemes. */
    fun testHeadingLevelsAreColoredInBundledSchemes() {
        val manager = EditorColorsManager.getInstance()
        for (name in listOf(EditorColorsManager.DEFAULT_SCHEME_NAME, "Darcula")) {
            val scheme = manager.getScheme(name)!!
            val text = scheme.getAttributes(HighlighterColors.TEXT).foregroundColor ?: scheme.defaultForeground
            for ((i, key) in OrgHighlighterColors.HEADING_LEVELS.withIndex()) {
                val color = scheme.getAttributes(key).foregroundColor
                assertNotNull("$name level ${i + 1} has no color", color)
                assertFalse("$name level ${i + 1} looks like body text", color == text)
            }
        }
    }

    /** Schemes from theme plugins don't inherit `colorSchemes/`, so only fallbacks color Org there. */
    fun testEveryKeyHasAFallbackForThemeSchemes() {
        val withoutFallback = OrgColorSettingsPage().attributeDescriptors.map { it.key }
            .filter { it.fallbackAttributeKey == null }
        assertEquals(listOf(OrgHighlighterColors.UNDERLINE), withoutFallback)
    }

    fun testColorSettingsDemoUsesOnlyKnownTags() {
        val page = OrgColorSettingsPage()
        val tags = Regex("</?([a-z0-9]+)>").findAll(page.demoText).map { it.groupValues[1] }.toSet()
        assertEquals(OrgColorSettingsPage.TAGS.keys, tags)
        assertEquals(page.attributeDescriptors.size, page.attributeDescriptors.map { it.key }.toSet().size)
    }

    private fun annotatedKeys(text: String, after: String = ""): List<String> {
        val found = myFixture.editor.document.text.indexOf(after + text)
        assertTrue("'$text' not found", found >= 0)
        val start = found + after.length
        return myFixture.doHighlighting()
            .filter { it.startOffset == start && it.endOffset == start + text.length }
            .mapNotNull { it.forcedTextAttributesKey?.externalName }
    }

    private fun lexerKeys(text: String): List<String> {
        val start = myFixture.editor.document.text.indexOf(text)
        val iterator = (myFixture.editor as EditorEx).highlighter.createIterator(start)
        assertEquals(text, myFixture.editor.document.text.substring(iterator.start, iterator.end))
        return iterator.textAttributesKeys.map { it.externalName }
    }
}
