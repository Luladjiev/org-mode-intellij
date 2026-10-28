package com.luladjiev.orgmode.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.luladjiev.orgmode.OrgBundle
import com.luladjiev.orgmode.folding.OrgFoldingBuilder

/** Application-level Org settings. */
@Service(Service.Level.APP)
@State(name = "OrgModeSettings", storages = [Storage("org-mode.xml")])
class OrgSettings : SimplePersistentStateComponent<OrgSettings.SettingsState>(SettingsState()) {
    class SettingsState : BaseState() {
        /** TAB and Shift+TAB on a Heading line cycle visibility. */
        var tabCyclesVisibility by property(true)

        /** Emacs `org-cycle-separator-lines`: how folded Subtrees treat the blank lines before the next Heading. */
        var separatorLines by property(2)
    }

    var tabCyclesVisibility: Boolean
        get() = state.tabCyclesVisibility
        set(value) {
            state.tabCyclesVisibility = value
        }

    /**
     * Blank lines needed before a Heading to keep one of them visible below a folded Subtree; fewer are folded away.
     * 0 folds them all; a negative N keeps them all once there are at least -N. Blank lines ending the file always show.
     */
    var separatorLines: Int
        get() = state.separatorLines
        set(value) {
            if (value == state.separatorLines) return
            state.separatorLines = value
            OrgFoldingBuilder.refreshOpenEditors()
        }

    companion object {
        fun getInstance(): OrgSettings = service()
    }
}

/** Settings | Languages & Frameworks | Org Mode. */
class OrgSettingsConfigurable : BoundConfigurable(OrgBundle.message("settings.display.name")) {
    override fun createPanel() = panel {
        val settings = OrgSettings.getInstance()
        row {
            checkBox(OrgBundle.message("settings.tab.cycles.visibility"))
                .bindSelected(settings::tabCyclesVisibility)
        }
        row(OrgBundle.message("settings.separator.lines")) {
            spinner(-10..10).bindIntValue(settings::separatorLines)
        }.rowComment(OrgBundle.message("settings.separator.lines.comment"))
    }
}
