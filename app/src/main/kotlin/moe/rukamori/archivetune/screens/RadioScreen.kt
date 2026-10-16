/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.PinnedTitleBar
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.design.components.rememberTitleCollapse
import moe.rukamori.archivetune.viewmodels.RadioSectionUiModel
import moe.rukamori.archivetune.viewmodels.RadioTileUiModel
import moe.rukamori.archivetune.viewmodels.RadioUiState
import moe.rukamori.archivetune.viewmodels.RadioViewModel

/**
 * Radio タブ。
 *
 * YouTube Music には局のカタログが無いため、代わりに**ムードとジャンル**の
 * カラータイルを並べ、押すとその条件で再生できるページ（Browse）へ送る。
 * タイルの色は YouTube Music が返す `stripeColor` をそのまま下地にしていて、
 * アプリ側で色を作り替えていない。
 *
 * @param navController タイルを押したときの遷移に使う。
 */
@Composable
fun RadioScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val viewModel: RadioViewModel = hiltViewModel()
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()
    val uiState by viewModel.uiState.collectAsState()

    val listState = rememberLazyListState()
    val titleCollapsed = rememberTitleCollapse(listState)
    val title = stringResource(R.string.radio)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(windowInsets),
        ) {
            when (val state = uiState) {
                is RadioUiState.Loading -> {
                    Column(Modifier.fillMaxSize()) {
                        LargeTitleHeader(title = title)
                        LoadingState(Modifier.fillMaxWidth().weight(1f))
                    }
                }

                is RadioUiState.Error -> {
                    Column(Modifier.fillMaxSize()) {
                        LargeTitleHeader(title = title)
                        ErrorState(
                            title = title,
                            message = stringResource(R.string.no_results_found),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onRetry = { viewModel.retry() },
                        )
                    }
                }

                is RadioUiState.Empty -> {
                    Column(Modifier.fillMaxSize()) {
                        LargeTitleHeader(title = title)
                        EmptyState(
                            title = title,
                            message = stringResource(R.string.no_results_found),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            actionLabel = stringResource(R.string.refresh),
                            onAction = { viewModel.retry() },
                        )
                    }
                }

                is RadioUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = metrics.block.dp),
                    ) {
                        item(key = "header") {
                            LargeTitleHeader(title = title)
                        }

                        items(items = state.sections, key = { it.title }) { section ->
                            RadioSection(
                                section = section,
                                onOpen = { tile -> navController.navigate(Routes.browse(tile.browseId)) },
                            )
                        }
                    }
                }
            }
        }

        PinnedTitleBar(
            title = title,
            visible = titleCollapsed,
        )
    }
}

/** セクション1つ（見出し＋横スクロールのカラータイル）。 */
@Composable
private fun RadioSection(
    section: RadioSectionUiModel,
    onOpen: (RadioTileUiModel) -> Unit,
) {
    val metrics = appleMetrics()

    Column {
        SectionHeader(title = section.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
            horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
        ) {
            items(items = section.tiles, key = { it.browseId }) { tile ->
                RadioTile(tile = tile, onClick = { onOpen(tile) })
            }
        }
    }
}

/**
 * ムード／ジャンルのタイル。
 *
 * 素の `stripeColor` を上端に置き、下端へ向けて沈めたグラデーションにする。
 * 枠線は引かず、面の濃淡だけでカードの縁を作る。
 */
@Composable
private fun RadioTile(
    tile: RadioTileUiModel,
    onClick: () -> Unit,
) {
    val type = appleType()
    val interactionSource = remember { MutableInteractionSource() }
    val base = Color(tile.stripeColor)
    val deep =
        Color(
            red = base.red * 0.42f,
            green = base.green * 0.42f,
            blue = base.blue * 0.42f,
        )

    Box(
        modifier =
            Modifier
                .width(168.dp)
                .height(92.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(base, deep)))
                .clickable(interactionSource = interactionSource, indication = null) { onClick() }
                .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = tile.title,
            style = type.headline.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
