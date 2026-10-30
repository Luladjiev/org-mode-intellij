<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Org Mode Changelog

## [Unreleased]

### Added

- `.org` files: a parser for Headings, TODO Keywords (with `#+TODO`, `#+SEQ_TODO` and `#+TYP_TODO`), priorities, tags,
  planning lines, drawers, blocks, keywords, lists with checkboxes, tables, comments and links. Unknown syntax is kept
  as written.
- Syntax highlighting, configurable under Settings | Editor | Color Scheme | Org, that also follows theme plugins'
  color schemes.
- Folding of Subtrees, Sections, drawers and blocks. TAB on a Heading line cycles folded, children and subtree;
  Shift+TAB cycles the document through overview, contents and show all. TAB cycling can be turned off under
  Settings | Languages & Frameworks | Org Mode.
- Structure View of the Headings with their TODO Keyword, priority and tags.
- Code | Org Mode actions: insert a Heading or TODO Heading after the current Subtree (Emacs `C-RET`/`C-S-RET`), promote or demote a Heading or a Subtree, move a Subtree up or down (also with Move
  Statement Up/Down on a Heading line), cycle an empty Heading's level (also TAB at its end), cycle the TODO Keyword,
  toggle a checkbox, and go to the next or previous Heading, sibling or parent. Each edit is one undoable command.
- Optional IdeaVim commands (IdeaVim 2.28.0 or newer), turned on with `set orgmode`: every command is a
  `<Plug>(org-…)` to map to your own keys, including Tab cycling with `nmap <Tab> <Plug>(org-cycle)`.
  Heading motions take counts, work in Visual mode and join the jump list. In Insert mode, `<Plug>(org-cycle-level)`
  gives Emacs's Tab on a new Heading, the insert-Heading commands work in Normal and Insert mode and leave you in
  Insert mode on the new Heading, and Promote/Demote Heading act on a Heading line as Emacs M-←/M-→ do. Outside
  Org files the keys do what they normally do.
