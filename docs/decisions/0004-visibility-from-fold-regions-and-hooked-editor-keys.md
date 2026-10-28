# Visibility cycling reads fold regions; TAB and Move Statement are wrapped, not rebound

Emacs cycles a Heading through folded → children → subtree, and the whole document through overview → contents → show all, remembering the previous state through `last-command`. We derive the state from IntelliJ's fold regions instead, so cycling continues correctly after the user folds or unfolds with the IDE's own actions, and nothing has to be stored per editor. To make the children and contents states expressible, a Subtree that has child Subtrees gets two regions starting at the end of its Heading line: one over the whole Subtree and one over its Section only. Both end at the line break before the next Heading, so the blank lines before it fold too, except those Emacs `org-cycle-separator-lines` keeps visible (a setting, default 2: at least two blank lines keep one). Blank lines that end the file stay outside every region.

TAB, Shift+TAB and Move Statement Up/Down keep their platform shortcuts. We register `editorActionHandler` wrappers that act only when the caret is on a Heading line of an Org editor (and, for TAB, with no selection, no completion popup and no IdeaVim Insert mode); otherwise they call the platform's own handler. At the end of an empty Heading, TAB instead cycles its level (Emacs `org-cycle-level`), also in IdeaVim Insert mode and with visibility cycling turned off, since Emacs does the same. IdeaVim's mode reaches `org-intellij` through our `insertModeProvider` extension point, implemented in `org-ideavim`, so `org-intellij` still never references IdeaVim.

## Considered Options

- **Remember the last cycling state per editor, like Emacs**: rejected; it goes stale as soon as the user folds with IntelliJ's actions, and needs invalidation on every edit.
- **One region per Subtree only**: rejected; folding "everything but the child Headings" is not expressible, so the children and contents states would be impossible.
- **New Org keymap entries for TAB and Move Statement**: rejected; they would shadow completion, indentation and line moving in Org files, and keymaps cannot express "only on a Heading line".
- **`StatementUpDownMover` for Move Subtree**: rejected; the platform swaps line ranges per caret, so the multi-caret refusal, the "Cannot move past parent" hint and the `org-core` golden-tested semantics would not apply.

## Consequences

- A collapsed Section region looks the same as a collapsed Subtree region when the Subtree has no children; both exist only when it has children, so IntelliJ's Collapse/Expand act on the outer one first.
- Where both regions start, the gutter shows the arrow of the smaller one, the Section's. A plain click on it folds the Subtree instead (`OrgGutterMouseListener`), as a user clicking a Heading's arrow expects.
- Edits that recreate a folded Subtree (moves) re-collapse its regions after the folding pass, by mapping their start offsets through the edit. A region's end can change with its position (blank lines at the end of the file never fold), so the region at the mapped start whose end is closest is collapsed.
- Changing the separator setting rebuilds the regions of open Org editors; regions whose range changed come back expanded.
- Another modal-editing plugin can keep TAB in its insert mode by implementing `insertModeProvider`.
- IdeaVim's mode is shared between editors in tests: a test that leaves Insert mode affects later TAB tests, so IdeaVim tests return to Normal mode in `tearDown`.
