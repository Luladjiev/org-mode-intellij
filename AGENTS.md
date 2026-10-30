# AGENTS.md

This file provides guidance to coding agents working in this repository. Claude Code reads it through `CLAUDE.md`.

## Project

A native IntelliJ Platform plugin (Kotlin) that implements Org-mode without Emacs. Key documents:

- `GLOSSARY.md`: canonical domain terms. Use them in code and docs: **Heading** (never "headline"),
  **Section**, **Subtree**, **TODO Keyword**, **TODO Sequence**.
- `docs/decisions/`: architecture decision records (`NNNN-slug.md`).
- `README.md` (users), `docs/keymap.md` (every command, Action ID and key) and `docs/architecture.md`: update them when
  commands, keys or modules change.

Current state: Phase 4 (MVP Stabilization) in progress. Plugin ID `com.luladjiev.org-mode`, display name "Org Mode", Apache-2.0, JDK 21 (per CI).
Supported builds: 253 (2025.3) and newer, unified IntelliJ IDEA distribution (use `intellijIdea(...)`, not
`intellijIdeaCommunity`).

## Commands

```bash
./gradlew buildPlugin                          # installable ZIP in org-intellij/build/distributions/
./gradlew check                                # all tests + checks (what CI runs), incl. testWithoutIdeaVim
./gradlew test --tests "com.luladjiev.orgmode.SomeTest"            # single test class
./gradlew test --tests "com.luladjiev.orgmode.SomeTest.methodName" # single test method
./gradlew runIde                               # sandbox IDE with the plugin loaded
./gradlew verifyPlugin                         # Plugin Verifier compatibility check
./gradlew :org-intellij:test -PtestIdeaVimVersion=2.28.0  # tests against another IdeaVim (min supported: 2.28.0)
emacs --batch -l scripts/gen-fixtures.el       # regenerate expected fixture outputs (needs Emacs; CI doesn't)
emacs --batch -l scripts/check-edit-fixtures.el  # compare edit fixtures with Emacs commands (needs Emacs)
./gradlew :org-core:jmh                        # JMH benchmarks on the generated 1 MB document (not part of check)
```

Run one module's tests with a prefix, e.g. `./gradlew :org-core:test --tests ...`. Versions (`platformVersion`,
`pluginSinceBuild`, `ideaVimVersion`, `verifyPlatformVersions`) live in `gradle.properties`.

## Architecture

The root project is only an aggregator. `org-intellij` is the plugin project: it owns `plugin.xml` (so its
platform tests load the real descriptor), merges `org-ideavim` into its jar (`pluginComposedModule`) and produces
`org-intellij/build/distributions/org-mode-*.zip`. `org-ideavim` depends on `org-intellij` (`compileOnly`), never the
reverse: `org-intellij` composes it without compiling against it, and must never reference IdeaVim or `org-ideavim`
classes in main code. Three modules:

- **org-core**: plain `kotlin("jvm")`, JUnit 5. Lexer, parser, TODO rules, structural edit transformations. Must
  not import IntelliJ or IdeaVim classes; the build enforces this by not depending on the platform. Its
  `testFixtures` hold the generated 1 MB `LargeDocument`, shared by its tests, `src/jmh` and
  `org-intellij`'s performance test.
- **org-intellij**: FileType/Language, PSI, highlighting, folding, Structure View, actions, settings. JUnit 4 +
  Platform test framework.
- **org-ideavim**: classic `VimExtension`, loaded via `<depends optional="true" config-file="org-mode-ideavim.xml">IdeaVIM</depends>`.
- **test-fixtures/org/**: our own `.org` fixtures plus expected outputs generated from Emacs `org-element`
  (`parser/NAME.org` with `NAME.tree`), and golden edit tests (`edit/OPERATION/NAME.org` with `<caret>` markers and
  `NAME.expected.org`), run by both `org-core` and the IDE actions. Never copy Org-mode's GPLv3 tests or private
  notes into it. Known differences from Emacs are listed in `docs/compatibility.md`.

Parsing (`docs/decisions/0001`): one grammar in `org-core` that builds trees through a `TreeBuilder` interface.
`org-intellij` adapts `PsiBuilder`; `org-core` has a plain builder for tests and edits. Never add a second
document model. The lexer is TODO-keyword-agnostic: the parser classifies a Heading's first word against the
document's TODO Sequences (`#+TODO`/`#+SEQ_TODO`/`#+TYP_TODO`), so keyword highlighting comes from PSI.
Syntax authority is the Org Syntax spec; Emacs `org-element` (Org 9.8.7 locally) breaks ties.

Editing: resolve the target, validate preconditions, compute the *smallest* text edits in `org-core` on a text
snapshot (`OrgSnapshot`, `StructureEdits`, `TodoEdits`, `CheckboxEdits`), then apply through IntelliJ write commands
(`OrgCommands.apply`) so Undo/Redo works. TAB, Shift+TAB and Move Statement are `editorActionHandler` wrappers that
fall through to the platform off Heading lines (`docs/decisions/0004`). Never rewrite a whole document for a
small edit; preserve unknown constructs verbatim.

IdeaVim (`docs/decisions/0002`): IdeaVim has no buffer-local mappings and handlers can't fall through.
Commands are `<Plug>(org-…)` with no default keys; handlers check for an Org editor and otherwise replay keys
via `executeNormalWithoutMapping`. The extension is the `orgmode` IdeaVim option, set only from `.ideavimrc`.
Don't use the experimental `com.intellij.vim.api` Plugin API. IDE startup must never fail because of IdeaVim.
`org-intellij` reaches IdeaVim only through its `insertModeProvider` extension point, implemented in `org-ideavim`. Testing: `org-intellij`'s `test` runs with IdeaVim installed (`testPlugin`); `testWithoutIdeaVim` reruns the
IdeaVim-independent tests with it disabled, so tests that touch IdeaVim classes must be excluded there. With IdeaVim
in Normal mode the caret cannot sit past a line's end, so test carets go before a line's last character. Timing
tests need "Performance" in the class name: otherwise the test logger records the daemon's debug output for every
highlight, which triples highlighting time on large files.

Threading and security: no full-document analysis on the UI thread while typing; never execute embedded code
automatically; don't touch files outside configured roots; don't log note contents.

## Rules

- Work only on the current milestone. v0.1 excludes Agenda, Capture, Refile, timestamps and Babel.
- Write golden tests for structural transformations **before** implementing them: input `.org` + expected `.org`,
  asserting the transformation and exact preservation of untouched text.
- Every user command gets a stable `org.mode.<Verb><Object>` Action ID, never renamed once released.
- No mandatory third-party plugin dependencies; IdeaVim stays optional.
- Prefer documented public IntelliJ Platform APIs; document any deprecated API use and why.
- Don't claim Org/Emacs parity without executable compatibility tests.
- Record decisions that are hard to reverse, surprising and a real trade-off in `docs/decisions/`; add resolved
  domain terms to `GLOSSARY.md`.
- Run the relevant tests and report failures; keep changes focused and reversible.
