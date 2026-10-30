package com.luladjiev.orgmode.ideavim

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.text.VersionComparatorUtil
import com.maddyhome.idea.vim.VimPlugin

/**
 * IdeaVim compatibility checks. The adapter calls IdeaVim APIs that IdeaVim does not
 * promise to keep, so every entry point from IdeaVim or `org-intellij` goes through [call], and the adapter stays off
 * on releases older than [MIN_VERSION]. Either way Org editing and the IDE actions keep working.
 */
internal object IdeaVimCompat {
    /** The first IdeaVim release built for 253; older ones lack APIs the adapter uses. */
    const val MIN_VERSION = "2.28.0"

    private val log = logger<IdeaVimCompat>()

    @Volatile
    private var reported = false

    /** IdeaVim's own accessor; the platform's plugin lookups are internal API (Plugin Verifier, 262). */
    val version: String? get() = call(null) { VimPlugin.getVersion() }

    val isSupported: Boolean by lazy {
        val version = version
        val supported = version != null && VersionComparatorUtil.compare(version, MIN_VERSION) >= 0
        if (!supported) log.warn("IdeaVim $version is older than $MIN_VERSION; Org Mode's IdeaVim mappings stay off")
        supported
    }

    /**
     * Runs [block], which calls IdeaVim, and returns [fallback] if this IdeaVim lacks something it uses (a
     * [LinkageError] such as [NoSuchMethodError]). Logged once as a warning, so an incompatible IdeaVim never
     * raises IDE errors on every key.
     */
    inline fun <T> call(fallback: T, block: () -> T): T =
        try {
            block()
        } catch (e: LinkageError) {
            reportIncompatible(e)
            fallback
        }

    fun reportIncompatible(e: LinkageError) {
        if (reported) return
        reported = true
        log.warn("IdeaVim $version is incompatible with Org Mode's IdeaVim mappings", e)
    }
}
