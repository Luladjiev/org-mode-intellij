package com.luladjiev.orgmode

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.luladjiev.orgmode.psi.OrgSubtree

/** Headings only, nested by level, labelled with TODO Keyword, priority and title, tags greyed. */
class OrgStructureViewTest : BasePlatformTestCase() {
    fun testHeadingsNestByLevel() {
        myFixture.configureByText(
            "notes.org",
            """
            #+TITLE: Notes
            intro
            * TODO [#A] Project :work:urgent:
            body
            *** Skipped level
            ** DONE Child
            - [ ] item
            * Plain
            """.trimIndent(),
        )
        myFixture.testStructureView { component ->
            PlatformTestUtil.expandAll(component.tree)
            PlatformTestUtil.assertTreeEqual(
                component.tree,
                """
                -notes.org
                 -TODO [#A] Project
                  Skipped level
                  DONE Child
                 Plain
                """.trimIndent() + "\n",
            )
        }
    }

    /** A caret anywhere in a Subtree, its Section included, belongs to that Subtree's Heading. */
    fun testCaretInSectionSelectsItsHeading() {
        myFixture.configureByText("notes.org", "* A\n** B\nbo<caret>dy\n* C\n")
        myFixture.testStructureView { component ->
            val current = component.treeModel.currentEditorElement as OrgSubtree
            assertEquals("B", current.heading.title)
        }
    }

    fun testTagsAreTheLocationString() {
        myFixture.configureByText("notes.org", "* Project :work:urgent:\n")
        myFixture.testStructureView { component ->
            val presentation = component.treeModel.root.children.single().presentation
            assertEquals("Project", presentation.presentableText)
            assertEquals(":work:urgent:", presentation.locationString)
        }
    }
}
