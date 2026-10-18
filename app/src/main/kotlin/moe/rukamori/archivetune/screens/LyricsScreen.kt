/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.CurrentTrackHeader
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.viewmodels.LyricsRenderScreenState
import moe.rukamori.archivetune.viewmodels.LyricsRenderViewModel

/** 現在行を画面の上から何割の位置へ置くか。 */
private const val ACTIVE_LINE_ANCHOR = 0.34f

/** 現在行以外の不透明度。 */
private const val INACTIVE_LINE_ALPHA = 0.30f

/** 上下で歌詞を溶かす帯の高さ。 */
private val FADE_HEIGHT = 72.dp

/**
 * 全画面の同期歌詞。
 *
 * 歌詞そのものの解析は既存の [LyricsRenderViewModel] に任せる（歌詞プロバイダと
 * 解析は触らない方針）。この画面から足すのは表示と同期、そして**取得のきっかけ**
 * だけ。端末に歌詞が無いまま画面を開くと、行が作られずに読み込みで止まって
 * しまうため、[LyricsRenderViewModel.ensureLyrics] を呼んで1曲につき1回だけ
 * 取りに行かせる。
 *
 * Apple Music の歌詞と同じ組み方にしている。**行の大きさは全行で同じ**にし、
 * 現在行だけを白、それ以外を薄く落とす（大きさを変えると視線が行ごとに跳ねる）。
 * 前後の行は上下の帯で背景へ溶かし、いま歌っている一節だけが浮かぶようにする。
 *
 * 同期は行単位。再生位置が進むと「開始時刻が最も新しい行」がアクティブになり、
 * 画面の上から [ACTIVE_LINE_ANCHOR] の位置へ自動で送られる。
 *
 * 行をタップするとその行の先頭へシークする。歌詞側からも再生へ戻せる。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val viewModel: LyricsRenderViewModel = hiltViewModel()
    val type = appleType()

    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()
    val renderState by viewModel.state.collectAsState()

    val mediaId = mediaMetadata?.id

    var position by remember(mediaId) { mutableLongStateOf(playerConnection.player.currentPosition) }
    var pendingSeek by remember(mediaId) { mutableStateOf<Long?>(null) }

    // 歌詞の読み込みは曲ごとに1回だけ。duration が後から確定し直しても
    // 再バインドすると取得がやり直されるため、1曲で固定する。
    LaunchedEffect(mediaId) {
        val metadata = mediaMetadata
        if (metadata == null || metadata.id.isBlank()) return@LaunchedEffect
        val resolved =
            metadata.duration.takeIf { it > 0 }?.toLong()?.times(1000L)
                ?: playerConnection.player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
                ?: 0L
        viewModel.bind(metadata.id, resolved)
        // 端末に歌詞が無ければここで取りに行かせる。取得が無いと
        // この画面は読み込みのまま止まってしまう。
        viewModel.ensureLyrics(metadata)
    }

    LaunchedEffect(mediaId, playbackState) {
        var unmatchedTicks = 0
        while (isActive) {
            delay(100)
            val realPosition = playerConnection.player.currentPosition
            val target = pendingSeek
            when {
                target == null -> {
                    position = realPosition
                    unmatchedTicks = 0
                }

                abs(realPosition - target) < 500L || ++unmatchedTicks > 25 -> {
                    pendingSeek = null
                    unmatchedTicks = 0
                    position = realPosition
                }

                else -> Unit
            }
        }
    }

    fun seekTo(ms: Long) {
        pendingSeek = ms
        position = ms
        playerConnection.player.seekTo(ms)
        if (playbackState == Player.STATE_READY) playerConnection.player.playWhenReady = true
    }

    val backdrop = rememberArtworkBackdrop(mediaId = mediaId, thumbnailUrl = mediaMetadata?.thumbnailUrl)

    val lines =
        when (val prepared = renderState) {
            is LyricsRenderScreenState.Success -> prepared.lyrics.lines
            else -> emptyList()
        }

    // 再生位置以下の最後の行。まだ1行目より前なら0をアクティブにする（イントロ扱い）。
    val currentIndex =
        remember(position, lines) {
            if (lines.isEmpty()) {
                -1
            } else {
                lines.indexOfLast { it.startMs <= position }.coerceAtLeast(0)
            }
        }

    val listState = rememberLazyListState()
    var viewportHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // 現在行を上から [ACTIVE_LINE_ANCHOR] の位置へ置けるだけの前後の余白。
    val anchorSpace = with(density) { (viewportHeight * ACTIVE_LINE_ANCHOR).toDp() }

    LaunchedEffect(currentIndex, viewportHeight, lines.size) {
        if (currentIndex !in lines.indices || viewportHeight <= 0) return@LaunchedEffect
        val offset = with(density) { -(viewportHeight * ACTIVE_LINE_ANCHOR).toInt() }
        listState.animateScrollToItem(currentIndex, offset)
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(backdrop.top, backdrop.bottom),
                    ),
                )
                .windowInsetsPadding(windowInsets),
    ) {
        // 上端は Now Playing の縮小版（Apple Music のキュー・歌詞と同じ骨格）。
        CurrentTrackHeader(onBack = onBack)

        when (val state = renderState) {
            is LyricsRenderScreenState.Loading -> {
                LoadingState(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }

            is LyricsRenderScreenState.Empty -> {
                EmptyState(
                    title = stringResource(R.string.lyrics),
                    message = stringResource(R.string.lyrics_not_found),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }

            is LyricsRenderScreenState.Error -> {
                ErrorState(
                    title = stringResource(R.string.lyrics),
                    message = stringResource(R.string.lyrics_not_found),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                )
            }

            is LyricsRenderScreenState.Success -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .onSizeChanged { viewportHeight = it.height },
                        horizontalAlignment = Alignment.Start,
                    ) {
                        // 端に寄った行が背景へ溶けるように、前後に余白を置く。
                        item(key = "lead") {
                            Box(Modifier.height(anchorSpace))
                        }

                        itemsIndexed(
                            items = state.lyrics.lines,
                            key = { index, _ -> index },
                        ) { index, line ->
                            val isCurrent = index == currentIndex

                            // 大きさは全行で同じ。変わってよいのは白の濃さだけ。
                            val color by
                                animateColorAsState(
                                    targetValue =
                                        if (isCurrent) {
                                            Color.White
                                        } else {
                                            Color.White.copy(alpha = INACTIVE_LINE_ALPHA)
                                        },
                                    animationSpec = tween(280),
                                    label = "lyricLine",
                                )

                            val text = line.text.ifEmpty {
                                if (line.isInstrumental) "♪" else ""
                            }
                            if (text.isNotEmpty()) {
                                Text(
                                    text = text,
                                    style = type.title1,
                                    color = color,
                                    textAlign = TextAlign.Start,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { seekTo(line.startMs) }
                                            .padding(horizontal = 24.dp, vertical = 6.dp),
                                )
                            }

                            if (!line.translation.isNullOrBlank()) {
                                Text(
                                    text = line.translation.orEmpty(),
                                    style = type.subhead,
                                    color =
                                        Color.White.copy(
                                            alpha = if (isCurrent) 0.70f else 0.22f,
                                        ),
                                    textAlign = TextAlign.Start,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                            }
                        }

                        item(key = "tail") {
                            Box(Modifier.height(anchorSpace * 2))
                        }
                    }

                    // 上端はヘッダーへ、下端は画面外へ溶かす。
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(FADE_HEIGHT)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(backdrop.top, Color.Transparent),
                                    ),
                                ),
                    )
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(FADE_HEIGHT)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, backdrop.bottom),
                                    ),
                                ),
                    )
                }
            }
        }
    }
}
