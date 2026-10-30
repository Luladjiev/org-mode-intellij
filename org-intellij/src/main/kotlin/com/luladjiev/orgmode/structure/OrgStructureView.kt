package com.luladjiev.orgmode.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiFile
import com.luladjiev.orgmode.psi.OrgFile
import com.luladjiev.orgmode.psi.OrgSubtree
import javax.swing.Icon

/** Structure View of an Org file: its Headings only, nested by level. */
class OrgStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile) = object : TreeBasedStructureViewBuilder() {
        override fun createStructureViewModel(editor: Editor?): StructureViewModel =
            OrgStructureViewModel(psiFile as OrgFile, editor)

        override fun isRootNodeShown() = true
    }
}

private class OrgStructureViewModel(file: OrgFile, editor: Editor?) :
    StructureViewModelBase(file, editor, OrgFileElement(file)),
    StructureViewModel.ElementInfoProvider {
    init {
        // A caret anywhere in a Subtree, its Section included, selects its Heading.
        withSuitableClasses(OrgSubtree::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement) = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement) = false
}

/**
 * A node over a PSI element, built on the public [StructureViewTreeElement] rather than `PsiTreeElementBase`, which
 * the Plugin Verifier cannot resolve in 2026.2. Equal elements make equal nodes, so the tree keeps its state on
 * rebuilds.
 */
private abstract class OrgTreeElement<T : NavigatablePsiElement>(private val psi: T) :
    StructureViewTreeElement, ItemPresentation {
    override fun getValue(): T = psi

    override fun getPresentation(): ItemPresentation = this

    override fun getIcon(unused: Boolean): Icon? = null

    override fun navigate(requestFocus: Boolean) = psi.navigate(requestFocus)

    override fun canNavigate() = psi.isValid && psi.canNavigate()

    override fun canNavigateToSource() = psi.isValid && psi.canNavigateToSource()

    override fun getChildren(): Array<TreeElement> =
        if (psi.isValid) children(psi).toTypedArray() else emptyArray()

    protected abstract fun children(psi: T): List<TreeElement>

    override fun equals(other: Any?) = other is OrgTreeElement<*> && other.psi == psi

    override fun hashCode() = psi.hashCode()
}

private class OrgFileElement(file: OrgFile) : OrgTreeElement<OrgFile>(file) {
    override fun getPresentableText(): String = value.name

    override fun children(psi: OrgFile): List<TreeElement> =
        psi.children.filterIsInstance<OrgSubtree>().map(::OrgSubtreeElement)
}

/**
 * A Heading: TODO Keyword, priority and title as the text, tags as the greyed location string. Its children are the
 * Subtrees one level down in the PSI, so a skipped level nests under the nearest shallower Heading.
 */
private class OrgSubtreeElement(subtree: OrgSubtree) : OrgTreeElement<OrgSubtree>(subtree) {
    override fun getPresentableText(): String? {
        if (!value.isValid) return null
        val heading = value.heading
        return listOfNotNull(heading.todoKeyword?.text, heading.priority?.text, heading.title.trim().ifEmpty { null })
            .joinToString(" ")
    }

    override fun getLocationString(): String? {
        if (!value.isValid) return null
        val tags = value.heading.tags.ifEmpty { return null }
        return tags.joinToString(":", prefix = ":", postfix = ":")
    }

    override fun children(psi: OrgSubtree): List<TreeElement> = psi.childSubtrees.map(::OrgSubtreeElement)
}
