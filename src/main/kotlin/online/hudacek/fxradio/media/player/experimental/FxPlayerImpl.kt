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
package online.hudacek.fxradio.media.player.experimental

import javafx.application.Platform
import javafx.scene.media.Media
import mu.KotlinLogging
import online.hudacek.fxradio.media.MediaPlayer
import online.hudacek.fxradio.ui.util.msgFormat
import online.hudacek.fxradio.viewmodel.PlayerState
import online.hudacek.fxradio.viewmodel.PlayerViewModel
import tornadofx.FX
import tornadofx.find
import tornadofx.get
import tornadofx.onChange
import javafx.scene.media.MediaPlayer as JFXMediaPlayer

private val logger = KotlinLogging.logger {}

/**
 * Player using the native JavaFX media framework (MP3, AAC, HLS).
 * Formats like Ogg/Opus/FLAC are not supported, use VLC for those.
 */
class FxPlayerImpl(override val playerType: MediaPlayer.Type = MediaPlayer.Type.FX) : MediaPlayer {

    private var jfxPlayer: JFXMediaPlayer? = null
    private var fxVolume: Double = 1.0
    private val metaDataService by lazy { IcyMetaDataService() }

    override fun play(streamUrl: String) {
        stop()
        runCatching {
            jfxPlayer = createPlayer(Media(streamUrl))
            runOnFx { metaDataService.restartFor(streamUrl) }
        }.onFailure {
            logger.error(it) { "Exception when playing stream!" }
            showError(it.localizedMessage ?: it.toString())
        }
    }

    override fun changeVolume(newVolume: Double) {
        // Recalculates the app volume scale to the JavaFX range 0.0 - 1.0
        fxVolume = if (newVolume < -34.5) 0.0 else ((newVolume + 40) / 100).coerceIn(0.0, 1.0)
        jfxPlayer?.volume = fxVolume
    }

    override fun stop() {
        runOnFx { metaDataService.cancel() }
        jfxPlayer?.let {
            it.stop()
            it.dispose()
        }
        jfxPlayer = null
    }

    override fun release() {
        logger.info { "Releasing JavaFX player..." }
        stop()
    }

    private fun createPlayer(media: Media) = JFXMediaPlayer(media).apply {
        logger.debug { "Requested new player for ${media.source}" }

        volume = fxVolume
        cycleCount = JFXMediaPlayer.INDEFINITE
        isAutoPlay = true

        errorProperty().onChange {
            showError(it?.localizedMessage ?: "Unknown error")
        }
    }

    private fun showError(message: String) = Platform.runLater {
        val errorMessage = FX.messages["player.streamError"].msgFormat(message)
        find<PlayerViewModel>().stateProperty.value = PlayerState.Error(errorMessage)
    }

    private fun runOnFx(block: () -> Unit) =
        if (Platform.isFxApplicationThread()) block() else Platform.runLater(block)
}