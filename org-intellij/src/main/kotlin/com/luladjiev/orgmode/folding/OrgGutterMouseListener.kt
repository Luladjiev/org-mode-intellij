package com.luladjiev.orgmode.folding

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.luladjiev.orgmode.lang.OrgFileType
import java.awt.event.InputEvent
import java.awt.event.MouseEvent

/**
 * Makes a gutter arrow on a Heading line fold the whole Subtree ([OrgVisibility.gutterClicked]). Listeners see the
 * press before the editor, which skips its own folding when the event is consumed. Clicks with modifiers keep the
 * platform's meaning.
 */
class OrgGutterMouseListener : EditorMouseListener {
    override fun mousePressed(event: EditorMouseEvent) {
        if (event.area != EditorMouseEventArea.FOLDING_OUTLINE_AREA) return
        val mouse = event.mouseEvent
        if (mouse.button != MouseEvent.BUTTON1 || mouse.clickCount != 1 || mouse.modifiersEx and MODIFIERS != 0) return
        val editor = event.editor
        if (editor.virtualFile?.fileType != OrgFileType) return
        val gutter = editor.gutter as? EditorGutterComponentEx ?: return
        val region = gutter.findFoldingAnchorAt(mouse.x, mouse.y) ?: return
        if (OrgVisibility.gutterClicked(editor, region)) event.consume()
    }

    private companion object {
        const val MODIFIERS = InputEvent.SHIFT_DOWN_MASK or InputEvent.CTRL_DOWN_MASK or InputEvent.ALT_DOWN_MASK or
            InputEvent.META_DOWN_MASK
    }
}
