# Org Mode for IntelliJ

Native [Org-mode](https://orgmode.org) support for IntelliJ-based IDEs, no Emacs required. Edit, fold, navigate and
restructure `.org` files with IDE actions or optional IdeaVim mappings. Files stay plain Org text, compatible with Emacs:
the plugin changes only what a command touches.

Status: v0.1 in development. See [CHANGELOG.md](CHANGELOG.md).

## Features

- **Language**: an Org parser for Headings, TODO Keywords (including `#+TODO`/`#+SEQ_TODO`/`#+TYP_TODO`), priorities,
  tags, planning lines, drawers, blocks, keywords, lists with checkboxes, tables, comments, links and emphasis.
  Syntax the parser does not know is kept verbatim.
- **Highlighting**: Heading levels, TODO and done keywords, priorities, tags and markup, configurable under
  Settings | Editor | Color Scheme | Org.
- **Folding**: Subtrees, Sections, drawers and blocks, with Emacs-style visibility cycling. TAB on a Heading line cycles
  folded → children → subtree; Shift+TAB on a Heading line cycles the document: overview → contents → show all.
- **Structure View**: the Headings, nested by level, with TODO Keyword, priority and tags.
- **Structural editing**: insert a Heading (or TODO Heading) after the current Subtree, promote/demote a Heading or a whole Subtree, move a Subtree up or down among its siblings
  (also with Move Statement Up/Down on a Heading line), cycle the TODO Keyword, toggle a checkbox. Each is one undoable
  command and edits only the lines it has to.
- **Navigation**: next/previous visible Heading, next/previous sibling, parent Heading.
- **IdeaVim** (optional): `<Plug>(org-…)` commands to map to your own keys.

Not in v0.1: Agenda, Capture, Refile, timestamps and scheduling commands, Babel, export, checkbox statistics
(`[n/m]`) and `#+STARTUP` folding.

## Installation

The plugin is not on JetBrains Marketplace yet. Build it and install from disk:

```bash
./gradlew buildPlugin
```

Then Settings | Plugins | ⚙ | Install Plugin from Disk… and choose `org-intellij/build/distributions/org-mode-*.zip`.
Disable any other plugin that handles `.org` files first.

## Usage

Open any `.org` file. All commands are under **Code | Org Mode** (shown in Org editors) and in Find Action
(⇧⌘A / Ctrl+Shift+A). Out of the box only these keys act on Org files, and only on a Heading line:

| Key | On a Heading line | Elsewhere |
|---|---|---|
| TAB | cycle the Subtree's visibility; at the end of an empty Heading, its level | normal TAB |
| Shift+TAB | cycle the whole document's visibility | normal Shift+TAB |
| Move Statement Up/Down (⇧⌘↑/↓, Ctrl+Shift+Up/Down) | move the Subtree among its siblings | normal Move Statement |

Assign shortcuts to the other commands in Settings | Keymap (search "Org Mode"). [docs/keymap.md](docs/keymap.md) lists
every command with its Action ID, the Emacs key it mirrors and its IdeaVim mapping.

Settings | Languages & Frameworks | Org Mode turns TAB visibility cycling off and sets Emacs `org-cycle-separator-lines`.

### IdeaVim

Turn the commands on with `set orgmode` in `~/.ideavimrc`, then map the keys you want; none are mapped for you:

```vim
set orgmode
nmap <leader>ot <Plug>(org-cycle-todo)
nmap ]] <Plug>(org-next-heading)
nmap [[ <Plug>(org-previous-heading)
nmap <Tab> <Plug>(org-cycle)
nmap <S-Tab> <Plug>(org-global-cycle)
imap <Tab> <Plug>(org-cycle-level)
```

Outside Org files every mapping falls back to what the keys normally do. [docs/keymap.md](docs/keymap.md) lists every
`<Plug>` with its modes.

## Compatibility

| | Supported | Checked by |
|---|---|---|
| IntelliJ IDEA | 2025.3 (build 253) and newer | tests on 2025.3; Plugin Verifier on 2025.3 and 2026.2 |
| Other IntelliJ-based IDEs | 2025.3 and newer should work (the plugin needs only the platform) | not tested |
| IdeaVim | optional; 2.28.0 and newer | IdeaVim tests on 2.28.0, 2.32.2, 2.38.0, 2.41.0, 2.44.1, 2.46.2 and 2.47.3 |
| IdeaVim older than 2.28.0 | the IdeaVim mappings stay off; everything else works | CI on 2.27.2 |
| Without IdeaVim | everything except the IdeaVim mappings | every test run also runs without IdeaVim |
| Operating systems | Windows, macOS, Linux | CI on all three |
| Org syntax | the [Org Syntax spec](https://orgmode.org/worg/org-syntax.html); Emacs Org 9.8.7 breaks ties | parser and edit fixtures compared with Emacs |

Known differences from Emacs are listed in [docs/compatibility.md](docs/compatibility.md).

## Documentation

- [docs/keymap.md](docs/keymap.md): every command, its Action ID, Emacs key and IdeaVim mapping.
- [docs/compatibility.md](docs/compatibility.md): differences from Emacs Org.
- [docs/architecture.md](docs/architecture.md): how the plugin is built.
- [docs/decisions/](docs/decisions/): architecture decision records; [GLOSSARY.md](GLOSSARY.md): domain terms.

## Development

JDK 21. The build downloads the IDE it compiles against.

```bash
./gradlew check          # all tests, with and without IdeaVim
./gradlew runIde         # a sandbox IDE with the plugin
./gradlew verifyPlugin   # Plugin Verifier against the IDEs in gradle.properties
./gradlew :org-core:jmh  # benchmarks on a generated 1 MB document
```

[docs/architecture.md](docs/architecture.md) describes the modules and where to change what.

## License

[Apache License 2.0](LICENSE).
