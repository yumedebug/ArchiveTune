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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface RadioUiState {
    data object Loading : RadioUiState

    data class Success(
        val sections: List<RadioSectionUiModel>,
    ) : RadioUiState

    data object Empty : RadioUiState

    data object Error : RadioUiState
}

/**
 * カラータイル1枚。
 *
 * [stripeColor] は YouTube Music が返す素の色値（`Long`）。アプリ側で
 * 好みの色に置き換えず、そのままタイルの下地に使う。
 */
@Immutable
data class RadioTileUiModel(
    val title: String,
    val stripeColor: Long,
    val browseId: String,
    /**
     * どのカテゴリかを決める追加パラメータ。
     *
     * YouTube Music のムード／ジャンルのタイルは `browseId` が全タイル共通
     * （`FEmusic_moods_and_genres_category`）で、区別は `params` だけが持つ。
     * 落とすと browse が 404 になる。
     */
    val params: String?,
)

@Immutable
data class RadioSectionUiModel(
    val title: String,
    val tiles: List<RadioTileUiModel>,
)

/**
 * Radio タブの ViewModel。
 *
 * YouTube Music には放送局カタログが無く、`RD` 始まりの局 ID も API からは
 * 返らない（局はシード曲からの自動生成でしか作れない）。そこで本画面では
 * **ムードとジャンル**のセクションを出し、それぞれのタイルを局生成ページ
 * （Browse）へ送る形にしている。
 *
 * init で自動ロードし、[retry] で取り直す。
 */
@HiltViewModel
class RadioViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow<RadioUiState>(RadioUiState.Loading)
        val uiState = _uiState.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() {
            load()
        }

        private fun load() {
            loadJob?.cancel()
            loadJob =
                viewModelScope.launch(Dispatchers.IO) {
                    _uiState.value = RadioUiState.Loading
                    try {
                        val sections =
                            YouTube
                                .moodAndGenres()
                                .getOrThrow()
                                .mapNotNull(::toSectionUiModel)
                                .filter { it.tiles.isNotEmpty() }

                        _uiState.value =
                            if (sections.isEmpty()) {
                                RadioUiState.Empty
                            } else {
                                RadioUiState.Success(sections)
                            }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        _uiState.value = RadioUiState.Error
                    }
                }
        }

        private fun toSectionUiModel(section: MoodAndGenres): RadioSectionUiModel? {
            if (section.title.isBlank()) return null
            return RadioSectionUiModel(
                title = section.title,
                tiles =
                    section.items
                        .filter { it.title.isNotBlank() && it.endpoint.browseId.isNotBlank() }
                        .map { item ->
                            RadioTileUiModel(
                                title = item.title,
                                stripeColor = item.stripeColor,
                                browseId = item.endpoint.browseId,
                                params = item.endpoint.params,
                            )
                        },
            )
        }
    }
