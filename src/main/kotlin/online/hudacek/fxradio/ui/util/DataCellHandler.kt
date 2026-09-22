package online.hudacek.fxradio.ui.util

import javafx.scene.Node
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.MouseEvent
import javafx.scene.input.TransferMode
import javafx.util.Duration
import mu.KotlinLogging
import online.hudacek.fxradio.apiclient.radiobrowser.model.Station
import online.hudacek.fxradio.viewmodel.LibraryState
import online.hudacek.fxradio.viewmodel.LibraryViewModel
import tornadofx.Component
import tornadofx.point
import tornadofx.put
import tornadofx.scale

private val logger = KotlinLogging.logger {}

/**
 * Basic drag and drop support for a station card (plain JavaFX Node)
 */
class DataCellHandler(
    private val cell: Node,
    private val station: Station,
    private val onReorder: (dropped: Station, target: Station) -> Unit
) : Component() {

    private val libraryViewModel: LibraryViewModel by inject()

    fun onDragDetected(e: MouseEvent) {
        if (!isFavouritesOpen()) return
        cell.startDragAndDrop(TransferMode.MOVE).put(dfStation, station)
        e.consume()
    }

    fun onDragOver(e: DragEvent) {
        if (!isFavouritesOpen()) return
        if (e.gestureSource != cell && e.dragboard.hasContent(dfStation)) {
            e.acceptTransferModes(TransferMode.MOVE)
        }
        e.consume()
    }

    fun onDragEntered(e: DragEvent) {
        if (!isFavouritesOpen()) return
        if (e.gestureSource != cell && e.dragboard.hasContent(dfStation)) {
            if (cell.scaleX == 1.0) cell.scale(scaleDuration, scaleEnterPoint)
        }
        e.consume()
    }

    fun onDragExited(e: DragEvent) {
        logger.trace { "OnDragExited for ${station.uuid}" }
        cell.scale(scaleDuration, scaleExitPoint)
        e.consume()
    }

    fun onDragDropped(e: DragEvent) {
        if (!isFavouritesOpen()) return
        with(e.dragboard) {
            try {
                if (hasContent(dfStation)) {
                    onReorder(getContent(dfStation) as Station, station)
                    clear()
                }
            } finally {
                e.isDropCompleted = true
            }
        }
        e.consume()
    }

    fun onDragDone(e: DragEvent) {
        e.consume()
    }

    private fun isFavouritesOpen() = libraryViewModel.stateProperty.value == LibraryState.Favourites

    companion object {
        private val dfStation = DataFormat("application/x-station")
        private val scaleDuration = Duration.seconds(0.05)
        private val scaleEnterPoint = point(0.9, 0.9)
        private val scaleExitPoint = point(1.0, 1.0)
    }
}