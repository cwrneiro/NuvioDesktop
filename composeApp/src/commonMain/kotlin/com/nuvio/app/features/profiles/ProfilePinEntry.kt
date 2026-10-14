package com.nuvio.app.features.profiles

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import com.nuvio.app.core.ui.NuvioTokens
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.core.ui.nuvioFocusBorder
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun InlinePinEntry(
    profileName: String,
    onVerified: () -> Unit,
    onCancel: () -> Unit,
    verifyPin: suspend (String) -> PinVerifyResult,
) {
    val tokens = MaterialTheme.nuvio
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isVerifying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }

    fun submitDigit(digit: String) {
        if (pin.length < 4 && !isVerifying) {
            error = null
            pin += digit
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (pin.length == 4) {
                isVerifying = true
                scope.launch {
                    val result = verifyPin(pin)
                    if (result.unlocked) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onVerified()
                    } else {
                        error = if (result.retryAfterSeconds > 0) {
                            getString(Res.string.pin_locked_try_again, result.retryAfterSeconds)
                        } else {
                            getString(Res.string.pin_incorrect)
                        }
                        pin = ""
                    }
                    isVerifying = false
                }
            }
        }
    }

    fun deleteDigit() {
        if (pin.isNotEmpty() && !isVerifying) {
            pin = pin.dropLast(1)
            error = null
        }
    }

    // Mirror PinEntryDialog: this Column is the hidden digit sink so hardware/
    // keyboard digits, Backspace and Esc work. Initial focus uses the
    // frame-retry idiom (the dialog may compose before focus is ready).
    LaunchedEffect(Unit) {
        repeat(8) {
            withFrameNanos { }
            if (runCatching { focusRequester.requestFocus() }.getOrDefault(false)) {
                return@LaunchedEffect
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(top = tokens.spacing.cardPadding)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = event.utf16CodePoint.takeIf { it in '0'.code..'9'.code }?.toChar()?.toString()
                when {
                    digit != null -> {
                        submitDigit(digit)
                        true
                    }
                    event.key == Key.Backspace || event.key == Key.Delete -> {
                        deleteDigit()
                        true
                    }
                    event.key == Key.Escape -> {
                        onCancel()
                        true
                    }
                    // Single-line-entry philosophy: Left/Right belong to the PIN
                    // entry itself (no-ops here) and must not let the global
                    // arrow dispatcher move focus off the digit sink.
                    event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> true
                    else -> false
                }
            },
    ) {
        Text(
            text = stringResource(Res.string.pin_enter_for, profileName),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.textMuted,
        )

        Spacer(modifier = Modifier.height(NuvioTokens.Space.s14))

        Row(horizontalArrangement = Arrangement.spacedBy(tokens.spacing.listGap)) {
            repeat(4) { index ->
                val filled = index < pin.length
                val dotScale = remember { Animatable(1f) }
                LaunchedEffect(filled) {
                    if (filled) {
                        dotScale.snapTo(1.4f)
                        dotScale.animateTo(
                            1f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                        )
                    }
                }

                val dotColor = when {
                    error != null -> tokens.colors.danger
                    filled -> tokens.colors.accent
                    else -> tokens.colors.borderDefault
                }
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = dotScale.value
                            scaleY = dotScale.value
                        }
                        .size(NuvioTokens.Space.s14)
                        .clip(tokens.shapes.avatar)
                        .then(
                            if (filled) Modifier.background(dotColor)
                            else Modifier.border(tokens.borders.medium, dotColor, tokens.shapes.avatar),
                        ),
                )
            }
        }

        AnimatedVisibility(
            visible = error != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text = error.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.danger,
                modifier = Modifier.padding(top = tokens.spacing.controlGap),
            )
        }

        Spacer(modifier = Modifier.height(NuvioTokens.Space.s14))

        CompactPinKeypad(
            onDigit = ::submitDigit,
            onBackspace = ::deleteDigit,
        )

        Spacer(modifier = Modifier.height(tokens.spacing.controlGap))

        Text(
            text = stringResource(Res.string.pin_cancel),
            style = MaterialTheme.typography.labelMedium,
            color = tokens.colors.accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .nuvioFocusBorder(tokens.shapes.compactCard)
                .clip(tokens.shapes.compactCard)
                .clickable(onClick = onCancel)
                .padding(horizontal = tokens.spacing.cardPadding, vertical = NuvioTokens.Space.s6),
        )
    }
}

@Composable
private fun CompactPinKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.controlGap, Alignment.CenterHorizontally),
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(tokens.components.avatarSize))
                        "⌫" -> {
                            Box(
                                modifier = Modifier
                                    .size(tokens.components.avatarSize)
                                    .nuvioFocusBorder(tokens.shapes.avatar)
                                    .clip(tokens.shapes.avatar)
                                    .background(tokens.colors.surfaceCard)
                                    .clickable(onClick = onBackspace),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = stringResource(Res.string.pin_backspace),
                                    tint = tokens.colors.textPrimary,
                                    modifier = Modifier.size(tokens.icons.md),
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(tokens.components.avatarSize)
                                    .nuvioFocusBorder(tokens.shapes.avatar)
                                    .clip(tokens.shapes.avatar)
                                    .background(tokens.colors.surfaceCard)
                                    .clickable { onDigit(key) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = key,
                                    style = MaterialTheme.typography.titleLarge,
                                    color = tokens.colors.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
