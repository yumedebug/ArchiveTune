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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.ListItemRow
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.MediaCard
import moe.rukamori.archivetune.design.components.PinnedTitleBar
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.design.components.rememberTitleCollapse
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.screens.onlineSearchResultRoute
import moe.rukamori.archivetune.viewmodels.LocalFilter
import moe.rukamori.archivetune.viewmodels.LocalSearchViewModel
import moe.rukamori.archivetune.viewmodels.OnlineSearchSuggestionViewModel
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryScreenState
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryViewModel
import moe.rukamori.archivetune.viewmodels.SearchSongsState
import moe.rukamori.archivetune.viewmodels.SearchSongsViewModel
import moe.rukamori.archivetune.db.entities.Artist as LocalArtist
import moe.rukamori.archivetune.db.entities.Album as LocalAlbum
import moe.rukamori.archivetune.db.entities.Playlist as LocalPlaylist

/**
 * Search タブ。
 *
 * 入力中は端末内の検索（[LocalSearchViewModel]）だけを行い、
 * クエリが無い間は検索ディスカバリー（[SearchDiscoveryViewModel]）を出す。
 * キーボードの「検索」を押したときだけ、オンライン検索結果の画面へ送る。
 *
 * オンライン検索は入力と同時に走らせない（リクエストが過剰になるため）。
 *
 * @param navController アルバム・アーティスト等の遷移、オンライン結果への送り込みに使う。
 */
@Composable
fun SearchScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val localSearchViewModel: LocalSearchViewModel = hiltViewModel()
    val discoveryViewModel: SearchDiscoveryViewModel = hiltViewModel()
    val suggestionViewModel: OnlineSearchSuggestionViewModel = hiltViewModel()
    val songsViewModel: SearchSongsViewModel = hiltViewModel()

    val windowInsets = LocalPlayerAwareWindowInsets.current
    val playerConnection = LocalPlayerConnection.current
    val palette = applePalette()
    val metrics = appleMetrics()
    val focusManager = LocalFocusManager.current

    val query by localSearchViewModel.query.collectAsState()
    val localResult by localSearchViewModel.result.collectAsState()
    val discoveryState by discoveryViewModel.state.collectAsState()
    val suggestionState by suggestionViewModel.viewState.collectAsState()
    // オンラインの曲は Compose の状態として持つ ViewModel から直接読む。
    val songsState = songsViewModel.state

    // 入力に合わせて候補とオンラインの曲を取りに行く。クエリが変わったときだけ渡す。
    LaunchedEffect(query) {
        suggestionViewModel.updateQuery(query)
        songsViewModel.updateQuery(query)
    }

    fun submitSearch(value: String = query) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        focusManager.clearFocus()
        navController.navigate(onlineSearchResultRoute(trimmed))
    }

    // 大見出しは一覧の先頭 item としてスクロールし、潜ったら固定バーへ差し替える。
    val listState = rememberLazyListState()
    val titleCollapsed = rememberTitleCollapse(listState)

    // 検索は黒を基調にする。見出し・フィールド・中身を1本の縦列にまとめ、
    // 上2つ（見出しと入力欄）がスクロールする手前までで隠れないようにする。
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(windowInsets),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = metrics.block.dp),
            ) {
                item(key = "header") {
                    LargeTitleHeader(title = stringResource(R.string.search))
                }

                item(key = "field") {
                    SearchField(
                        value = query,
                        onValueChange = { localSearchViewModel.query.value = it },
                        onSubmit = { submitSearch() },
                    )
                }

                if (query.isBlank()) {
                    // ディスカバリーは項目数が決まっておらず、
                    // 「見出し＋横スクロール」の繰り返しなので1つの item にまとめる。
                    // item の中だけがコンポジションなので、そこで各部品を呼ぶ。
                    item(key = "discovery") {
                        DiscoveryContent(
                            state = discoveryState,
                            onRetry = { discoveryViewModel.retry() },
                            onOpenSong = { item -> playerConnection?.playYtSong(item) },
                            onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                            onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                            onOpenBrowse = { browseId, params ->
                                navController.navigate(Routes.browse(browseId, params))
                            },
                        )
                    }
                } else {
                    // 入力があるときは、まず YouTube Music 側の曲を引く。
                    // 「YouTube にある曲が検索に出てこない」を無くすための本線で、
                    // 端末内（ライブラリ）の結果はその下へ足す。
                    val onlineSongs = (songsState as? SearchSongsState.Success)?.songs.orEmpty()

                    when (songsState) {
                        is SearchSongsState.Success -> {
                            if (onlineSongs.isNotEmpty()) {
                                item(key = "onlineHead") {
                                    SectionHeader(
                                        title = stringResource(R.string.top_results),
                                        // 絞り込み（アルバム・アーティスト・動画…）は
                                        // これまでどおり結果画面に任せる。
                                        actionLabel = stringResource(R.string.see_all),
                                        onAction = { submitSearch(query) },
                                    )
                                }
                                items(items = onlineSongs, key = { "online:${it.id}" }) { song ->
                                    SongRow(
                                        song = song.toMediaMetadata(),
                                        onClick = { playerConnection?.playYtSong(song) },
                                    )
                                }
                            }
                        }

                        is SearchSongsState.Loading -> {
                            // 曲が返るまでのつなぎとして候補（履歴＋サジェスト）を出す。
                            // 曲が返ったら入れ替えるので、並びを押し下げない。
                            val suggestions =
                                (suggestionState.history.map { it.query } + suggestionState.suggestions)
                                    .distinct()
                                    .filter { it.isNotBlank() }
                            if (suggestions.isEmpty()) {
                                item(key = "onlineLoading") {
                                    LoadingState(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(top = metrics.sm.dp),
                                    )
                                }
                            } else {
                                items(items = suggestions, key = { "suggestion:$it" }) { suggestion ->
                                    SuggestionRow(
                                        text = suggestion,
                                        onClick = { submitSearch(suggestion) },
                                    )
                                }
                            }
                        }

                        is SearchSongsState.Error -> {
                            val messageResId = songsState.messageResId
                            item(key = "onlineError") {
                                ErrorState(
                                    title = stringResource(R.string.search),
                                    message = stringResource(messageResId),
                                    onRetry = { songsViewModel.updateQuery(query) },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(top = metrics.sm.dp),
                                )
                            }
                        }

                        SearchSongsState.Idle -> Unit
                    }

                    val orderedKeys =
                        listOf(
                            LocalFilter.SONG,
                            LocalFilter.ALBUM,
                            LocalFilter.ARTIST,
                            LocalFilter.PLAYLIST,
                        )

                    // 絞り込み未指定時は ALL に全件が入る。そこにだけ見出しつけ、
                    // 後続はフィルタ別に見出しを付ける。
                    val allItems = localResult.map[LocalFilter.ALL].orEmpty()
                    val hasOnline = onlineSongs.isNotEmpty()

                    if (allItems.isNotEmpty()) {
                        // オンラインの曲と混ざって見えないよう、見出しで分ける。
                        if (hasOnline) {
                            item(key = "localHead") {
                                SectionHeader(title = stringResource(R.string.filter_library))
                            }
                        }
                        items(items = allItems, key = { "all:${it.id}" }) { item ->
                            LocalItemRow(
                                item = item,
                                allSongs = allItems,
                                onPlay = { index ->
                                    playerConnection?.playSongsStartingAt(allItems, index)
                                },
                                onOpen = { navController.openLocalItem(it) },
                            )
                        }
                    } else {
                        orderedKeys.forEach { key ->
                            val entries = localResult.map[key].orEmpty()
                            if (entries.isEmpty()) return@forEach

                            item(key = "head:$key") {
                                SectionHeader(title = localFilterLabel(key))
                            }
                            items(items = entries, key = { "${key}:${it.id}" }) { item ->
                                LocalItemRow(
                                    item = item,
                                    allSongs = entries,
                                    onPlay = { index ->
                                        playerConnection?.playSongsStartingAt(entries, index)
                                    },
                                    onOpen = { navController.openLocalItem(it) },
                                )
                            }
                        }
                    }

                    // どこにも何も無いときだけ空状態を出す。取得中はまだ分からないので出さない。
                    val localEmpty = allItems.isEmpty() && orderedKeys.all { localResult.map[it].isNullOrEmpty() }
                    if (localEmpty && !hasOnline && songsState !is SearchSongsState.Loading) {
                        item(key = "empty") {
                            EmptyState(
                                title = stringResource(R.string.search),
                                message = stringResource(R.string.no_results_found),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = metrics.block.dp),
                            )
                        }
                    }
                }
            }
        }

        PinnedTitleBar(
            title = stringResource(R.string.search),
            visible = titleCollapsed,
        )
    }
}

/** 検索候補1行。虫眼鏡を添え、押すとそのまま検索へ送る。 */
@Composable
private fun SuggestionRow(
    text: String,
    onClick: () -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(horizontal = metrics.gutter.dp, vertical = metrics.sm.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(metrics.md.dp))
        Text(
            text = text,
            style = type.body,
            color = palette.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 検索文字列の受け皿。52dp の高さに10dpの角丸で塗り、中身は自前の入力（Material の面は使わない）。 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = metrics.gutter.dp,
                    vertical = metrics.xs.dp,
                )
                .height(SEARCH_FIELD_HEIGHT)
                .clip(RoundedCornerShape(SEARCH_FIELD_CORNER))
                .background(palette.surface)
                .padding(horizontal = metrics.md.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(18.dp),
        )

        Spacer(Modifier.width(metrics.sm.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_hint),
                    style = type.body,
                    color = palette.labelTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = type.body.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
        }
    }
}

/** 検索フィールドの高さ。 */
private val SEARCH_FIELD_HEIGHT = 52.dp

/** 検索フィールドの角丸。 */
private val SEARCH_FIELD_CORNER = 10.dp

/** 端末内の1件を、曲なら再生行、それ以外は遷移行として描く。 */
@Composable
private fun LocalItemRow(
    item: LocalItem,
    allSongs: List<LocalItem>,
    onPlay: (Int) -> Unit,
    onOpen: (LocalItem) -> Unit,
) {
    when (item) {
        is Song -> {
            SongRow(
                song = item.toMediaMetadata(),
                onClick = { onPlay(allSongs.indexOfFirst { it.id == item.id }) },
            )
        }

        is LocalArtist -> {
            ListItemRow(
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                circularArtwork = true,
                onClick = { onOpen(item) },
            )
        }

        is LocalAlbum -> {
            ListItemRow(
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                onClick = { onOpen(item) },
            )
        }

        is LocalPlaylist -> {
            ListItemRow(
                title = item.title,
                thumbnailUrl = item.thumbnails.firstOrNull(),
                onClick = { onOpen(item) },
            )
        }
    }
}

/** クエリが無い間に出すディスカバリー内容。1つの item として描かれる。 */
@Composable
private fun DiscoveryContent(
    state: SearchDiscoveryScreenState,
    onRetry: () -> Unit,
    onOpenSong: (SongItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    // ムード・ジャンルは browseId が共通なので、どのカテゴリかを持つ params も渡す。
    onOpenBrowse: (String, String?) -> Unit,
) {
    val metrics = appleMetrics()

    when (state) {
        SearchDiscoveryScreenState.Loading -> {
            LoadingState(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.block.dp),
            )
        }

        is SearchDiscoveryScreenState.Error -> {
            ErrorState(
                title = stringResource(R.string.search),
                message = stringResource(state.messageResId),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.block.dp),
                onRetry = onRetry,
            )
        }

        SearchDiscoveryScreenState.Empty -> {
            EmptyState(
                title = stringResource(R.string.search),
                message = stringResource(R.string.no_results_found),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.block.dp),
            )
        }

        is SearchDiscoveryScreenState.Success -> {
            val data = state.data

            if (data.moodAndGenres.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.browse_categories))
                // Apple Music の検索画面と同じく2列で並べる。item の中は縦スクロールしか
                // 許されないので、行は手で組む。奇数個のときは空きを均等に割り付ける。
                Column(
                    modifier = Modifier.padding(horizontal = metrics.gutter.dp),
                    verticalArrangement = Arrangement.spacedBy(metrics.md.dp),
                ) {
                    data.moodAndGenres.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(metrics.md.dp)) {
                            pair.forEach { mood ->
                                CategoryTile(
                                    title = mood.title,
                                    stripeColor = mood.stripeColor,
                                    onClick = {
                                        onOpenBrowse(mood.endpoint.browseId, mood.endpoint.params)
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            if (data.suggestedSongs.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.suggestions))
                Column(Modifier.padding(horizontal = metrics.gutter.dp)) {
                    data.suggestedSongs.forEach { song ->
                        SongRow(
                            song = song.toMediaMetadata(),
                            onClick = { onOpenSong(song) },
                        )
                    }
                }
            }

            if (data.trendingAlbums.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.trending))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                ) {
                    items(items = data.trendingAlbums, key = { it.id }) { album ->
                        MediaCard(
                            title = album.title,
                            thumbnailUrl = album.thumbnail,
                            onClick = { onOpenAlbum(album.id) },
                        )
                    }
                }
            }

            if (data.suggestedArtists.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.artists))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                ) {
                    items(items = data.suggestedArtists, key = { it.id }) { artist ->
                        MediaCard(
                            title = artist.title,
                            thumbnailUrl = artist.thumbnail,
                            onClick = { onOpenArtist(artist.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「カテゴリをブラウズ」のタイル。
 *
 * 横長の長方形（高さ90dp・角丸10dp）に、YouTube Music が返した色帯
 * （`leftStripeColor`）をそのまま下地にする。その色から明るい側へ寄せた
 * 2色の横グラデーションを重ね、白い太字のカテゴリ名を左下に載せる。
 *
 * このデータにはアートワークが無いので、色と文字だけで成立させる
 * （無い画像をでっち上げることはしない）。
 */
@Composable
private fun CategoryTile(
    title: String,
    stripeColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = appleType()
    val base = Color(stripeColor)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(CATEGORY_TILE_HEIGHT)
                .clip(RoundedCornerShape(CATEGORY_TILE_CORNER))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(base, base.lightened(CATEGORY_TILE_SPREAD)),
                    ),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            style = type.headline.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

/** カテゴリタイルの高さ。 */
private val CATEGORY_TILE_HEIGHT = 90.dp

/** カテゴリタイルの角丸。 */
private val CATEGORY_TILE_CORNER = 10.dp

/** グラデーション右端の明るさ倍率。帯の色を保ったまま右へ明るくする。 */
private const val CATEGORY_TILE_SPREAD = 1.4f

/** 色を HSV で捉えて明度を上げる。文字（白）を載せても潰れない範囲に留める。 */
private fun Color.lightened(factor: Float): Color {
    val argb =
        android.graphics.Color.argb(
            (alpha * 255).toInt().coerceIn(0, 255),
            (red * 255).toInt().coerceIn(0, 255),
            (green * 255).toInt().coerceIn(0, 255),
            (blue * 255).toInt().coerceIn(0, 255),
        )
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[2] = (hsv[2] * factor).coerceAtMost(1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** 端末内検索のカテゴリ見出し。 */
@Composable
private fun localFilterLabel(filter: LocalFilter): String =
    when (filter) {
        LocalFilter.SONG -> stringResource(R.string.songs)
        LocalFilter.ALBUM -> stringResource(R.string.albums)
        LocalFilter.ARTIST -> stringResource(R.string.artists)
        LocalFilter.PLAYLIST -> stringResource(R.string.playlists)
        LocalFilter.ALL -> stringResource(R.string.search)
    }

/** 1件を再生キューの先頭にして始める。実体は ItemActions.kt の playLocalSongs。 */
private fun moe.rukamori.archivetune.playback.PlayerConnection.playSongsStartingAt(
    items: List<LocalItem>,
    index: Int,
) {
    val songs = items.filterIsInstance<Song>()
    val target = songs.indexOfFirst { it.id == items.getOrNull(index)?.id }
    playLocalSongs(songs, if (target < 0) 0 else target)
}
