/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import moe.rukamori.archivetune.design.components.CurrentTrackHeader
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.PillToggle
import moe.rukamori.archivetune.design.components.PlayerCircleButton
import moe.rukamori.archivetune.design.components.Scrubber
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.design.components.formatDuration
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** 履歴として出す件数。上端のブロックが縦に伸びきらないように抑える。 */
private const val HISTORY_LIMIT = 5

/**
 * 再生位置の測り直し間隔（再生中）。
 */
private const val POSITION_POLL_MS = 500L

/** 再生位置の測り直し間隔（停止中）。 */
private const val POSITION_POLL_IDLE_MS = 1500L

/**
 * 「履歴」と「次に再生」をまとめて置くブロック。
 *
 * 並べ替えの着地点を固定するため、この1つの item を常に先頭に置き、
 * キュー行は**常に index 1 から**始まるようにする。
 */
private const val QUEUE_PRELUDE_INDEX = 1

/**
 * 再生キュー。
 *
 * 再生エンジン側の [androidx.media3.exoplayer.source.Timeline.Window] の並びを
 * **そのまま見せる**ため、ViewModel を持たない。表示は [androidx.media3.Player] の
 * 状態を直接観察するものだけに限る。
 *
 * 構成は Apple Music のキューと同じ骨格：
 * 上端に Now Playing の縮小版 [CurrentTrackHeader]、その下に横長のモードボタン
 * （シャッフル／リピート／リピート1曲）と自動再生・クロスフェードの切り替え、
 * その下から履歴 → 「次に再生」 → キュー行、最下部がシークバーと再生コントロール。
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
    val type = appleType()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    // ピル2つ分の裏側。どちらも MusicService が DataStore から読んで効かせるので、
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
    val animatedTop by
        animateColorAsState(targetValue = backdrop.top, animationSpec = tween(650), label = "queueTop")
    val animatedBottom by
        animateColorAsState(targetValue = backdrop.bottom, animationSpec = tween(650), label = "queueBottom")

    // 抽出色はどちらのテーマでも暗い側に寄る。ライトテーマでは黒い面に
    // 黒い文字は読めないので、テーマ側の地色を半透明で掛けて明度を戻す。
    val isLightTheme = palette.background.luminance() > 0.5f
    val scrim =
        if (isLightTheme) {
            palette.background.copy(alpha = LIGHT_SCRIM_ALPHA)
        } else {
            Color.Black.copy(alpha = DARK_SCRIM_ALPHA)
        }

    // 進行バー用。曲ごとに測り直す。
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
                .background(Brush.verticalGradient(colors = listOf(animatedTop, animatedBottom))),
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
            CurrentTrackHeader(onBack = onBack)

            // ── モードボタン列 ──────────────────────────
            // 左にシャッフル・リピート・リピート1曲の横長ボタン3つ。
            // 右に自動再生とクロスフェードの切り替えを寄せる。
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = metrics.gutter.dp,
                            end = metrics.gutter.dp,
                            top = metrics.xs.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillToggle(
                    icon = if (shuffleEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                    selected = shuffleEnabled,
                    onClick = { playerConnection.player.shuffleModeEnabled = !shuffleEnabled },
                    contentDescription = stringResource(R.string.shuffle),
                )

                Spacer(Modifier.width(metrics.xs.dp))

                PillToggle(
                    icon = if (repeatMode == Player.REPEAT_MODE_ALL) R.drawable.repeat_on else R.drawable.repeat,
                    selected = repeatMode == Player.REPEAT_MODE_ALL,
                    onClick = {
                        playerConnection.player.repeatMode =
                            if (repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
                    },
                    contentDescription = stringResource(R.string.repeat_mode_all),
                )

                Spacer(Modifier.width(metrics.xs.dp))

                PillToggle(
                    icon = if (repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one_on else R.drawable.repeat_one,
                    selected = repeatMode == Player.REPEAT_MODE_ONE,
                    onClick = {
                        playerConnection.player.repeatMode =
                            if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                    },
                    contentDescription = stringResource(R.string.repeat_mode_one),
                )

                Spacer(Modifier.weight(1f))

                PillToggle(
                    icon = R.drawable.infinity,
                    selected = autoLoadMore,
                    onClick = { autoLoadMore = !autoLoadMore },
                    contentDescription = stringResource(R.string.autoplay),
                )

                Spacer(Modifier.width(metrics.xs.dp))

                QueueSwitch(
                    checked = crossfade,
                    onToggle = { crossfade = !crossfade },
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
                    // 履歴と「次に再生」の見出し。必ず先頭に1つだけ置き、
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
                                    backgroundContent = { QueueDeleteBackground() },
                                    onDismiss = {
                                        if (!removeQueueWindow(playerConnection.player, window)) {
                                            // タイムラインが入れ替わって外せなかった場合、行が画面外に
                                            // 残ったままになるので戻す。
                                            scope.launch { dismissState.reset() }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    QueueRow(
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

                QueueBottomBar(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    onSeek = { playerConnection.player.seekTo(it) },
                    onPlayPause = { playerConnection.player.togglePlayPause() },
                    onPrevious = { playerConnection.seekToPrevious() },
                    onNext = { playerConnection.seekToNext() },
                    onOpenLyrics = onOpenLyrics,
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
            SongRow(
                song = song.toMediaMetadata(),
                onClick = { onPlay(index) },
            )
        }
    }
}

/**
 * 「次に再生」の見出し。
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
 * クロスフェードのスイッチ。
 *
 * 添付のキューでは右端にスイッチ型のトグルが置かれている。丸いトグルのままでは
 * 他のピルと混ざるので、外側はピルと同じ面、内側にトラックとつまみを置く。
 *
 * 色はすべて palette 経由。キューの背景はテーマに依存して明るい面にもなるので、
 * 白で固定するとライトテーマでトラックごと消える。
 *
 * @param checked いま有効か。
 * @param onToggle 押したとき。
 */
@Composable
private fun QueueSwitch(
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val palette = applePalette()
    val description = stringResource(R.string.crossfade)

    Box(
        modifier =
            Modifier
                .size(width = 56.dp, height = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (checked) palette.accent else palette.fill)
                .semantics { this.contentDescription = description }
                .clickable(interactionSource = interactionSource, indication = null) { onToggle() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 40.dp, height = 22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (checked) {
                            Color.White.copy(alpha = 0.92f)
                        } else {
                            palette.onFill.copy(alpha = 0.35f)
                        },
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(3.dp)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (checked) palette.accent else palette.onFill),
            )
        }
    }
}

/**
 * キュー 1 行。
 *
 * 並べ替えの取っ手は呼び出し側（[QueueScreen]）が付け、行本体は
 * 左スワイプの削除とタップで再生する操作だけを持つ。
 */
@Composable
private fun QueueRow(
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

    SongRow(
        song = song,
        isPlaying = false,
        onClick = onClick,
    )
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
 * 画面下部の操作列。
 *
 * 上からシークバー（両端に時間）、大きな再生コントロール、
 * その下に歌詞・出力先・再生キューの3つ。
 */
@Composable
private fun QueueBottomBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenLyrics: () -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    val elapsed = formatDuration((positionMs / 1000L).toInt()) ?: ZERO_TIME
    val remaining =
        formatDuration(((durationMs - positionMs) / 1000L).toInt().coerceAtLeast(0)) ?: ZERO_TIME

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = metrics.gutter.dp,
                    end = metrics.gutter.dp,
                    top = metrics.xs.dp,
                    bottom = metrics.xs.dp,
                ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = elapsed,
                style = type.footnote,
                color = palette.labelSecondary,
                maxLines = 1,
            )
            Scrubber(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                playedColor = Color.White,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = metrics.sm.dp),
            )
            Text(
                text = remaining,
                style = type.footnote,
                color = palette.labelSecondary,
                maxLines = 1,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(76.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCircleButton(
                onClick = onPrevious,
                backgroundAlpha = 0f,
                buttonSize = 56.dp,
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }

            Spacer(Modifier.width(metrics.block.dp))

            PlayerCircleButton(
                onClick = onPlayPause,
                backgroundAlpha = 0.18f,
                buttonSize = 68.dp,
            ) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.width(metrics.block.dp))

            PlayerCircleButton(
                onClick = onNext,
                backgroundAlpha = 0f,
                buttonSize = 56.dp,
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerCircleButton(
                onClick = onOpenLyrics,
                backgroundAlpha = 0f,
                buttonSize = 44.dp,
                contentDescription = stringResource(R.string.lyrics),
            ) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = null,
                    tint = palette.onBackground.copy(alpha = 0.85f),
                    modifier = Modifier.size(21.dp),
                )
            }

            Spacer(Modifier.width(metrics.sm.dp))

            Icon(
                painter = painterResource(R.drawable.cast_output),
                contentDescription = null,
                tint = palette.labelSecondary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(metrics.xs.dp))
            Text(
                text = stringResource(R.string.output),
                style = type.footnote,
                color = palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // いま見ている画面。押しても遷移しない（現在地の表示）。
            PlayerCircleButton(
                onClick = { },
                backgroundAlpha = 0f,
                buttonSize = 44.dp,
                contentDescription = stringResource(R.string.queue),
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

/**
 * 左スワイプで裏に出す削除面。
 *
 * 行をスライドさせた方向の裏に赤い面と「削除」を置く。
 * 色は [moe.rukamori.archivetune.design.ApplePalette.error]、文字は面の上なので白で固定。
 */
@Composable
private fun RowScope.QueueDeleteBackground() {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Box(
        modifier =
            Modifier
                .fillMaxSize()
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

/** 時間が未取得のときに置く文字。 */
private const val ZERO_TIME = "0:00"

/** ライトテーマへ掛ける地色の濃さ。文字が読める明度まで戻す。 */
private const val LIGHT_SCRIM_ALPHA = 0.74f

/** ダークテーマへ掛ける地色の濃さ。 */
private const val DARK_SCRIM_ALPHA = 0.34f
