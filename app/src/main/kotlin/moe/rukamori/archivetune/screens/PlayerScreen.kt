/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.Artwork
import moe.rukamori.archivetune.design.components.FavoriteButton
import moe.rukamori.archivetune.design.components.PlayerCircleButton
import moe.rukamori.archivetune.design.components.Scrubber
import moe.rukamori.archivetune.design.components.SongMenuSheet
import moe.rukamori.archivetune.design.components.formatDuration
import moe.rukamori.archivetune.domain.player.ImmersivePlayerAction
import moe.rukamori.archivetune.domain.player.ImmersivePlayerEvent
import moe.rukamori.archivetune.domain.player.ImmersivePlayerScreenState
import moe.rukamori.archivetune.domain.player.ImmersivePlayerUiModel
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.viewmodels.ImmersivePlayerViewModel

/**
 * 全画面プレイヤー（Now Playing）。
 *
 * 背面はジャケットから抜いた2色のグラデーション。ジャケットが変わると
 * [androidx.compose.animation.animateColorAsState] で色が溶けて入れ替わる。
 *
 * 構成は上から下へ：閉じる → 巨大なジャケット → 曲名・アーティスト（左寄せ、
 * 右に★と「…」）→ 再生時間 → 搬送3点 → 音量 → 出力先とキュー。
 * **シャッフルとリピートはここに置かない**（Apple Music と同じくキュー画面の
 * ピル型トグルに寄せる）。
 *
 * 状態の読み取りは [ImmersivePlayerViewModel] に寄せている。★（お気に入り）と
 * 出力先デバイスはこの ViewModel がしか持たないため。シークと音量の**命令**は
 * 動作が確立している [LocalPlayerConnection] をそのまま通す。
 *
 * @param onDismiss 下向き矢印。プレイヤーを閉じる。
 * @param onOpenLyrics 歌詞ボタン。
 * @param onOpenQueue キューボタン。
 */
@Composable
fun PlayerScreen(
    onDismiss: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val viewModel: ImmersivePlayerViewModel = hiltViewModel()
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val type = appleType()
    val metrics = appleMetrics()

    // 曲メニューに渡す本体。ViewModel のモデルには曲情報が無いので接続側から読む。
    val currentSong by playerConnection.mediaMetadata.collectAsState()

    // ViewModel が再生の状態を追うため、接続を渡して切る。
    DisposableEffect(playerConnection, viewModel) {
        viewModel.bind(playerConnection)
        onDispose { viewModel.unbind(playerConnection) }
    }

    val screenState by viewModel.state.collectAsState()
    val model: ImmersivePlayerUiModel? =
        when (val state = screenState) {
            is ImmersivePlayerScreenState.Success -> state.model
            is ImmersivePlayerScreenState.Error -> state.previous
            else -> null
        }

    var showSongMenu by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }

    // 曲が変わったらシーク中の位置合わせを捨てる。
    var pendingSeek by remember(model?.mediaId) { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ImmersivePlayerEvent.OpenMenu -> showSongMenu = true
                is ImmersivePlayerEvent.OpenAlbum,
                is ImmersivePlayerEvent.OpenArtist,
                -> Unit // ここでは詳細画面へは進めない（再生に絞る画面のため）。
            }
        }
    }

    // ドラッグ中は ViewModel の位置更新が表示を上書きしないようにする。
    LaunchedEffect(model?.mediaId, model?.isPlaying) {
        var unmatchedTicks = 0
        while (isActive) {
            delay(100)
            if (scrubbing) continue

            val target = pendingSeek
            val realPosition = playerConnection.player.currentPosition
            when {
                target == null -> unmatchedTicks = 0

                abs(realPosition - target) < 500L || ++unmatchedTicks > 25 -> {
                    // 追いついた、または2.5秒たって追いつかない＝シーク自体が
                    // 無視された（バッファ切れ・エラー等）。表示を実測へ戻す。
                    pendingSeek = null
                    unmatchedTicks = 0
                }

                else -> Unit
            }
        }
    }

    fun seekTo(ms: Long) {
        val duration = model?.durationMs ?: 0L
        val clamped = ms.coerceIn(0L, duration.coerceAtLeast(0L))
        pendingSeek = clamped
        playerConnection.player.seekTo(clamped)
        if (playerConnection.playbackState.value == Player.STATE_READY) {
            playerConnection.player.playWhenReady = true
        }
    }

    val mediaId = model?.mediaId
    val backdrop = rememberArtworkBackdrop(mediaId = mediaId, thumbnailUrl = model?.artworkUrl)

    // 背景の色は曲ごとに変わる。切り替わりは溶けるように行う。
    val animatedTop by animateColorAsState(targetValue = backdrop.top, animationSpec = tween(650), label = "backdropTop")
    val animatedBottom by animateColorAsState(targetValue = backdrop.bottom, animationSpec = tween(650), label = "backdropBottom")

    // 停止中はジャケットがわずかに沈む。動いているときと同じ表情にしない。
    val artworkScale by animateFloatAsState(targetValue = if (model?.isPlaying == true) 1f else 0.965f, animationSpec = tween(260), label = "artworkScale")

    // 表示位置は、シーク先をまだプレイヤーが迎えていないときはその位置を保持する。
    val displayedPosition = pendingSeek ?: model?.positionMs ?: 0L
    val duration = model?.durationMs ?: 0L
    val volume = model?.volume ?: 0f

    val title = model?.title.orEmpty()
    val artistNames = model?.artists.orEmpty().joinToString(", ") { it.name }
    val deviceName = model?.outputDevice?.name.orEmpty().ifBlank { model?.sourceTitle.orEmpty() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                // 背景はジャケットそのもの。縮めた絵を画面いっぱいに引き伸ばして
                // 敷き、2色グラデーションを半透明で重ねて沈める。文字は必ず
                // グラデーションの上に乗るので、下端は必ず暗い面になる。
                .drawWithContent {
                    backdrop.artwork?.let { art ->
                        // 画面を覆うぶんだけ引き伸ばし、はみ出しは端で切る。
                        val scale =
                            max(
                                size.width / art.width.toFloat(),
                                size.height / art.height.toFloat(),
                            )
                        val dstWidth = (art.width * scale).toInt()
                        val dstHeight = (art.height * scale).toInt()
                        drawImage(
                            image = art,
                            dstOffset =
                                IntOffset(
                                    ((size.width - dstWidth) / 2).toInt(),
                                    ((size.height - dstHeight) / 2).toInt(),
                                ),
                            dstSize = IntSize(dstWidth, dstHeight),
                        )
                    }
                    // 絵が透ける帯は中ほどだけ。上端はボタン、下端は操作系が
                    // 乗るので、そこは重く落として文字の裏を一定に保つ。
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                0.00f to animatedTop.copy(alpha = 0.90f),
                                0.28f to animatedTop.copy(alpha = 0.70f),
                                0.62f to animatedTop.copy(alpha = 0.88f),
                                1.00f to animatedBottom,
                            ),
                    )
                    drawContent()
                }
                .windowInsetsPadding(windowInsets)
                .padding(horizontal = metrics.gutter.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── 上端：閉じる（中央に寄せる。Apple Music の取っ手と同じ位置） ──
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            PlayerIconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = stringResource(R.string.close),
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        // ── 巨大なジャケット（画面上半分を占める） ─────────────
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                url = model?.artworkUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 430.dp)
                        .aspectRatio(1f)
                        // 影は内側に置き、外側の graphicsLayer でまとめて縮める。
                        // 逆にすると停止中にジャケットだけ縮んで影が残り、
                        // 周囲に暗い輪郭が残る。
                        .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale }
                        .shadow(
                            elevation = 14.dp,
                            shape = RoundedCornerShape(metrics.artworkCorner.dp),
                            ambientColor = Color.Black,
                            spotColor = Color.Black,
                        ),
            )
        }

        // ── 曲名・アーティスト（左寄せ、右に★と「…」） ──────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = metrics.md.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = type.title3,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artistNames.isNotEmpty()) {
                    Text(
                        text = artistNames,
                        style = type.callout,
                        color = Color.White.copy(alpha = 0.66f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            FavoriteButton(
                isFavorite = model?.isLiked == true,
                onClick = { viewModel.onAction(ImmersivePlayerAction.ToggleLike) },
            )

            Spacer(Modifier.width(metrics.xs.dp))

            PlayerCircleButton(onClick = { showSongMenu = true }) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = stringResource(R.string.more),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── 書式（対応していれば表示。無いときは行自体を描かない） ──
        if (model?.formatDetails.isNullOrBlank().not()) {
            Text(
                text = model?.formatDetails.orEmpty(),
                style = type.footnote,
                color = Color.White.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.xs.dp),
            )
        }

        // ── スクラブバーと時間 ──────────────────────────────
        // 画面の主役はジャケットの色なので、進行バーは白にして主張を消す。
        Scrubber(
            positionMs = displayedPosition,
            durationMs = duration,
            onSeek = { seekTo(it) },
            onDragStateChange = { dragging ->
                scrubbing = dragging
                if (!dragging) pendingSeek = null
            },
            playedColor = Color.White,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = metrics.md.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatDuration((displayedPosition / 1000L).toInt()) ?: "0:00",
                style = type.caption,
                color = Color.White.copy(alpha = 0.60f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDuration((duration / 1000L).toInt()) ?: "0:00",
                style = type.caption,
                color = Color.White.copy(alpha = 0.60f),
            )
        }

        // ── 搬送3点 ───────────────────────────────────────
        // Apple Music 同様、この行には前・再生・次の3つだけを置く。
        // シャッフルとリピートはキュー画面のピル型トグルへ移した。
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(92.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PlayerIconButton(onClick = { playerConnection.seekToPrevious() }) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.widget_previous),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }

            PlayPauseButton(
                isPlaying = model?.isPlaying == true,
                onClick = { playerConnection.player.togglePlayPause() },
            )

            PlayerIconButton(onClick = { playerConnection.seekToNext() }) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.next),
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
        }

        // ── 音量 ─────────────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(if (volume <= 0.01f) R.drawable.volume_off else R.drawable.volume_up),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(16.dp),
            )

            Scrubber(
                positionMs = (volume.coerceIn(0f, 1f) * 1000f).roundToLong(),
                durationMs = 1000L,
                onSeek = { ms -> playerConnection.player.volume = (ms / 1000f).coerceIn(0f, 1f) },
                playedColor = Color.White,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = metrics.sm.dp),
            )

            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = stringResource(R.string.volume),
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(19.dp),
            )
        }

        // ── 出力先とキュー ────────────────────────────────
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(bottom = metrics.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.cast_output),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.60f),
                modifier = Modifier.size(19.dp),
            )

            Spacer(Modifier.width(metrics.sm.dp))

            Text(
                text = deviceName,
                style = type.footnote,
                color = Color.White.copy(alpha = 0.60f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            PlayerIconButton(onClick = onOpenLyrics) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = stringResource(R.string.lyrics),
                    tint = Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(21.dp),
                )
            }

            PlayerIconButton(onClick = onOpenQueue) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = stringResource(R.string.queue),
                    tint = Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }

    // 右上の「…」から一覧と同じ曲メニューを開く。全画面プレイヤーから
    // アーティストやアルバムの画面へは移動しないため、再生に絞る操作だけ出す。
    if (showSongMenu) {
        currentSong?.let { song ->
            SongMenuSheet(
                song = song,
                onDismiss = { showSongMenu = false },
                onPlayNext = {
                    playerConnection.playNext(song.toMediaItem())
                    showSongMenu = false
                },
                onAddToQueue = {
                    playerConnection.addToQueue(song.toMediaItem())
                    showSongMenu = false
                },
                onPlayRadio = {
                    playerConnection.playRadioFrom(song)
                    showSongMenu = false
                },
            )
        }
    }
}

/** 再生・一時停止の大きな円ボタン。白い面に濃いアイコンを載せる。 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(64.dp)
                .background(Color.White, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
            contentDescription = if (isPlaying) stringResource(R.string.widget_pause) else stringResource(R.string.play),
            tint = Color.Black,
            modifier = Modifier.size(if (isPlaying) 30.dp else 28.dp),
        )
    }
}

/** 枠を作らないアイコンボタン。タップ領域だけ確保し、面は出さない。 */
@Composable
private fun PlayerIconButton(
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
