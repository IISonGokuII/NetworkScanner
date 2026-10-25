package com.loopforge.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.loopforge.app.LoopViewModel
import com.loopforge.app.LoopViewModel.ExportState
import com.loopforge.app.LoopViewModel.Stage
import com.loopforge.app.ui.components.AuroraBackground
import com.loopforge.app.ui.screens.AnalyzingScreen
import com.loopforge.app.ui.screens.ExportOverlay
import com.loopforge.app.ui.screens.HomeScreen
import com.loopforge.app.ui.screens.StudioScreen

@Composable
fun LoopForgeApp(vm: LoopViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.open(uri)
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
    val exportState by vm.exportState.collectAsStateWithLifecycle()
    val progress by vm.analysisProgress.collectAsStateWithLifecycle()
    val frames by vm.analysisFrames.collectAsStateWithLifecycle()

    BackHandler(enabled = vm.stage != Stage.HOME && exportState !is ExportState.Running) {
        when {
            exportState !is ExportState.Idle -> vm.dismissExport()
            vm.stage == Stage.ANALYZING -> vm.cancelAnalysis()
            else -> vm.reset()
        }
    }

    AuroraBackground {
        AnimatedContent(
            targetState = vm.stage,
            transitionSpec = { (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.96f)) togetherWith fadeOut(tween(200)) },
            label = "stage",
        ) { stage ->
            when (stage) {
                Stage.HOME -> HomeScreen(vm.error, onPick = pick, onDismissError = { vm.error = null })
                Stage.ANALYZING -> AnalyzingScreen(vm.info, progress, frames, vm.analysisMessage, vm.filmstrip, vm::cancelAnalysis)
                Stage.STUDIO -> StudioScreen(vm, previewActive = exportState is ExportState.Idle)
            }
        }
        AnimatedVisibility(visible = exportState !is ExportState.Idle, enter = fadeIn(), exit = fadeOut()) {
            ExportOverlay(
                state = exportState,
                onCancel = vm::cancelExport,
                onDismiss = vm::dismissExport,
                onRetry = vm::startExport,
                onNewVideo = {
                    vm.reset()
                    pick()
                },
            )
        }
    }
}
