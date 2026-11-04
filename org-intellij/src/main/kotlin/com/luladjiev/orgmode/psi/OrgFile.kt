package com.luladjiev.orgmode.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider
import com.luladjiev.orgmode.lang.OrgFileType
import com.luladjiev.orgmode.lang.OrgLanguage

class OrgFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, OrgLanguage) {
    override fun getFileType() = OrgFileType

    override fun toString() = "Org file"
}
