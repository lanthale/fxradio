package online.hudacek.fxradio.media

import mu.KotlinLogging
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

/**
 * Creates the real player on a background thread so that slow native
 * initialization (e.g. VLC plugin scanning) does not block the UI thread.
 * All calls are queued and executed in order on the same thread.
 */
class DeferredMediaPlayer(
    private val requestedType: MediaPlayer.Type,
    create: () -> MediaPlayer
) : MediaPlayer {

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "player-init").apply { isDaemon = true }
    }

    @Volatile
    private var delegate: MediaPlayer? = null

    override val playerType: MediaPlayer.Type
        get() = delegate?.playerType ?: requestedType

    init {
        executor.execute {
            runCatching { delegate = create() }
                .onFailure { logger.error(it) { "Player initialization failed" } }
        }
    }

    override fun play(streamUrl: String) = executor.execute { delegate?.play(streamUrl) }
    override fun changeVolume(newVolume: Double) = executor.execute { delegate?.changeVolume(newVolume) }
    override fun stop() = executor.execute { delegate?.stop() }

    override fun release() {
        executor.execute { delegate?.release() }
        executor.shutdown()
    }
}