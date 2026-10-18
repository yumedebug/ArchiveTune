/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.viewmodels.OnlinePlaylistViewModel

/**
 * YouTube Music 上のプレイリスト詳細。
 *
 * [OnlinePlaylistViewModel] が init で読み、[OnlinePlaylistViewModel.retry] で取り直す。
 * 曲は [SongItem]（オンライン由来）なので、再生は endpoint 経由の [YouTubeQueue] で行う。
 * 端末内の一覧で使う `ListQueue` とは混ぜない（MediaItem を先に確定させると
 * 後続の自動追加が成立しなくなる）。
 *
 * 曲1件の再生は既存UIが使う構造（`ArtistScreen.kt:851-856`）に合わせている。
 * プレイリスト全体を起点にした起動は見出しの再生ボタン側で行う。
 *
 * 見出し部分は [ImmersiveDetailScaffold]（上端いっぱいのアートワーク＋抽出色の背景）を使う。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun OnlinePlaylistScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnlinePlaylistViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val context = LocalContext.current

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val dbPlaylist by viewModel.dbPlaylist.collectAsState()

    val stateModifier = Modifier.fillMaxSize().windowInsetsPadding(windowInsets)
    val current = playlist

    if (current == null) {
        if (error != null) {
            ErrorState(
                title = stringResource(R.string.playlists),
                message = error ?: stringResource(R.string.no_results_found),
                onRetry = { viewModel.retry() },
                modifier = stateModifier,
            )
        } else {
            LoadingState(stateModifier)
        }
        return
    }

    val conn = playerConnection
    val playEndpoint = current.playEndpoint
    val mediaItems = songs.map { it.toMediaItem() }
    val firstSong = songs.firstOrNull()

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
    // 図書館のお気に入り（ライブラリ）への出し入れ。他人の作ったプレイリストでも
    // ここから自分の図書館へ入れられる。
    val isFavorite = dbPlaylist?.playlist?.bookmarkedAt != null
    menuActions +=
        DetailMenuAction(
            stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.favorite),
        ) {
            viewModel.toggleFavorite()
        }
    menuActions +=
        DetailMenuAction(stringResource(R.string.share)) {
            shareText(context, current.shareLink)
        }

    // 一括再生とシャッフル。YouTube 側の再生 endpoint を持たないプレイリスト
    // （他人の作った公開プレイリストなど）でも押せるよう、曲一覧からキューを組む
    // 道を用意しておく。曲が1つも無いときだけボタンを出さない。
    val playAllQueue: () -> Unit = {
        val endpoint = playEndpoint
        if (endpoint != null) {
            conn?.playQueue(YouTubeQueue.playlist(endpoint))
        } else if (mediaItems.isNotEmpty()) {
            // endpoint が無い場合は手元の一覧でキューを組む。
            conn?.playQueue(ListQueue(title = current.title, items = mediaItems))
        }
    }
    val shuffleAllQueue: () -> Unit = {
        playAllQueue()
        // playQueue はキュー開始時にシャッフルを一度下げるため、
        // 起動させたあとに立てて残りを並べ替える。
        conn?.player?.shuffleModeEnabled = true
    }
    val canPlayAll = playEndpoint != null || mediaItems.isNotEmpty()
    val onPlayAll: (() -> Unit)? = if (canPlayAll) playAllQueue else null
    val onShuffleAll: (() -> Unit)? = if (canPlayAll) shuffleAllQueue else null

    ImmersiveDetailScaffold(
        title = current.title,
        thumbnailUrl = current.thumbnail,
        backdropKey = current.id,
        onBack = onBack,
        subtitle = current.author?.name,
        metadata = current.songCountText,
        description = current.description,
        onPlay = onPlayAll,
        onShuffle = onShuffleAll,
        onEnqueue = conn?.let { c -> { c.addToQueue(mediaItems) } },
        menuActions = menuActions,
    ) {
        itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song.toMediaMetadata(),
                // Apple Music のプレイリストと同じく、左端に1始まりの通し番号を出す。
                index = index + 1,
                onClick = {
                    // 曲を直接起点にする。endpoint を持たない曲は videoId から組み立て、
                    // ここで一覧そのものは渡さない（続きの自動追加は endpoint 起点のみ）。
                    conn?.playQueue(
                        YouTubeQueue(
                            endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id),
                            preloadItem = song.toMediaMetadata(),
                        ),
                    )
                },
            )
        }

        if (isLoading && songs.isEmpty()) {
            item(key = "loading") { LoadingState(Modifier.fillMaxWidth()) }
        }

        if (songs.isEmpty() && !isLoading) {
            item(key = "empty") {
                EmptyRow(message = stringResource(R.string.no_results_found))
            }
        }
    }
}
