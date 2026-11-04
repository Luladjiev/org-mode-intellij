package com.luladjiev.orgmode

import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

fun CodeInsightTestFixture.buildFolding() = EditorTestUtil.buildInitialFoldingsInBackground(editor)

/** The folded text of every region, in document order. */
fun CodeInsightTestFixture.regionTexts(): List<String> =
    editor.foldingModel.allFoldRegions.map { editor.document.getText(it.textRange) }

fun CodeInsightTestFixture.collapsedTexts(): List<String> =
    editor.foldingModel.allFoldRegions.filter { !it.isExpanded }.map { editor.document.getText(it.textRange) }

/** The document as the editor shows it: each outermost collapsed region replaced by `...`. */
fun CodeInsightTestFixture.foldedText(): String {
    val text = editor.document.text
    val shown = StringBuilder()
    var at = 0
    val collapsed = editor.foldingModel.allFoldRegions.filter { !it.isExpanded }
    for (region in collapsed.sortedWith(compareBy({ it.startOffset }, { -it.endOffset }))) {
        if (region.startOffset < at) continue
        shown.append(text, at, region.startOffset).append("...")
        at = region.endOffset
    }
    return shown.append(text, at, text.length).toString()
}

/** Collapses the one region whose folded text is [text]. */
fun CodeInsightTestFixture.collapse(text: String) {
    val region = editor.foldingModel.allFoldRegions.single { editor.document.getText(it.textRange) == text }
    editor.foldingModel.runBatchFoldingOperation { region.isExpanded = false }
}
