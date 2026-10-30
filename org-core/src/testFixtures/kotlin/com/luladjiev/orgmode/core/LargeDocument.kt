package com.luladjiev.orgmode.core

import kotlin.random.Random

/**
 * The generated benchmark document, shared by the `org-core` tests, the JMH benchmarks and the IDE performance test:
 * ~1 MB of Headings (depth up to 8, ~20k of them) with mixed body text, the same text on every run.
 */
object LargeDocument {
    const val SIZE = 1_000_000

    val text: String by lazy { generate(SIZE) }

    /** ~[size] characters, deterministic. */
    fun generate(size: Int): String {
        val random = Random(7)
        val bodies = listOf(
            "Plain text with *bold*, /italic/ and a [[https://orgmode.org][link]].\nA second line.\n",
            "- [ ] First item\n- [X] Second item\n  continued\n",
            "SCHEDULED: <2026-10-28 Wed>\n:PROPERTIES:\n:ID: 1234\n:END:\n",
            "#+BEGIN_SRC kotlin\nval x = 1\n#+END_SRC\n",
            "| a | b |\n|---+---|\n| 1 | 2 |\n",
            "",
        )
        return buildString {
            var level = 1
            var n = 0
            while (length < size) {
                level = (level + random.nextInt(-2, 2)).coerceIn(1, 8)
                append("*".repeat(level)).append(if (n % 3 == 0) " TODO " else " ").append("Heading ").append(n++)
                append(if (n % 5 == 0) " :tag:\n" else "\n")
                append(bodies[random.nextInt(bodies.size)])
            }
        }
    }

    /** The start of the last level-1 Heading, which has a previous sibling to move above. */
    fun lastTopLevelHeading(text: String): Int = text.lastIndexOf("\n* ") + 1
}
