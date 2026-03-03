package com.bucks.blutendance

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bucks.blutendance.call.CallManager
import com.bucks.blutendance.call.ContactResolver
import com.bucks.blutendance.ui.theme.FirstappTheme
import kotlinx.coroutines.delay

/**
 * Full-screen activity displayed during an active / incoming call.
 * Uses the same white theme as the rest of the app for consistency.
 */
class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Show over lock-screen on all API levels ──────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        enableEdgeToEdge()
        setContent {
            FirstappTheme(darkTheme = false) {
                InCallScreen(onFinish = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }
}

/* ── Colours used throughout the in-call screen ──────────────────── */
private val BgWhite         = Color.White
private val TextPrimary     = Color(0xFF202124)
private val TextSecondary   = Color(0xFF5F6368)
private val AvatarBg        = Color(0xFFE8EAED)
private val AvatarIcon      = Color(0xFF5F6368)
private val BtnBg           = Color(0xFFF1F3F4)
private val BtnBgActive     = Color(0xFF1A73E8)
private val BtnIconDefault  = Color(0xFF202124)
private val BtnIconActive   = Color.White
private val BtnLabel        = Color(0xFF5F6368)
private val DtmfBg          = Color(0xFFF1F3F4)
private val DtmfDigitBg     = Color(0xFFE8EAED)
private val CallGreen       = Color(0xFF4CAF50)
private val CallRed         = Color(0xFFF44336)

/* ================================================================== */
/*  In-call Compose UI                                                 */
/* ================================================================== */

@Composable
private fun InCallScreen(onFinish: () -> Unit) {
    /* ── Observed state ───────────────────────────────────────── */
    val callState   by CallManager.callState.collectAsState()
    val currentCall by CallManager.currentCall.collectAsState()
    val isMuted     by CallManager.isMuted.collectAsState()
    val audioRoute  by CallManager.audioRoute.collectAsState()

    val isSpeaker   = audioRoute == CallAudioState.ROUTE_SPEAKER
    val isBluetooth = audioRoute == CallAudioState.ROUTE_BLUETOOTH

    var callDuration by remember { mutableIntStateOf(0) }
    var showDtmfPad  by remember { mutableStateOf(false) }

    // ── Entrance animation trigger ───────────────────────────
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val context = LocalContext.current

    /* ── Timer ────────────────────────────────────────────────── */
    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            callDuration = 0
            while (true) { delay(1000); callDuration++ }
        }
    }

    /* ── Auto-close ───────────────────────────────────────────── */
    LaunchedEffect(callState) {
        if (callState == Call.STATE_DISCONNECTED) { delay(1500); onFinish() }
    }

    val phoneNumber = remember(currentCall) {
        currentCall?.details?.handle?.schemeSpecificPart
    }
    val callerInfo = remember(phoneNumber) {
        if (phoneNumber != null) {
            ContactResolver.getDisplayName(context, phoneNumber)
        } else "Unknown"
    }
    val displayNumber = remember(phoneNumber, callerInfo) {
        if (callerInfo != phoneNumber && phoneNumber != null) phoneNumber else null
    }

    val stateText = when (callState) {
        Call.STATE_RINGING                       -> "Incoming Call"
        Call.STATE_CONNECTING, Call.STATE_DIALING -> "Calling\u2026"
        Call.STATE_ACTIVE                        -> formatCallTime(callDuration)
        Call.STATE_HOLDING                       -> "On Hold"
        Call.STATE_DISCONNECTED                  -> "Call Ended"
        else                                     -> "\u2026"
    }

    val showControls = callState == Call.STATE_ACTIVE ||
                       callState == Call.STATE_HOLDING ||
                       callState == Call.STATE_DIALING ||
                       callState == Call.STATE_CONNECTING

    val hasMultipleCalls = CallManager.liveCallCount > 1

    /* ── Layout ───────────────────────────────────────────────── */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgWhite)
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))

            // ── Avatar (slides down from top) ───────────────────
            val avatarAlpha by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = tween(500),
                label = "avatarAlpha"
            )
            val avatarOffset by animateDpAsState(
                targetValue = if (appeared) 0.dp else (-40).dp,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "avatarOffset"
            )

            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .offset(y = avatarOffset)
                    .alpha(avatarAlpha),
                shape = CircleShape,
                color = AvatarBg
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, null, Modifier.size(48.dp), tint = AvatarIcon)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Caller info (fades in) ──────────────────────────
            val textAlpha by animateFloatAsState(
                targetValue = if (appeared) 1f else 0f,
                animationSpec = tween(600, delayMillis = 150),
                label = "textAlpha"
            )
            Column(
                modifier = Modifier.alpha(textAlpha),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(callerInfo, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (displayNumber != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(displayNumber, fontSize = 14.sp, color = TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text(stateText, fontSize = 16.sp, color = TextSecondary)
            }

            Spacer(Modifier.weight(1f))

            // ── Action grid (slides up from bottom) ─────────────
            AnimatedVisibility(
                visible = showControls && !showDtmfPad,
                enter = slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(250)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Row 1: Mute · Keypad · Speaker
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionBtn(
                            icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            label = if (isMuted) "Unmute" else "Mute",
                            active = isMuted
                        ) { CallManager.toggleMute() }

                        ActionBtn(Icons.Filled.Dialpad, "Keypad") { showDtmfPad = true }

                        ActionBtn(
                            icon = if (isSpeaker) Icons.AutoMirrored.Filled.VolumeUp
                                   else Icons.AutoMirrored.Filled.VolumeOff,
                            label = "Speaker",
                            active = isSpeaker
                        ) { CallManager.toggleSpeaker() }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Row 2: Add call · Hold/Resume · Bluetooth
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        ActionBtn(Icons.Filled.PersonAdd, "Add call") {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }

                        ActionBtn(
                            icon = if (callState == Call.STATE_HOLDING) Icons.Filled.PlayArrow
                                   else Icons.Filled.Pause,
                            label = if (callState == Call.STATE_HOLDING) "Resume" else "Hold",
                            active = callState == Call.STATE_HOLDING
                        ) {
                            if (callState == Call.STATE_HOLDING) CallManager.unhold()
                            else CallManager.hold()
                        }

                        ActionBtn(
                            icon = Icons.Filled.Bluetooth,
                            label = "Bluetooth",
                            active = isBluetooth
                        ) { CallManager.setAudioRouteBluetooth() }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Row 3: Merge/Conference · Swap (when multiple calls)
                    AnimatedVisibility(
                        visible = hasMultipleCalls,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            ActionBtn(Icons.Filled.CallMerge, "Merge") {
                                CallManager.mergeConference()
                            }
                            ActionBtn(Icons.Filled.SwapCalls, "Swap") {
                                CallManager.swapCalls()
                            }
                            Spacer(Modifier.width(80.dp))
                        }
                    }
                }
            }

            // ── DTMF keypad (slides in from right) ──────────────
            AnimatedVisibility(
                visible = showDtmfPad,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(animationSpec = tween(250)),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(animationSpec = tween(200))
            ) {
                DtmfPad(onDigit = { CallManager.sendDtmf(it) }, onClose = { showDtmfPad = false })
            }

            Spacer(Modifier.height(16.dp))

            // ── Answer / Hang-up (slides up from bottom) ────────
            AnimatedVisibility(
                visible = callState == Call.STATE_RINGING,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CircleCallButton(CallRed, Icons.Filled.CallEnd, "Decline") {
                        CallManager.hangup()
                    }
                    CircleCallButton(CallGreen, Icons.Filled.Call, "Answer") {
                        CallManager.answer()
                    }
                }
            }

            AnimatedVisibility(
                visible = callState != Call.STATE_RINGING && callState != Call.STATE_DISCONNECTED,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessLow)
                ) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                CircleCallButton(CallRed, Icons.Filled.CallEnd, "Hang Up") {
                    CallManager.hangup()
                }
            }

            // ── Call Ended state (scale in) ─────────────────────
            AnimatedVisibility(
                visible = callState == Call.STATE_DISCONNECTED,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Text(
                    "Call Ended",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }

            Spacer(Modifier.height(48.dp))
        }
    }
}

/* ================================================================== */
/*  DTMF keypad                                                        */
/* ================================================================== */

@Composable
private fun DtmfPad(onDigit: (Char) -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DtmfBg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val rows = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf('*', '0', '#')
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { digit ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(DtmfDigitBg)
                            .clickable { onDigit(digit) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(digit.toString(), fontSize = 26.sp, color = TextPrimary,
                            fontWeight = FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Close, "Close Keypad", tint = TextPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

/* ================================================================== */
/*  Reusable composables                                               */
/* ================================================================== */

@Composable
private fun CircleCallButton(
    bg: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    // Subtle scale-in animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btnScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.scale(scale)
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = bg,
            contentColor = Color.White,
            modifier = Modifier.size(72.dp),
            shape = CircleShape
        ) {
            Icon(icon, label, Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, color = TextSecondary)
    }
}

@Composable
private fun ActionBtn(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    // Animate background color change when toggled
    val bgColor by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(200),
        label = "actionBtnBg"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (active) BtnBgActive else BtnBg,
                    shape = CircleShape
                )
        ) {
            Icon(
                icon, label,
                tint = if (active) BtnIconActive else BtnIconDefault,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = BtnLabel,
            textAlign = TextAlign.Center, maxLines = 1)
    }
}

/* ================================================================== */
/*  Utility                                                            */
/* ================================================================== */

private fun formatCallTime(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
