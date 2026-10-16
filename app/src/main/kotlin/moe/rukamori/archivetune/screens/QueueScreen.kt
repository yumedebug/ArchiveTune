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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.rememberPreference

/**
 * 再生キュー。
 *
 * 再生エンジン側の [androidx.media3.exoplayer.source.Timeline.Window] の並びを
 * **そのまま見せる**ため、ViewModel を持たない。表示は [androidx.media3.Player] の
 * 状態を直接観察するものだけに限る。
 *
 * 構成は Apple Music のキューと同じ骨格：
 * 上端に Now Playing の縮小版 [CurrentTrackHeader]、その下にピル型トグル
 * （シャッフル／リピート／自動再生／クロスフェードのスイッチ）、
 * 以下が「次に再生」の行。
 *
 * 行をタップすると、そのウィンドウの**最初のピリオド**へ移る
 * （ウィンドウ番号ではなく [androidx.media3.Timeline.Window.firstPeriodIndex] を
 * 使うのが既存のキュー画面と同じ扱い）。
 *
 * 次に再生する行は左スワイプで削除できる。再生中の行だけスワイプ対象から外し、
 * 再生が途切れないようにしている。
 *
 * @param onBack 戻る矢印。プレイヤーなど上位の画面へ戻す。
 */
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()
    val type = appleType()

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val shuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    // ピル2つ分の裏側。どちらも MusicService が DataStore から読んで効かせるので、
    // 画面側は書き込むだけにする。
    val autoLoadMore by rememberPreference(AutoLoadMoreKey, true)
    val crossfade by rememberPreference(CrossfadeEnabledKey, false)

    val upcoming =
        remember(queueWindows, currentWindowIndex) {
            queueWindows.filterIndexed { index, _ -> index != currentWindowIndex }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        CurrentTrackHeader(onBack = onBack)

        // ── ピル型トグル列 ──────────────────────────────
        // Apple Music と同様、左にシャッフルとリピート、右に自動再生と
        // クロスフェードのスイッチを寄せる。枠は作らず面の濃淡だけ。
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = metrics.gutter.dp, end = metrics.gutter.dp, top = metrics.xs.dp),
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
                icon =
                    when (repeatMode) {
                        Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                        else -> R.drawable.repeat
                    },
                selected = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = { playerConnection.player.toggleRepeatMode() },
                contentDescription =
                    stringResource(
                        when (repeatMode) {
                            Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                            Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                            else -> R.string.repeat_mode_off
                        },
                    ),
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
                contentPadding = PaddingValues(bottom = metrics.gutter.dp),
            ) {
                if (upcoming.isNotEmpty()) {
                    item(key = "continueHeader") {
                        ContinueHeader(sourceTitle = queueTitle)
                    }
                }

                // 同じ曲を重ねてキューすると uid が重複するので、出現回数を足して一意にする。
                // 位置を足さないと削除のたびにキーが変わり、スワイプ状態が別の行へ移る。
                val uidCounts = mutableMapOf<String, Int>()
                val keyed =
                    upcoming.map { window ->
                        val uid = window.uid.toString()
                        val occurrence = (uidCounts[uid] ?: 0) + 1
                        uidCounts[uid] = occurrence
                        window to "$uid#$occurrence"
                    }

                items(
                    items = keyed,
                    key = { (_, key) -> key },
                ) { (window, _) ->
                    val dismissState = rememberSwipeToDismissBoxState()
                    val scope = rememberCoroutineScope()
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
                    ) {
                        QueueRow(
                            window = window,
                            onClick = {
                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                playerConnection.player.playWhenReady = true
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 「次に再生」の見出し。
 *
 * Apple Music は太字の見出しと、その下に小さな出典（プレイリスト名やミックス名）を
 * 重ねる。ここも同じ形で [queueTitle] を出す。
 */
@Composable
private fun ContinueHeader(
    sourceTitle: String,
) {
    val metrics = appleMetrics()
    val palette = applePalette()
    val type = appleType()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = metrics.gutter.dp, end = metrics.gutter.dp, top = metrics.md.dp, bottom = metrics.xs.dp),
    ) {
        Text(
            text = stringResource(R.string.continue_playing),
            style = type.title3,
            color = palette.onBackground,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
 * 並べ替えの取っ手は**置いていない**。この画面の行は今後も SwipeToDismiss と
 * 自由な並べ替えの両方を受けるが、並べ替え側が未実装のまま取っ手だけ出すと
 * 引っ張っても動かないボタンになるため。
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
