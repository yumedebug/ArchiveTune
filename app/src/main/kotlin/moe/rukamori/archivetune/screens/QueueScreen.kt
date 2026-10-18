/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.CrossfadeEnabledKey
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.Artwork
import moe.rukamori.archivetune.design.components.CurrentTrackHeader
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** 履歴として出す件数。上端のブロックが縦に伸びきらないように抑える。 */
private const val HISTORY_LIMIT = 5

/** 再生位置の測り直し間隔（再生中）。 */
private const val POSITION_POLL_MS = 500L

/** 再生位置の測り直し間隔（停止中）。 */
private const val POSITION_POLL_IDLE_MS = 1500L

/**
 * 「History」と「Playing Next」をまとめて置くブロック。
 *
 * 並べ替えの着地点を固定するため、この1つの item を常に先頭に置き、
 * キュー行は**常に index 1 から**始まるようにする。
 */
private const val QUEUE_PRELUDE_INDEX = 1

/** 先頭に置く取っ手の高さ。 */
private val GRABBER_HEIGHT = 22.dp

/** 下端に置く進行線の高さ。 */
private val PROGRESS_HEIGHT = 2.dp

/**
 * 再生キュー（Apple Music の "Playing Next"）。
 *
 * 再生エンジン側の [Timeline.Window] の並びを**そのまま見せる**ため、
 * ViewModel を持たない。表示は [Player] の状態を直接観察するものだけに限る。
 *
 * 組み方は Apple Music のキューに寄せている。
 *
 *  - 上端は取っ手と Now Playing の縮小版 [CurrentTrackHeader]、その下に細い進行線
 *  - 本文は「History」→「Playing Next」→ 予定の曲。**予定の曲は
 *    ジャケット＋曲名＋アーティストだけ**の行にし、再生時間や「…」は置かない
 *  - いちばん下は**無地のアイコンだけ**のモード行（シャッフル／リピート／
 *    リピート1曲／自動再生／クロスフェード）。白い面で塗るピル型のトグルは
 *    他社のプレイヤーの記号なので使わない
 *  - **搬送（再生・送り）はここに置かない**。Apple Music 同様、それは
 *    Now Playing の役割で、この画面は並びを見る画面に絞る
 *
 * 背景は全画面プレイヤーと同じく [rememberArtworkBackdrop] から取るので、
 * 画面を切り替えても色は飛ばない。
 *
 * 行は左スワイプで削除でき、右端のハンドルはドラッグで並べ替えられる。
 * 再生中の行だけスワイプ対象から外し、再生が途切れないようにしている。
 *
 * @param onBack 戻る矢印。プレイヤーなど上位の画面へ戻す。
 * @param onOpenLyrics 歌詞ボタン。
 */
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    onOpenLyrics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    // モード行の裏側。どちらも MusicService が DataStore から読んで効かせるので、
    // 画面側は書き込むだけにする。
    var autoLoadMore by rememberPreference(AutoLoadMoreKey, true)
    var crossfade by rememberPreference(CrossfadeEnabledKey, false)

    // 背景の色。キュー・歌詞・全画面プレイヤーの3つが同じキーで取りに行くので、
    // 切り替えても同じ値になり、色が飛ばない。
    val backdrop =
        rememberArtworkBackdrop(
            mediaId = mediaMetadata?.id,
            thumbnailUrl = mediaMetadata?.thumbnailUrl,
        )

    // 抽出色はどちらのテーマでも暗い側に寄る。ライトテーマでは黒い面に
    // 黒い文字は読めないので、テーマ側の地色を半透明で掛けて明度を戻す。
    val isLightTheme = palette.background.luminance() > 0.5f
    val scrim =
        if (isLightTheme) {
            palette.background.copy(alpha = LIGHT_SCRIM_ALPHA)
        } else {
            Color.Black.copy(alpha = DARK_SCRIM_ALPHA)
        }

    // 進行線用。曲ごとに測り直す。
    var positionMs by remember(mediaMetadata?.id) { mutableLongStateOf(0L) }
    var durationMs by remember(mediaMetadata?.id) { mutableLongStateOf(0L) }
    LaunchedEffect(mediaMetadata?.id, isPlaying) {
        while (isActive) {
            positionMs = playerConnection.player.currentPosition.coerceAtLeast(0L)
            val playerDuration = playerConnection.player.duration
            if (playerDuration > 0L) durationMs = playerDuration
            delay(if (isPlaying) POSITION_POLL_MS else POSITION_POLL_IDLE_MS)
        }
    }

    // 履歴は端末の再生イベント。ダミーのデータは作らない。
    var history by remember { mutableStateOf<List<Song>>(emptyList()) }
    var historyVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(historyVersion) {
        history =
            playerConnection
                .database
                .events(limit = HISTORY_LIMIT, offset = 0)
                .map { it.song }
    }
    fun clearHistory() {
        // 書き込みは DB 側のスレッドへ送る。画面は即座に空にして表示だけ戻す。
        playerConnection.database.query { clearListenHistory() }
        history = emptyList()
    }

    val upcoming =
        remember(queueWindows, currentWindowIndex) {
            queueWindows.filterIndexed { index, _ -> index != currentWindowIndex }
        }

    // 同じ曲を重ねてキューすると uid が重複するので、出現回数を足して一意にする。
    // 位置を足さないと削除のたびにキーが変わり、スワイプ状態が別の行へ移る。
    // 並べ替えの着地点を固定するため、この計算は LazyColumn の外へ出す。
    val keyed =
        remember(upcoming) {
            val uidCounts = mutableMapOf<String, Int>()
            upcoming.map { window ->
                val uid = window.uid.toString()
                val occurrence = (uidCounts[uid] ?: 0) + 1
                uidCounts[uid] = occurrence
                window to "$uid#$occurrence"
            }
        }

    val listState = rememberLazyListState()

    // ドラッグで並べ替える。上のブロックが1つ固定されているので、
    // lazy index から QUEUE_PRELUDE_INDEX を引けばキュー行の番号になる。
    val reorderableState =
        rememberReorderableLazyListState(listState) { from, to ->
            val fromIndex = from.index - QUEUE_PRELUDE_INDEX
            val toIndex = to.index - QUEUE_PRELUDE_INDEX
            if (fromIndex !in keyed.indices) return@rememberReorderableLazyListState
            val target = toIndex.coerceIn(keyed.indices)
            if (target == fromIndex) return@rememberReorderableLazyListState
            playerConnection.moveQueueItem(
                sourceWindow = keyed[fromIndex].first,
                targetWindow = keyed[target].first,
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(backdrop.top, backdrop.bottom))),
    ) {
        // 下絵は 48px に縮めたものなので、画面いっぱいに伸ばすだけで自然にぼける。
        backdrop.artwork?.let { art ->
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(scrim))

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(windowInsets),
        ) {
            // ── 取っ手。シートとして持ち上げる動きに合わせる ────────
            Box(
                modifier = Modifier.fillMaxWidth().height(GRABBER_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(width = 36.dp, height = 5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.35f)),
                )
            }

            CurrentTrackHeader(onBack = onBack)

            // ── 細い進行線 ───────────────────────────────────
            val progress =
                if (durationMs > 0L) {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = metrics.gutter.dp, vertical = metrics.sm.dp)
                        .height(PROGRESS_HEIGHT)
                        .clip(RoundedCornerShape(PROGRESS_HEIGHT / 2))
                        .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(Color.White),
                )
            }

            if (queueWindows.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.queue),
                    message = stringResource(R.string.no_track_playing),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(bottom = metrics.sm.dp),
                ) {
                    // 履歴と「Playing Next」の見出し。必ず先頭に1つだけ置き、
                    // キュー行の番号（lazy index 1 〜）を固定する。
                    item(key = "prelude") {
                        Column {
                            if (history.isNotEmpty()) {
                                QueueHistorySection(
                                    history = history,
                                    onPlay = { index ->
                                        playerConnection.playLocalSongs(history, index)
                                    },
                                    onClear = { clearHistory() },
                                )
                            }
                            if (upcoming.isNotEmpty()) {
                                ContinueHeader(
                                    sourceTitle = queueTitle.orEmpty(),
                                    autoplayEnabled = autoLoadMore,
                                )
                            }
                        }
                    }

                    items(
                        items = keyed,
                        key = { (_, key) -> key },
                    ) { (window, key) ->
                        val dismissState = rememberSwipeToDismissBoxState()
                        val scope = rememberCoroutineScope()
                        ReorderableItem(reorderableState, key = key) { isDragging ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(if (isDragging) palette.surfaceRaised else Color.Transparent),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromStartToEnd = false,
                                    // 削除面は**スワイプした分だけ**出す。行そのものは
                                    // 地色を持たない（背後のジャケットを見せている）ため、
                                    // 常時描くと赤い帯が全行に見えてしまう。
                                    backgroundContent = {
                                        QueueDeleteBackground(progress = dismissState.progress)
                                    },
                                    onDismiss = {
                                        if (!removeQueueWindow(playerConnection.player, window)) {
                                            // タイムラインが入れ替わって外せなかった場合、行が画面外に
                                            // 残ったままになるので戻す。
                                            scope.launch { dismissState.reset() }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    QueueTrackRow(
                                        window = window,
                                        onClick = {
                                            playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                            playerConnection.player.playWhenReady = true
                                        },
                                    )
                                }

                                QueueDragHandle(
                                    modifier =
                                        Modifier
                                            .padding(end = metrics.xs.dp)
                                            .size(44.dp)
                                            .draggableHandle(),
                                )
                            }
                        }
                    }
                }
            }

            // ── モード行 ─────────────────────────────────────
            // 歌詞（左）と、無地のアイコンだけのモード切替（右）。
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = metrics.gutter.dp,
                            end = metrics.gutter.dp,
                            top = metrics.xs.dp,
                            bottom = metrics.sm.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QueueModeButton(
                    icon = R.drawable.lyrics,
                    selected = false,
                    onClick = onOpenLyrics,
                    contentDescription = stringResource(R.string.lyrics),
                )

                Spacer(Modifier.weight(1f))

                QueueModeButton(
                    icon = if (shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                    selected = shuffleEnabled,
                    onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
                    contentDescription = stringResource(R.string.shuffle),
                )

                QueueModeButton(
                    icon = if (repeatMode == Player.REPEAT_MODE_ALL) R.drawable.repeat_on else R.drawable.repeat,
                    selected = repeatMode == Player.REPEAT_MODE_ALL,
                    onClick = {
                        playerConnection.player.repeatMode =
                            if (repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
                    },
                    contentDescription = stringResource(R.string.repeat_mode_all),
                )

                QueueModeButton(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one_on else R.drawable.repeat_one,
                    selected = repeatMode == Player.REPEAT_MODE_ONE,
                    onClick = {
                        playerConnection.player.repeatMode =
                            if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    },
                    contentDescription = stringResource(R.string.repeat_mode_one),
                )

                QueueModeButton(
                    icon = R.drawable.infinity,
                    selected = autoLoadMore,
                    onClick = { autoLoadMore = !autoLoadMore },
                    contentDescription = stringResource(R.string.autoplay),
                )

                QueueModeButton(
                    icon = R.drawable.sync,
                    selected = crossfade,
                    onClick = { crossfade = !crossfade },
                    contentDescription = stringResource(R.string.crossfade),
                )
            }
        }
    }
}

/**
 * 「History」の見出しと、直近に再生した曲。
 *
 * 見出しは太字、右端に「Clear」を置く。中身は端末の再生イベントそのもので、
 * 1件も無ければブロックごと描画しない。
 */
@Composable
private fun QueueHistorySection(
    history: List<Song>,
    onPlay: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Column(modifier = Modifier.fillMaxWidth().padding(top = metrics.md.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.gutter.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history),
                style = type.title3,
                color = palette.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.clear),
                style = type.subhead,
                color = palette.accent,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onClear() }
                        .padding(horizontal = metrics.xs.dp, vertical = 2.dp),
            )
        }

        history.forEachIndexed { index, song ->
            QueueMetadataRow(
                metadata = song.toMediaMetadata(),
                onClick = { onPlay(index) },
            )
        }
    }
}

/**
 * 「Playing Next」の見出し。
 *
 * Apple Music は太字の見出しと、その下に小さな出典（プレイリスト名やミックス名）を
 * 重ねる。ここも同じ形で [queueTitle] を出す。自動再生が入っているときは
 * その旨を一行足す。
 */
@Composable
private fun ContinueHeader(
    sourceTitle: String,
    autoplayEnabled: Boolean,
) {
    val metrics = appleMetrics()
    val palette = applePalette()
    val type = appleType()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = metrics.gutter.dp,
                    end = metrics.gutter.dp,
                    top = metrics.md.dp,
                    bottom = metrics.xs.dp,
                ),
    ) {
        Text(
            text = stringResource(R.string.continue_playing),
            style = type.title3,
            color = palette.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (autoplayEnabled) {
            Text(
                text = stringResource(R.string.autoplaying_similar_music),
                style = type.footnote,
                color = palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (sourceTitle.isNotBlank()) {
            Text(
                text = sourceTitle,
                style = type.footnote,
                color = palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * モード行の1ボタン。
 *
 * **面を持たせない。** ピル型に塗ると他社のプレイヤーの記号になり、行が
 * にぎやかになって曲の並びが読めなくなる。Apple Music のキューと同じく
 * 記号だけを置き、入っているものだけアクセント色にする。
 */
@Composable
private fun QueueModeButton(
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    contentDescription: String?,
) {
    val palette = applePalette()

    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (selected) palette.accent else palette.onBackground.copy(alpha = 0.75f),
            modifier = Modifier.size(21.dp),
        )
    }
}

/**
 * キュー 1 行。
 *
 * Apple Music のキューの行は**ジャケットと曲名とアーティストだけ**で、
 * 再生時間も「…」も置かない。並びを見る画面なので情報を増やさない。
 */
@Composable
private fun QueueTrackRow(
    window: Timeline.Window,
    onClick: () -> Unit,
) {
    val metadata = window.mediaItem.metadata
    val song =
        metadata ?: run {
            // メタデータが無い行（外部スコープ等）でも行は潰さず、識別に使える
            // 最小限だけ足す。
            MediaMetadata(
                id = window.mediaItem.mediaId,
                title = window.mediaItem.mediaId,
                artists = emptyList(),
                duration = 0,
            )
        }

    QueueMetadataRow(metadata = song, onClick = onClick)
}

/** ジャケット＋曲名＋アーティストの行。履歴とキューの両方で使う。 */
@Composable
private fun QueueMetadataRow(
    metadata: MediaMetadata,
    onClick: () -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()
    val artistNames = metadata.artists.joinToString(", ") { it.name }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(
                    start = metrics.gutter.dp,
                    end = metrics.sm.dp,
                    top = metrics.sm.dp,
                    bottom = metrics.sm.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = metadata.thumbnailUrl,
            contentDescription = metadata.title,
            cornerRadius = (metrics.artworkCorner - 2).coerceAtLeast(4).dp,
            modifier = Modifier.size(46.dp),
        )

        Spacer(Modifier.width(metrics.md.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metadata.title,
                style = type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (artistNames.isNotEmpty()) {
                Text(
                    text = artistNames,
                    style = type.footnote,
                    color = palette.labelSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 右端の並べ替えハンドル。
 *
 * 記号だけを持ち、面は持たない。ドラッグ自体は呼び出し側の
 * `draggableHandle()` が受け持つ。
 */
@Composable
private fun QueueDragHandle(modifier: Modifier = Modifier) {
    val palette = applePalette()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(R.drawable.drag_handle),
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 左スワイプで裏に出す削除面。
 *
 * 行をスライドさせた方向の裏に赤い面と「削除」を置く。
 * 色は [moe.rukamori.archivetune.design.ApplePalette.error]、文字は面の上なので白で固定。
 *
 * [progress] はスワイプ量（0〜1）。行そのものは地色を持たず背後のジャケットを
 * 透かしているので、常時描くと赤い帯が全行に見えてしまう。必ずスワイプ量で
 * 不透明度を落として、掴んでいる行だけに出す。
 */
@Composable
private fun RowScope.QueueDeleteBackground(progress: Float) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.coerceIn(0f, 1f) }
                .background(palette.error),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = metrics.gutter.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.delete),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(metrics.xs.dp))
            Text(
                text = stringResource(R.string.delete),
                style = type.footnote,
                color = Color.White,
            )
        }
    }
}

/**
 * 表示中の [Timeline.Window] に相当するメディア項目をプレイヤーから外す。
 *
 * `queueWindows` は再生順に並んだウィンドウの写しなので、そこから取り出した
 * 番号はそのまま [androidx.media3.Player.removeMediaItem] に渡せない。
 * タイムラインを引き直して先頭 period 番号が一致するウィンドウを探し、
 * そのメディア項目番号を探して外す（並べ替え実装後も耐える）。
 *
 * @return 実際に外せたら `true`。見つからなければ `false`（呼び出し側に行を戻させる）。
 */
private fun removeQueueWindow(
    player: Player,
    window: Timeline.Window,
): Boolean {
    if (player.currentTimeline.isEmpty) return false
    for (index in 0 until player.mediaItemCount) {
        val candidate = player.currentTimeline.getWindow(index, Timeline.Window())
        if (candidate.firstPeriodIndex == window.firstPeriodIndex) {
            player.removeMediaItem(index)
            return true
        }
    }
    return false
}

/** ライトテーマへ掛ける地色の濃さ。文字が読める明度まで戻す。 */
private const val LIGHT_SCRIM_ALPHA = 0.74f

/** ダークテーマへ掛ける地色の濃さ。 */
private const val DARK_SCRIM_ALPHA = 0.34f
