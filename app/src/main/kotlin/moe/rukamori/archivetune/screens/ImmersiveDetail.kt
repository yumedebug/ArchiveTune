/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.Artwork
import moe.rukamori.archivetune.design.components.BackButton
import moe.rukamori.archivetune.design.components.PinnedTitleBar
import moe.rukamori.archivetune.design.components.rememberTitleCollapse

/**
 * 詳細画面の「…」に出す1アクション。
 *
 * Apple の詳細画面は右上に溢れメニューを持つ。ここでは**実際に効く操作だけ**を載せる
 * （空のメニュー項目や装飾だけの項目は作らない）。
 */
class DetailMenuAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * アルバム・プレイリスト詳細の没入型スケルトン。
 *
 * Apple Music の詳細画面と同じ階層を作る。
 *
 * - 画面いちばん上から**横幅いっぱい**にアートワークを描き、ステータスバーの背後まで届ける
 * - ジャケットの下端は徐々に透明化し、アートから抽出した色の背景へ溶け込ませる（境界線は引かない）
 * - 背景は上=明るめ・下=暗めのグラデーション。文字はここで視認性を確保する
 * - スクロールでアートワークが消えたら [PinnedTitleBar]（戻る・「…」付き）へ切り替わる
 *
 * アートワークの色は [rememberArtworkBackdrop]（既存の抽出処理）から取る。抽出色の上へ
 * 48px に縮めた画像を引き伸ばし、48dp のブラーでさらに溶かす。ブラー自体は Android 12 以降の
 * API で、未対応端末では引き伸ばしのみで成立する（面は欠けない）。
 *
 * @param backdropKey 抽出色のキャッシュキー。同じ詳細を再表示したときに再抽出しない。
 * @param subtitle アーティスト名や作成者。`null` なら描画しない。
 * @param metadata 年・曲数・公開日などの副次行。`null` なら描画しない。
 * @param description 説明文（グレーの小さめ文字）。取得できない画面は `null`。
 * @param onPlay 白いカプセルの再生ボタン。`null` なら出さない。
 * @param onShuffle 小さな半透明のシャッフルボタン。`null` なら出さない。
 * @param onEnqueue 丸い半透明の「キューに追加」ボタン。`null` なら出さない。
 * @param menuActions 「…」に出す操作。空なら「…」ごと描画しない。
 * @param content 曲一覧。ヒーローの下へそのまま続けられるよう [LazyListScope] で受け取る。
 */
@Composable
fun ImmersiveDetailScaffold(
    title: String,
    thumbnailUrl: String?,
    backdropKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    metadata: String? = null,
    description: String? = null,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    menuActions: List<DetailMenuAction> = emptyList(),
    content: LazyListScope.() -> Unit,
) {
    val metrics = appleMetrics()
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val density = LocalDensity.current

    val backdrop = rememberArtworkBackdrop(mediaId = backdropKey, thumbnailUrl = thumbnailUrl)
    val listState = rememberLazyListState()

    val topInsetDp = with(density) { windowInsets.getTop(density).toDp() }
    val bottomInsetDp = with(density) { windowInsets.getBottom(density).toDp() }

    // アートは正方形で横幅いっぱい。ヒーローの下端はこの高さに一致する。
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val artHeight = maxWidth
        val artFraction =
            if (maxHeight.value > 0f) (artHeight.value / maxHeight.value).coerceIn(0f, 1f) else 0.5f

        val topColor = lerp(backdrop.top, Color.Black, 0.35f)
        val artColor = lerp(backdrop.top, Color.Black, 0.80f)
        val bottomColor = lerp(backdrop.bottom, Color.Black, 0.20f)

        // アートワークの下端（= artFraction の位置）が暗くなるよう配列する。
        // ここがヘッダ文字の下地になるため、そこだけは落とし込みを強めに取る。
        val background =
            Brush.verticalGradient(
                0f to topColor,
                artFraction to artColor,
                1f to bottomColor,
            )

        val collapseThreshold = maxOf(artHeight - 44.dp - topInsetDp, 0.dp)
        val collapsed = rememberTitleCollapse(listState, threshold = collapseThreshold)

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(background),
        ) {
            // アートの色を背景へ広げる層。1枚の絵を全画面へ引き伸ばしてから溶かす。
            backdrop.artwork?.let { artwork ->
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.38f,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(48.dp),
                )
            }
            // 下へ向かって暗く落とすスクリム。白い文字の視認性はこちらが担保する。
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.30f),
                                artFraction to Color.Black.copy(alpha = 0.45f),
                                1f to Color.Black.copy(alpha = 0.80f),
                            ),
                        ),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 下端のガラス（ミニプレイヤー＋ナビ＋ジェスチャーバー）の分だけ余白を確保する。
            contentPadding = PaddingValues(bottom = bottomInsetDp + metrics.block.dp),
        ) {
            item(key = "hero") {
                DetailHeroBlock(
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    subtitle = subtitle,
                    metadata = metadata,
                    description = description,
                    artHeight = artHeight,
                    artColor = artColor,
                    topInsetDp = topInsetDp,
                    onBack = onBack,
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    onEnqueue = onEnqueue,
                    menuActions = menuActions,
                )
            }
            content()
        }

        // アートワークが画面外に出たあとに差し込む、コンパクトなタイトルバー。
        val trailingAction: (@Composable RowScope.() -> Unit)? =
            if (menuActions.isEmpty()) {
                null
            } else {
                { DetailMoreButton(actions = menuActions) }
            }
        PinnedTitleBar(
            title = title,
            visible = collapsed,
            leading = { BackButton(onClick = onBack, tint = Color.White) },
            trailing = trailingAction,
            containerColor = artColor,
        )
    }
}

/**
 * ヒーロー本体。巨大アートワーク＋重なる戻る／「…」＋情報＋操作ボタン。
 *
 * ステータスバーの背後までアートを描くため、このブロックだけはインセットを内側で受ける。
 */
@Composable
private fun DetailHeroBlock(
    title: String,
    thumbnailUrl: String?,
    subtitle: String?,
    metadata: String?,
    description: String?,
    artHeight: Dp,
    artColor: Color,
    topInsetDp: Dp,
    onBack: () -> Unit,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    onEnqueue: (() -> Unit)?,
    menuActions: List<DetailMenuAction>,
) {
    val type = appleType()
    val metrics = appleMetrics()
    val windowInsets = LocalPlayerAwareWindowInsets.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Artwork(
                url = thumbnailUrl,
                contentDescription = title,
                cornerRadius = 0.dp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
            )

            // ジャケットの下端を徐々に透明化し、背景へ溶かす。境界線は作らない。
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(artHeight * 0.42f)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to artColor,
                            ),
                        ),
            )

            // ステータスバーと押下ボタンの下地。上端ほど濃く、下へ抜ける。
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .height(topInsetDp + 64.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                1f to Color.Black.copy(alpha = 0f),
                            ),
                        ),
            )

            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top))
                        .height(44.dp)
                        .padding(horizontal = metrics.gutter.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BackButton(onClick = onBack, tint = Color.White)
                Spacer(Modifier.weight(1f))
                if (menuActions.isNotEmpty()) {
                    DetailMoreButton(actions = menuActions)
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = metrics.gutter.dp,
                        end = metrics.gutter.dp,
                        top = metrics.md.dp,
                    ),
        ) {
            Text(
                text = title,
                style = type.title1,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(metrics.xs.dp))
                Text(
                    text = subtitle,
                    style = type.callout,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (metadata != null) {
                Spacer(Modifier.height(metrics.xs.dp))
                Text(
                    text = metadata,
                    style = type.footnote,
                    color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (description != null) {
                Spacer(Modifier.height(metrics.sm.dp))
                Text(
                    text = description,
                    style = type.footnote,
                    color = Color.White.copy(alpha = 0.45f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        DetailActionsRow(
            onShuffle = onShuffle,
            onPlay = onPlay,
            onEnqueue = onEnqueue,
        )
    }
}

/**
 * シャッフル（半透明）／再生（白いカプセル・最目立たせる）／追加（半透明の丸）。
 *
 * 枠線は引かず、塗りとアイコンだけで構成する。ボタンが無い場合はその枠も閉じない。
 */
@Composable
private fun DetailActionsRow(
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onEnqueue: (() -> Unit)?,
) {
    val type = appleType()
    val metrics = appleMetrics()
    val translucent = Color.White.copy(alpha = 0.14f)

    if (onShuffle == null && onPlay == null && onEnqueue == null) return

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = metrics.gutter.dp,
                    end = metrics.gutter.dp,
                    top = metrics.section.dp,
                    bottom = metrics.xs.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
    ) {
        if (onShuffle != null) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(translucent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onShuffle() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = stringResource(R.string.shuffle),
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.shuffle),
                    style = type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }

        if (onPlay != null) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onPlay() }
                        .padding(horizontal = metrics.gutter.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(metrics.sm.dp))
                Text(
                    text = stringResource(R.string.play),
                    style = type.headline,
                    color = Color.Black,
                )
            }
        }

        if (onEnqueue != null) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(translucent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onEnqueue() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = stringResource(R.string.add_to_queue),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/**
 * 画像の上に置く「…」。押すと操作の数だけメニューを出す。
 *
 * ヒーローとタイトルバーの両方から呼ばれ、どちらの高さでも収まるようアンカーは自身の外枠に取る。
 */
@Composable
private fun DetailMoreButton(actions: List<DetailMenuAction>) {
    val expanded = remember { mutableStateOf(false) }

    Box {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded.value = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more_label),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            style = appleType().body,
                        )
                    },
                    onClick = {
                        expanded.value = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/**
 * 一覧からURLを共有する。
 *
 * ブラウザを開かずに端末の共有シートへ渡すだけ。URLを持たない項目（端末内のアルバム等）では
 * 呼ばない。
 */
internal fun shareText(
    context: Context,
    text: String,
) {
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, null))
}
