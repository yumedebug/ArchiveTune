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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType

/**
 * 大見出し右端に置くアカウント丸ボタン。
 *
 * 導線を失っていた設定（アカウント）画面への入口。写真は持たないため、
 * サインイン済みならアカウント名の先頭1文字、未サインインなら人型アイコンを
 * [palette.fill] の淡い円に収める。**Apple のロゴ・商標は使わない。**
 *
 * 押したときの行き先は呼び出し側が決める（ここでは遷移を知らない）。
 *
 * @param onClick 押したとき。
 * @param accountName アカウント名。空文字なら未サインイン扱いで人型アイコン。
 * @param contentDescription 丸ボタンの説明。`null` なら継承する。
 */
@Composable
fun AvatarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accountName: String = "",
    contentDescription: String? = null,
) {
    val palette = applePalette()
    val type = appleType()

    val initial = accountName.trim().firstOrNull()?.uppercaseChar()

    // ランダ内では `contentDescription` が SemanticsPropertyReceiver の
    // プロパティと名前が衝突するため、値を先に逃がしておく。
    val description = contentDescription
    val semanticsModifier =
        if (description == null) {
            Modifier
        } else {
            Modifier.semantics { this.contentDescription = description }
        }

    Box(
        modifier =
            modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(palette.fill)
                .then(semanticsModifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            Text(
                text = initial.toString(),
                style = type.subhead.copy(fontWeight = FontWeight.SemiBold),
                color = palette.onFill,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = null,
                tint = palette.onFill,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
