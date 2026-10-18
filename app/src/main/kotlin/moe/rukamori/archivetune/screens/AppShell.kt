/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.LocalOpenSongMenu
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.components.GlassNavigationBar
import moe.rukamori.archivetune.design.components.MiniPlayer
import moe.rukamori.archivetune.design.components.NavigationTab
import moe.rukamori.archivetune.design.components.SongMenuSheet
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * 新UIの共通シェル。
 *
 * 画面は [NavHost] が持つ。ボトムナビとミニプレイヤーはその**外**に置く。
 * 面はどちらも不透明なので、背後の画面を捕捉する仕組み（Haze）は持たない。
 *
 * 下端の重なりはシェルが一括で決め、子画面は [LocalPlayerAwareWindowInsets] を読むだけで
 * よい。全画面プレイヤー・歌詞・キューでは下端の面を隠すので、そのときだけインセットも
 * 縮める（背後に余白を残さない）。
 */
@Composable
fun AppShell(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current
    val palette = applePalette()
    val metrics = appleMetrics()
    val density = LocalDensity.current

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 曲行右端の「…」で開く曲メニュー。開いている曲をシェルが一括で持ち、
    // どの画面からでも同じシートを開けるようにする。
    var menuSong by remember { mutableStateOf<MediaMetadata?>(null) }

    // `let` はインラインなので、中の collectAsState はそのまま composable の中で評価される。
    val hasTrack = connection?.let { it.mediaMetadata.collectAsState().value } != null

    // 全画面プレイヤー・歌詞・キューでは下端の面を隠す。設定は通常画面として扱い、
    // ナビを残す（Apple の設定もシートではなく押した先の画面として扱う）。
    // ログインも全画面で、WebView にナビやミニプレイヤーを重ねない。
    val immersive = currentRoute == Routes.Player || currentRoute == Routes.Lyrics ||
        currentRoute == Routes.Queue || currentRoute == Routes.Login

    val systemBarBottomDp =
        with(density) { WindowInsets.systemBars.getBottom(density).toDp() }

    val chromeBottomDp =
        if (immersive) {
            0.dp
        } else {
            metrics.navBarHeight.dp +
                (if (hasTrack) metrics.miniPlayerHeight.dp else 0.dp) +
                systemBarBottomDp
        }

    val contentInsets =
        WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            .add(
                WindowInsets(
                    left = 0,
                    top = 0,
                    right = 0,
                    bottom = with(density) { chromeBottomDp.roundToPx() },
                ),
            )

    // Apple Music と同じ5タブ。順序は Routes.MainTabs と揃える。
    // 検索もカプセルの中に含める（分離した円形ボタンはやめる）。
    val mainTabs =
        remember {
            listOf(
                NavigationTab(
                    labelId = R.string.home,
                    iconInactive = R.drawable.home_outlined,
                    iconActive = R.drawable.home_filled,
                    route = Routes.Home,
                ),
                NavigationTab(
                    labelId = R.string.new_releases,
                    iconInactive = R.drawable.new_release,
                    iconActive = R.drawable.new_release,
                    route = Routes.New,
                ),
                NavigationTab(
                    labelId = R.string.radio,
                    iconInactive = R.drawable.radio,
                    iconActive = R.drawable.radio,
                    route = Routes.Radio,
                ),
                NavigationTab(
                    labelId = R.string.filter_library,
                    iconInactive = R.drawable.library_outlined,
                    iconActive = R.drawable.library_filled,
                    route = Routes.Library,
                ),
                NavigationTab(
                    labelId = R.string.search,
                    iconInactive = R.drawable.search,
                    iconActive = R.drawable.search,
                    route = Routes.Search,
                ),
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background),
    ) {
        CompositionLocalProvider(
            LocalPlayerAwareWindowInsets provides contentInsets,
            // 一覧の曲行から「…」を押すと、この合図で曲メニューを開く。
            LocalOpenSongMenu provides { song -> menuSong = song },
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home,
                modifier =
                    Modifier
                        .fillMaxSize(),
                enterTransition = {
                    when (targetState.destination.route) {
                        // タブの入れ替えは重ね替え。面が見え切っているので動きを立てない。
                        in Routes.MainTabs -> fadeIn(tween(200))
                        // 全画面プレイヤー系は下から持ち上げる（Apple のシートと同じ向き）。
                        Routes.Player, Routes.Lyrics, Routes.Queue ->
                            slideInVertically(tween(320)) { it } + fadeIn(tween(240))
                        // それ以外（詳細・設定）は右から押す。
                        else -> slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(220))
                    }
                },
                exitTransition = {
                    when (targetState.destination.route) {
                        in Routes.MainTabs -> fadeOut(tween(160))
                        Routes.Player, Routes.Lyrics, Routes.Queue ->
                            slideOutVertically(tween(300)) { it } + fadeOut(tween(200))
                        else -> slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(180))
                    }
                },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    when (initialState.destination.route) {
                        Routes.Player, Routes.Lyrics, Routes.Queue ->
                            slideOutVertically(tween(300)) { it } + fadeOut(tween(200))
                        in Routes.MainTabs -> fadeOut(tween(160))
                        else -> slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(180))
                    }
                },
            ) {
                composable(Routes.Home) {
                    HomeScreen(
                        navController = navController,
                        onSettings = { navController.navigate(Routes.Settings) },
                    )
                }
                composable(Routes.New) { NewScreen(navController) }
                composable(Routes.Radio) { RadioScreen(navController) }
                composable(Routes.Library) {
                    LibraryScreen(
                        navController = navController,
                        onSettings = { navController.navigate(Routes.Settings) },
                    )
                }
                composable(Routes.Search) { SearchScreen(navController) }

                composable(Routes.Player) {
                    PlayerScreen(
                        onDismiss = { navController.popBackStack() },
                        onOpenLyrics = { navController.navigate(Routes.Lyrics) },
                        onOpenQueue = { navController.navigate(Routes.Queue) },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                    )
                }
                composable(Routes.Lyrics) {
                    LyricsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Queue) {
                    QueueScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.Settings) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onSignIn = { navController.navigate(Routes.Login) },
                    )
                }

                composable(Routes.Login) {
                    LoginScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.OnlineSearch) {
                    OnlineSearchResultsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { navController.navigate(Routes.album(it)) },
                        onOpenArtist = { navController.navigate(Routes.artist(it)) },
                        onOpenPlaylist = { navController.navigate(Routes.onlinePlaylist(it)) },
                    )
                }

                composable(Routes.Album) {
                    AlbumScreen(
                        onBack = { navController.popBackStack() },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                    )
                }
                composable(Routes.Artist) {
                    ArtistScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                        onOpenPlaylist = { id -> navController.navigate(Routes.onlinePlaylist(id)) },
                    )
                }
                composable(Routes.OnlinePlaylist) {
                    OnlinePlaylistScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                    )
                }
                composable(Routes.LocalPlaylist) {
                    LocalPlaylistScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    route = Routes.Browse,
                    arguments =
                        listOf(
                            navArgument(Routes.ArgBrowseId) {
                                type = NavType.StringType
                            },
                            // ムード・ジャンルはこの params にしか「どのカテゴリか」が無い。
                            // 省略可能にして、渡されない画面（ランキング等）も通す。
                            navArgument(Routes.ArgParams) {
                                type = NavType.StringType
                                nullable = true
                            },
                        ),
                ) {
                    BrowseScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                        onOpenPlaylist = { id -> navController.navigate(Routes.onlinePlaylist(id)) },
                    )
                }
            }
        }

        if (!immersive) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(1f),
            ) {
                MiniPlayer(
                    onOpenPlayer = { navController.navigate(Routes.Player) },
                )
                GlassNavigationBar(
                    tabs = mainTabs,
                    selectedRoute = currentRoute,
                    onSelect = { tab ->
                        if (tab.route == currentRoute) {
                            // 選択済みタブの再タップは一覧を先頭へ戻す。
                            navController.currentBackStackEntry
                                ?.savedStateHandle
                                ?.set(ScrollToTopKey, true)
                        } else {
                            // タブの上に積んだ詳細画面（プレイヤー・アーティスト・設定）は
                            // **持ち越さない**。持ち越すと、アーティスト画面からホームを押した
                            // ときに、保存されていた詳細画面の並びが復元されてホームへ戻れなくなる。
                            // 保存と復元は「タブからタブへ移るとき」だけ行う。
                            val fromTab = currentRoute in Routes.MainTabs
                            navController.navigate(tab.route) {
                                popUpTo(Routes.Home) { saveState = fromTab }
                                launchSingleTop = true
                                restoreState = fromTab
                            }
                        }
                    },
                )
            }
        }

        // 曲メニュー。浮遊ナビとミニプレイヤーの上に重ねて出す。
        menuSong?.let { song ->
            val conn = connection
            SongMenuSheet(
                song = song,
                onDismiss = { menuSong = null },
                onPlayNext = conn?.let { c -> { c.playNext(song.toMediaItem()) } },
                onAddToQueue = conn?.let { c -> { c.addToQueue(song.toMediaItem()) } },
                onPlayRadio = conn?.let { c -> { c.playRadioFrom(song) } },
                onOpenArtist =
                    song.artists.firstOrNull()?.id?.let { id ->
                        { navController.navigate(Routes.artist(id)) }
                    },
                onOpenAlbum =
                    song.album?.id?.let { id ->
                        { navController.navigate(Routes.album(id)) }
                    },
            )
        }
    }
}

/** 再タップで一覧を先頭へ戻すための合図。各タブがこのキーを監視する。 */
internal const val ScrollToTopKey = "scrollToTop"
