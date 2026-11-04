package com.luladjiev.orgmode

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.luladjiev.orgmode.settings.OrgSettingsConfigurable

/** Runs both with IdeaVim installed (`test`) and disabled (`testWithoutIdeaVim`). */
class PluginLoadTest : BasePlatformTestCase() {
    private val ideaVimExpected = System.getProperty("org.mode.test.ideaVimExpected", "true").toBoolean()

    fun testPluginDescriptorIsLoaded() {
        assertNotNull(PluginManagerCore.getPlugin(PluginId.getId("com.luladjiev.org-mode")))
    }

    fun testIdeaVimPresenceMatchesTestRun() {
        assertEquals(ideaVimExpected, PluginManagerCore.isLoaded(PluginId.getId("IdeaVIM")))
    }

    /** Each IdeaVim test task really runs against the IdeaVim release it names. */
    fun testIdeaVimVersionMatchesTestRun() {
        val expected = System.getProperty("org.mode.test.ideaVimVersion") ?: return
        assertEquals(expected, PluginManagerCore.getPlugin(PluginId.getId("IdeaVIM"))?.version)
    }

    /** The Settings page builds with or without IdeaVim. */
    fun testSettingsPageBuilds() {
        val configurable = OrgSettingsConfigurable()
        try {
            assertNotNull(configurable.createComponent())
            configurable.reset()
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
    }
}
