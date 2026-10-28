# IdeaVim mappings are global, guarded to Org editors

IdeaVim (2.47.3) has no buffer-local mappings and an extension handler cannot decline a key, yet the mappings must act only in Org editors. We register each command as `<Plug>(org-…)` with no keys of its own, so users map the keys they want, and every handler checks that the editor holds an Org file; elsewhere it replays the keys mapped to its `<Plug>` unmapped via `executeNormalWithoutMapping`. With `nmap <leader>ot <Plug>(org-cycle-todo)`, `\ot` in a non-Org editor therefore does exactly what plain Vim would do with no mapping (`o` opens a line, `t` is typed), which is the behavior a buffer-local mapping would give.

The Phase 0 spike (`OrgVimExtensionTest`) confirmed:

- The classic `IdeaVIM.vimExtension` extension point works from an optional `<depends>` config file; the extension appears as the `orgmode` toggle option, off by default.
- Setting or unsetting that option from code (`injector.optionGroup.setToggleOption` / `unsetToggleOption`) enables or disables the extension.
- Disabling removes every mapping the extension owns.
- The plugin loads and its tests pass with IdeaVim disabled (`testWithoutIdeaVim`).

## Considered Options

- **IdeaVim's new `com.intellij.vim.api` Plugin API**: rejected for now; experimental, and its annotation processor is not published for external plugins.
- **Only document `.ideavimrc` snippets mapping to `<Action>(org.mode.…)`**: rejected; no Org-file scoping.

## Consequences

- There are no default keys. `<leader>o…` defaults were removed: IdeaVim's `putKeyMappingIfMissing` only checks for mappings *to* the `<Plug>` and would overwrite the user's own keys, which disabling could not restore, so defaults needed their own checks for mapped keys and prefixes, and `<Nop>` mappings to switch one off. Leaving every key to the user removes that, and any clash with other `<leader>` mappings.
- A handler cannot see which keys triggered it, so it replays the keys the user mapped to its `<Plug>`. If several keys map to one `<Plug>`, it cannot tell which was typed and replays nothing.
- IdeaVim leaves a typed count in its command builder after an extension handler. A replay therefore keeps it (`3J` mapped to a `<Plug>` still joins three lines outside Org files), and a handler that acts must clear it, as IdeaVim's own Matchit does, or it carries over to the next command.
- `orgmode` is set only from `.ideavimrc`, like any IdeaVim extension. A Settings toggle that also set it was removed: IdeaVim keeps no option state across restarts, so the toggle needed our own saved setting and a post-startup activity that relied on IdeaVim's startup order, and with `set orgmode` in `.ideavimrc` unchecking it only lasted until the next restart.
- Heading motions work in Visual mode but not after an operator. IdeaVim turns an extension handler's caret move into a characterwise range (`ExternalActionHandler` is never linewise) that also stops a character short, so Vim's exclusive-linewise rule cannot apply and `d` from a Heading line would leave an empty line. Subtree text objects (v1.0) are the way to operate on Subtrees.
- The adapter uses `VimExtensionFacade`, `injector`, `KeyHandler` and the `ToKeysMappingInfo` lookup, which IdeaVim does not promise to keep stable. Even Kotlin conveniences differ between releases (`MappingMode.N` compiles to a getter that 2.32 lacks; `KeyMapping.getAll` is missing in 2.27), so the adapter requires IdeaVim 2.28.0, the first release built for 253, and CI runs the IdeaVim tests against that release besides `ideaVimVersion`. A `LinkageError` from a future IdeaVim is caught at every entry point (`IdeaVimCompat.call`), logged once, and turns into a fallback, so TAB and the IDE actions keep working.
