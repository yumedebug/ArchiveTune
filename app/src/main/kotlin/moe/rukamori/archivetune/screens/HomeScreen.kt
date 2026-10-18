/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AccountNameKey
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.AvatarButton
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.MediaCard
import moe.rukamori.archivetune.design.components.PinnedTitleBar
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.design.components.SignInPrompt
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.design.components.rememberTitleCollapse
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeEvent
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.HomeViewModel

/** 特集カードの横幅（画面幅に対する割合）。次のカードが右端で覗く程度にする。 */
private val PICKUP_WIDTH_FRACTION = 0.75f

/** 特集カードの縦横比（高さ ÷ 幅）。 */
private val PICKUP_ASPECT = 1f / 1.3f

/** 通常のカルーセルカードの一辺。Apple Music の「最近の再生」と同じ寸法帯。 */
private val CARD_WIDTH = 172.dp

/** 円形のアーティストカードの一辺。 */
private val ARTIST_CARD_WIDTH = 140.dp

/** セクション見出し右端に出す矢印。 */
private const val CHEVRON = "›"

/**
 * Home タブ。
 *
 * データは [HomeViewModel] が init で自動取得するので、画面側の役割は
 * 4状態（読み込み／成功／空／エラー）の分岐と、成功時の並べ方だけ。
 *
 * 成功時は YouTube Music が返したセクションを**先頭からそのまま**積む。
 * ViewModel が抽出して別の場所に置いている分（特集カード・クイックピック・
 * 最近再生した項目）だけは元の位置へ戻せないため、Apple Music の Listen Now と
 * 同じ順に置き直す：
 * **特集カード（最上部） → クイックピック → 最近再生した項目 → 返ってきた順**。
 *
 * 末尾付近まで来たら続きのページを取りに行き、同じ取得単位は二度送らない。
 *
 * @param navController 曲以外（アルバム・アーティスト等）の遷移に使う。
 * @param onSettings 大見出し右端のアカウント丸ボタンが押されたとき。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val screenState by viewModel.screenState.collectAsState()

    // 右上の丸ボタンに映すアカウント名。未ログインなら空文字（人型アイコンになる）。
    val accountName by rememberPreference(AccountNameKey, defaultValue = "")

    // 大見出しと固定バー、どちらにも同じ丸ボタンを置く（置き場所が二つあっても
    // 導線が一本になるように）。行き先は呼び出し側が決める。
    val avatar: @Composable RowScope.() -> Unit = {
        AvatarButton(
            onClick = onSettings,
            accountName = accountName,
            contentDescription = stringResource(R.string.settings),
        )
    }

    // ポッドキャスト等、ViewModel から画面へ返るイベントを受け取る。
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenPodcast -> navController.navigate(Routes.browse(event.browseId))
                is HomeEvent.PlayPodcastEpisode -> Unit
            }
        }
    }

    // 大タイトルは先頭 item としてスクロールし、画面外へ出たら上部の
    // 小さな見出しが同じ題を差し替える（Apple Music のナビバー相当）。
    val listState = rememberLazyListState()
    val titleCollapsed = rememberTitleCollapse(listState)

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
            when (val state = screenState) {
                HomeScreenState.Loading -> {
                    HomeSkeleton()
                }

                is HomeScreenState.Empty -> {
                    Column(Modifier.fillMaxSize()) {
                        HomeScreenHeader(avatar)
                        EmptyState(
                            title = stringResource(R.string.home),
                            message = stringResource(R.string.no_results_found),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            actionLabel = stringResource(R.string.refresh),
                            onAction = { viewModel.onAction(HomeAction.Refresh) },
                        )
                    }
                }

                is HomeScreenState.Error -> {
                    Column(Modifier.fillMaxSize()) {
                        HomeScreenHeader(avatar)
                        ErrorState(
                            title = stringResource(R.string.home),
                            message = stringResource(state.messageResId),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            onRetry = { viewModel.onAction(HomeAction.Refresh) },
                        )
                    }
                }

                is HomeScreenState.Success -> {
                    val uiState = state.uiState
                    val continuation = uiState.homePage?.continuation

                    // 末尾まで近づいたら続きのページを1回だけ取りに行く。
                    // 同じ取得単位を二度送ると同じセクションが二重に並ぶので、
                    // 送った単位を覚えてから dispatch する。
                    var sentContinuation by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(listState, continuation) {
                        val token = continuation ?: return@LaunchedEffect
                        snapshotFlow {
                            val info = listState.layoutInfo
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                            last >= 0 && last >= info.totalItemsCount - 3
                        }.collect { nearEnd ->
                            if (nearEnd && !uiState.isLoadingMore && sentContinuation != token) {
                                sentContinuation = token
                                viewModel.onAction(HomeAction.LoadMore(token))
                            }
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.onAction(HomeAction.Refresh) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(bottom = metrics.block.dp),
                        ) {
                            item(key = "header") {
                                HomeScreenHeader(avatar)
                            }

                            // サインインしていないときだけ中身が現れる。再生の入口を
                            // 設定に埋もれさせないため、ホームの先頭に置いておく。
                            // 押すするとアプリ内のログイン画面を開く（Cookie の貼り付けはしない）。
                            item(key = "signIn") {
                                SignInPrompt(onSignIn = { navController.navigate(Routes.Login) })
                            }

                            val chips = uiState.homePage?.chips.orEmpty()
                            if (uiState.showCategoryChips && chips.isNotEmpty()) {
                                item(key = "chips") {
                                    ChipRow(
                                        chips = chips,
                                        selected = uiState.selectedChip,
                                        onSelect = { viewModel.onAction(HomeAction.SelectChip(it)) },
                                    )
                                }
                            }

                            // 抽出していた特集カード。ホームのいちばん上に、大きな
                            // 縦長カードのカルーセルとして戻す。
                            val community = uiState.communitySection
                            if (community != null && community.featuredCards.isNotEmpty()) {
                                item(key = "pickup:community") {
                                    PickupSection(
                                        title =
                                            community.title.ifBlank {
                                                stringResource(R.string.you_might_like)
                                            },
                                        cards = community.featuredCards,
                                        onOpenCard = { card ->
                                            openFeaturedCard(card, navController, playerConnection)
                                        },
                                    )
                                }
                            }

                            // 抽出していたクイックピック（YouTube Music の提案）。
                            val quickPickSection = uiState.remoteQuickPicks
                            if (quickPickSection != null && quickPickSection.items.isNotEmpty()) {
                                item(key = "pickup:quickPicks") {
                                    HomeSection(
                                        section = quickPickSection,
                                        onOpenItem = { item ->
                                            openYtItem(item, navController, playerConnection)
                                        },
                                        onPlayAll = { endpoint ->
                                            playerConnection?.playQueue(YouTubeQueue.playlist(endpoint))
                                        },
                                        onOpenDetail = { browseId, params ->
                                            navController.navigate(Routes.browse(browseId, params))
                                        },
                                        onOpenCard = { card ->
                                            openFeaturedCard(card, navController, playerConnection)
                                        },
                                    )
                                }
                            }

                            // 「最近再生した項目」。Apple Music の Listen Now と同じく、
                            // 特集カードのすぐ後ろ・おすすめのミックスより前へ置く。
                            if (uiState.keepListening.isNotEmpty()) {
                                item(key = "keepListening") {
                                    LocalItemSection(
                                        title = stringResource(R.string.recently_played),
                                        items = uiState.keepListening,
                                        onOpen = { item ->
                                            openLocalHomeItem(item, uiState.keepListening, navController, playerConnection)
                                        },
                                    )
                                }
                            }

                            // YouTube Music が返したセクション。名前・順序・中身は
                            // 変えず、重複した分だけ落として並べる。
                            // LazyListScope の中では remember を呼べないので、
                            // 通しのたびに作り直す集合を使う。
                            val seenTitles = mutableSetOf<String>()
                            uiState.homePage?.sections.orEmpty().forEachIndexed { index, section ->
                                if (section.items.isEmpty()) return@forEachIndexed
                                if (!seenTitles.add(section.title)) return@forEachIndexed

                                item(key = "section:$index:${section.title}") {
                                    HomeSection(
                                        section = section,
                                        onOpenItem = { item ->
                                            openYtItem(item, navController, playerConnection)
                                        },
                                        onPlayAll = { endpoint ->
                                            playerConnection?.playQueue(YouTubeQueue.playlist(endpoint))
                                        },
                                        onOpenDetail = { browseId, params ->
                                            navController.navigate(Routes.browse(browseId, params))
                                        },
                                        onOpenCard = { card ->
                                            openFeaturedCard(card, navController, playerConnection)
                                        },
                                    )
                                }
                            }

                            if (uiState.quickPicks.isNotEmpty()) {
                                item(key = "quickPicks") {
                                    Column {
                                        SectionHeader(title = stringResource(R.string.quick_picks))
                                        Column(Modifier.padding(horizontal = metrics.gutter.dp)) {
                                            uiState.quickPicks.forEachIndexed { index, song ->
                                                SongRow(
                                                    song = song.toMediaMetadata(),
                                                    onClick = {
                                                        playerConnection?.playLocalSongs(
                                                            songs = uiState.quickPicks,
                                                            startIndex = index,
                                                        )
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (uiState.accountPlaylists.isNotEmpty()) {
                                item(key = "accountPlaylists") {
                                    Column(modifier = Modifier.padding(top = metrics.sm.dp)) {
                                        SectionHeader(title = stringResource(R.string.playlists))
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                                            horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                                        ) {
                                            items(
                                                items = uiState.accountPlaylists,
                                                key = { it.id },
                                            ) { playlist ->
                                                MediaCard(
                                                    title = playlist.title,
                                                    thumbnailUrl = playlist.thumbnail,
                                                    subtitle = playlist.author?.name,
                                                    size = CARD_WIDTH,
                                                    onClick = {
                                                        navController.navigate(Routes.onlinePlaylist(playlist.id))
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        PinnedTitleBar(
            title = stringResource(R.string.home),
            visible = titleCollapsed,
            trailing = avatar,
        )
    }
}

/** 大見出しと、その下を通すヘアライン。読み込み・空・エラーでも同じ形にして揃える。 */
@Composable
private fun HomeScreenHeader(trailing: (@Composable RowScope.() -> Unit)?) {
    val palette = applePalette()

    Column {
        LargeTitleHeader(
            title = stringResource(R.string.home),
            trailing = trailing,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.separator),
        )
    }
}

/** セクションを、返ってきた中身に合わせて描き分けた形。 */
private enum class SectionLayout { Pickup, SongList, ArtistCircle, CardGrid }

private fun layoutOf(section: HomePage.Section): SectionLayout =
    when {
        section.featuredCards.isNotEmpty() -> SectionLayout.Pickup
        section.items.all { it is ArtistItem } -> SectionLayout.ArtistCircle
        section.items.all { it is SongItem } -> SectionLayout.SongList
        else -> SectionLayout.CardGrid
    }

/**
 * YouTube Music の横並びセクション。
 *
 * 中身に合わせて形を変える。特集カードは大きな縦長カード、曲だけなら横長の行、
 * アーティストだけなら丸いカード、それ以外は正方形のカード。
 *
 * @param onOpenItem カードを1枚開く。
 * @param onPlayAll 見出しの「すべて再生」。
 * @param onOpenDetail 見出し右の矢印（より多く表示する）。browseId と、どのカテゴリかを
 *   決める params の組で渡す（ムード・ジャンルは params が無いと 404 になる）。
 * @param onOpenCard 特集カードを開く。
 */
@Composable
private fun HomeSection(
    section: HomePage.Section,
    onOpenItem: (YTItem) -> Unit,
    onPlayAll: (WatchEndpoint) -> Unit,
    onOpenDetail: (String, String?) -> Unit,
    onOpenCard: (HomePage.Section.FeaturedCard) -> Unit,
) {
    val metrics = appleMetrics()

    // playEndpoint / endpoint は core モジュールの公開 val なので、このモジュールから
    // スマートキャストできない。一旦ローカルへ落とさないとラムダ内でも
    // 「Nullチェックができない」コンパイルエラーになる。
    val playEndpoint = section.playEndpoint
    val detailEndpoint = section.endpoint
    val detailBrowseId = detailEndpoint?.browseId

    Column(modifier = Modifier.padding(top = metrics.sm.dp)) {
        SectionHeader(
            title = section.title,
            actionLabel =
                when {
                    playEndpoint != null -> stringResource(R.string.play_all)
                    detailBrowseId != null -> CHEVRON
                    else -> null
                },
            onAction =
                when {
                    playEndpoint != null -> { { onPlayAll(playEndpoint) } }
                    detailBrowseId != null -> { { onOpenDetail(detailBrowseId, detailEndpoint?.params) } }
                    else -> null
                },
        )

        when (layoutOf(section)) {
            SectionLayout.Pickup ->
                PickupRow(
                    cards = section.featuredCards,
                    onOpenCard = onOpenCard,
                )

            SectionLayout.SongList ->
                Column(Modifier.padding(horizontal = metrics.gutter.dp)) {
                    section.items.forEach { item ->
                        if (item is SongItem) {
                            SongRow(
                                song = item.toMediaMetadata(),
                                onClick = { onOpenItem(item) },
                            )
                        }
                    }
                }

            SectionLayout.ArtistCircle ->
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                ) {
                    items(items = section.items, key = { it.id }, contentType = { it::class }) { item ->
                        MediaCard(
                            title = item.title,
                            thumbnailUrl = item.thumbnail,
                            size = ARTIST_CARD_WIDTH,
                            cornerRadius = ARTIST_CARD_WIDTH / 2,
                            onClick = { onOpenItem(item) },
                        )
                    }
                }

            SectionLayout.CardGrid ->
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                ) {
                    items(items = section.items, key = { it.id }, contentType = { it::class }) { item ->
                        MediaCard(
                            title = item.title,
                            thumbnailUrl = item.thumbnail,
                            subtitle = subtitleFor(item),
                            size = CARD_WIDTH,
                            onClick = { onOpenItem(item) },
                        )
                    }
                }
        }
    }
}

/** カルーセルだけの特集セクション（見出し＋大きなカード）。 */
@Composable
private fun PickupSection(
    title: String,
    cards: List<HomePage.Section.FeaturedCard>,
    onOpenCard: (HomePage.Section.FeaturedCard) -> Unit,
) {
    Column(modifier = Modifier.padding(top = appleMetrics().sm.dp)) {
        SectionHeader(title = title)
        PickupRow(cards = cards, onOpenCard = onOpenCard)
    }
}

/**
 * 大きな縦長カードの横スクロール。
 *
 * 幅は画面の約75%、高さは幅の1.3倍。角丸は16dp、間隔は12dp。
 * 次のカードが画面右端で少し覗くようにしている。
 */
@Composable
private fun PickupRow(
    cards: List<HomePage.Section.FeaturedCard>,
    onOpenCard: (HomePage.Section.FeaturedCard) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = appleMetrics().gutter.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = cards, key = { it.id }) { card ->
            PickupCard(card = card, onClick = { onOpenCard(card) })
        }
    }
}

/** 1枚の特集カード。画像を面いっぱいに敷き、下端の暗いグラデーションの上へ白文字を置く。 */
@Composable
private fun LazyItemScope.PickupCard(
    card: HomePage.Section.FeaturedCard,
    onClick: () -> Unit,
) {
    val type = appleType()
    val subtitle = card.subtitle

    Box(
        modifier =
            Modifier
                .fillParentMaxWidth(PICKUP_WIDTH_FRACTION)
                .aspectRatio(PICKUP_ASPECT)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1C1C1E))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
    ) {
        AsyncImage(
            model = card.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
        ) {
            Text(
                text = card.title,
                style = type.title3.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 端末内のおすすめ（もう一度聴く 等）を、カードの列として描く。 */
@Composable
private fun LocalItemSection(
    title: String,
    items: List<LocalItem>,
    onOpen: (LocalItem) -> Unit,
) {
    val metrics = appleMetrics()

    Column(modifier = Modifier.padding(top = metrics.sm.dp)) {
        SectionHeader(title = title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
            horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
        ) {
            items(items = items, key = { it.id }, contentType = { it::class }) { item ->
                MediaCard(
                    title = item.title,
                    thumbnailUrl = item.thumbnailUrl,
                    subtitle = if (item is Song) item.artists.firstOrNull()?.name else null,
                    size = CARD_WIDTH,
                    onClick = { onOpen(item) },
                )
            }
        }
    }
}

/** 特集カードの1枚を開く。詳細があれば開き、無ければ再生できる形で鳴らす。 */
private fun openFeaturedCard(
    card: HomePage.Section.FeaturedCard,
    navController: NavHostController,
    playerConnection: PlayerConnection?,
) {
    val browseId = card.endpoint?.browseId
    if (browseId != null) {
        // ムード・ジャンルのカードは params が無いと browse が 404 になる。
        navController.navigate(Routes.browse(browseId, card.endpoint?.params))
        return
    }
    val endpoint = card.playEndpoint ?: card.shuffleEndpoint ?: card.radioEndpoint
    if (endpoint != null) {
        playerConnection?.playQueue(YouTubeQueue.playlist(endpoint))
    }
}

/** 曲はその場で再生し、それ以外は既存の遷移ヘルパへ渡す。 */
private fun openYtItem(
    item: YTItem,
    navController: NavHostController,
    playerConnection: PlayerConnection?,
) {
    if (item is SongItem) {
        playerConnection?.playYtSong(item)
    } else {
        navController.openYtItem(item) { browseId ->
            navController.navigate(Routes.browse(browseId))
        }
    }
}

/** 端末内の1件。曲ならその一覧の先頭から再生し、それ以外は詳細へ。 */
private fun openLocalHomeItem(
    item: LocalItem,
    all: List<LocalItem>,
    navController: NavHostController,
    playerConnection: PlayerConnection?,
) {
    if (item is Song) {
        val songs = all.filterIsInstance<Song>()
        val index = songs.indexOfFirst { it.id == item.id }
        playerConnection?.playLocalSongs(songs = songs, startIndex = if (index < 0) 0 else index)
    } else {
        navController.openLocalItem(item)
    }
}

private fun subtitleFor(item: YTItem): String? =
    when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is AlbumItem -> item.artists?.joinToString(", ") { it.name }
        is ArtistItem -> null
        is PlaylistItem -> item.author?.name
        else -> null
    }

/** カテゴリチップ。選択中は塗り、未選択は枠のみ。 */
@Composable
private fun ChipRow(
    chips: List<HomePage.Chip>,
    selected: HomePage.Chip?,
    onSelect: (HomePage.Chip?) -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = metrics.gutter.dp,
                    vertical = metrics.xs.dp,
                ),
        horizontalArrangement = Arrangement.spacedBy(metrics.xs.dp),
    ) {
        chips.forEach { chip ->
            val isSelected = chip.title == selected?.title
            Text(
                text = chip.title,
                style = type.subhead.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                color = if (isSelected) palette.onBackground else palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(metrics.roundCorner.dp))
                        .background(if (isSelected) palette.surfaceRaised else palette.surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSelect(if (isSelected) null else chip)
                        }
                        .padding(horizontal = metrics.md.dp, vertical = metrics.xs.dp + 2.dp),
            )
        }
    }
}

/**
 * 初回読み込みのスケルトン。
 *
 * 実データが0件の [EmptyState] と混同しないよう、構成だけを模した面を出す。
 * 面はゆっくり明滅して、止まっているのか読み込み中なのかが分かるようにしている。
 */
@Composable
private fun HomeSkeleton() {
    val palette = applePalette()
    val metrics = appleMetrics()

    val transition = rememberInfiniteTransition(label = "homeSkeleton")
    val alpha by
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "homeSkeletonAlpha",
        )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "skeletonHeader") {
            HomeScreenHeader(trailing = null)
        }
        items(items = SKELETON_ROWS, key = { it }) {
            SkeletonBlock(
                alpha = alpha,
                modifier = Modifier.padding(horizontal = metrics.gutter.dp),
            )
        }
    }
}

private val SKELETON_ROWS = (0 until 4).toList()

@Composable
private fun SkeletonBlock(alpha: Float, modifier: Modifier = Modifier) {
    val palette = applePalette()
    val metrics = appleMetrics()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = metrics.section.dp),
        verticalArrangement = Arrangement.spacedBy(metrics.sm.dp),
    ) {
        Box(
            Modifier
                .width(180.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.skeleton.copy(alpha = alpha)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(metrics.artworkCorner.dp))
                        .background(palette.skeleton.copy(alpha = alpha)),
                )
            }
        }
    }
}
