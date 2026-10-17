/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.extensions.currentMetadata
import moe.rukamori.archivetune.extensions.getCurrentQueueIndex
import moe.rukamori.archivetune.extensions.getQueueWindows
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.MusicService.MusicBinder
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.canvas.CanvasPlaybackRequest
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.utils.reportException
import java.util.Locale

internal enum class CanvasArtworkRefetchResult {
    Success,
    Failure,
    AlreadyRunning,
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    val service = binder.service
    val player = service.player
    val localPlayer = service.localPlayer

    val playbackState = MutableStateFlow(player.playbackState)
    private val _isPlaying = MutableStateFlow(player.isPlaying)
    val isPlaying = _isPlaying.asStateFlow()
    val playbackParameters = MutableStateFlow(player.playbackParameters)
    val mediaMetadata = service.currentMediaMetadata
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.lyrics(mediaMetadata?.id)
        }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val aodModeEnabled = MutableStateFlow(false)

    val error = MutableStateFlow<PlaybackException?>(null)
    private var dismissedPlaybackError: PlaybackException? = null
    val waitingForNetworkConnection = service.waitingForNetworkConnection
    val queueRestoreCompleted = service.queueRestoreCompleted

    internal val canvasNetworkAllowed = service.canvasPlaybackUseCase.policy
        .map { it.networkAllowed }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), false)

    private val canvasArtworkRefetchMutex = Mutex()
    private val _isCanvasArtworkRefetching = MutableStateFlow(false)
    internal val isCanvasArtworkRefetching = _isCanvasArtworkRefetching.asStateFlow()

    private var metadataExtractionJob: Job? = null

    init {
        player.addListener(this)

        playbackState.value = player.playbackState
        _isPlaying.value = player.isPlaying
        playbackParameters.value = player.playbackParameters
        queueTitle.value = service.queueTitle
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        currentMediaItemIndex.value = player.currentMediaItemIndex
        shuffleModeEnabled.value = player.shuffleModeEnabled
        repeatMode.value = player.repeatMode
        if (player.mediaItemCount > 0 && service.currentMediaMetadata.value == null) {
            service.currentMediaMetadata.value = player.currentMetadata
        }

        metadataExtractionJob =
            scope.launch(Dispatchers.IO) {
                mediaMetadata
                    .distinctUntilChangedBy { it?.id }
                    .collectLatest { metadata ->
                        val mediaId = metadata?.id ?: return@collectLatest
                        if (mediaId.isLocalMediaId()) {
                            val storedFormat = database.format(mediaId).first()
                            if (storedFormat != null && storedFormat.bitrate == 0 && storedFormat.sampleRate == null) {
                                val result =
                                    extractLocalAudioProperties(context, mediaId)
                                        ?: return@collectLatest
                                ensureActive()
                                val finalBitrate =
                                    if (result.first <= 0 && result.second == null) {
                                        -1
                                    } else {
                                        result.first
                                    }
                                database.updateLocalAudioMetadata(mediaId, finalBitrate, result.second)
                            }
                        }
                    }
            }
    }

    private suspend fun extractLocalAudioProperties(
        context: Context,
        uriString: String,
    ): Pair<Int, Int?>? =
        withContext(Dispatchers.IO) {
            val extractor = android.media.MediaExtractor()
            var bitrate = 0
            var sampleRate: Int? = null
            try {
                val uri = android.net.Uri.parse(uriString)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) {
                    timber.log.Timber
                        .tag("LocalMetadataExtractor")
                        .w("Could not open file descriptor for %s", uriString)
                    return@withContext null
                }
                pfd.use { descriptor ->
                    extractor.setDataSource(descriptor.fileDescriptor)
                    if (extractor.trackCount == 0) {
                        return@withContext Pair(-1, null)
                    }
                    var foundAudioTrack = false
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            foundAudioTrack = true
                            if (format.containsKey(android.media.MediaFormat.KEY_BIT_RATE)) {
                                bitrate = format.getInteger(android.media.MediaFormat.KEY_BIT_RATE)
                            }
                            if (format.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                                sampleRate = format.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
                            }
                            break
                        }
                    }
                    if (!foundAudioTrack) {
                        return@withContext Pair(-1, null)
                    }
                }
                Pair(bitrate, sampleRate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                timber.log.Timber
                    .tag("LocalMetadataExtractor")
                    .w(e, "Permission denied extracting metadata for %s", uriString)
                null
            } catch (e: java.io.FileNotFoundException) {
                timber.log.Timber
                    .tag("LocalMetadataExtractor")
                    .w(e, "File not found for %s", uriString)
                null
            } catch (e: java.io.IOException) {
                val message = e.message?.lowercase() ?: ""
                if ("unsupported" in message || "malformed" in message || "invalid" in message || "failed to instantiate" in message) {
                    timber.log.Timber
                        .tag("LocalMetadataExtractor")
                        .w(e, "Confirmed unsupported file %s", uriString)
                    Pair(-1, null)
                } else {
                    timber.log.Timber
                        .tag("LocalMetadataExtractor")
                        .w(e, "Transient I/O error extracting metadata for %s", uriString)
                    null
                }
            } catch (e: Exception) {
                timber.log.Timber
                    .tag("LocalMetadataExtractor")
                    .w(e, "Unexpected error extracting metadata for %s", uriString)
                null
            } finally {
                runCatching { extractor.release() }
            }
        }

    fun playQueue(queue: Queue) {
        service.playQueue(queue)
    }

    fun startRadioSeamlessly() {
        service.startRadioSeamlessly()
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        service.playNext(items)
    }

    fun moveQueueItemToNext(mediaItemIndex: Int) {
        service.moveQueueItemToNext(mediaItemIndex)
    }

    /**
     * 表示中のキュー順を入れ替える。
     *
     * [queueWindows] は再生順に並んだ写しなので、そこから取り出した番号をそのまま
     * [androidx.media3.Player.moveMediaItem] に渡せない。タイムラインを引き直して
     * 先頭 period 番号が一致するメディア項目を探してから動かす。
     *
     * シャッフル中はタイムライン順がそのまま再生順にならないため、単純に移動すると
     * 画面の並びが変わらない。そのときは**表示順のまま**並べ替えた配列を
     * シャッフル順へ書き直す（先頭は再生中なので、曲が飛ぶことはない）。
     *
     * @param sourceWindow 並べ替え元。
     * @param targetWindow 並べ替え先（ここへ移動する）。
     */
    @OptIn(UnstableApi::class)
    fun moveQueueItem(
        sourceWindow: Timeline.Window,
        targetWindow: Timeline.Window,
    ) {
        if (player.mediaItemCount < 2 || player.currentMediaItemIndex < 0) return

        val sourceIndex = mediaItemIndexOf(sourceWindow) ?: return
        val targetIndex = mediaItemIndexOf(targetWindow) ?: return
        if (sourceIndex == targetIndex) return

        if (!player.shuffleModeEnabled) {
            player.moveMediaItem(sourceIndex, targetIndex)
            return
        }

        val mediaIndexByPeriod = HashMap<Int, Int>()
        for (index in 0 until player.mediaItemCount) {
            val window = player.currentTimeline.getWindow(index, Timeline.Window())
            mediaIndexByPeriod[window.firstPeriodIndex] = index
        }
        val order = player.getQueueWindows().mapNotNull { mediaIndexByPeriod[it.firstPeriodIndex] }
        if (order.size != player.mediaItemCount) return

        val fromPosition = order.indexOf(sourceIndex)
        val toPosition = order.indexOf(targetIndex)
        if (fromPosition < 0 || toPosition < 0 || fromPosition == toPosition) return

        val moved =
            order
                .toMutableList()
                .apply { add(toPosition, removeAt(fromPosition)) }
        localPlayer.setShuffleOrder(DefaultShuffleOrder(moved.toIntArray(), System.currentTimeMillis()))
    }

    /** [window] に当たるメディア項目番号。無ければ `null`。 */
    private fun mediaItemIndexOf(window: Timeline.Window): Int? {
        if (player.currentTimeline.isEmpty) return null
        for (index in 0 until player.mediaItemCount) {
            val candidate = player.currentTimeline.getWindow(index, Timeline.Window())
            if (candidate.firstPeriodIndex == window.firstPeriodIndex) return index
        }
        return null
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        service.addToQueue(items)
    }

    fun playFromVoiceSearch(query: String) {
        service.playFromVoiceSearch(query)
    }

    fun toggleLike() {
        service.toggleLike()
    }

    internal suspend fun refetchCanvasArtwork(
        metadata: MediaMetadata,
        requireVertical: Boolean,
    ): CanvasArtworkRefetchResult {
        if (!canvasArtworkRefetchMutex.tryLock()) return CanvasArtworkRefetchResult.AlreadyRunning

        _isCanvasArtworkRefetching.value = true
        return try {
            val country = Locale.getDefault().country
            val storefront = if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
            val refreshed = service.canvasPlaybackUseCase.refresh(
                CanvasPlaybackRequest(
                    mediaId = metadata.id,
                    title = metadata.title,
                    artist = metadata.artists.firstOrNull()?.name.orEmpty(),
                    storefront = storefront,
                    requireVertical = requireVertical,
                ),
            )
            if (!refreshed) return CanvasArtworkRefetchResult.Failure

            CanvasArtworkRefetchResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            timber.log.Timber.tag("CanvasArtwork").w(error, "Canvas refetch failed for %s", metadata.id)
            CanvasArtworkRefetchResult.Failure
        } finally {
            _isCanvasArtworkRefetching.value = false
            canvasArtworkRefetchMutex.unlock()
        }
    }

    fun dismissPlaybackError() {
        dismissedPlaybackError = error.value ?: player.playerError
        error.value = null
    }

    fun seekToNext() {
        val state = service.togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (state?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            service.requestTogetherControl(moe.rukamori.archivetune.together.ControlAction.SkipNext)
            return
        }
        player.seekToNext()
        player.prepare()
        player.playWhenReady = true
    }

    fun seekToPrevious() {
        val state = service.togetherSessionState.value as? moe.rukamori.archivetune.together.TogetherSessionState.Joined
        if (state?.role is moe.rukamori.archivetune.together.TogetherRole.Guest) {
            service.requestTogetherControl(moe.rukamori.archivetune.together.ControlAction.SkipPrevious)
            return
        }
        player.seekToPrevious()
        player.prepare()
        player.playWhenReady = true
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        updatePlaybackError(player.playerError)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _isPlaying.value = isPlaying
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        this.playbackParameters.value = playbackParameters
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        updatePlaybackError(playbackError)
    }

    private fun updatePlaybackError(playbackError: PlaybackException?) {
        when {
            playbackError == null -> {
                dismissedPlaybackError = null
                error.value = null
            }

            playbackError !== dismissedPlaybackError -> {
                dismissedPlaybackError = null
                error.value = playbackError
            }
        }
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                !window.isLive ||
                player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                window.isDynamic ||
                player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        player.removeListener(this)
        metadataExtractionJob?.cancel()
        metadataExtractionJob = null
    }
}
