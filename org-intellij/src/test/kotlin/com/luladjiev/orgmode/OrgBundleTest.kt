package com.luladjiev.orgmode

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.util.ResourceBundle

class OrgBundleTest : BasePlatformTestCase() {
    /**
     * The bundle turns a bare `&` into a mnemonic marker, which breaks HTML entities such as `&lt;` (shown as `lt;`);
     * a literal `&` is written `\\&` in the properties file.
     */
    fun testNoMessageHasAStrayMnemonic() {
        for (key in ResourceBundle.getBundle("messages.OrgBundle").keySet()) {
            val message = OrgBundle.message(key)
            assertFalse("$key: $message", message.contains('\u001B'))
        }
    }
}
