package com.luladjiev.orgmode.core.edit

/** Replaces `[start, end)` of a text snapshot with [replacement]; an insertion has `start == end`. */
data class TextEdit(val start: Int, val end: Int, val replacement: String)

/** Applies non-overlapping [TextEdit]s, given in any order, to [text]. */
fun List<TextEdit>.applyTo(text: CharSequence): String {
    val result = StringBuilder(text)
    for (edit in sortedByDescending { it.start }) result.replace(edit.start, edit.end, edit.replacement)
    return result.toString()
}

/** Why an edit was refused; the IDE shows it as an editor hint and changes nothing. */
enum class Refusal {
    /** The offset is not inside a Subtree (it is before the first Heading). */
    NO_HEADING,

    /** A level-1 Heading cannot be promoted. */
    TOP_LEVEL,

    /** There is no same-level sibling to swap with in that direction. */
    MOVE_PAST_PARENT,

    /** The offset is not on the first line of a list item with a checkbox. */
    NO_CHECKBOX,

    /** Level cycling needs the caret at the end of an empty Heading. */
    NOT_AT_EMPTY_HEADING,
}

sealed interface EditResult {
    /**
     * The smallest edits that perform the operation. [mapOffset] tells where an offset of the old text ends up,
     * for carets and folds: through the edits by default, or along with a moved Subtree.
     */
    class Applied(val edits: List<TextEdit>, private val relocation: ((Int) -> Int?)? = null) : EditResult {
        fun mapOffset(offset: Int): Int {
            relocation?.invoke(offset)?.let { return it }
            var mapped = offset
            for (edit in edits) {
                when {
                    offset >= edit.end && (offset > edit.start || edit.start == edit.end) ->
                        mapped += edit.replacement.length - (edit.end - edit.start)

                    offset > edit.start -> mapped -= offset - edit.start
                }
            }
            return mapped
        }
    }

    data class Refused(val refusal: Refusal) : EditResult
}
