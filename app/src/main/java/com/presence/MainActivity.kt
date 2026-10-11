package com.presence

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.presence.session.PresenceViewModel
import com.presence.session.Screen
import com.presence.ui.screens.ActiveScreen
import com.presence.ui.screens.CountdownScreen
import com.presence.ui.screens.FailedScreen
import com.presence.ui.screens.HomeScreen
import com.presence.ui.screens.MissionScreen
import com.presence.ui.screens.ReceiptScreen
import com.presence.ui.screens.VerifyingScreen
import com.presence.ui.theme.BrandColors
import com.presence.ui.theme.ClockInTheme
import com.presence.ui.theme.Motion
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Native launch screen: white, the clock mark, its hand moves once (API 31+). Never held open:
        // the first frame replaces it as soon as it's drawn; data loads inside the app.
        installSplashScreen().setOnExitAnimationListener { splash ->
            val reduced = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            if (reduced) return@setOnExitAnimationListener splash.remove()
            val v = splash.view
            AnimatorSet().apply {
                playTogether(ObjectAnimator.ofFloat(v, "alpha", 1f, 0f),
                    ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.04f), ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.04f))
                duration = Motion.screen.toLong()
                doOnEnd { splash.remove() }
                start()
            }
        }
        super.onCreate(savedInstanceState)
        // White UI: dark status/nav bar icons regardless of system theme.
        enableEdgeToEdge(SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            SystemBarStyle.light(Color.WHITE, Color.WHITE))
        val sender = ActivityResultSender(this)
        setContent { ClockInTheme { ClockInRoot(sender) } }
    }
}

@Composable
fun ClockInRoot(sender: ActivityResultSender, vm: PresenceViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    // Foreground continuity is evidence: report when the session screen leaves the foreground.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) vm.onForeground(false)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    BackHandler(enabled = s.screen != Screen.Home) { vm.home() }

    Box(Modifier.fillMaxSize()) {
        // Crossfade between screens; Active/Countdown updates keep the same key so they don't re-animate.
        AnimatedContent(s.screen, contentKey = { it::class }, label = "screen",
            transitionSpec = { fadeIn(tween(Motion.screen)) togetherWith fadeOut(tween(Motion.micro)) }) { screen ->
            when (screen) {
                Screen.Home -> HomeScreen(s, onConnect = { vm.connect(sender) }, onSelect = vm::select,
                    onOpen = vm::openMission, onDisconnect = vm::disconnect, onRetry = { vm.refresh() })
                Screen.Mission -> MissionScreen(s, onBack = vm::home, onStart = { vm.startMission(sender) })
                is Screen.Countdown -> CountdownScreen(screen.seconds)
                is Screen.Active -> ActiveScreen(screen, onPulse = vm::onPulse)
                is Screen.Verifying -> VerifyingScreen(screen)
                is Screen.Result -> ReceiptScreen(screen, onDone = vm::home)
                is Screen.Failed -> FailedScreen(screen, onRetry = vm::openMission, onHome = vm::home)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 72.dp)) {
            Snackbar(it, containerColor = BrandColors.Ink, contentColor = BrandColors.White)
        }
    }
}
