/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.BackButton
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * 再生キュー。
 *
 * 再生エンジン側の [androidx.media3.exoplayer.source.Timeline.Window] を
 * そのまま見せるため、ViewModel を持たない。
 *
 * 行をタップすると、そのウィンドウの**最初のピリオド**へ移る。
 * ウィンドウ番号ではなく [androidx.media3.Timeline.Window.firstPeriodIndex] を
 * 使うのが正（既存のキュー画面と同じ扱い）。
 *
 * 次に再生する行は左スワイプで削除できる（[QueueDeleteBackground]）。
 * 再生中の行だけはスワイプ対象から外し、途切れさせない。
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

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = metrics.gutter.dp - metrics.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
        }

        LargeTitleHeader(
            title = stringResource(R.string.queue),
            subtitle = queueTitle,
        )

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
            // 同じ曲を重ねてキューしたとき uid が重複するので、出現順を足して一意にする。
            // 位置（index）を足すと削除のたびにキーが変わり、スワイプ状態が別の行へ移る。
            val uidCounts = mutableMapOf<String, Int>()
            val upcoming =
                queueWindows
                    .filterIndexed { index, _ -> index != currentWindowIndex }
                    .map { window ->
                        val uid = window.uid.toString()
                        val occurrence = (uidCounts[uid] ?: 0) + 1
                        uidCounts[uid] = occurrence
                        window to "$uid#$occurrence"
                    }

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentPadding =
                    PaddingValues(
                        bottom = metrics.gutter.dp,
                    ),
            ) {
                val currentWindow = queueWindows.getOrNull(currentWindowIndex)
                if (currentWindow != null) {
                    item(key = "current-${currentWindow.uid}") {
                        QueueRow(
                            window = currentWindow,
                            label = stringResource(R.string.now_playing),
                            isPlaying = true,
                            onClick = { playerConnection.player.togglePlayPause() },
                        )
                    }
                    item(key = "upNextHeader") {
                        SectionHeader(
                            title = stringResource(R.string.up_next),
                            actionLabel =
                                stringResource(R.string.clear).takeIf {
                                    queueWindows.size > 1
                                },
                            onAction = {
                                // Apple Music の Up Next 同様、再生中の 1 曲だけ残して
                                // 残りのキューを空にする。Player の API を呼ぶだけで
                                // 再生エンジン側のコードは変更しない。
                                val player = playerConnection.player
                                if (player.mediaItemCount == 0) return@SectionHeader
                                val keep = player.currentMediaItemIndex.coerceIn(0, player.mediaItemCount - 1)
                                if (player.mediaItemCount > keep + 1) {
                                    player.removeMediaItems(keep + 1, player.mediaItemCount)
                                }
                                if (keep > 0) {
                                    player.removeMediaItems(0, keep)
                                }
                            },
                        )
                    }
                }

                items(
                    items = upcoming,
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
                                // タイムラインが入れ替わっていて外せなかった場合、
                                // 行が画面外に居たままになるので戻す。
                                scope.launch { dismissState.reset() }
                            }
                        },
                    ) {
                        QueueRow(
                            window = window,
                            label = null,
                            isPlaying = false,
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
 * キュー 1 行。再生中の行だけ [SectionHeader] を上に載せ、
 * 残りは Apple Music の「次に再生」に合わせて見出しの下へ並べる。
 */
@Composable
private fun QueueRow(
    window: Timeline.Window,
    label: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    if (label != null) {
        SectionHeader(title = label)
    }

    val metadata = window.mediaItem.metadata
    val song =
        metadata ?: run {
            // メタデータが無い項目（外部スコープ等）でも
            // 行は潰さず、識別に使える文字だけ出す。
            MediaMetadata(
                id = window.mediaItem.mediaId,
                title = window.mediaItem.mediaId,
                artists = emptyList(),
                duration = 0,
            )
        }

    SongRow(
        song = song,
        isPlaying = isPlaying,
        onClick = onClick,
    )
}

/**
 * 左スワイプで裏に出す削除面。
 *
 * Apple Music の Up Next と同じく、行をスライドさせた方向の裏に赤い面と
 * 「削除」を置く。色は [ApplePalette.error][moe.rukamori.archivetune.design.ApplePalette]、
 * 文字はその面の上なので白で固定する。
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
 * 番号はそのまま [Player.removeMediaItem] に渡せない。タイムラインを引き直して
 * 先頭 period 番号が一致するウィンドウ（= メディア項目）番号を探して外す。
 * 1 ウィンドウ 1 period のこのアプリの並びでは両者はずれることがないが、
 * 引き直すことで将来の並べ替え実装にも耐える。
 *
 * 現在再生中の行は呼び出し側でスワイプ対象にしないので、再生が途切れない。
 *
 * @return 実際に外せたら `true`。タイムラインが入れ替わっていて対応する
 *   項目が見つからなかった場合は `false`（呼び出し側に行を戻させる）。
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
