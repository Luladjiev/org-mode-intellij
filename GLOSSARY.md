# Org Mode for IntelliJ

Org-mode editing inside IntelliJ-based IDEs, compatible with files written in Emacs.

## Language

### Document structure

**Heading**:
A line starting with one or more asterisks followed by a space; the number of asterisks is its level.
_Avoid_: Headline, outline item, node

**Section**:
The content directly under a Heading's line, up to its first child Heading: planning line, property drawer and body text.
_Avoid_: Body, section body, content

**Subtree**:
A Heading together with its Section and all descendant Subtrees.
_Avoid_: Branch, entry, heading (when the children are meant)

**Drawer**:
Lines between a `:NAME:` line and an `:END:` line inside a Section; without the `:END:` line it is plain text.

**Property Drawer**:
A Drawer named `PROPERTIES` placed directly under a Heading or its planning line, holding only `:KEY: value` lines.
_Avoid_: Properties block

**Block**:
Lines between `#+BEGIN_NAME` and a matching `#+END_NAME` line. A **Verbatim Block** (`SRC`, `EXAMPLE`, `EXPORT`, `COMMENT`, `VERSE`) holds raw text; any other Block holds Org elements.
_Avoid_: Raw block, lesser block

### Tasks

**TODO Keyword**:
The first word of a Heading when it belongs to the document's TODO Sequences; either an active keyword (e.g. `TODO`, `NEXT`) or a done keyword (e.g. `DONE`, `CANCELLED`).
_Avoid_: Status, task state

**TODO Sequence**:
An ordered list of TODO Keywords declared in a document by a `#+TODO`, `#+SEQ_TODO` or `#+TYP_TODO` line, with `|` separating active from done keywords; `TODO | DONE` when none is declared.
_Avoid_: Workflow (reserved for IDE-level configuration in v0.2)

### Editing

**Promote / Demote**:
Remove or add one level. A Heading command changes only that Heading line; a Subtree command changes every Heading in the Subtree.
_Avoid_: Outdent, indent, shift (for Headings)

**Sibling**:
A Heading with the same parent and the same level, with no Heading of a lower level between them. Move Subtree swaps a Subtree with its adjacent Sibling.
_Avoid_: Neighbor, peer

**Visibility**:
How much of a Subtree is shown: **folded** (Heading only), **children** (Section and child Headings) or **subtree** (everything); for a whole document **overview** (level-1 Headings), **contents** (all Headings) or **show all**.
_Avoid_: Expansion state, outline view
