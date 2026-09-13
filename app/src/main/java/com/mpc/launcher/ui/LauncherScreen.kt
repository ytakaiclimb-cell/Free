package com.mpc.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mpc.launcher.data.FormFactor
import com.mpc.launcher.system.SystemScreens
import com.mpc.launcher.ui.modules.PageTitle
import androidx.compose.ui.platform.LocalContext

/** Below this window width the launcher uses its folded layout. */
private const val FOLDED_WIDTH_DP = 600

/** Pages, the grab bar, and whatever sheet is on top of them. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(state: LauncherState) {
    val configuration = LocalConfiguration.current
    val form = if (configuration.screenWidthDp >= FOLDED_WIDTH_DP) {
        FormFactor.UNFOLDED
    } else {
        FormFactor.FOLDED
    }

    LaunchedEffect(form, state.resumeTick) { state.load(form) }

    val pageCount = state.pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    // The machine wakes up: sliders run to their marks, knobs wind round and the
    // calendar lights up, on arrival and again on every page turn.
    val entry = remember { Animatable(0f) }
    LaunchedEffect(state.resumeTick, pagerState.currentPage) {
        entry.snapTo(0f)
        entry.animateTo(1f, tween(620))
    }

    BackHandler { state.dismissTop() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Skin.Paper),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !state.editing,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { pageIndex ->
                val page = state.pages.getOrNull(pageIndex)
                if (page != null) {
                    Box(Modifier.fillMaxSize()) {
                        PageTitle(
                            title = page.title,
                            subtitle = page.subtitle,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 6.dp, top = 24.dp),
                        )
                        PageCanvas(
                            state = state,
                            pageIndex = pageIndex,
                            page = page,
                            entry = entry.value,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 54.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                        )
                    }
                }
            }

            PageDots(current = pagerState.currentPage, count = pageCount)

            GrabBar(onSwipeUp = { state.openDrawer() })
        }

        if (state.editing) {
            EditBar(
                state = state,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 44.dp),
            )
        }

        if (state.drawerOpen) {
            Drawer(
                state = state,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        state.moduleById(state.openFolder)?.let { module ->
            FolderSheet(
                state = state,
                module = module,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }

        state.picker?.let { target ->
            AppPicker(
                state = state,
                target = target,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
            )
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { page ->
            Box(
                Modifier
                    .size(if (page == current) 7.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (page == current) Skin.Ink else Skin.TextFaint),
            )
        }
    }
}

@Composable
private fun GrabBar(onSwipeUp: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -12f) onSwipeUp()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(132.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Skin.TextFaint),
        )
    }
}

/** The strip that appears while the grid is being rearranged. */
@Composable
private fun EditBar(state: LauncherState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Skin.Ink)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditAction("ページ追加") { state.addPage() }
        EditAction("通知") { SystemScreens.notificationAccess(context) }
        EditAction("ホーム設定") { SystemScreens.homeApp(context) }
        EditAction("初期化") { state.resetLayout() }
        EditAction("完了") { state.stopEditing() }
    }
}

@Composable
private fun EditAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Skin.Paper,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clickable(onClick = onClick),
    )
}
