/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.lyrics.PrepareLyricsUseCase
import moe.rukamori.archivetune.lyrics.PreparedLyrics
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface LyricsRenderScreenState {
    data object Loading : LyricsRenderScreenState

    @Immutable
    data class Success(
        val lyrics: PreparedLyrics,
    ) : LyricsRenderScreenState

    data object Empty : LyricsRenderScreenState

    @Immutable
    data class Error(
        val reason: LyricsRenderError,
    ) : LyricsRenderScreenState
}

enum class LyricsRenderError {
    INVALID_LYRICS,
}

@HiltViewModel
class LyricsRenderViewModel
    @Inject
    constructor(
        private val prepareLyrics: PrepareLyricsUseCase,
        private val lyricsHelper: LyricsHelper,
        private val database: MusicDatabase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<LyricsRenderScreenState>(LyricsRenderScreenState.Loading)
        val state: StateFlow<LyricsRenderScreenState> = _state.asStateFlow()

        private var observationJob: Job? = null
        private var fetchJob: Job? = null
        private var binding: Binding? = null

        /** 取得を投げた曲。画面の再構成で二重に取りに行かせない。 */
        private val fetchedMediaIds = mutableSetOf<String>()

        /**
         * 表示中の曲の歌詞を、端末に無ければ取りに行く。
         *
         * 演奏側（`MusicService`）の取得は「歌詞を表示する」設定が入っているときだけ
         * 走る。その設定が既定のままの端末では行が永遠に作られず、この画面が
         * 読み込みのまま止まっていた。ここで画面側からも1曲につき1回だけ
         * 取得を投げ、行が作られ次第 [bind] の観測が拾って表示できるようにする。
         *
         * 取得そのものは既存の [LyricsHelper] に任せ、解析は [PrepareLyricsUseCase]
         * に任せる。ここは「取得のきっかけ」だけを足す。
         */
        fun ensureLyrics(mediaMetadata: MediaMetadata) {
            val mediaId = mediaMetadata.id
            if (mediaId.isBlank() || mediaMetadata.isPodcast) return
            if (!fetchedMediaIds.add(mediaId)) return

            fetchJob?.cancel()
            fetchJob =
                viewModelScope.launch(Dispatchers.IO) {
                    if (database.getLyricsById(mediaId) != null) return@launch
                    val lyrics =
                        try {
                            lyricsHelper.getLyrics(mediaMetadata)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (throwable: Throwable) {
                            reportException(throwable)
                            // 取りに行けなかった。行が無いままだと画面が読み込みで
                            // 止まるので、「見つからなかった」として1行だけ残す。
                            database.query {
                                insertLyricsIfAbsent(id = mediaId, lyrics = LYRICS_NOT_FOUND)
                            }
                            return@launch
                        }
                    database.query {
                        insertLyricsIfAbsent(id = mediaId, lyrics = lyrics)
                    }
                }
        }

        fun bind(
            mediaId: String,
            durationMs: Long,
        ) {
            val nextBinding = Binding(mediaId, durationMs.coerceAtLeast(0L))
            if (binding == nextBinding) return

            val mediaChanged = binding?.mediaId != mediaId
            binding = nextBinding
            observationJob?.cancel()
            if (mediaChanged) _state.value = LyricsRenderScreenState.Loading
            observationJob =
                viewModelScope.launch {
                    prepareLyrics.observe(nextBinding.mediaId, nextBinding.durationMs).collect { result ->
                        _state.value =
                            result.fold(
                                onSuccess = { lyrics ->
                                    when {
                                        lyrics == null -> LyricsRenderScreenState.Loading
                                        lyrics.lines.isEmpty() -> LyricsRenderScreenState.Empty
                                        else -> LyricsRenderScreenState.Success(lyrics)
                                    }
                                },
                                onFailure = { throwable ->
                                    if (throwable is CancellationException) throw throwable
                                    reportException(throwable)
                                    LyricsRenderScreenState.Error(LyricsRenderError.INVALID_LYRICS)
                                },
                            )
                    }
                }
        }

        private data class Binding(
            val mediaId: String,
            val durationMs: Long,
        )
    }
