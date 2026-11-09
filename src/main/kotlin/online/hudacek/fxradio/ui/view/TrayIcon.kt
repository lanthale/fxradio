/*
 *     FXRadio - Internet radio directory
 *     Copyright (C) 2020  hudacek.online
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Affero General Public License as
 *     published by the Free Software Foundation, either version 3 of the
 *     License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU Affero General Public License for more details.
 *
 *     You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package online.hudacek.fxradio.ui.view

import javafx.application.Platform
import javafx.collections.ListChangeListener
import online.hudacek.fxradio.Config
import online.hudacek.fxradio.FxRadio
import online.hudacek.fxradio.apiclient.radiobrowser.model.Station
import online.hudacek.fxradio.util.toObservable
import online.hudacek.fxradio.viewmodel.FavouritesViewModel
import online.hudacek.fxradio.viewmodel.PlayerState
import online.hudacek.fxradio.viewmodel.PlayerViewModel
import online.hudacek.fxradio.viewmodel.PreferencesViewModel
import online.hudacek.fxradio.viewmodel.SelectedStation
import online.hudacek.fxradio.viewmodel.SelectedStationViewModel
import tornadofx.Controller
import tornadofx.get
import java.awt.Menu
import java.awt.MenuItem
import java.awt.SystemTray.getSystemTray
import java.awt.TrayIcon
import javax.swing.SwingUtilities

/**
 * Manages system tray icon
 */
class TrayIcon : Controller() {

    private val playerViewModel: PlayerViewModel by inject()
    private val selectedStationViewModel: SelectedStationViewModel by inject()
    private val preferencesViewModel: PreferencesViewModel by inject()
    private val favouritesViewModel: FavouritesViewModel by inject()

    private var trayIcon: TrayIcon? = null

    private var favouritesMenu: Menu? = null

    private val favouritesListener = ListChangeListener<Station> { refreshFavouritesMenu() }

    private val showIconObservable = preferencesViewModel.useTrayIconProperty.toObservable()

    private fun createIcon() = with(app) {
        trayicon(resources.stream(Config.Resources.APP_TRAY_ICON), tooltip = FxRadio.APP_NAME, autoSize = true) {
            trayIcon = this

            setOnMouseClicked(fxThread = true) {
                showPrimaryStage()
            }

            menu(FxRadio.APP_NAME) {
                item(messages["show"] + " " + FxRadio.APP_NAME) {
                    setOnAction(fxThread = true) {
                        showPrimaryStage()
                    }
                }
                addSeparator()

                item(selectedStationViewModel.nameProperty.value) {
                    selectedStationViewModel.stationObservable.subscribe {
                        label = it.name
                    }
                    isEnabled = false
                }

                item(messages["menu.player.toggle"]) {
                    playerViewModel.stateObservable.subscribe {
                        label = if (it is PlayerState.Playing) {
                            messages["menu.player.stop"]
                        } else {
                            messages["menu.player.start"]
                        }
                    }
                    setOnAction(fxThread = true) {
                        playerViewModel.togglePlayerState()
                    }
                }
                addSeparator()

                favouritesMenu = Menu(messages["favourites"]).also { add(it) }
                Platform.runLater {
                    favouritesViewModel.stationsProperty.addListener(favouritesListener)
                    refreshFavouritesMenu()
                }

                addSeparator()
                item(messages["exit"]) {
                    setOnAction(fxThread = true) {
                        Platform.exit()
                    }
                }
            }
        }
    }

    /**
     * Rebuilds the favourites submenu, showing at most [MAX_FAVOURITES] stations
     */
    private fun refreshFavouritesMenu() {
        // Take snapshot on FX thread, modify AWT menu on Swing thread
        val stations = favouritesViewModel.stationsProperty.value?.take(MAX_FAVOURITES) ?: emptyList()
        SwingUtilities.invokeLater {
            val menu = favouritesMenu ?: return@invokeLater
            menu.removeAll()
            stations.forEach { station ->
                menu.add(MenuItem(station.name).apply {
                    addActionListener {
                        Platform.runLater {
                            selectedStationViewModel.item = SelectedStation(station)
                        }
                    }
                })
            }
            menu.isEnabled = stations.isNotEmpty()
        }
    }

    private fun removeIcon() {
        Platform.setImplicitExit(true)
        favouritesViewModel.stationsProperty.removeListener(favouritesListener)
        SwingUtilities.invokeLater {
            favouritesMenu = null
            trayIcon?.let { getSystemTray().remove(it) }
        }
    }

    private fun showPrimaryStage() {
        primaryStage.show()
        primaryStage.toFront()
    }

    fun subscribe() = showIconObservable.subscribe {
        if (it) {
            createIcon()
        } else {
            removeIcon()
        }
    }

    companion object {
        private const val MAX_FAVOURITES = 10
    }
}
