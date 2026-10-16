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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 再生系画面（Now Playing / キュー / 歌詞）で共有する操作部品。
 *
 * 3画面は同じ背景（ジャケット由来の面）の上に載るので、部品側は
 * 「白の半透明な面 ＋ 白い記号」だけで組み、画面ごとの色を持たせない。
 * 枠線は使わない（Apple Music 同様、面と余白だけで境界を作る）。
 */

/**
 * 枠を作らない円形アイコンボタン。
 *
 * [content] に記号を渡す。面は [backgroundAlpha] の白のみで、押下の当たり判定は
 * [clickable] が確保する。
 *
 * @param backgroundAlpha 円の白。0 なら面を出さない。
 * @param contentDescription アクセシビリティ用の説明。
 */
@Composable
fun PlayerCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundAlpha: Float = 0.14f,
    buttonSize: Dp = 40.dp,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .size(buttonSize)
                .clip(CircleShape)
                .then(
                    if (backgroundAlpha > 0f) {
                        Modifier.background(Color.White.copy(alpha = backgroundAlpha))
                    } else {
                        Modifier
                    },
                ).clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * キューと歌詞の上端に置く、縮んだ Now Playing の帯。
 *
 * 小さなジャケット → 曲名とアーティスト → ★（お気に入り）→ 「…」 の順。
 * Apple Music がキュー・歌詞の両方で同じ帯を出す形をなぞっている。
 *
 * @param isFavorite いまお気に入りに入っているか。★の塗りが変わる。
 * @param onToggleFavorite ★を押したとき。
 * @param onOpenMenu 「…」を押したとき。
 */
@Composable
fun PlayerHeaderRow(
    artworkUrl: String?,
    title: String,
    subtitle: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = appleMetrics()
    val type = appleType()

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(
            url = artworkUrl,
            contentDescription = null,
            modifier = Modifier.size(44.dp),
        )

        Spacer(Modifier.width(metrics.md.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = type.headline,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = Color.White.copy(alpha = 0.62f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        FavoriteButton(isFavorite = isFavorite, onClick = onToggleFavorite)

        Spacer(Modifier.width(metrics.xs.dp))

        PlayerCircleButton(onClick = onOpenMenu) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more),
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * ★のボタン。輪郭の星と、塗りつぶしの星を入れ替える。
 *
 * 有効時はアクセント色、無効時は白を落とした色で控える（面は出さない）。
 */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = applePalette()

    PlayerCircleButton(
        onClick = onClick,
        modifier = modifier,
        backgroundAlpha = 0f,
    ) {
        Icon(
            painter = painterResource(if (isFavorite) R.drawable.star_filled else R.drawable.star),
            contentDescription = null,
            tint = if (isFavorite) palette.accent else Color.White.copy(alpha = 0.72f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * キュー上部のピル型トグル（シャッフル／リピート／自動再生）。
 *
 * 選択中は白い面を敷いて立ち上げ、非選択は半透明の面に落とす。
 * 幅は一定のまま面と記号だけが変わる（Apple Music のトグル行と同じ）。
 *
 * @param icon 表示する記号。
 * @param selected いま有効か。
 */
@Composable
fun PillToggle(
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .size(width = 52.dp, height = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = if (selected) 0.92f else 0.14f))
                .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (selected) Color.Black else Color.White.copy(alpha = 0.80f),
            modifier = Modifier.size(19.dp),
        )
    }
}

/** 帯の右側（★と「…」）だけを並べるための受け口。 */
typealias PlayerHeaderActionsScope = RowScope
