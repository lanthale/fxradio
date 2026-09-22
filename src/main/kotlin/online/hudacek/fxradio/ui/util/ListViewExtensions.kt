package online.hudacek.fxradio.ui.util

import javafx.scene.Node
import javafx.scene.control.ListCell
import javafx.scene.control.ListView
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent

/**
 * Replacement for tornadofx.onUserSelect, which internally calls isInsideRow()
 * and tries to load the internal, long-gone JavaFX class
 * com.sun.javafx.scene.control.skin.TableColumnHeader (removed since JavaFX 9).
 * This version uses only public JavaFX API (ListCell.isEmpty) to detect
 * whether the click landed on an actual row.
 */
fun <T> ListView<T>.onUserSelectSafe(clickCount: Int = 2, action: (T) -> Unit) {
    addEventFilter(MouseEvent.MOUSE_CLICKED) { event ->
        if (event.button == MouseButton.PRIMARY && event.clickCount == clickCount) {
            var node = event.target as? Node
            while (node != null && node !== this) {
                if (node is ListCell<*> && !node.isEmpty) {
                    @Suppress("UNCHECKED_CAST")
                    (node.item as? T)?.let(action)
                    break
                }
                node = node.parent
            }
        }
    }
}