package com.luladjiev.orgmode.ideavim

import com.intellij.openapi.editor.Editor
import com.luladjiev.orgmode.core.edit.HeadingMotion
import com.luladjiev.orgmode.core.edit.OrgSnapshot
import com.luladjiev.orgmode.editor.OrgCommands
import com.luladjiev.orgmode.folding.OrgVisibility
import com.luladjiev.orgmode.psi.OrgFile
import com.luladjiev.orgmode.psi.OrgSubtree
import com.luladjiev.orgmode.settings.OrgSettings
import com.maddyhome.idea.vim.KeyHandler
import com.maddyhome.idea.vim.api.ExecutionContext
import com.maddyhome.idea.vim.api.VimEditor
import com.maddyhome.idea.vim.api.injector
import com.maddyhome.idea.vim.command.MappingMode
import com.maddyhome.idea.vim.command.OperatorArguments
import com.maddyhome.idea.vim.extension.ExtensionHandler
import com.maddyhome.idea.vim.extension.VimExtension
import com.maddyhome.idea.vim.extension.VimExtensionFacade.executeNormalWithoutMapping
import com.maddyhome.idea.vim.extension.VimExtensionFacade.putExtensionHandlerMapping
import com.maddyhome.idea.vim.impl.state.toMappingMode
import com.maddyhome.idea.vim.key.ToKeysMappingInfo
import com.maddyhome.idea.vim.newapi.ij
import java.util.EnumSet

/**
 * IdeaVim adapter. Enabled by IdeaVim's `orgmode` toggle option (`set orgmode` in `.ideavimrc`).
 *
 * Each command is exposed as `<Plug>(org-…)` with no keys of its own: users map the keys they want in `.ideavimrc`.
 * IdeaVim mappings are global, so handlers act only in Org editors and otherwise replay the keys mapped to them
 * unmapped, as a buffer-local Vim mapping would behave.
 */
class OrgVimExtension : VimExtension {
    override fun getName() = NAME

    override fun init() {
        if (IdeaVimCompat.isSupported) IdeaVimCompat.call(Unit) { installMappings() }
    }

    private fun installMappings() {
        for (handler in HANDLERS) {
            putExtensionHandlerMapping(handler.modes, handler.plug, owner, handler, false)
        }
    }

    /** A `<Plug>(org-…)` command: [perform]s in an Org editor, or replays the keys mapped to it when it does not. */
    private abstract class OrgHandler(private val plugName: String) : ExtensionHandler {
        val plug by lazy { injector.parser.parseKeys("<Plug>($plugName)") }

        /**
         * The modes the `<Plug>` exists in; edits and cycling are Normal-mode only. Built from
         * the enum, not `MappingMode.N`, whose getter IdeaVim 2.32 and older lack.
         */
        open val modes: Set<MappingMode> = EnumSet.of(MappingMode.NORMAL)

        override fun execute(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) =
            IdeaVimCompat.call(Unit) { run(editor, context, operatorArguments) }

        private fun run(editor: VimEditor, context: ExecutionContext, operatorArguments: OperatorArguments) {
            val ijEditor = editor.ij
            val file = if (isOrgEditor(ijEditor)) OrgCommands.orgFile(ijEditor) else null
            if (file != null && perform(editor, context, file, operatorArguments.count1)) {
                // IdeaVim leaves the count in place after an extension handler; clear it, as its Matchit does, so it
                // does not carry over to the next command. A replay keeps it for the replayed keys.
                KeyHandler.getInstance().keyHandlerState.commandBuilder.resetCount()
            } else {
                replay(editor)
            }
        }

        /** Runs the command, or returns false to fall through to the replayed keys. */
        abstract fun perform(editor: VimEditor, context: ExecutionContext, file: OrgFile, count: Int): Boolean

        /**
         * Replays, unmapped, the keys the user mapped to [plug] in the current mode, with the typed count. A handler
         * cannot see which keys were typed, so when several are mapped to it nothing is replayed.
         */
        private fun replay(editor: VimEditor) {
            val mappings = injector.keyGroup.getKeyMapping(editor.mode.toMappingMode())
            val keys = mappings.getAll(emptyList()).map { it.getPath() }
                .filter { (mappings[it] as? ToKeysMappingInfo)?.toKeys == plug }
                .singleOrNull() ?: return
            executeNormalWithoutMapping(keys, editor.ij)
        }
    }

    /** Cycles what [target] finds, if anything, when TAB cycling is enabled in settings; otherwise falls through. */
    private open class CycleHandler<T : Any>(
        plugName: String,
        private val target: (Editor, OrgFile) -> T?,
        private val cycle: (Editor, T) -> Unit,
    ) : OrgHandler(plugName) {
        override fun perform(editor: VimEditor, context: ExecutionContext, file: OrgFile, count: Int): Boolean {
            val ijEditor = editor.ij
            if (!OrgSettings.getInstance().tabCyclesVisibility) return false
            val target = target(ijEditor, file) ?: return false
            OrgVisibility.updateRegions(ijEditor)
            cycle(ijEditor, target)
            return true
        }
    }

    /**
     * Emacs TAB (`org-cycle`), as the IDE's TAB: at the end of an empty Heading cycles its level, whatever the
     * settings; on other Heading lines cycles visibility; elsewhere falls through.
     */
    private class OrgCycleHandler :
        CycleHandler<OrgSubtree>("org-cycle", OrgCommands::subtreeOnCaretLine, OrgVisibility::cycle) {
        override fun perform(editor: VimEditor, context: ExecutionContext, file: OrgFile, count: Int): Boolean {
            val ijEditor = editor.ij
            if (!OrgCommands.isAtEndOfEmptyHeading(ijEditor)) return super.perform(editor, context, file, count)
            OrgCommands.cycleLevel(ijEditor, file)
            return true
        }
    }

    private open class ActionHandler(plugName: String, private val actionId: String) : OrgHandler(plugName) {
        override fun perform(editor: VimEditor, context: ExecutionContext, file: OrgFile, count: Int): Boolean {
            val action = injector.actionExecutor.getAction(actionId) ?: return true
            injector.actionExecutor.executeAction(editor, action, context)
            return true
        }
    }

    /**
     * An action that also exists in Insert mode, where it runs only if [inInsertMode] holds for the caret and otherwise
     * falls through, so the mapped key is typed.
     */
    private class InsertModeActionHandler(
        plugName: String,
        actionId: String,
        override val modes: Set<MappingMode>,
        private val inInsertMode: (OrgSnapshot, Int) -> Boolean,
    ) : ActionHandler(plugName, actionId) {
        override fun perform(editor: VimEditor, context: ExecutionContext, file: OrgFile, count: Int): Boolean {
            val ijEditor = editor.ij
            if (editor.mode.toMappingMode() == MappingMode.INSERT &&
                !inInsertMode(OrgCommands.snapshot(ijEditor), ijEditor.caretModel.offset)
            ) {
                return false
            }
            return super.perform(editor, context, file, count)
        }
    }

    /**
     * Inserts a Heading from Normal or Insert mode and leaves the editor in Insert mode at its end, as Doom Emacs's
     * `C-RET` does in Normal mode.
     */
    private class InsertHeadingHandler(plugName: String, actionId: String) : ActionHandler(plugName, actionId) {
        override val modes: Set<MappingMode> = NORMAL_INSERT

        override fun perform(editor: VimEditor, context: ExecutionContext, file: OrgFile, count: Int): Boolean {
            val document = editor.ij.document
            val stamp = document.modificationStamp
            val normal = editor.mode.toMappingMode() == MappingMode.NORMAL
            super.perform(editor, context, file, count)
            if (normal && document.modificationStamp != stamp) {
                executeNormalWithoutMapping(injector.parser.parseKeys("A"), editor.ij)
            }
            return true
        }
    }

    /**
     * Moves each caret [count] Headings along [motion], as far as it can, and records a jump like Vim's `[[` and
     * `]]` do, so `<C-o>` returns. With no Heading to move to, nothing happens. In Visual mode the move extends the
     * selection. There is no Operator-pending mapping: IdeaVim gives an extension motion only a characterwise range,
     * so `d` from a Heading line would leave an empty line where Vim deletes whole lines.
     */
    private class MotionHandler(plugName: String, private val motion: HeadingMotion) : OrgHandler(plugName) {
        override val modes: Set<MappingMode> = EnumSet.of(MappingMode.NORMAL, MappingMode.VISUAL)

        override fun perform(editor: VimEditor, context: ExecutionContext, file: OrgFile, count: Int): Boolean {
            val ijEditor = editor.ij
            val snapshot = OrgCommands.snapshot(ijEditor)
            val isVisible = OrgCommands.isVisible(ijEditor)
            val targets = editor.carets().mapNotNull { caret ->
                motion.target(snapshot, caret.offset, isVisible, count)?.let { caret to it.start }
            }
            if (targets.isEmpty()) return true
            injector.jumpService.saveJumpLocation(editor)
            for ((caret, offset) in targets) caret.moveToOffset(offset)
            return true
        }
    }

    companion object {
        const val NAME = "orgmode"

        private val NORMAL_INSERT: Set<MappingMode> = EnumSet.of(MappingMode.NORMAL, MappingMode.INSERT)

        // Limited to v0.1 commands; Agenda, Capture and Refile arrive with those features.
        private val HANDLERS = listOf(
            ActionHandler("org-cycle-todo", "org.mode.CycleTodo"),
            // In Insert mode only on a Heading line, as Emacs M-←/M-→ (`org-metaleft`/`org-metaright`).
            InsertModeActionHandler("org-promote-heading", "org.mode.PromoteHeading", NORMAL_INSERT, ::isOnHeadingLine),
            InsertModeActionHandler("org-demote-heading", "org.mode.DemoteHeading", NORMAL_INSERT, ::isOnHeadingLine),
            // Emacs TAB on a new Heading (`org-cycle-level`).
            InsertModeActionHandler(
                "org-cycle-level",
                "org.mode.CycleLevel",
                EnumSet.of(MappingMode.INSERT),
                OrgSnapshot::isAtEndOfEmptyHeading,
            ),
            // Emacs C-RET and C-S-RET (`org-insert-heading-respect-content`, `org-insert-todo-heading-respect-content`).
            InsertHeadingHandler("org-insert-heading-after-subtree", "org.mode.InsertHeadingAfterSubtree"),
            InsertHeadingHandler("org-insert-todo-heading-after-subtree", "org.mode.InsertTodoHeadingAfterSubtree"),
            ActionHandler("org-move-subtree-up", "org.mode.MoveSubtreeUp"),
            ActionHandler("org-move-subtree-down", "org.mode.MoveSubtreeDown"),
            ActionHandler("org-cycle-visibility", "org.mode.CycleVisibility"),
            ActionHandler("org-cycle-global-visibility", "org.mode.CycleGlobalVisibility"),
            MotionHandler("org-next-heading", HeadingMotion.NEXT_HEADING),
            MotionHandler("org-previous-heading", HeadingMotion.PREVIOUS_HEADING),
            MotionHandler("org-next-sibling", HeadingMotion.NEXT_SIBLING),
            MotionHandler("org-previous-sibling", HeadingMotion.PREVIOUS_SIBLING),
            MotionHandler("org-parent-heading", HeadingMotion.PARENT_HEADING),
            OrgCycleHandler(),
            CycleHandler("org-global-cycle", target = { _, file -> file }, cycle = OrgVisibility::cycleGlobal),
        )

        private fun isOnHeadingLine(snapshot: OrgSnapshot, offset: Int) = snapshot.headingOnLine(offset) != null

        private fun isOrgEditor(editor: Editor) = editor.virtualFile?.extension.equals("org", ignoreCase = true)
    }
}
