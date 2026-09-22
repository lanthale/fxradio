package online.hudacek.fxradio.ui.view.stations

import javafx.collections.ListChangeListener
import javafx.geometry.Pos
import javafx.scene.input.DragEvent
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseEvent
import javafx.scene.layout.FlowPane
import javafx.scene.layout.VBox
import javafx.util.Duration
import online.hudacek.fxradio.apiclient.radiobrowser.model.Station
import online.hudacek.fxradio.apiclient.radiobrowser.model.description
import online.hudacek.fxradio.ui.BaseView
import online.hudacek.fxradio.ui.menu.platformContextMenu
import online.hudacek.fxradio.ui.util.DataCellHandler
import online.hudacek.fxradio.ui.util.make
import online.hudacek.fxradio.ui.util.showWhen
import online.hudacek.fxradio.ui.util.smallLabel
import online.hudacek.fxradio.ui.util.stationView
import online.hudacek.fxradio.viewmodel.FavouritesViewModel
import online.hudacek.fxradio.viewmodel.SelectedStation
import online.hudacek.fxradio.viewmodel.SelectedStationViewModel
import online.hudacek.fxradio.viewmodel.StationsState
import online.hudacek.fxradio.viewmodel.StationsViewModel
import org.controlsfx.glyphfont.FontAwesome
import tornadofx.action
import tornadofx.addClass
import tornadofx.booleanBinding
import tornadofx.get
import tornadofx.item
import tornadofx.label
import tornadofx.move
import tornadofx.onHover
import tornadofx.onLeftClick
import tornadofx.paddingAll
import tornadofx.paddingTop
import tornadofx.point
import tornadofx.putString
import tornadofx.px
import tornadofx.scale
import tornadofx.scrollpane
import tornadofx.separator
import tornadofx.stringBinding
import tornadofx.style
import tornadofx.tooltip

private const val CELL_WIDTH = 140.0
private const val LOGO_SIZE = 100.0
private const val VERIFIED_ICON_SIZE = 13.0

/**
 * Main view of stations.
 * Shows radio station logo and name as a wrapping grid of cards.
 *
 * NOTE: This replaces the former tornadofx.DataGrid-based implementation.
 * DataGrid's skin relies on the internal class
 * com.sun.javafx.scene.control.skin.VirtualContainerBase, which was moved
 * to the public javafx.scene.control.skin package back in JavaFX 9 and
 * was never fixed upstream (TornadoFX is unmaintained/archived).
 * A plain ScrollPane + FlowPane avoids that dependency entirely.
 */
class StationsDataGridView : BaseView() {

    private val selectedStationViewModel: SelectedStationViewModel by inject()
    private val stationsViewModel: StationsViewModel by inject()
    private val favouritesViewModel: FavouritesViewModel by inject()

    private val cardNodes = mutableMapOf<Station, VBox>()

    private val flow = FlowPane().apply {
        hgap = 8.0
        vgap = 8.0
        styleClass.add("datagrid") // keep old CSS (Styles.kt / StylesDark.kt) applying
    }

    override val root = scrollpane(fitToWidth = true) {
        id = "stations"
        isFocusTraversable = true
        content = flow

        stationsViewModel.stationsProperty.addListener(ListChangeListener { rebuildCards() })
        rebuildCards()

        // Keyboard navigation (replaces DataGridHandler)
        setOnKeyPressed { event ->
            val stations = stationsViewModel.stationsProperty
            if (stations.isEmpty()) return@setOnKeyPressed

            val currentIndex = stations.indexOf(selectedStationViewModel.item.station)
            when (event.code) {
                KeyCode.RIGHT -> {
                    selectStation(stations[(currentIndex + 1).coerceIn(0, stations.size - 1)])
                    event.consume()
                }
                KeyCode.LEFT -> {
                    selectStation(stations[(currentIndex - 1).coerceAtLeast(0)])
                    event.consume()
                }
                KeyCode.TAB -> {
                    if (currentIndex == -1) selectStation(stations[0])
                    event.consume()
                }
                else -> Unit
            }
        }

        // Keep visual selection synced with selection made elsewhere in the app
        selectedStationViewModel.stationObservable.subscribe {
            highlightSelected(it)
        }

        showWhen {
            stationsViewModel.stateProperty.booleanBinding {
                when (it) {
                    is StationsState.Fetched -> true
                    else -> false
                }
            }
        }
    }

    private fun rebuildCards() {
        cardNodes.clear()
        flow.children.setAll(stationsViewModel.stationsProperty.map(::createCard))
        highlightSelected(selectedStationViewModel.item.station)
    }

    private fun createCard(station: Station): VBox {
        val card = VBox().apply {
            alignment = Pos.BOTTOM_CENTER
            prefWidth = CELL_WIDTH
            styleClass.add("datagrid-cell") // keep old CSS applying

            onHover {
                tooltip(station.name)
                if (it) {
                    scale(Duration.seconds(0.07), point(1.05, 1.05))
                } else {
                    scale(Duration.seconds(0.07), point(1.0, 1.0))
                }
            }

            stationView(station, LOGO_SIZE) {
                paddingAll = 5
                subscribe()
            }

            label(station.name) {
                if (station.hasExtendedInfo) {
                    graphic = FontAwesome.Glyph.CHECK_CIRCLE.make(size = VERIFIED_ICON_SIZE)
                }
                paddingTop = 5
                style { fontSize = 12.5.px }
            }
            smallLabel(station.description)

            onLeftClick { selectStation(station) }

            val handler = DataCellHandler(this, station) { dropped, target ->
                stationsViewModel.stationsProperty.move(dropped, stationsViewModel.stationsProperty.indexOf(target))
                favouritesViewModel.commit()
                rebuildCards()
            }
            addEventFilter(MouseEvent.DRAG_DETECTED, handler::onDragDetected)
            addEventFilter(DragEvent.DRAG_OVER, handler::onDragOver)
            addEventFilter(DragEvent.DRAG_ENTERED, handler::onDragEntered)
            addEventFilter(DragEvent.DRAG_EXITED, handler::onDragExited)
            addEventFilter(DragEvent.DRAG_DROPPED, handler::onDragDropped)
            addEventFilter(DragEvent.DRAG_DONE, handler::onDragDone)

            platformContextMenu {
                item(messages["menu.station.favourite"]) {
                    val itemName = favouritesViewModel.stationsProperty.stringBinding { l ->
                        if (l?.contains(station)!!) messages["menu.station.favouriteRemove"]
                        else messages["menu.station.favourite"]
                    }
                    textProperty().bind(itemName)
                    action {
                        with(favouritesViewModel) {
                            if (stationsProperty.contains(station)) removeFavourite(station)
                            else addFavourite(station)
                        }
                    }
                }
                separator()
                item(messages["menu.station.vote"]) {
                    action { appEvent.votedStations.onNext(station) }
                }
                item(messages["copy.streamUrl"]) {
                    action { clipboard.putString(station.urlResolved) }
                }
            }
        }
        cardNodes[station] = card
        return card
    }

    private fun highlightSelected(selected: Station?) {
    cardNodes.forEach { (station, node) ->
        node.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, station == selected)
    }
}

companion object {
    private val SELECTED_PSEUDO_CLASS: javafx.css.PseudoClass =
        javafx.css.PseudoClass.getPseudoClass("selected")
}

    private fun selectStation(station: Station) {
        if (selectedStationViewModel.item.station != station) {
            selectedStationViewModel.item = SelectedStation(station)
        }
    }
}