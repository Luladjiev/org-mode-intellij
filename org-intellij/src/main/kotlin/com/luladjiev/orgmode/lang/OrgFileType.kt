package com.luladjiev.orgmode.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.IconLoader
import com.luladjiev.orgmode.OrgBundle
import javax.swing.Icon

/** `.org` files. The encoding is left to the IDE, which defaults to UTF-8. */
object OrgFileType : LanguageFileType(OrgLanguage) {
    val ICON: Icon = IconLoader.getIcon("/icons/org.svg", OrgFileType::class.java)

    override fun getName() = "Org"

    override fun getDescription() = OrgBundle.message("filetype.org.description")

    override fun getDefaultExtension() = "org"

    override fun getIcon() = ICON
}
