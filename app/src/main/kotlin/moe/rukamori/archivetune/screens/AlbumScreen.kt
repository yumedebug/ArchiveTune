/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.viewmodels.AlbumUiState
import moe.rukamori.archivetune.viewmodels.AlbumViewModel

/**
 * アルバム詳細。
 *
 * [AlbumViewModel] が init で取得を始め、失敗時は [AlbumViewModel.retry] で取り直す。
 * 再生は既存UIと同じく [LocalAlbumRadio] を使う（端末内の曲を先に並べ、尽きたら
 * YouTube Music から続きを補う）。楽曲一覧そのものは [AlbumWithSongs.songs] から読み、
 * 画面側で並べ替えたり作り直したりはしない。
 *
 * 見出し部分は [ImmersiveDetailScaffold]（上端いっぱいのアートワーク＋抽出色の背景）で、
 * 曲一覧はその下へ同じスクロールとして続けられる。
 *
 * @param onBack 戻る矢印。
 * @param onOpenArtist アーティスト名を押したときの行き先。
 */
@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AlbumViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val uiState by viewModel.uiState.collectAsState()
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()

    val stateModifier = Modifier.fillMaxSize().windowInsetsPadding(windowInsets)

    when (uiState) {
        AlbumUiState.Loading -> LoadingState(stateModifier)

        AlbumUiState.Empty ->
            EmptyState(
                title = stringResource(R.string.albums),
                message = stringResource(R.string.no_results_found),
                modifier = stateModifier,
            )

        is AlbumUiState.Error ->
            ErrorState(
                title = stringResource(R.string.albums),
                message = stringResource(R.string.no_results_found),
                onRetry = { viewModel.retry() },
                modifier = stateModifier,
            )

        AlbumUiState.Success -> {
            val current = albumWithSongs
            if (current == null) {
                LoadingState(stateModifier)
            } else {
                val conn = playerConnection
                val mediaItems = current.songs.map { it.toMediaItem() }
                val firstSong = current.songs.firstOrNull()
                val tracksLabel = stringResource(R.string.tracks_label)
                val metadata =
                    listOfNotNull(
                        current.album.year?.toString(),
                        current.songs.size.takeIf { it > 0 }?.let { "$it $tracksLabel" },
                    ).joinToString(" · ")

                val menuActions = mutableListOf<DetailMenuAction>()
                if (conn != null && mediaItems.isNotEmpty()) {
                    menuActions +=
                        DetailMenuAction(stringResource(R.string.play_next)) {
                            conn.playNext(mediaItems)
                        }
                    firstSong?.let { seed ->
                        menuActions +=
                            DetailMenuAction(stringResource(R.string.start_radio)) {
                                conn.playRadioFrom(seed.toMediaMetadata())
                            }
                    }
                }

                ImmersiveDetailScaffold(
                    title = current.album.title,
                    thumbnailUrl = firstSong?.thumbnailUrl,
                    backdropKey = current.album.id,
                    onBack = onBack,
                    subtitle = current.artists.joinToString(", ") { it.name },
                    metadata = metadata.takeIf { it.isNotBlank() },
                    onPlay = conn?.let { c -> { c.playQueue(LocalAlbumRadio(current)) } },
                    onShuffle =
                        conn?.let { c ->
                            {
                                // playQueue はキュー開始時にシャッフルを一度下げるため、
                                // 起動させたあとに立てて残りを並べ替える。
                                c.playQueue(LocalAlbumRadio(current))
                                c.player.shuffleModeEnabled = true
                            }
                        },
                    onEnqueue = conn?.let { c -> { c.addToQueue(mediaItems) } },
                    menuActions = menuActions,
                ) {
                    itemsIndexed(items = current.songs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song.toMediaMetadata(),
                            // Apple Music のアルバム一覧と同じく、左端に1始まりのトラック番号を出す。
                            index = index + 1,
                            onClick = {
                                // 行を押したときはその曲を先頭にして、同じ一覧を渡す。
                                conn?.playQueue(
                                    LocalAlbumRadio(current, startIndex = index),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
