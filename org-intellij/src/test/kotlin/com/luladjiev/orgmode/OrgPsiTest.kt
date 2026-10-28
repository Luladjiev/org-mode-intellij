package com.luladjiev.orgmode

import com.intellij.lang.ASTNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.luladjiev.orgmode.core.parser.SyntaxElement
import com.luladjiev.orgmode.core.parser.SyntaxNode
import com.luladjiev.orgmode.core.parser.SyntaxToken
import com.luladjiev.orgmode.core.parser.SyntaxTree
import com.luladjiev.orgmode.lang.OrgFileType
import com.luladjiev.orgmode.psi.OrgFile
import com.luladjiev.orgmode.psi.OrgHeading
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText

class OrgPsiTest : BasePlatformTestCase() {
    fun testOrgFilesGetOrgSupport() {
        val file = myFixture.configureByText("notes.org", "* Heading\n")
        assertInstanceOf(file, OrgFile::class.java)
        assertEquals(OrgFileType, file.virtualFile.fileType)
    }

    /** PSI built through `PsiBuilder` has exactly the shape of the `org-core` tree (docs/decisions/0001). */
    fun testPsiMatchesCoreTreeOnFixtures() {
        for (path in parserFixtures()) {
            val text = path.readText()
            val file = myFixture.configureByText(path.name, text)
            assertEquals(path.name, text, file.text)
            assertEquals(path.name, dump(SyntaxTree.parse(text)), dump(file.node))
        }
    }

    fun testHeadingAccessors() {
        val file = myFixture.configureByText("a.org", "#+TODO: NEXT | DONE\n** NEXT [#B] Write *tests* :a:b:\n")
        val heading = PsiTreeUtil.findChildOfType(file, OrgHeading::class.java)!!
        assertEquals(2, heading.level)
        assertEquals("NEXT", heading.todoKeyword?.text)
        assertEquals("[#B]", heading.priority?.text)
        assertEquals("Write *tests*", heading.title)
        assertEquals(listOf("a", "b"), heading.tags)
    }

    /** Typing must leave the same PSI as a fresh parse, including edits to TODO Sequence declarations. */
    fun testIncrementalReparseMatchesFreshParse() {
        val text = parserFixtures().first { it.name == "mixed.org" }.readText()
        myFixture.configureByText("mixed.org", text)
        val edits = listOf("* ", "#+TODO: TODO | DONE NEXT\n", "\n#+END_SRC", ":END:\n", "- ", "#+BEGIN_SRC\n", "*", "\n\n")
        val document = myFixture.editor.document
        for ((i, edit) in edits.withIndex()) {
            val offset = (document.textLength * (i + 1) / (edits.size + 1))
            WriteCommandAction.runWriteCommandAction(project) { document.insertString(offset, edit) }
            PsiDocumentManager.getInstance(project).commitDocument(document)
            PsiTestUtil.checkFileStructure(myFixture.file)
        }
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "#+TODO: NEXT | DONE\n") }
        PsiDocumentManager.getInstance(project).commitDocument(document)
        PsiTestUtil.checkFileStructure(myFixture.file)
    }

    private fun parserFixtures(): List<Path> {
        val dir = Path.of(System.getProperty("org.mode.fixtures"), "parser")
        return Files.list(dir).use { files -> files.filter { it.name.endsWith(".org") }.sorted().toList() }
    }

    private fun dump(root: ASTNode) = buildString {
        fun visit(node: ASTNode, depth: Int) {
            append("  ".repeat(depth)).append(node.elementType).append(node.textRange).append('\n')
            node.getChildren(null).forEach { visit(it, depth + 1) }
        }
        root.getChildren(null).forEach { visit(it, 0) }
    }

    private fun dump(root: SyntaxElement) = buildString {
        fun visit(node: SyntaxNode, depth: Int) {
            val type = when (node) {
                is SyntaxElement -> node.type.name
                is SyntaxToken -> node.type.name
            }
            append("  ".repeat(depth)).append(type).append("(${node.startOffset},${node.endOffset})").append('\n')
            if (node is SyntaxElement) node.children.forEach { visit(it, depth + 1) }
        }
        root.children.forEach { visit(it, 0) }
    }
}
