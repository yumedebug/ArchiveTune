/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette

/**
 * 浮いた面（ナビバー・ミニプレイヤー・シート・設定カード）の共通の器。
 *
 * **かつてのリキッドグラス（背後をブラーする半透明面）は全廃した。**
 * ブラーの上に薄い文字を載せると、明るいジャケットの上でラベルが読めなくなる。
 * いまは [moe.rukamori.archivetune.design.ApplePalette.surface] の**不透明な塗り**と
 * 0.5dp のヘアラインだけで面を作る。視認性を最優先にした結果で、
 * Apple Music のバー・シートも同じ「不透明な面＋境界線」で組まれている。
 *
 * @param shape 面の輪郭。バーなら `RectangleShape`、シートなら上端だけ丸める。
 * @param color 塗り。既定は [moe.rukamori.archivetune.design.ApplePalette.surface]。
 */
@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = applePalette()
    val metrics = appleMetrics()

    val fill = if (color == Color.Unspecified) palette.surface else color

    Box(
        modifier =
            modifier
                .clip(shape)
                .background(fill)
                .border(
                    width = metrics.hairline.dp,
                    color = palette.separator,
                    shape = shape,
                ),
    ) {
        content()
    }
}
