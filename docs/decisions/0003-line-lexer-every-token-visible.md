# A line lexer whose every token the parser sees

The `org-core` lexer works a line at a time and hands every token to the parser: `ParserDefinition` declares no whitespace or comment tokens, so `PsiBuilder` skips nothing and PSI has no `PsiWhiteSpace` or `PsiComment` nodes. Org is line-structured; the grammar needs indentation (list nesting), newlines (element boundaries) and comment lines (they are elements) as ordinary tokens, and hiding them from `PsiBuilder` would mean re-deriving them from offsets.

A verbatim block (`#+BEGIN_SRC`, `EXAMPLE`, `EXPORT`, `COMMENT`, `VERSE`) is lexed as one chunk, its body as raw lines, but only when the lexer finds the matching end line before the next Heading; otherwise the begin line is plain text, as in Org. Only the first token of a line or chunk reports the initial lexer state, so the editor highlighter always restarts at a line start outside a verbatim block.

Highlighting is split: the lexer colors everything it can see locally, and an annotator colors what needs the parser (Heading level, TODO Keywords, priority position).

## Considered Options

- **Whitespace and newlines as `PsiBuilder` whitespace**: rejected; the parser would lose line structure and indentation.
- **Lexer without lookahead, parser decides whether a block is verbatim**: rejected; the lexer would highlight emphasis and lists inside source blocks.
- **Lexer state per block name, restartable anywhere**: rejected as needless; editor highlighters only restart at initial-state tokens.

## Consequences

- Typing the missing end line of an unterminated verbatim block only re-highlights its body when the highlighter next relexes from above the block (for instance on reopening). The PSI is always correct, as the parser relexes the whole file.
- Platform features that look for `PsiWhiteSpace` or `PsiComment` (word selection, comment-aware actions) see Org tokens instead and may need Org-specific support.
