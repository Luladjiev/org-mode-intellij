# Keymap

Every Org command is an IDE action under **Code | Org Mode**. Its Action ID (`org.mode.…`) never changes once released,
so keymaps and `.ideavimrc` files can rely on it.

## Commands

| Command | Action ID | IDE key | Emacs | `<Plug>` |
|---|---|---|---|---|
| Cycle Visibility | `org.mode.CycleVisibility` | TAB on a Heading line | `TAB` | `(org-cycle-visibility)` |
| Cycle Global Visibility | `org.mode.CycleGlobalVisibility` | Shift+TAB on a Heading line | `S-TAB` | `(org-cycle-global-visibility)` |
| Next Heading | `org.mode.NextHeading` | | `C-c C-n` | `(org-next-heading)` |
| Previous Heading | `org.mode.PreviousHeading` | | `C-c C-p` | `(org-previous-heading)` |
| Next Sibling | `org.mode.NextSibling` | | `C-c C-f` | `(org-next-sibling)` |
| Previous Sibling | `org.mode.PreviousSibling` | | `C-c C-b` | `(org-previous-sibling)` |
| Parent Heading | `org.mode.ParentHeading` | | `C-c C-u` | `(org-parent-heading)` |
| Promote Heading | `org.mode.PromoteHeading` | | `M-←` | `(org-promote-heading)` |
| Demote Heading | `org.mode.DemoteHeading` | | `M-→` | `(org-demote-heading)` |
| Promote Subtree | `org.mode.PromoteSubtree` | | `M-S-←` | |
| Demote Subtree | `org.mode.DemoteSubtree` | | `M-S-→` | |
| Cycle Level | `org.mode.CycleLevel` | TAB at the end of an empty Heading | `TAB` | `(org-cycle-level)` |
| Move Subtree Up | `org.mode.MoveSubtreeUp` | Move Statement Up on a Heading line | `M-↑` | `(org-move-subtree-up)` |
| Move Subtree Down | `org.mode.MoveSubtreeDown` | Move Statement Down on a Heading line | `M-↓` | `(org-move-subtree-down)` |
| Cycle TODO | `org.mode.CycleTodo` | | `C-c C-t` | `(org-cycle-todo)` |
| Toggle Checkbox | `org.mode.ToggleCheckbox` | | `C-c C-c` | |

- **Next/Previous Heading** visit every visible Heading, whatever its level. **Next/Previous Sibling** stay at the same
  level under the same parent (Emacs `org-forward-heading-same-level`); before the first Heading, Next Sibling goes to
  it. All navigation skips Headings inside folds and puts the caret on the Heading's first column.
- **Promote/Demote Heading** change only the Heading line; its children keep their level. The **Subtree** variants
  shift the Heading and everything under it. A level-1 Heading cannot be promoted.
- **Cycle Level** works only at the end of an empty Heading (stars and maybe a TODO Keyword), as Emacs
  `org-cycle-level`: compared with the Heading before it, each press makes it a child, then moves it up through the
  ancestors' levels, then back to a sibling. TAB does it even with TAB cycling turned off in Settings.
- **Move Subtree** swaps the Subtree with its previous or next sibling and never moves it out of its parent.
- With several carets, **Cycle TODO** and **Toggle Checkbox** act once per Heading or checkbox; the structural commands
  are refused.

## Assigning IDE shortcuts

Only TAB, Shift+TAB and Move Statement Up/Down do Org things by default, and only on a Heading line; elsewhere they keep
their usual meaning. For the other commands, open Settings | Keymap, search for "Org Mode" or the command's name, and
add a shortcut. The Emacs keys above are a starting point, but most conflict with IDE shortcuts in the default keymaps
(the Keymap page warns about conflicts). Two-stroke shortcuts work too, so Emacs chords such as `C-c C-t` can be
assigned where they don't clash.

## IdeaVim

The IdeaVim mappings need IdeaVim 2.28.0 or newer. Turn them on in `~/.ideavimrc`:

```vim
set orgmode
```

The plugin maps no keys: map each `<Plug>` from the table above to the keys you want, with `nmap` (and `xmap` for
Heading motions in Visual mode). `<Plug>` mappings need the recursive `map` commands, not `noremap`. For example, with
`<leader>o` and the letters of the Emacs keys:

```vim
nmap <leader>ot <Plug>(org-cycle-todo)
nmap <leader>oh <Plug>(org-promote-heading)
nmap <leader>ol <Plug>(org-demote-heading)
nmap <leader>ok <Plug>(org-move-subtree-up)
nmap <leader>oj <Plug>(org-move-subtree-down)
nmap <leader>oz <Plug>(org-cycle-visibility)
nmap <leader>on <Plug>(org-next-heading)
nmap <leader>op <Plug>(org-previous-heading)
nmap <leader>of <Plug>(org-next-sibling)
nmap <leader>ob <Plug>(org-previous-sibling)
nmap <leader>ou <Plug>(org-parent-heading)
```

### Modes

- Heading motions work in Normal and Visual mode, where they extend the selection. They take a count (`3]]`) and stop
  at the last Heading they can reach. Each move is a jump, so `<C-o>` returns.
- Editing and visibility commands are Normal mode only, except those in [Insert mode](#insert-mode).
- There are no Operator-pending motions: IdeaVim would give `d]]` a characterwise range that leaves an empty line.
  Subtree text objects are planned for v1.0.

### Insert mode

Tab in Insert mode already works as in Emacs: at the end of an empty Heading it cycles the level (the IDE's TAB, see
Cycle Level). Two `<Plug>`s also work in Insert mode, and where they don't act the key is typed as usual:

- `<Plug>(org-cycle-level)`: Cycle Level, for another key.
- `<Plug>(org-demote-heading)` and `<Plug>(org-promote-heading)`: as Emacs `M-→`/`M-←`, only on a Heading line,
  from anywhere on it.

Doom Emacs's Insert-mode Tab and Shift+Tab are these two mapped to Tab and Shift+Tab:

```vim
imap <Tab> <Plug>(org-demote-heading)
imap <S-Tab> <Plug>(org-promote-heading)
```

### Outside Org files

IdeaVim mappings are global, so the Org mappings also exist in other files. There the keys run as if the Org mapping
did not exist, a count included: `]]` mapped to `<Plug>(org-next-heading)` is still Vim's `]]` in a Kotlin file. This
needs a single key sequence per `<Plug>` and mode: with two, the command cannot tell which was typed, and outside Org
files nothing happens.

### Tab cycling

In Normal mode IdeaVim handles Tab itself (Vim's jump forward), so the IDE's TAB above never sees it. Map it:

```vim
nmap <Tab> <Plug>(org-cycle)
nmap <S-Tab> <Plug>(org-global-cycle)
```

`<Plug>(org-cycle)` does what the IDE's TAB does (Emacs `org-cycle`): at the end of an empty Heading it cycles the
level, elsewhere on a Heading line the visibility; off a Heading line, and in other files, Tab keeps its Vim meaning. `<Plug>(org-global-cycle)` cycles the whole document from anywhere in an Org file.

### Commands without a `<Plug>`

Promote/Demote Subtree and Toggle Checkbox have no `<Plug>`; map their actions directly:

```vim
nmap <leader>oH <Action>(org.mode.PromoteSubtree)
nmap <leader>oL <Action>(org.mode.DemoteSubtree)
nmap <leader>ox <Action>(org.mode.ToggleCheckbox)
```

An `<Action>` mapping does nothing outside Org files instead of falling back to its keys.
