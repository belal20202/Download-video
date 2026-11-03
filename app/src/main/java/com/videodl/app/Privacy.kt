package com.videodl.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val PRIVACY_DATE = "3 نوفمبر 2026"

private val PRIVACY_SECTIONS: List<Pair<String, String>> = listOf(
    "1. ملخص سريع" to
        "• التطبيق لا يتطلب إنشاء حساب ولا تسجيل دخول.\n" +
        "• لا يجمع بياناتك الشخصية ولا يرسلها إلى أي خادم تابع لنا.\n" +
        "• لا يحتوي على إعلانات ولا أدوات تحليل أو تتبع.\n" +
        "• الملفات المحمّلة تُحفظ على جهازك فقط.",

    "2. البيانات التي لا نجمعها" to
        "لا نجمع ولا نخزّن ولا نبيع ولا نشارك: الاسم، البريد الإلكتروني، رقم الهاتف، الموقع الجغرافي، " +
        "جهات الاتصال، الصور والملفات الشخصية، معرّف الإعلانات، سجل التصفح أو سجل الروابط التي تحمّلها.",

    "3. كيف يعمل التطبيق" to
        "• الروابط: عند لصق رابط أو مشاركته إلى التطبيق، يتصل جهازك مباشرة بالمنصة صاحبة الفيديو " +
        "(يوتيوب، فيسبوك، انستغرام، تيك توك) لجلب معلومات الفيديو وتحميله. هذا الاتصال بين جهازك والمنصة، " +
        "وتخضع بياناته لسياسة خصوصية تلك المنصة.\n" +
        "• الصورة المصغّرة: تُجلب من المنصة نفسها لعرضها داخل التطبيق.\n" +
        "• الحافظة: لا يقرأ التطبيق الحافظة إلا عند ضغطك زر «لصق».\n" +
        "• الملفات المؤقتة: تُستخدم أثناء التحميل ثم تُحذف بعد الانتهاء.\n" +
        "• تحديث المحرك: عند ضغطك «تحديث محرك التحميل» يتصل التطبيق بالإنترنت لتنزيل نسخة أحدث من أداة " +
        "yt-dlp مفتوحة المصدر، دون إرسال أي بيانات شخصية.",

    "4. الصلاحيات" to
        "• الإنترنت (INTERNET): لازمة للتعرف على الفيديو وتحميله.\n" +
        "• لا يطلب التطبيق صلاحيات الكاميرا أو الميكروفون أو الموقع أو جهات الاتصال أو الرسائل.\n" +
        "• تُحفظ الملفات عبر خدمة التخزين الرسمية في أندرويد دون صلاحية وصول واسعة إلى ملفاتك.",

    "5. التخزين" to
        "تُحفظ الفيديوهات في مجلد Movies/VideoDL والملفات الصوتية في Music/VideoDL على جهازك، " +
        "فتظهر في المعرض وتطبيقات الموسيقى، ولك حرية فتحها أو مشاركتها أو حذفها في أي وقت. " +
        "بحذف التطبيق تُحذف بياناته الداخلية، وتبقى الملفات المحمّلة حتى تحذفها بنفسك.",

    "6. المكتبات الخارجية" to
        "يعتمد التطبيق على مكتبات مفتوحة المصدر، أبرزها yt-dlp و FFmpeg عبر مكتبة youtubedl-android. " +
        "تعمل هذه المكتبات محلياً على جهازك، ولا يرسل التطبيق عبرها بياناتك إلى أي جهة تابعة لنا.",

    "7. حقوق الملكية والاستخدام المسؤول" to
        "التطبيق أداة للاستخدام الشخصي فقط. أنت وحدك المسؤول عن التأكد من أن لديك الحق في تحميل المحتوى، " +
        "وعن الالتزام بحقوق الملكية الفكرية وشروط استخدام كل منصة. التطبيق غير تابع لأي منصة ولا مرتبط بها.",

    "8. خصوصية الأطفال" to
        "التطبيق لا يجمع أي بيانات عن أي مستخدم، ومنهم الأطفال، ولا يوجّه أي محتوى تسويقي إليهم. " +
        "ننصح أولياء الأمور بمتابعة المحتوى الذي يحمّله الأطفال.",

    "9. أمان البيانات" to
        "لأن التطبيق لا يجمع بياناتك ولا يرسلها إلى خوادمنا، فلا توجد قاعدة بيانات للمستخدمين يمكن اختراقها. " +
        "تبقى بياناتك على جهازك تحت سيطرتك.",

    "10. التغييرات على هذه السياسة" to
        "قد نحدّث هذه السياسة من وقت لآخر. يظهر تاريخ آخر تحديث في أعلاها، ويُعد استمرارك في استخدام " +
        "التطبيق بعد التحديث موافقة على النسخة الجديدة.",

    "11. التواصل" to
        "للاستفسار عن هذه السياسة، تواصل معنا عبر قسم Issues في صفحة مستودع المشروع على GitHub."
)

@Composable
fun PrivacyPill(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(Panel)
            .border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🔒", fontSize = 11.sp)
        Spacer(Modifier.width(5.dp))
        Text("سياسة الخصوصية", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PrivacyDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel2,
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("إغلاق", color = IqGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        title = {
            Column {
                Text("سياسة الخصوصية", color = Color.White, fontWeight = FontWeight.Bold)
                Text("آخر تحديث: $PRIVACY_DATE", color = TextDim, fontSize = 12.sp)
            }
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PRIVACY_SECTIONS.forEach { (title, body) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(body, color = Color(0xFFD7DEE8), fontSize = 13.sp, lineHeight = 21.sp)
                    }
                }
            }
        }
    )
}
