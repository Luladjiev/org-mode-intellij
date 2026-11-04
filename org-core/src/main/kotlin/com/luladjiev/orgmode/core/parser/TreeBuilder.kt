package com.luladjiev.orgmode.core.parser

import com.luladjiev.orgmode.core.lexer.OrgTokenType

/**
 * The only way [OrgParser] builds a tree (docs/decisions/0001): IntelliJ adapts its `PsiBuilder`, [SyntaxTree]
 * builds plain nodes. Every token is visible to the parser; nothing is skipped as whitespace.
 */
interface TreeBuilder {
    /** The whole document text. */
    val text: CharSequence

    /** Type of the token [steps] tokens after the current one, or null past the end. */
    fun lookAhead(steps: Int): OrgTokenType?

    /** Start offset of the token [steps] tokens after the current one; the text length past the end. */
    fun lookAheadStart(steps: Int): Int

    fun advance()

    /** Opens an element at the current token. */
    fun mark(): Marker

    interface Marker {
        /** Closes the element before the current token. */
        fun done(type: OrgElementType)

        /** Discards the element, leaving its contents to the parent. */
        fun drop()
    }
}
