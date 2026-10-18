/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.screens.rememberArtworkBackdrop

/** 再生位置の測り直し間隔（再生中）。 */
private const val POSITION_POLL_MS = 500L

/** 再生位置の測り直し間隔（停止中）。描画に必要な差は無いが止まらないようにする。 */
private const val POSITION_POLL_IDLE_MS = 1500L

/** 進捗バーの太さ。下端の細い線として覗かせる程度に抑える。 */
private val PROGRESS_HEIGHT = 2.dp

/**
 * Apple Music 風のミニプレイヤー。
 *
 * ナビゲーションバーの真上に**画面幅いっぱいの帯**として置く。角を丸めて浮かせた
 * 面は他社のプレイヤーの記号になるため使わない。Apple Music のタブバーと続けて
 * 1本の操作帯に見えるよう、上端のヘアラインを進行バーと兼ねる。
 *
 * 背景はジャケットから抜いた色を、テーマに合わせて暗く／明るく寄せたもの
 * （[rememberArtworkBackdrop]）。文字は乗った面の明るさから自動で反転する。
 *
 * - **タップ** → 全画面プレイヤーへ
 * - **横スワイプ** → 前の曲／次の曲（しきい値まで引っ張ると発火し、指を離すと戻る）
 * - **右端のボタン** → 次の曲
 * - 再生／一時停止は `PlayerConnection.isPlaying` と同期
 *
 * 再生中の曲が1つもないときは描画しない。ダミーの曲を並べることはない。
 *
 * @param onOpenPlayer 全画面プレイヤーを開く。
 */
@Composable
fun MiniPlayer(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection = LocalPlayerConnection.current ?: return
    val mediaMetadata by connection.mediaMetadata.collectAsState()
    val isPlaying by connection.isPlaying.collectAsState()
    val metadata = mediaMetadata ?: return

    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()
    val density = LocalDensity.current

    // アートから抜いた2色。解析が終わるまでは既定の灰が入る。
    val backdrop = rememberArtworkBackdrop(
        mediaId = metadata.id,
        thumbnailUrl = metadata.thumbnailUrl,
    )

    // ライトテーマなら白へ、ダークテーマなら黒へ寄せて面を作る。
    // ミニプレイヤーは常に下端にあるため、抽出色のままでは上部の明るい背景に
    // 浮いてしまう。テーマ側へ寄せて「置かれている場所」と同じ明度帯に揃える。
    val isLightTheme = palette.background.luminance() > 0.5f
    val container =
        if (isLightTheme) {
            lerp(backdrop.top, Color.White, CONTAINER_TOWARD_WHITE)
        } else {
            lerp(backdrop.top, Color.Black, CONTAINER_TOWARD_BLACK)
        }
    // 面の明るさから文字色を決める。抽出色の差で可読性が落ちないようにする。
    val contentColor =
        if (container.luminance() > 0.45f) Color(0xFF111111) else Color.White

    // 進行バー。曲ごとに測り直す。
    var positionMs by remember(metadata.id) { mutableLongStateOf(0L) }
    var durationMs by remember(metadata.id) { mutableLongStateOf(0L) }
    LaunchedEffect(metadata.id, isPlaying) {
        while (isActive) {
            positionMs = connection.player.currentPosition.coerceAtLeast(0L)
            val playerDuration = connection.player.duration
            if (playerDuration > 0L) durationMs = playerDuration
            delay(if (isPlaying) POSITION_POLL_MS else POSITION_POLL_IDLE_MS)
        }
    }
    val progress =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    // スワイプによる横移動。しきい値を超えると曲が変わり、離すとばねで戻る。
    var rawOffsetPx by remember { mutableFloatStateOf(0f) }
    val animatedOffsetPx by animateFloatAsState(
        targetValue = rawOffsetPx,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "miniPlayerSwipe",
    )
    val thresholdPx = with(density) { 72.dp.toPx() }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedOffsetPx },
    ) {
        Surface(
            shape = RectangleShape,
            color = container,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(metrics.miniPlayerHeight.dp)
                        .pointerInput(metadata.id) {
                            detectHorizontalDragGestures(
                                onDragStart = { rawOffsetPx = 0f },
                                onDragEnd = { rawOffsetPx = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    rawOffsetPx =
                                        (rawOffsetPx + dragAmount)
                                            .coerceIn(-thresholdPx * 1.2f, thresholdPx * 1.2f)
                                    if (abs(rawOffsetPx) >= thresholdPx) {
                                        // 右へ送ると前の曲、左へ送ると次の曲。
                                        if (rawOffsetPx > 0f) {
                                            connection.seekToPrevious()
                                        } else {
                                            connection.seekToNext()
                                        }
                                        rawOffsetPx = 0f
                                    }
                                },
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenPlayer() }
                        .padding(start = metrics.gutter.dp, end = metrics.xs.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = metadata.thumbnailUrl,
                    contentDescription = metadata.title,
                    cornerRadius = (metrics.artworkCorner - 2).coerceAtLeast(4).dp,
                    modifier = Modifier.size(46.dp),
                )

                Spacer(Modifier.width(metrics.md.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = metadata.title,
                        style = type.callout.copy(fontWeight = FontWeight.SemiBold),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metadata.artists.joinToString(", ") { it.name },
                        style = type.footnote,
                        color = contentColor.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                MiniTransportButton(
                    onClick = { connection.player.togglePlayPause() },
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp),
                    )
                }

                MiniTransportButton(
                    onClick = { connection.seekToNext() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.skip_next),
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        // 上端の細い進行バー。タブバーとの境目のヘアラインを兼ねる。
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(PROGRESS_HEIGHT)
                    .background(contentColor.copy(alpha = 0.24f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(contentColor),
            )
        }
    }
}

/** ミニプレイヤー上の丸い操作。タップ領域だけ確保し、面は持たせない。 */
@Composable
private fun MiniTransportButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** ライトへ寄せる割合。抽出色を残したまま明るい面にする。 */
private const val CONTAINER_TOWARD_WHITE = 0.72f

/** ダークへ寄せる割合。抽出色を残したまま沈んだ面にする。 */
private const val CONTAINER_TOWARD_BLACK = 0.58f
