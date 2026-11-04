package com.luladjiev.orgmode.core.parser

/** Composite node kinds built by [OrgParser]. Names follow `GLOSSARY.md`. */
enum class OrgElementType {
    /** Root of a tree built by [SyntaxTree]; IntelliJ uses its own file element instead. */
    DOCUMENT,

    /** A Heading with its Section and all descendant Subtrees. */
    SUBTREE,

    /** The Heading line, without its newline. */
    HEADING,
    TODO_KEYWORD,
    DONE_KEYWORD,
    PRIORITY,
    TITLE,

    /** Content under a Heading (or before the first one) up to the next Heading; absent when only blank lines. */
    SECTION,
    PLANNING,
    PROPERTY_DRAWER,
    NODE_PROPERTY,
    DRAWER,

    /** `#+BEGIN_NAME` … `#+END_NAME`; verbatim blocks hold raw lines, others hold elements. */
    BLOCK,
    KEYWORD,

    /** `#+NAME:`, `#+CAPTION:`, `#+ATTR_…:` and similar lines attached to the element below them. */
    AFFILIATED_KEYWORD,
    COMMENT,
    FIXED_WIDTH,
    HORIZONTAL_RULE,
    TABLE,
    TABLE_ROW,
    PLAIN_LIST,
    ITEM,
    PARAGRAPH,
    LINK,
}
