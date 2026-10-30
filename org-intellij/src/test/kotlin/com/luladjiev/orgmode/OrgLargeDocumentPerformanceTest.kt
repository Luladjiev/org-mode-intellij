package com.luladjiev.orgmode

import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.psi.SyntaxTraverser
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.luladjiev.orgmode.core.LargeDocument
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

/**
 * Opening the generated 1 MB document, lexer highlighting, the PSI, the highlighting passes and folding
 * each finish within [LIMIT], which leaves room for slow CI machines.
 *
 * "Performance" in the class name makes this a stress test to the platform, which turns off the test logger's debug
 * output: otherwise the daemon logs every one of the ~37k highlights, and that alone triples the highlighting time.
 */
class OrgLargeDocumentPerformanceTest : BasePlatformTestCase() {
    fun testHighlightingAndFoldingFinishInTime() {
        val text = LargeDocument.text
        myFixture.configureByText("large.org", text)

        val lexer = measureTime {
            EditorHighlighterFactory.getInstance().createEditorHighlighter(project, myFixture.file.virtualFile)
                .setText(text)
        }
        val psi = measureTime { SyntaxTraverser.psiTraverser(myFixture.file).count() }
        val highlighting = measureTime { assertNotEmpty(myFixture.doHighlighting()) }
        val folding = measureTime { myFixture.buildFolding() }
        println("1 MB document: lexer $lexer, PSI $psi, highlighting passes $highlighting, folding $folding")

        assertNotEmpty(myFixture.editor.foldingModel.allFoldRegions.toList())
        assertWithin("Lexer highlighting", lexer)
        assertWithin("Building the PSI", psi)
        assertWithin("Highlighting passes", highlighting)
        assertWithin("Folding", folding)
    }

    private fun assertWithin(what: String, time: Duration) =
        assertTrue("$what took $time, more than $LIMIT", time < LIMIT)

    private companion object {
        val LIMIT = 10.seconds
    }
}
