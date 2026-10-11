package com.presence.ui.screens

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.presence.session.Screen
import com.presence.ui.components.Gap
import com.presence.ui.components.Page
import com.presence.ui.components.Pip
import com.presence.ui.components.PipMood
import com.presence.ui.components.PrimaryButton
import com.presence.ui.components.ProgressRing
import com.presence.ui.components.StatusLabel
import com.presence.ui.components.TopBar
import com.presence.ui.components.minutesLabel
import com.presence.ui.theme.BrandColors
import com.presence.ui.theme.LocalReducedMotion
import com.presence.ui.theme.Motion
import com.presence.ui.theme.SemanticColors

/** The quietest screen: a timer, one line of status, and a check-in button only when one is due. */
@Composable
fun ActiveScreen(a: Screen.Active, onPulse: () -> Unit) = Page {
    val view = LocalView.current
    val context = LocalContext.current
    // The phone sits face-up on the desk: keep the screen (and so the session) alive.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // One buzz per check-in: that's how a phone-down user knows to look. No other haptics.
    LaunchedEffect(a.promptVisible) {
        if (a.promptVisible) context.getSystemService(Vibrator::class.java)?.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }

    TopBar {
        if (a.leftForeground) StatusLabel("Session interrupted") else StatusLabel("Session active", accent = true)
    }
    Spacer(Modifier.weight(1f))
    Timer(a)
    Gap(24)
    Text("Check-in ${(a.checkpointsDone + 1).coerceAtMost(a.checkpointsTotal)} of ${a.checkpointsTotal}",
        style = MaterialTheme.typography.labelMedium, color = SemanticColors.TertiaryText,
        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Gap(8)
    Text(
        when {
            a.leftForeground -> "You left the app, so this session can't be verified."
            a.confirmedThisWindow -> "Checked in. Phone down until the next buzz."
            else -> "Phone down. It buzzes when it's time to check in."
        },
        style = MaterialTheme.typography.bodyLarge,
        color = if (a.leftForeground) SemanticColors.PrimaryText else SemanticColors.SecondaryText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.weight(1f))
    CheckIn(a.promptVisible, onPulse)
    Gap(24)
}

@Composable
private fun Timer(a: Screen.Active) {
    val remaining = ((a.durationMs - a.elapsedMs).coerceAtLeast(0) + 999) / 1000
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ProgressRing((a.elapsedMs.toFloat() / a.durationMs).coerceIn(0f, 1f), Modifier.size(264.dp),
            color = if (a.leftForeground) SemanticColors.Disabled else BrandColors.Green)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%02d:%02d".format(remaining / 60, remaining % 60),
                style = MaterialTheme.typography.displayLarge, color = SemanticColors.PrimaryText)
            Text("${minutesLabel((a.durationMs / 1000).toInt())} goal",
                style = MaterialTheme.typography.bodyMedium, color = SemanticColors.SecondaryText)
        }
    }
}

/** Fixed-height slot so the layout never jumps when a check-in appears. Pip is alive but slow and quiet. */
@Composable
private fun CheckIn(visible: Boolean, onConfirm: () -> Unit) {
    val reduced = LocalReducedMotion.current
    Box(Modifier.fillMaxWidth().height(144.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(visible, label = "checkin", transitionSpec = {
            val d = if (reduced) 0 else Motion.control
            fadeIn(tween(d)) togetherWith fadeOut(tween(d))
        }) { show ->
            if (show) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Still here?", style = MaterialTheme.typography.titleMedium, color = SemanticColors.PrimaryText)
                Gap(12)
                PrimaryButton("I'm here", onClick = onConfirm)
            } else Pip(PipMood.Still, 136.dp, entrance = true, live = true) // hops back in after each check-in
        }
    }
}

@Composable
fun CountdownScreen(seconds: Int) = Page {
    TopBar()
    Spacer(Modifier.weight(1f))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Pip(PipMood.Still, 176.dp, entrance = true, live = true)
        Gap(24)
        Text("Put your phone down.", style = MaterialTheme.typography.headlineLarge, color = SemanticColors.PrimaryText,
            textAlign = TextAlign.Center)
        Gap(8)
        Text("Keep CLOCK IN on screen. It buzzes when it's time to check in.",
            style = MaterialTheme.typography.bodyLarge, color = SemanticColors.SecondaryText, textAlign = TextAlign.Center)
        Gap(32)
        Text("Starting in $seconds", style = MaterialTheme.typography.labelMedium, color = SemanticColors.TertiaryText,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    Spacer(Modifier.weight(1f))
}
