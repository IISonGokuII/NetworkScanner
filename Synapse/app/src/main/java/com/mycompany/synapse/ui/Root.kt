package com.mycompany.synapse.ui

import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.ui.components.AppBackground
import com.mycompany.synapse.ui.screens.AllGamesScreen
import com.mycompany.synapse.ui.screens.AwardsScreen
import com.mycompany.synapse.ui.screens.DomainScreen
import com.mycompany.synapse.ui.screens.DrillScreen
import com.mycompany.synapse.ui.screens.DuelScreen
import com.mycompany.synapse.ui.screens.LearnScreen
import com.mycompany.synapse.ui.screens.LessonScreen
import com.mycompany.synapse.ui.screens.ProfilesScreen
import com.mycompany.synapse.ui.screens.TestScreen
import com.mycompany.synapse.ui.screens.WeekReviewScreen
import com.mycompany.synapse.ui.screens.GameStatsScreen
import com.mycompany.synapse.ui.screens.HomeScreen
import com.mycompany.synapse.ui.screens.MoreScreen
import com.mycompany.synapse.ui.screens.PlayScreen
import com.mycompany.synapse.ui.screens.ProgressScreen
import com.mycompany.synapse.ui.screens.WelcomeScreen
import com.mycompany.synapse.ui.screens.WorkoutDoneScreen
import com.mycompany.synapse.ui.theme.Synapse
import com.mycompany.synapse.ui.theme.SynapseTheme
import com.mycompany.synapse.ui.theme.isAppDark

@Composable
fun SynapseRoot(activity: ComponentActivity) {
    val dark = isAppDark(Repo.themeMode)
    DisposableEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT)
        else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose { }
    }
    SynapseTheme(dark, colorblind = Repo.cvdMode) {
        AppBackground {
            if (!Repo.onboarded) {
                WelcomeScreen(onStart = { Repo.completeOnboarding() })
            } else {
                MainContent()
            }
        }
    }
}

@Composable
private fun MainContent() {
    val nav = remember { Navigator() }
    val holder = rememberSaveableStateHolder()
    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }
    val link = DeepLink.pending
    LaunchedEffect(link) {
        if (link != null) {
            nav.push(link)
            DeepLink.pending = null
        }
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = nav.current,
            transitionSpec = {
                when {
                    initialState.isTab && targetState.isTab -> fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    nav.forward -> (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(200)))
                    else -> (slideInHorizontally(tween(320)) { -it / 3 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(200)))
                }
            },
            label = "screens",
        ) { screen ->
            holder.SaveableStateProvider(screen.toString()) {
                when (screen) {
                    Screen.Home -> HomeScreen(nav)
                    Screen.Progress -> ProgressScreen(nav)
                    Screen.Awards -> AwardsScreen()
                    Screen.More -> MoreScreen(nav)
                    Screen.AllGames -> AllGamesScreen(nav)
                    Screen.Duel -> DuelScreen(nav)
                    Screen.Learn -> LearnScreen(nav)
                    Screen.Profiles -> ProfilesScreen(nav)
                    is Screen.LessonDetail -> LessonScreen(screen.lessonId, nav)
                    is Screen.Drill -> DrillScreen(screen.drillId, nav)
                    is Screen.Test -> TestScreen(screen.kind, nav)
                    is Screen.WeekReviewScreen -> WeekReviewScreen(screen.weekStartEpochDay, nav)
                    is Screen.DomainDetail -> DomainScreen(screen.domain, nav)
                    is Screen.Play -> PlayScreen(screen, nav)
                    is Screen.GameStats -> GameStatsScreen(screen.gameId, nav)
                    is Screen.WorkoutDone -> WorkoutDoneScreen(screen.entries, nav)
                }
            }
        }
        AnimatedVisibility(
            visible = nav.current.isTab,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            BottomBar(nav)
        }
    }
}

private enum class Tab(val label: String, val screen: Screen) {
    NET("Netz", Screen.Home),
    LEARN("Lernen", Screen.Learn),
    PROGRESS("Fortschritt", Screen.Progress),
    AWARDS("Erfolge", Screen.Awards),
    MORE("Mehr", Screen.More),
}

@Composable
private fun BottomBar(nav: Navigator) {
    val c = Synapse.colors
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(c.surfaceStrong.copy(alpha = 0.94f))
            .border(1.dp, c.stroke, shape)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        for (tab in Tab.entries) {
            val selected = nav.current == tab.screen
            val color = if (selected) c.domain(Domain.RECHNEN) else c.textMuted
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(remember { MutableInteractionSource() }, indication = androidx.compose.material3.ripple()) {
                        if (!selected) nav.tab(tab.screen)
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (tab) {
                    Tab.NET -> Canvas(Modifier.size(24.dp)) {
                        val ctr = center
                        val r = size.minDimension * 0.36f
                        val pts = (0 until 5).map { k ->
                            val a = Math.toRadians(-90.0 + k * 72.0)
                            ctr + Offset((r * kotlin.math.cos(a)).toFloat(), (r * kotlin.math.sin(a)).toFloat())
                        }
                        for (p in pts) drawLine(color, ctr, p, 1.6.dp.toPx())
                        for (p in pts) drawCircle(color, 2.6.dp.toPx(), p)
                        drawCircle(color, 3.4.dp.toPx(), ctr)
                    }
                    Tab.PROGRESS -> Canvas(Modifier.size(24.dp)) {
                        val w = size.width
                        val h = size.height
                        val pts = listOf(Offset(w * 0.1f, h * 0.75f), Offset(w * 0.38f, h * 0.5f), Offset(w * 0.6f, h * 0.62f), Offset(w * 0.9f, h * 0.22f))
                        for (i in 0 until pts.size - 1) drawLine(color, pts[i], pts[i + 1], 2.2.dp.toPx(), StrokeCap.Round)
                        drawCircle(color, 2.6.dp.toPx(), pts.last())
                        drawLine(color.copy(alpha = 0.5f), Offset(w * 0.08f, h * 0.92f), Offset(w * 0.92f, h * 0.92f), 1.5.dp.toPx(), StrokeCap.Round)
                    }
                    Tab.LEARN -> Canvas(Modifier.size(24.dp)) {
                        val w = size.width
                        val h = size.height
                        val sw = 1.8.dp.toPx()
                        // aufgeschlagenes Buch
                        drawLine(color, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.5f, h * 0.85f), sw, StrokeCap.Round)
                        drawLine(color, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.1f, h * 0.18f), sw, StrokeCap.Round)
                        drawLine(color, Offset(w * 0.1f, h * 0.18f), Offset(w * 0.1f, h * 0.78f), sw, StrokeCap.Round)
                        drawLine(color, Offset(w * 0.1f, h * 0.78f), Offset(w * 0.5f, h * 0.85f), sw, StrokeCap.Round)
                        drawLine(color, Offset(w * 0.5f, h * 0.25f), Offset(w * 0.9f, h * 0.18f), sw, StrokeCap.Round)
                        drawLine(color, Offset(w * 0.9f, h * 0.18f), Offset(w * 0.9f, h * 0.78f), sw, StrokeCap.Round)
                        drawLine(color, Offset(w * 0.9f, h * 0.78f), Offset(w * 0.5f, h * 0.85f), sw, StrokeCap.Round)
                    }
                    Tab.AWARDS -> Icon(Icons.Default.Star, null, tint = color, modifier = Modifier.size(24.dp))
                    Tab.MORE -> Icon(Icons.Default.Settings, null, tint = color, modifier = Modifier.size(24.dp))
                }
                Text(tab.label, color = color, style = MaterialTheme.typography.labelSmall)
                if (selected) {
                    Canvas(Modifier.size(width = 18.dp, height = 3.dp)) {
                        drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
                    }
                }
            }
        }
    }
}
