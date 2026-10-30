package com.luladjiev.orgmode.psi

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.luladjiev.orgmode.core.lexer.OrgTokenType
import com.luladjiev.orgmode.core.parser.OrgElementType
import com.luladjiev.orgmode.lang.OrgLanguage

/** IntelliJ token type for an `org-core` token. */
class OrgTokenElementType(val type: OrgTokenType) : IElementType(type.name, OrgLanguage)

/** IntelliJ element type for an `org-core` element. */
class OrgCompositeElementType(val type: OrgElementType) : IElementType(type.name, OrgLanguage)

object OrgTypes {
    val FILE = IFileElementType(OrgLanguage)

    private val tokens = OrgTokenType.entries.map(::OrgTokenElementType)
    private val elements = OrgElementType.entries.map(::OrgCompositeElementType)

    fun token(type: OrgTokenType): IElementType = tokens[type.ordinal]

    fun element(type: OrgElementType): IElementType = elements[type.ordinal]
}
