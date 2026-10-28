package com.luladjiev.orgmode.core.lexer

/**
 * Token kinds produced by [OrgLexer].
 *
 * TODO Keywords are not tokens: a Heading's first word is a plain [TEXT] that the parser classifies against the
 * document's TODO Sequences (docs/decisions/0001).
 */
enum class OrgTokenType {
    NEWLINE,
    WHITESPACE,
    TEXT,

    /** The asterisks that start a Heading. */
    STARS,

    /** `[#A]` near the start of a Heading; the parser decides whether it is in a priority position. */
    PRIORITY_COOKIE,

    /** `:tag1:tag2:` at the end of a Heading. */
    TAGS,

    /** `#+KEY:` including the `#+` and the colon. */
    KEYWORD_NAME,
    KEYWORD_VALUE,

    /** `#+BEGIN_NAME`, without the parameters. */
    BLOCK_BEGIN,
    BLOCK_PARAMETERS,

    /** One body line of a verbatim block (`SRC`, `EXAMPLE`, `EXPORT`, `COMMENT`, `VERSE`), without its newline. */
    BLOCK_CONTENT,
    BLOCK_END,

    /** A line holding only `:NAME:`; inside a property drawer this is a property with an empty value. */
    DRAWER_NAME,
    DRAWER_END,

    /** `:NAME:` followed by a value. */
    PROPERTY_NAME,
    PROPERTY_VALUE,

    /** `SCHEDULED:`, `DEADLINE:` or `CLOSED:` on a line that starts with one of them. */
    PLANNING_KEYWORD,

    /** A whole `# comment` line after its indentation. */
    COMMENT,

    /** A whole `: fixed width` line after its indentation. */
    FIXED_WIDTH,
    HORIZONTAL_RULE,
    BULLET,
    CHECKBOX,
    TABLE_BAR,

    /** A whole `|---+---|` line after its indentation. */
    TABLE_RULE,

    // Inline markup, each a single token including its markers.
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKE_THROUGH,
    VERBATIM,
    CODE,

    /** `[[`, `][` or `]]` of a bracket link. */
    LINK_BRACKET,
    LINK_PATH,
    LINK_DESCRIPTION,
    PLAIN_LINK,
    TIMESTAMP,
}
