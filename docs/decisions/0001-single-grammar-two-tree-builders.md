# One Org grammar in org-core, two tree builders

The Org lexer and parser live in `org-core` and build trees only through a small `TreeBuilder` interface (`mark` / `done` / `advance`). `org-intellij` adapts IntelliJ's `PsiBuilder` to it, and `org-core` has a plain in-memory builder for tests and structural edits on text snapshots. This keeps parsing testable without IntelliJ while leaving PSI as the only tree the IDE sees, so there are never two parse trees to keep in sync.

## Considered Options

- **org-core owns its own AST and PSI wraps it**: rejected; it fights IntelliJ's incremental reparsing and still leaves two trees.
- **Separate parsers for org-core and PSI**: rejected; the grammars drift, a real divergence risk.

## Consequences

- TODO Keywords are not known to the lexer: it emits a generic word token and the parser classifies it against the document's TODO Sequences, so editing a `#+TODO` line re-highlights live.
- `org-core` must not grow a second, richer document model; anything the IDE needs comes from the shared grammar.
