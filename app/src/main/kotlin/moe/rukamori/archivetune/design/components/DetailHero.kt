/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 詳細画面の先頭（大アート＋題名＋再生ボタン）。
 *
 * アルバム・アーティスト・プレイリスト・検索結果が同じ形の冒頭を持つ。
 * 戻る矢印を先頭に置き、その下にアートを中央寄せする。Apple の詳細画面が
 * タイトルを見出しの下に落としている形に合わせている。
 *
 * @param subtitle アーティスト名や件数など。`null` なら描画しない。
 * @param metadata 年や総再生時間など、副次行。`null` なら描画しない。
 * @param onPlay 再生ボタン。`null` なら出さない（検索結果など）。
 * @param onShuffle シャッフルボタン。Apple Music のアーティスト画面と同じく
 *   再生の隣に並べる。`null` なら出さない。
 * @param artworkShape アートの形。アーティストは正円を渡す。
 */
@Composable
fun DetailHero(
    title: String,
    thumbnailUrl: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    metadata: String? = null,
    onPlay: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    artworkShape: Shape = RoundedCornerShape(12.dp),
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Column(modifier = modifier.fillMaxWidth()) {
        BackButton(onClick = onBack)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = metrics.gutter.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Artwork(
                url = thumbnailUrl,
                contentDescription = title,
                modifier =
                    Modifier
                        .size(208.dp)
                        .clip(artworkShape),
            )

            Spacer(Modifier.width(metrics.md.dp))

            Text(
                text = title,
                style = type.title1,
                color = palette.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = metrics.md.dp),
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = palette.labelSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = metrics.xs.dp),
                )
            }
            if (metadata != null) {
                Text(
                    text = metadata,
                    style = type.caption,
                    color = palette.labelTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (onPlay != null || onShuffle != null) {
                HeroActions(onPlay = onPlay, onShuffle = onShuffle)
            }

            Spacer(Modifier.width(metrics.block.dp))
        }
    }
}

/**
 * 見出しの下に置く「シャッフル」と「再生」。
 *
 * アルバム・プレイリストの見出し（`DetailActionsRow`）と同じ並びに揃える。
 * 左が控えめなシャッフル、右が幅いっぱいの再生。**どの再生リストでもこの2つが
 * 出ている**状態を作るのが目的なので、片方だけのときも同じ位置に置く。
 */
@Composable
private fun HeroActions(
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = metrics.gutter.dp,
                    end = metrics.gutter.dp,
                    top = metrics.section.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
    ) {
        if (onShuffle != null) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.fill)
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
                    tint = palette.onFill,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.shuffle),
                    style = type.subhead.copy(fontWeight = FontWeight.SemiBold),
                    color = palette.onFill,
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
                        // 地色と文字色を反転させる。暗いテーマでは白地に黒、
                        // 明るいテーマでは黒地に白になり、どちらでも浮いて見える。
                        .background(palette.onBackground)
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
                    tint = palette.background,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(metrics.sm.dp))
                Text(
                    text = stringResource(R.string.play),
                    style = type.headline,
                    color = palette.background,
                )
            }
        }
    }
}

