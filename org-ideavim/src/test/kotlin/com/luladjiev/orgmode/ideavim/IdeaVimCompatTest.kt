package com.luladjiev.orgmode.ideavim

import org.junit.Assert.assertEquals
import org.junit.Test

/** An IdeaVim that lacks an API the adapter calls degrades to a fallback instead of failing. */
class IdeaVimCompatTest {
    @Test
    fun linkageErrorsBecomeTheFallback() {
        assertEquals("fallback", IdeaVimCompat.call("fallback") { throw NoSuchMethodError("removed") })
        assertEquals("fallback", IdeaVimCompat.call("fallback") { throw NoClassDefFoundError("removed") })
    }

    @Test
    fun resultsPassThrough() {
        assertEquals(42, IdeaVimCompat.call(0) { 42 })
    }
}
