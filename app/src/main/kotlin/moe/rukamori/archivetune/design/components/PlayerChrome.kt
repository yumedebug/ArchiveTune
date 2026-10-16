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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.extensions.toMediaItem

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
    val palette = applePalette()

    Box(
        modifier =
            modifier
                .size(buttonSize)
                .clip(CircleShape)
                .then(
                    if (backgroundAlpha > 0f) {
                        Modifier.background(palette.fill.copy(alpha = backgroundAlpha))
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
    val palette = applePalette()

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
                color = palette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = type.footnote,
                    color = palette.labelSecondary,
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
                tint = palette.onBackground,
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
            tint = if (isFavorite) palette.accent else palette.labelSecondary,
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
    val palette = applePalette()

    Box(
        modifier =
            modifier
                .size(width = 52.dp, height = 36.dp)
                .clip(RoundedCornerShape(18.dp))
                // 選択中はアクセントの面、非選択は中立の面。どちらも
                // palette 経由なので、キューの背景がどちらのテーマでも消えない。
                .background(if (selected) palette.accent else palette.fill)
                .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (selected) Color.White else palette.onFill,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * キューと歌詞の上端に置く、再生中曲の帯（戻る矢印つき）。
 *
 * Apple Music はこの2画面に Now Playing の縮小版を頭へ乗せ、下端を
 * 共通の操作行にする。ここでは上端をこの部品で代用する。
 *
 * ★の塗りは [MediaMetadata.liked] を使わない。**曲ごとの追従更新が無い**ため
 * （`MusicService` がライク後にメタデータを書き直さない）、DB を直接購読する。
 * こうしないと押しても星が埋まらない。
 *
 * 「…」の曲メニューもこの部品が内包する。渡すラムダが無いと押しても動かない
 * ボタンになり、それは動かすな偽物になるため。
 *
 * @param onBack 戻る矢印。上位の画面へ戻す。
 */
@Composable
fun CurrentTrackHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val metrics = appleMetrics()
    val metadata by playerConnection.mediaMetadata.collectAsState()
    val mediaId = metadata?.id

    val liked by remember(mediaId) {
        database.song(mediaId).map { it?.song?.liked == true }
    }.collectAsState(initial = false)

    var showMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = metrics.gutter.dp - metrics.xs.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack, tint = Color.White)
        }

        PlayerHeaderRow(
            artworkUrl = metadata?.thumbnailUrl,
            title = metadata?.title.orEmpty(),
            subtitle = metadata?.artists.orEmpty().joinToString(", ") { it.name },
            isFavorite = liked,
            onToggleFavorite = { playerConnection.toggleLike() },
            onOpenMenu = { showMenu = true },
            modifier = Modifier.padding(horizontal = metrics.gutter.dp),
        )
    }

    // 一覧と同じ曲メニュー。この帯を出す2画面からも再生まわりの操作だけ使えるようにする。
    if (showMenu) {
        metadata?.let { song ->
            SongMenuSheet(
                song = song,
                onDismiss = { showMenu = false },
                onPlayNext = {
                    playerConnection.playNext(song.toMediaItem())
                    showMenu = false
                },
                onAddToQueue = {
                    playerConnection.addToQueue(song.toMediaItem())
                    showMenu = false
                },
                onPlayRadio = {
                    playerConnection.playRadioFrom(song)
                    showMenu = false
                },
            )
        }
    }
}

