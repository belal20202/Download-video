package com.videodl.app

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ الألوان
val Bg = Color(0xFF080B10)
val Panel = Color(0xFF111722)
val Panel2 = Color(0xFF182030)
val IqRed = Color(0xFFE02A3F)
val IqGreen = Color(0xFF12A150)
val TextDim = Color(0xFF9AA7B8)
val Gold = Color(0xFFE8C56A)
val FlagBrush: Brush get() = Brush.horizontalGradient(listOf(IqRed, Color.White, IqGreen))

fun Modifier.panel(radius: Dp = 22.dp): Modifier {
    val s = RoundedCornerShape(radius)
    return this.clip(s).background(Panel).border(1.dp, Color(0x1FFFFFFF), s)
}

/** نص بتدرّج ألوان (أحمر ← أبيض ← أخضر) مع لمعان متحرك. */
@Composable
fun GradText(
    text: String,
    size: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.ExtraBold,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign = TextAlign.Unspecified
) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "x"
    )
    val brush = Brush.linearGradient(
        colors = listOf(IqRed, Color.White, IqGreen),
        start = Offset(x * 800f, 0f),
        end = Offset(x * 800f + 400f, 0f),
        tileMode = TileMode.Mirror
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            brush = brush,
            fontSize = size,
            fontWeight = weight,
            letterSpacing = letterSpacing,
            textAlign = textAlign,
            shadow = Shadow(Color.Black, Offset(0f, 2f), 6f)
        )
    )
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: List<Color> = listOf(IqRed, Color(0xFFB01020)),
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (enabled) Brush.horizontalGradient(colors)
    else Brush.horizontalGradient(listOf(Color(0xFF2A3342), Color(0xFF2A3342)))
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .height(52.dp)
            .clip(shape)
            .background(Panel2)
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
fun PlatformPill(name: String, color: Color) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Panel)
            .border(1.dp, Color(0x22FFFFFF), shape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MadeInIraqBanner() {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0x44E02A3F), Color(0x14FFFFFF), Color(0x4412A150))
                )
            )
            .border(1.4.dp, FlagBrush, shape)
            .padding(vertical = 18.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✦  ✦  ✦", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, letterSpacing = 6.sp)
        Spacer(Modifier.height(6.dp))
        GradText("صُنع في العراق", 34.sp)
        Spacer(Modifier.height(6.dp))
        Text("بأيدٍ عراقية • فخر وإبداع", color = Color(0xFFD7DEE8), fontSize = 13.sp)
    }
}

@Composable
fun QualityRow(q: Quality, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val borderBrush: Brush =
        if (selected) Brush.horizontalGradient(listOf(IqRed, IqGreen)) else SolidColor(Color(0x22FFFFFF))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Panel2 else Panel)
            .border(if (selected) 1.6.dp else 1.dp, borderBrush, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(2.dp, if (selected) IqGreen else TextDim, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(11.dp).background(IqGreen, CircleShape))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(q.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val size = fmtSize(q.sizeBytes)
            val kind = if (q.isAudioOnly) "MP3 • صوت فقط" else "MP4 • فيديو مع صوت"
            Text(
                kind + if (size.isNotEmpty()) " • ≈ $size" else "",
                color = TextDim,
                fontSize = 12.sp
            )
        }
        if (selected) Text("✓", color = IqGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ------------------------------------------------------------------ الشاشة الرئيسية
@Composable
fun HomeScreen(vm: MainVM) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val m = vm.meta

    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Bg)))
    ) {
        Box(
            Modifier
                .size(280.dp)
                .offset((-90).dp, (-70).dp)
                .background(Brush.radialGradient(listOf(IqRed.copy(alpha = 0.22f), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier
                .size(320.dp)
                .align(Alignment.BottomEnd)
                .offset(110.dp, 90.dp)
                .background(Brush.radialGradient(listOf(IqGreen.copy(alpha = 0.20f), Color.Transparent)), CircleShape)
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- الترويسة (سياسة الخصوصية أعلى اليسار)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(logoSize = 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    GradText("محمّل الفيديو", 20.sp)
                    Text("VIDEO DOWNLOADER", color = TextDim, fontSize = 9.sp, letterSpacing = 1.sp)
                }
                PrivacyPill { showPrivacy = true }
            }

            // ---- صنع في العراق
            MadeInIraqBanner()

            // ---- إدخال الرابط
            Column(Modifier.fillMaxWidth().panel(24.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("الصق رابط الفيديو", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OutlinedTextField(
                    value = vm.url,
                    onValueChange = { vm.url = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("https://...", color = TextDim) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = IqGreen,
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        cursorColor = IqGreen
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("لصق", Modifier.weight(1f)) {
                        clipboard.getText()?.text?.let { vm.url = it.trim() }
                    }
                    GradientButton(
                        if (vm.busy) "جارٍ العمل..." else "تعرّف على الفيديو",
                        Modifier.weight(2f),
                        enabled = !vm.busy && !vm.downloading
                    ) { vm.fetch() }
                }
            }

            // ---- المنصات
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformPill("YouTube", Color(0xFFFF3B30))
                PlatformPill("Facebook", Color(0xFF1877F2))
                PlatformPill("Instagram", Color(0xFFE1306C))
                PlatformPill("TikTok", Color(0xFF25F4EE))
            }

            // ---- بطاقة الفيديو
            if (m != null) {
                Column(Modifier.fillMaxWidth().panel(24.dp).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Panel2)
                    ) {
                        val t = vm.thumb
                        if (t != null) {
                            Image(
                                bitmap = t,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("🎬", fontSize = 40.sp, modifier = Modifier.align(Alignment.Center))
                        }
                        if (m.durationSec > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(fmtDuration(m.durationSec), color = Color.White, fontSize = 12.sp)
                            }
                        }
                        if (m.platform.isNotEmpty()) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xCC000000))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(m.platform, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                    Text(
                        m.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (m.uploader.isNotEmpty()) {
                        Text(m.uploader, color = TextDim, fontSize = 13.sp)
                    }
                }

                // ---- الجودات
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("الجودات المتاحة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "✓ تم التحقق من ${m.qualities.size} خيار متاح فعلاً لهذا الفيديو",
                        color = IqGreen,
                        fontSize = 12.sp
                    )
                    m.qualities.forEachIndexed { i, q ->
                        QualityRow(q, selected = i == vm.selectedIndex, enabled = !vm.downloading) {
                            vm.selectedIndex = i
                        }
                    }
                }

                // ---- التحميل
                if (vm.downloading) {
                    Column(Modifier.fillMaxWidth().panel(22.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(
                            progress = { vm.progress },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
                            color = IqGreen,
                            trackColor = Color(0xFF263044)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${(vm.progress * 100).toInt()}%", color = Color.White, fontWeight = FontWeight.Bold)
                            Text(vm.etaText, color = TextDim, fontSize = 13.sp)
                        }
                        GhostButton("إلغاء", Modifier.fillMaxWidth()) { vm.cancel() }
                    }
                } else {
                    GradientButton(
                        "⬇   تحميل الآن",
                        Modifier.fillMaxWidth(),
                        enabled = !vm.busy,
                        colors = listOf(IqGreen, Color(0xFF0E7C3A))
                    ) { vm.download() }
                }
            }

            // ---- الدعاء
            if (vm.dua.isNotEmpty()) {
                val shape = RoundedCornerShape(22.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Color(0x1AE8C56A))
                        .border(1.dp, Gold.copy(alpha = 0.6f), shape)
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🤲  دعاء", color = Gold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        vm.dua,
                        color = Color.White,
                        fontSize = 19.sp,
                        lineHeight = 32.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ---- الحالة
            if (vm.status.isNotEmpty()) {
                val color = when {
                    vm.isError -> Color(0xFFFF6B6B)
                    vm.savedUri != null -> IqGreen
                    else -> TextDim
                }
                Text(vm.status, color = color, fontSize = 14.sp, lineHeight = 22.sp)
            }

            // ---- تشغيل الملف بعد الحفظ
            val saved = vm.savedUri
            if (saved != null) {
                GhostButton("▶   تشغيل الملف", Modifier.fillMaxWidth()) {
                    try {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .setDataAndType(saved, vm.savedMime)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            // ---- تحديث المحرك
            TextButton(onClick = { vm.updateEngine() }, enabled = !vm.busy && !vm.downloading) {
                Text("تحديث محرك التحميل (إذا توقف موقع عن العمل)", color = TextDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    // ---- نافذة سياسة الخصوصية
    if (showPrivacy) {
        PrivacyDialog(onClose = { showPrivacy = false })
    }
}
