package com.videodl.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection

class MainActivity : ComponentActivity() {
    private val vm: MainVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { Screen(vm) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND) {
            val text = i.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val link = Regex("https?://\\S+").find(text)?.value ?: return
            vm.url = link
            vm.title = null
            vm.fetch()
        }
    }
}

@Composable
fun Screen(vm: MainVM) {
    val clipboard = LocalClipboardManager.current
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("محمّل الفيديو", style = MaterialTheme.typography.headlineMedium)
            Text("فيسبوك • انستغرام • تيك توك • يوتيوب", style = MaterialTheme.typography.bodyMedium)

            OutlinedTextField(
                value = vm.url,
                onValueChange = { vm.url = it },
                label = { Text("رابط الفيديو") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { clipboard.getText()?.text?.let { vm.url = it.trim() } }) { Text("لصق") }
                Button(onClick = { vm.fetch() }, enabled = !vm.busy && !vm.downloading) { Text("جلب") }
            }

            vm.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }

            if (vm.title != null) {
                Text("الجودة:", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { QualityChip("أفضل جودة", vm.selected == "best") { vm.selected = "best" } }
                    items(vm.heights) { h ->
                        QualityChip("${h}p", vm.selected == h.toString()) { vm.selected = h.toString() }
                    }
                    item { QualityChip("صوت MP3", vm.selected == "audio") { vm.selected = "audio" } }
                }
                if (vm.downloading) {
                    Button(onClick = { vm.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("إلغاء") }
                } else {
                    Button(onClick = { vm.download() }, modifier = Modifier.fillMaxWidth()) { Text("تحميل") }
                }
            }

            if (vm.downloading || vm.progress > 0f) {
                LinearProgressIndicator(progress = { vm.progress }, modifier = Modifier.fillMaxWidth())
            }
            Text(vm.status)

            TextButton(onClick = { vm.updateEngine() }, enabled = !vm.busy && !vm.downloading) {
                Text("تحديث محرك التحميل (إذا توقف موقع عن العمل)")
            }
            Text(
                "للاستخدام الشخصي فقط. احترم حقوق صاحب المحتوى وشروط كل منصة.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
