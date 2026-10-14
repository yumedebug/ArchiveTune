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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.components.EmptyRow
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.viewmodels.LocalPlaylistViewModel

/**
 * 端末内のプレイリスト詳細。
 *
 * [LocalPlaylistViewModel] が init で再生順の並びを整えたうえで曲を流すので、
 * 画面は `playlistSongs` を読むだけ。並べ替えや削除は既存のDB経路を介するため、
 * ここでは再生と表示に絞る。
 *
 * 再生は [ListQueue]（端末内の曲をそのまま並べる）。オンライン曲をシードにした
 * ラジオ生成とは別の経路なので混ぜない。
 *
 * 見出し部分は [ImmersiveDetailScaffold]（上端いっぱいのアートワーク＋抽出色の背景）を使う。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun LocalPlaylistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocalPlaylistViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val context = LocalContext.current

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()

    val stateModifier = Modifier.fillMaxSize().windowInsetsPadding(windowInsets)

    // `!!` を避けるためローカルへ落とす。これで以降はスマートキャストが効く。
    val current = playlist

    if (current == null) {
        LoadingState(stateModifier)
        return
    }

    val conn = playerConnection
    val title = current.title
    val mediaItems = songs.map { it.song.toMediaItem() }
    val firstSong = songs.firstOrNull()
    val tracksLabel = stringResource(R.string.tracks_label)
    val metadata = current.songCount.takeIf { it > 0 }?.let { "$it $tracksLabel" }

    val menuActions = mutableListOf<DetailMenuAction>()
    if (conn != null && mediaItems.isNotEmpty()) {
        menuActions +=
            DetailMenuAction(stringResource(R.string.play_next)) {
                conn.playNext(mediaItems)
            }
        firstSong?.let { seed ->
            menuActions +=
                DetailMenuAction(stringResource(R.string.start_radio)) {
                    conn.playRadioFrom(seed.song.toMediaMetadata())
                }
        }
    }
    current.playlist.shareLink?.let { link ->
        menuActions +=
            DetailMenuAction(stringResource(R.string.share)) {
                shareText(context, link)
            }
    }

    ImmersiveDetailScaffold(
        title = title,
        thumbnailUrl = current.thumbnails.firstOrNull(),
        backdropKey = current.id,
        onBack = onBack,
        metadata = metadata,
        onPlay =
            conn?.let { c ->
                {
                    // 空のプレイリストでは再生を起たない。
                    if (mediaItems.isNotEmpty()) {
                        c.playQueue(ListQueue(title = title, items = mediaItems))
                    }
                }
            },
        onShuffle =
            conn?.let { c ->
                {
                    if (mediaItems.isNotEmpty()) {
                        // playQueue はキュー開始時にシャッフルを一度下げるため、
                        // 起動させたあとに立てて残りを並べ替える。
                        c.playQueue(ListQueue(title = title, items = mediaItems))
                        c.player.shuffleModeEnabled = true
                    }
                }
            },
        onEnqueue = conn?.let { c -> { c.addToQueue(mediaItems) } },
        menuActions = menuActions,
    ) {
        itemsIndexed(items = songs, key = { _, song -> song.map.id }) { index, song ->
            SongRow(
                song = song.song.toMediaMetadata(),
                // Apple Music のプレイリストと同じく、左端に1始まりの通し番号を出す。
                index = index + 1,
                onClick = {
                    if (mediaItems.isNotEmpty()) {
                        conn?.playQueue(
                            ListQueue(
                                title = title,
                                items = mediaItems,
                                startIndex = index.coerceIn(mediaItems.indices),
                            ),
                        )
                    }
                },
            )
        }

        if (songs.isEmpty()) {
            item(key = "empty") {
                EmptyRow(message = stringResource(R.string.no_results_found))
            }
        }
    }
}
