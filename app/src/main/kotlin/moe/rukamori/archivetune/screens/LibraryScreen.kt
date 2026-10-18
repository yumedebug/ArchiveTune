/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.Artwork
import moe.rukamori.archivetune.design.components.AvatarButton
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.ListItemRow
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryAlbumsViewModel
import moe.rukamori.archivetune.viewmodels.LibraryArtistsViewModel
import moe.rukamori.archivetune.viewmodels.LibraryPlaylistsViewModel
import moe.rukamori.archivetune.viewmodels.LibrarySongsViewModel
import moe.rukamori.archivetune.viewmodels.LibraryViewModel

/**
 * Library タブ。
 *
 * 並びは Apple Music のライブラリと同じ順に組む：
 * **3列のアートグリッド → カテゴリ行 → 選んだカテゴリの一覧**。
 *
 * カテゴリ行は下線タブの代わりに入る（添付では Playlists / Artists / Albums /
 * Songs が色付きアイコンの行になっている）。押した側だけアクセントにして、
 * いま何の一覧が出ているかを行の色で示す。
 *
 * [LibraryViewModel.filter] が選択状態を持つので、そこだけを見て表示を切り替える。
 * 曲・アーティスト・アルバム・プレイリストはそれぞれ専用 ViewModel が流す。
 *
 * @param navController アルバム・アーティスト・プレイリストの詳細へ送る。
 * @param onSettings 大見出し右端のアカウント丸ボタンから設定へ入る。
 */
@Composable
fun LibraryScreen(
    navController: NavHostController,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val songsViewModel: LibrarySongsViewModel = hiltViewModel()
    val artistsViewModel: LibraryArtistsViewModel = hiltViewModel()
    val albumsViewModel: LibraryAlbumsViewModel = hiltViewModel()
    val playlistsViewModel: LibraryPlaylistsViewModel = hiltViewModel()

    val windowInsets = LocalPlayerAwareWindowInsets.current
    val playerConnection = LocalPlayerConnection.current
    val palette = applePalette()
    val metrics = appleMetrics()

    // 右上の丸ボタンに映すアカウント名。未ログインなら空文字（人型アイコンになる）。
    val accountName by rememberPreference(AccountNameKey, defaultValue = "")

    val filterState = libraryViewModel.filter
    val songs by songsViewModel.allSongs.collectAsState()
    val artists by artistsViewModel.allArtists.collectAsState()
    val albums by albumsViewModel.allAlbums.collectAsState()
    val playlists by playlistsViewModel.allPlaylists.collectAsState()

    val active =
        when (filterState.value) {
            LibraryFilter.ARTISTS, LibraryFilter.ALBUMS, LibraryFilter.PLAYLISTS -> filterState.value
            else -> LibraryFilter.SONGS
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        // 大見出し右端のアカウント丸ボタン（Apple Music の右上アバター相当）。
        // これが設定（アカウント）画面へ入る唯一の導線。
        LargeTitleHeader(
            title = stringResource(R.string.filter_library),
            trailing = {
                AvatarButton(
                    onClick = onSettings,
                    accountName = accountName,
                    contentDescription = stringResource(R.string.settings),
                )
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.block.dp),
        ) {
            // ── お気に入り ─────────────────────────────────────
            // 図書館のいちばん最初に置く。開いてすぐ目に入る位置に「いいねした曲」と
            // 自分のプレイリストを並べ、後ろのカテゴリ一覧と役割を分ける。
            item(key = "favoritesHeader") {
                SectionHeader(title = stringResource(R.string.favorite))
            }
            item(key = "favorites") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LibraryCategoryRow(
                        icon = R.drawable.favorite,
                        tint = favoriteRowTints[0],
                        title = stringResource(R.string.liked_songs),
                        selected = false,
                        onClick = {
                            // いいねした曲は専用のまとめ（歌ページの♡が入る場所）。
                            navController.navigate(
                                Routes.localPlaylist(PlaylistEntity.LIKED_PLAYLIST_ID),
                            )
                        },
                    )
                    LibraryCategoryRow(
                        icon = R.drawable.queue_music,
                        tint = favoriteRowTints[1],
                        title = stringResource(R.string.favorite_playlists),
                        trailing = playlists.size.takeIf { it > 0 }?.toString(),
                        selected = active == LibraryFilter.PLAYLISTS,
                        onClick = { filterState.value = LibraryFilter.PLAYLISTS },
                    )
                }
            }

            // ── 最近追加した項目 ───────────────────────────────
            // 3列のアートグリッド。枠線は引かず、隙間だけで区切る。
            if (albums.isNotEmpty()) {
                item(key = "recentHeader") {
                    SectionHeader(title = stringResource(R.string.recently_added))
                }
                item(key = "recentGrid") {
                    RecentlyAddedGrid(
                        albums =
                            albums
                                .take(6)
                                .map { album ->
                                    LibraryAlbumCell(
                                        id = album.id,
                                        title = album.title,
                                        thumbnailUrl = album.thumbnailUrl,
                                    )
                                },
                        onOpen = { cell -> navController.navigate(Routes.album(cell.id)) },
                    )
                }
            }

            // ── カテゴリ行（下線タブの代わり） ─────────────────
            item(key = "categories") {
                LibraryCategoryRows(
                    active = active,
                    counts =
                        LibraryCategoryCounts(
                            artists = artists.size,
                            albums = albums.size,
                            playlists = playlists.size,
                            songs = songs.size,
                        ),
                    onSelect = { filterState.value = it },
                )
            }

            // ── 選んだカテゴリの一覧 ───────────────────────────
            // いま何の一覧が出ているかを見出しで示す（行を押した直後に迷わない）。
            item(key = "listHeader") {
                SectionHeader(
                    title =
                        stringResource(
                            when (active) {
                                LibraryFilter.ARTISTS -> R.string.artists
                                LibraryFilter.ALBUMS -> R.string.albums
                                LibraryFilter.PLAYLISTS -> R.string.playlists
                                else -> R.string.songs
                            },
                        ),
                )
            }

            when (active) {
                LibraryFilter.ARTISTS -> {
                    if (artists.isEmpty()) {
                        item(key = "empty") { LibraryEmptyState() }
                    } else {
                        items(items = artists, key = { it.id }) { artist ->
                            ListItemRow(
                                title = artist.title,
                                thumbnailUrl = artist.thumbnailUrl,
                                circularArtwork = true,
                                onClick = { navController.navigate(Routes.artist(artist.id)) },
                            )
                        }
                    }
                }

                LibraryFilter.ALBUMS -> {
                    if (albums.isEmpty()) {
                        item(key = "empty") { LibraryEmptyState() }
                    } else {
                        items(items = albums, key = { it.id }) { album ->
                            ListItemRow(
                                title = album.title,
                                thumbnailUrl = album.thumbnailUrl,
                                onClick = { navController.navigate(Routes.album(album.id)) },
                            )
                        }
                    }
                }

                LibraryFilter.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        item(key = "empty") { LibraryEmptyState() }
                    } else {
                        items(items = playlists, key = { it.id }) { playlist ->
                            ListItemRow(
                                title = playlist.title,
                                subtitle = playlist.songCount.takeIf { it > 0 }?.toString(),
                                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                                onClick = {
                                    navController.navigate(Routes.localPlaylist(playlist.id))
                                },
                            )
                        }
                    }
                }

                else -> {
                    if (songs.isEmpty()) {
                        item(key = "empty") { LibraryEmptyState() }
                    } else {
                        itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
                            SongRow(
                                song = song.toMediaMetadata(),
                                onClick = {
                                    // 端末内に置く曲をまとめて再生する。選択行を先頭に
                                    // 置く。オンライン曲をシードにしたラジオ生成とは
                                    // 経路を分けており、キューを作り直さない。
                                    // playLocalSongs の実体は ItemActions.kt。
                                    playerConnection?.playLocalSongs(songs, index)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 一覧が空のときの受け皿。行が無くなっても上部のグリッドとカテゴリは残す。 */
@Composable
private fun LibraryEmptyState() {
    EmptyState(
        title = stringResource(R.string.filter_library),
        message = stringResource(R.string.no_results_found),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(240.dp),
    )
}

/**
 * 「最近追加した項目」を3列で敷くグリッド。
 *
 * Lazy の入れ子（[androidx.compose.foundation.lazy.LazyVerticalGrid] を
 * [LazyColumn] の item の中に置く）は測定が二重になり落ちやすいため、
 * 行ごとに普通の [Row] を組む。出すのは先頭数件だけなので遅延は要らない。
 */
/** グリッド1枚ぶんの表示に必要な3項目だけを持つ型（ViewModel の返す型には依存しない）。 */
private data class LibraryAlbumCell(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
)

@Composable
private fun RecentlyAddedGrid(
    albums: List<LibraryAlbumCell>,
    onOpen: (LibraryAlbumCell) -> Unit,
) {
    val metrics = appleMetrics()
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = metrics.gutter.dp, end = metrics.gutter.dp, bottom = metrics.xs.dp),
    ) {
        albums.chunked(3).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = metrics.md.dp)) {
                rowItems.forEachIndexed { index, album ->
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(end = if (index == rowItems.lastIndex) 0.dp else metrics.sm.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                ) { onOpen(album) },
                    ) {
                        Artwork(
                            url = album.thumbnailUrl,
                            contentDescription = album.title,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f),
                        )
                        Text(
                            text = album.title,
                            style = appleType().footnote,
                            color = applePalette().onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = metrics.xs.dp),
                        )
                    }
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** カテゴリ行の地色。Apple Music のライブラリと同じく1行ずつ違う色にする。 */
private val categoryTints =
    listOf(
        Color(0xFF3E7BFA),
        Color(0xFFE0489C),
        Color(0xFFE2483F),
        Color(0xFFF0872B),
    )

/** お気に入り行の地色。いいねした曲はハートの赤、プレイリストは青。 */
private val favoriteRowTints =
    listOf(
        Color(0xFFE2483F),
        Color(0xFF3E7BFA),
    )

/** カテゴリ行の右端に出す件数。 */
private data class LibraryCategoryCounts(
    val artists: Int = 0,
    val albums: Int = 0,
    val playlists: Int = 0,
    val songs: Int = 0,
)

/** カテゴリ行1本ぶんの定義。 */
private data class LibraryCategoryDef(
    val labelId: Int,
    val icon: Int,
    val filter: LibraryFilter,
    val count: Int,
)

/**
 * Playlists / Artists / Albums / Songs の4行。
 *
 * 色付きの四角いアイコン、カテゴリ名、右端に件数とシェブロンで構成され、
 * 下線タブは無い。選択中の行だけ名前をアクセントにして現在地を示す。
 */
@Composable
private fun LibraryCategoryRows(
    active: LibraryFilter,
    counts: LibraryCategoryCounts,
    onSelect: (LibraryFilter) -> Unit,
) {
    val metrics = appleMetrics()

    val rows =
        listOf(
            LibraryCategoryDef(R.string.playlists, R.drawable.queue_music, LibraryFilter.PLAYLISTS, counts.playlists),
            LibraryCategoryDef(R.string.artists, R.drawable.person, LibraryFilter.ARTISTS, counts.artists),
            LibraryCategoryDef(R.string.albums, R.drawable.album, LibraryFilter.ALBUMS, counts.albums),
            LibraryCategoryDef(R.string.songs, R.drawable.music_note, LibraryFilter.SONGS, counts.songs),
        )

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = metrics.xs.dp),
    ) {
        rows.forEachIndexed { index, row ->
            LibraryCategoryRow(
                icon = row.icon,
                tint = categoryTints[index % categoryTints.size],
                title = stringResource(row.labelId),
                trailing = row.count.takeIf { it > 0 }?.toString(),
                selected = row.filter == active,
                onClick = { onSelect(row.filter) },
            )
        }
    }
}

/** カテゴリ1行。枠線は引かず、左の色面と余白だけで行を分ける。 */
@Composable
private fun LibraryCategoryRow(
    icon: Int,
    tint: Color,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                ) { onClick() }
                .padding(horizontal = metrics.gutter.dp, vertical = metrics.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp),
            )
        }

        Spacer(Modifier.width(metrics.md.dp))

        Text(
            text = title,
            style = type.body,
            color = if (selected) palette.accent else palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = type.footnote,
                color = palette.labelSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.width(metrics.sm.dp))
        }

        Icon(
            painter = painterResource(R.drawable.chevron_right),
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}
