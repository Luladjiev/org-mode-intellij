package com.luladjiev.orgmode.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import com.luladjiev.orgmode.core.lexer.OrgLexer
import com.luladjiev.orgmode.psi.OrgTypes

/** Exposes the `org-core` lexer as an IntelliJ lexer. */
class OrgLexerAdapter : LexerBase() {
    private val lexer = OrgLexer()

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) =
        lexer.start(buffer, startOffset, endOffset, initialState)

    override fun getState() = lexer.state

    override fun getTokenType(): IElementType? = lexer.tokenType?.let(OrgTypes::token)

    override fun getTokenStart() = lexer.tokenStart

    override fun getTokenEnd() = lexer.tokenEnd

    override fun advance() = lexer.advance()

    override fun getBufferSequence() = lexer.buffer

    override fun getBufferEnd() = lexer.bufferEnd
}
