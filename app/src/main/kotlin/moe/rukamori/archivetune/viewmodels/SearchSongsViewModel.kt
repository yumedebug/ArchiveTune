/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.HideVideoKey
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.innertube.models.filterVideo
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException

/** 検索欄の入力から引くオンライン曲の状態。 */
sealed interface SearchSongsState {
    /** まだ入力が無い。 */
    data object Idle : SearchSongsState

    /** 取得中。 */
    data object Loading : SearchSongsState

    /** 取れた曲。空なら「見つからない」。 */
    data class Success(val songs: List<SongItem>) : SearchSongsState

    /** 取得に失敗。 */
    data class Error(@StringRes val messageResId: Int) : SearchSongsState
}

/**
 * 検索タブの入力から、YouTube Music の曲を引く。
 *
 * 旧UIの [OnlineSearchViewModel] はルート引数からクエリを読むため、検索タブの
 * ように「入力のたびに変わるクエリ」には使えない。ここでは入力そのものを持ち、
 * 打鍵のたびに投げないよう**間引いて**から1回だけ引く。
 *
 * 画面（Composable）から Innertube を直接呼ばないための受け皿でもある。
 * 取得の条件（露骨な表現・動画の除外、AI コンテンツフィルタ）は
 * [OnlineSearchViewModel] と同じ扱いにそろえている。
 */
@HiltViewModel
class SearchSongsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        /** いまの取得結果。 */
        var state: SearchSongsState by mutableStateOf(SearchSongsState.Idle)
            private set

        /** 走っている取得。新しい入力が来たら捨てる。 */
        private var searchJob: Job? = null

        /** 最後に引いたクエリ。同じ文字列で引き直さない。 */
        private var searchedQuery: String? = null

        /**
         * 入力を渡す。
         *
         * 打鍵ごとに呼ばれるので、[SEARCH_DEBOUNCE_MS] 待ってから引く。空文字なら
         * 取得をやめて [SearchSongsState.Idle] に戻す（前の結果を残さない）。
         */
        fun updateQuery(value: String) {
            val trimmed = value.trim()
            searchJob?.cancel()

            if (trimmed.isEmpty()) {
                searchedQuery = null
                state = SearchSongsState.Idle
                return
            }
            if (trimmed == searchedQuery && state is SearchSongsState.Success) return

            // 入力中は前の結果を消さずに読み込み中を出す（一覧が毎回空になると
            // 点滅して読みにくい）。ただし別の語に変わったときだけは消す。
            if (state !is SearchSongsState.Success || searchedQuery != trimmed) {
                state = SearchSongsState.Loading
            }

            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    searchedQuery = trimmed
                    state = search(trimmed)
                }
        }

        private suspend fun search(query: String): SearchSongsState =
            YouTube
                .search(query, FILTER_SONG)
                .fold(
                    onSuccess = { result ->
                        val policy = loadAiContentFilterPolicy()
                        val songs =
                            filterAiContent(
                                result.items
                                    .filterIsInstance<SongItem>()
                                    .distinctBy { it.id }
                                    .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                                    .filterVideo(context.dataStore.get(HideVideoKey, false)),
                                policy,
                            )
                        SearchSongsState.Success(songs)
                    },
                    onFailure = { error ->
                        reportException(error)
                        SearchSongsState.Error(R.string.error_unknown)
                    },
                )
    }

/** 入力を止めてから引くまでの待ち時間。打鍵のたびに投げないための間。 */
private const val SEARCH_DEBOUNCE_MS = 350L
