package com.example.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.firebase.attemptAutoSignIn
import com.example.data.firebase.onGoogleSignInClicked
import com.example.ui.theme.BranchIndigo
import com.example.ui.theme.LocalBranchColors
import com.example.ui.theme.StatusDoneColor

@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val branchColors = LocalBranchColors.current

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope,
        )
    }

    val pulseTransition = rememberInfiniteTransition(label = "auth_branch_pulse")
    val nodeGlow by pulseTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(branchColors.canvasBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Architectural dot grid + branching geometry background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 28.dp.toPx()
            val dotRadius = 1.1.dp.toPx()
            var x = step * 0.5f
            while (x < size.width) {
                var y = step * 0.5f
                while (y < size.height) {
                    drawCircle(
                        color = branchColors.gridDot.copy(alpha = 0.7f),
                        radius = dotRadius,
                        center = Offset(x, y),
                    )
                    y += step
                }
                x += step
            }

            // Subtle ambient radial wash
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BranchIndigo.copy(alpha = if (branchColors.isDark) 0.14f else 0.08f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.3f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.5f, size.height * 0.3f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 460.dp)
                .align(Alignment.Center)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Architectural Fork & Branch Emblem
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xFF10182B),
                    tonalElevation = 6.dp,
                    shadowElevation = 12.dp,
                    modifier = Modifier.size(96.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                        val cx = size.width * 0.5f
                        val bottomY = size.height * 0.9f
                        val splitY = size.height * 0.45f
                        val topY = size.height * 0.22f
                        val leftX = size.width * 0.16f
                        val rightX = size.width * 0.84f
                        val strokeW = 5.dp.toPx()

                        val trunkPath = Path().apply {
                            moveTo(cx, bottomY)
                            lineTo(cx, splitY)
                            cubicTo(cx, topY, cx * 0.55f, topY, leftX, topY)
                        }
                        val rightPath = Path().apply {
                            moveTo(cx, splitY)
                            cubicTo(cx, topY, cx + (rightX - cx) * 0.45f, topY, rightX, topY)
                        }
                        drawPath(
                            path = trunkPath,
                            color = Color(0xFFF8FAFC),
                            style = Stroke(width = strokeW, cap = StrokeCap.Round),
                        )
                        drawPath(
                            path = rightPath,
                            color = Color(0xFFF8FAFC),
                            style = Stroke(width = strokeW, cap = StrokeCap.Round),
                        )
                        drawCircle(
                            color = StatusDoneColor.copy(alpha = 0.28f * nodeGlow),
                            radius = 10.dp.toPx(),
                            center = Offset(leftX, topY),
                        )
                        drawCircle(
                            color = StatusDoneColor,
                            radius = 5.5.dp.toPx(),
                            center = Offset(leftX, topY),
                        )
                        drawCircle(
                            color = StatusDoneColor.copy(alpha = 0.28f * nodeGlow),
                            radius = 10.dp.toPx(),
                            center = Offset(rightX, topY),
                        )
                        drawCircle(
                            color = StatusDoneColor,
                            radius = 5.5.dp.toPx(),
                            center = Offset(rightX, topY),
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = stringResource(R.string.auth_tagline),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.auth_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMessage = null
                        isLoading = true
                        onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = {
                                isLoading = false
                                onAuthSuccess()
                            },
                            onAuthError = { msg ->
                                isLoading = false
                                errorMessage = msg
                            },
                            scope = scope,
                            onAuthCancelled = {
                                isLoading = false
                            },
                        )
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("google_sign_in_button"),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.2.dp,
                            color = MaterialTheme.colorScheme.background,
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.auth_signing_in),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.AccountTree,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = stringResource(R.string.auth_sign_in_google),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
