package com.videodl.app

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** شعار التطبيق (يُرسم برمجياً): حلقة حمراء/خضراء مع سهم تحميل. */
@Composable
fun AppLogo(logoSize: Dp, ring: Float = 1f, drop: Float = 1f) {
    Canvas(Modifier.size(logoSize)) {
        val w = size.width
        val c = Offset(w / 2f, w / 2f)
        val r = w * 0.36f
        val stroke = w * 0.075f

        drawCircle(Color(0xFF0F1520), radius = w / 2f, center = c)
        drawCircle(Color(0x22FFFFFF), radius = w / 2f, center = c, style = Stroke(width = w * 0.012f))

        val tl = Offset(c.x - r, c.y - r)
        val sz = Size(r * 2f, r * 2f)
        drawArc(IqRed, 180f, 180f * ring, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(IqGreen, 0f, 180f * ring, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))

        val a = Color.White.copy(alpha = drop.coerceIn(0f, 1f))
        val dy = (1f - drop) * (-w * 0.18f)
        val sw = w * 0.07f
        drawLine(
            a,
            Offset(c.x, c.y - w * 0.19f + dy),
            Offset(c.x, c.y + w * 0.07f + dy),
            strokeWidth = sw,
            cap = StrokeCap.Round
        )
        val head = Path().apply {
            moveTo(c.x - w * 0.12f, c.y - w * 0.05f + dy)
            lineTo(c.x, c.y + w * 0.08f + dy)
            lineTo(c.x + w * 0.12f, c.y - w * 0.05f + dy)
        }
        drawPath(head, a, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(
            Color.White.copy(alpha = 0.9f * ring),
            Offset(c.x - w * 0.15f, c.y + w * 0.22f),
            Offset(c.x + w * 0.15f, c.y + w * 0.22f),
            strokeWidth = sw * 0.8f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun LoadingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val colors = listOf(IqRed, Color.White, IqGreen)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEachIndexed { i, c ->
            val s by t.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = i * 150, easing = FastOutSlowInEasing),
                    RepeatMode.Reverse
                ),
                label = "d$i"
            )
            Box(
                Modifier
                    .size(10.dp)
                    .graphicsLayer { scaleX = s; scaleY = s }
                    .background(c, CircleShape)
            )
        }
    }
}

@Composable
fun SplashScreen() {
    val ring = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    val tag = remember { Animatable(0f) }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "p"
    )

    LaunchedEffect(Unit) {
        ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        drop.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(800)
        name.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(1600)
        tag.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF0A0A10))))
    ) {
        Box(
            Modifier
                .size(340.dp)
                .align(Alignment.TopStart)
                .offset((-120).dp, (-80).dp)
                .background(
                    Brush.radialGradient(listOf(IqRed.copy(alpha = 0.30f * pulse), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(360.dp)
                .align(Alignment.BottomEnd)
                .offset(120.dp, 100.dp)
                .background(
                    Brush.radialGradient(listOf(IqGreen.copy(alpha = 0.28f * pulse), Color.Transparent)),
                    CircleShape
                )
        )

        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(logoSize = 150.dp, ring = ring.value, drop = drop.value)
            Spacer(Modifier.height(30.dp))

            Box(
                Modifier
                    .alpha(name.value)
                    .graphicsLayer {
                        val s = 0.85f + 0.15f * name.value
                        scaleX = s
                        scaleY = s
                        translationY = (1f - name.value) * 30f
                    }
            ) {
                GradText("محمّل الفيديو", 40.sp)
            }

            Spacer(Modifier.height(12.dp))

            Box(
                Modifier
                    .alpha(tag.value)
                    .graphicsLayer { translationY = (1f - tag.value) * 40f }
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = IqRed)) { append("صُنع ") }
                        withStyle(SpanStyle(color = Color.White)) { append("في ") }
                        withStyle(SpanStyle(color = IqGreen)) { append("العراق") }
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LoadingDots()
            Spacer(Modifier.height(14.dp))
            Text(
                "VIDEO DOWNLOADER",
                color = TextDim,
                fontSize = 10.sp,
                letterSpacing = 3.sp
            )
        }
    }
}
