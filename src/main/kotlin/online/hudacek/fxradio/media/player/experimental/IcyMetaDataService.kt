package online.hudacek.fxradio.media.player.experimental

import javafx.concurrent.ScheduledService
import javafx.concurrent.Task
import javafx.util.Duration
import mu.KotlinLogging
import okhttp3.OkHttpClient
import okhttp3.Request
import online.hudacek.fxradio.event.AppEvent
import online.hudacek.fxradio.media.StreamMetaData
import tornadofx.find
import java.io.DataInputStream
import java.io.EOFException
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Regularly fetches ICY stream metadata (station name, now playing) from [streamUrl].
 * Replacement of the former HumbleMetaDataService.
 */
class IcyMetaDataService(private var streamUrl: String = "") : ScheduledService<StreamMetaData?>() {

    init {
        period = Duration.seconds(55.0)
        delay = Duration.seconds(5.0)
    }

    fun restartFor(streamUrl: String) {
        this.streamUrl = streamUrl
        restart()
    }

    override fun createTask(): Task<StreamMetaData?> = object : Task<StreamMetaData?>() {

        private val appEvent = find<AppEvent>()

        override fun call(): StreamMetaData? {
            require(streamUrl.isNotEmpty()) { "streamUrl should not be empty." }
            return IcyMetaDataReader.read(streamUrl)
        }

        override fun succeeded() {
            value?.let {
                logger.info { "ICY MetaData retrieved: $it" }
                appEvent.streamMetaDataUpdates.onNext(it)
            }
        }

        override fun failed() = logger.error(exception) { "Fetching ICY metadata failed." }
    }
}

object IcyMetaDataReader {

    private const val MAX_BLOCKS = 3
    private val titleRegex = Regex("StreamTitle='(.*?)';", RegexOption.DOT_MATCHES_ALL)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Returns null if the stream offers no ICY metadata. */
    fun read(streamUrl: String): StreamMetaData? {
        val request = Request.Builder()
            .url(streamUrl)
            .header("Icy-MetaData", "1")
            .header("User-Agent", "FXRadio")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val metaInt = response.header("icy-metaint")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
            val stationName = response.header("icy-name").orEmpty()
            val input = DataInputStream(response.body.byteStream())

            // Some servers send an empty block first, so try a few blocks
            repeat(MAX_BLOCKS) {
                input.skipFully(metaInt)
                val blockLength = input.read() * 16
                if (blockLength > 0) {
                    val buffer = ByteArray(blockLength)
                    input.readFully(buffer)
                    val title = titleRegex.find(String(buffer, Charsets.UTF_8))?.groupValues?.get(1)
                    if (!title.isNullOrBlank()) {
                        return StreamMetaData(stationName = stationName, nowPlaying = title)
                    }
                }
            }
            return null
        }
    }

    private fun DataInputStream.skipFully(count: Int) {
        val buffer = ByteArray(4096)
        var remaining = count
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) throw EOFException()
            remaining -= read
        }
    }
}