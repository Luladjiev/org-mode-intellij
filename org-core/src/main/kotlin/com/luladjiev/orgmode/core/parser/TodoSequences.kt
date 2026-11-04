package com.luladjiev.orgmode.core.parser

import com.luladjiev.orgmode.core.lexer.OrgLexer
import com.luladjiev.orgmode.core.lexer.OrgTokenType.KEYWORD_NAME
import com.luladjiev.orgmode.core.lexer.OrgTokenType.KEYWORD_VALUE
import com.luladjiev.orgmode.core.lexer.OrgTokenType.NEWLINE

/** One TODO Sequence: active keywords, then done keywords (`TODO NEXT | DONE CANCELLED`). */
data class TodoSequence(val active: List<String>, val done: List<String>)

/** The TODO Sequences of a document. */
class TodoSequences(val sequences: List<TodoSequence>) {
    enum class Kind { ACTIVE, DONE }

    private val kinds: Map<String, Kind> = buildMap {
        for (sequence in sequences) {
            sequence.active.forEach { putIfAbsent(it, Kind.ACTIVE) }
            sequence.done.forEach { putIfAbsent(it, Kind.DONE) }
        }
    }

    /** Classifies a Heading's first word; TODO Keywords are case-sensitive. */
    fun classify(word: CharSequence): Kind? = kinds[word.toString()]

    /**
     * The keyword after [keyword] when cycling, as in Emacs: no keyword, then the first sequence's keywords in order,
     * then no keyword again; a keyword cycles within its own sequence. `#+TYP_TODO` cycles like `#+SEQ_TODO`.
     */
    fun next(keyword: String?): String? {
        if (keyword == null) return sequences.firstOrNull()?.let { it.active + it.done }?.firstOrNull()
        val sequence = sequences.firstOrNull { keyword in it.active || keyword in it.done } ?: return null
        val keywords = sequence.active + sequence.done
        return keywords.getOrNull(keywords.indexOf(keyword) + 1)
    }

    companion object {
        val DEFAULT = TodoSequences(listOf(TodoSequence(listOf("TODO"), listOf("DONE"))))

        private val DECLARATIONS = listOf("#+TODO:", "#+SEQ_TODO:", "#+TYP_TODO:")

        /**
         * Reads the `#+TODO:`, `#+SEQ_TODO:` and `#+TYP_TODO:` keywords of [text]. As in Emacs, any declaration replaces
         * the default `TODO | DONE` sequence, and a sequence without `|` treats its last keyword as done. Lines inside
         * verbatim blocks are block content, not keywords, so an example of a declaration declares nothing.
         */
        fun parse(text: CharSequence): TodoSequences {
            val sequences = mutableListOf<TodoSequence>()
            val lexer = OrgLexer()
            lexer.start(text)
            var declaration = false
            while (true) {
                when (lexer.tokenType ?: break) {
                    KEYWORD_NAME -> declaration = DECLARATIONS.any {
                        lexer.tokenEnd - lexer.tokenStart == it.length &&
                            text.regionMatches(lexer.tokenStart, it, 0, it.length, ignoreCase = true)
                    }

                    KEYWORD_VALUE -> if (declaration) {
                        parseSequence(text.substring(lexer.tokenStart, lexer.tokenEnd))?.let(sequences::add)
                    }

                    NEWLINE -> declaration = false
                    else -> {}
                }
                lexer.advance()
            }
            return if (sequences.isEmpty()) DEFAULT else TodoSequences(sequences)
        }

        private fun parseSequence(value: String): TodoSequence? {
            // `TODO(t)` and `WAIT(w@/!)` carry fast-access keys and logging settings.
            val words = value.split(' ', '\t').map { it.substringBefore('(') }.filter { it.isNotEmpty() }
            val bar = words.indexOf("|")
            val keywords = words.filter { it != "|" }
            if (keywords.isEmpty()) return null
            return if (bar < 0) {
                TodoSequence(keywords.dropLast(1), keywords.takeLast(1))
            } else {
                TodoSequence(words.take(bar), words.drop(bar + 1).filter { it != "|" })
            }
        }
    }
}
