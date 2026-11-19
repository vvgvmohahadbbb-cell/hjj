package com.asaar.lebanon.admin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

val Green = Color(0xFF1F4D3A)
val Gold = Color(0xFFE0A94A)
val Ink = Color(0xFF14201B)
val Muted = Color(0xFF66736C)
val Bg = Color(0xFFF4F6F2)
val Red = Color(0xFFC8382E)
val Ok = Color(0xFF2E8B57)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White, secondary = Gold)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { AdminApp() }
            }
        }
    }
}

fun upd(rows: MutableList<Prod>, id: String, f: (Prod) -> Prod) {
    val i = rows.indexOfFirst { it.id == id }
    if (i >= 0) rows[i] = f(rows[i])
}

fun prepImage(ctx: Context, uri: Uri): ByteArray? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
    var s = 1
    while (o.outWidth / s > 800 || o.outHeight / s > 800) s *= 2
    val o2 = BitmapFactory.Options().apply { inSampleSize = s }
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
    val k = 400f / maxOf(bmp.width, bmp.height)
    val sc = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
    val res = Bitmap.createBitmap(sc.width, sc.height, Bitmap.Config.ARGB_8888)
    val cv = AndroidCanvas(res)
    cv.drawColor(android.graphics.Color.WHITE)
    cv.drawBitmap(sc, 0f, 0f, null)
    val out = ByteArrayOutputStream()
    res.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return out.toByteArray()
}

@Composable
fun AdminApp() {
    val ctx = LocalContext.current
    var token by remember { mutableStateOf(Store.prefs(ctx).getString("token", "") ?: "") }
    if (token.isBlank()) {
        Login { t ->
            val clean = t.trim()
            Store.prefs(ctx).edit().putString("token", clean).apply()
            token = clean
        }
    } else {
        Editor(token) {
            Store.prefs(ctx).edit().remove("token").apply()
            token = ""
        }
    }
}

@Composable
fun Login(onSave: (String) -> Unit) {
    var t by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().background(Bg).padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("إدارة الأسعار", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Green)
        Text("ادخل رمز GitHub (Token) مرة وحدة. بيتخزّن مشفّر على هالجوال، ولا تعطيه لحدا.", color = Muted)
        Text(
            "طريقة عمله: GitHub ← Settings ← Developer settings ← Personal access tokens ← Fine-grained tokens ← Generate new token. اختر مستودع hjj فقط، وصلاحية Contents: Read and write.",
            color = Muted, fontSize = 13.sp
        )
        OutlinedTextField(value = t, onValueChange = { t = it }, label = { Text("Token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { if (t.isNotBlank()) onSave(t) },
            colors = ButtonDefaults.buttonColors(containerColor = Green)
        ) { Text("حفظ ودخول") }
    }
}

@Composable
fun Editor(token: String, onLogout: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows = remember { mutableStateListOf<Prod>() }
    var rate by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var good by remember { mutableStateOf(true) }
    var pickFor by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val id = pickFor
            scope.launch {
                val b = withContext(Dispatchers.IO) { runCatching { prepImage(ctx, uri) }.getOrNull() }
                if (b == null) {
                    msg = "تعذّر قراءة الصورة"; good = false
                } else {
                    upd(rows, id) { it.copy(pending = b) }
                    msg = "الصورة جاهزة، اضغط نشر"; good = true
                }
            }
        }
    }

    fun load() {
        scope.launch {
            busy = true; msg = ""
            val r = withContext(Dispatchers.IO) { Gh.getPrices(token) }
            r.onSuccess { pair ->
                val (rt, list) = parseRows(pair.second)
                rate = rt.toString()
                rows.clear()
                rows.addAll(list)
            }.onFailure { msg = it.message ?: "خطأ"; good = false }
            busy = false
        }
    }

    fun publish() {
        scope.launch {
            busy = true; msg = ""
            val snapshot = rows.toList()
            val rt = rate.toLongOrNull() ?: 0L
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    if (rt <= 0) error("اكتب سعر الدولار")
                    if (snapshot.any { it.name.isBlank() }) error("في منتج بدون اسم")
                    val list = snapshot.toMutableList()
                    for (k in list.indices) {
                        val x = list[k]
                        val pb = x.pending
                        if (pb != null) {
                            val path = "images/${x.id}_${System.currentTimeMillis()}.jpg"
                            Gh.put(path, token, pb, "صورة ${x.name}", null).getOrThrow()
                            list[k] = x.copy(img = path, pending = null)
                        }
                    }
                    val sha = Gh.getPrices(token).getOrThrow().first
                    Gh.put("prices.json", token, buildJson(rt, list).toByteArray(Charsets.UTF_8), "تحديث الأسعار", sha).getOrThrow()
                    list.map { committed(it) }
                }
            }
            r.onSuccess { done ->
                rows.clear()
                rows.addAll(done)
                msg = "تم النشر. بيوصل للناس خلال دقايق."; good = true
            }.onFailure { msg = it.message ?: "فشل النشر"; good = false }
            busy = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().background(Green).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("إدارة الأسعار", Modifier.weight(1f), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = { load() }) { Text("تحديث", color = Gold, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onLogout) { Text("خروج", color = Color.White) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
        if (msg.isNotBlank()) {
            Text(msg, color = if (good) Ok else Red, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = rate,
                onValueChange = { rate = it.filter { c -> c.isDigit() }.take(9) },
                label = { Text("سعر الدولار (ل.ل)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("بحث") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        val shown = rows.filter { query.isBlank() || it.name.contains(query.trim(), true) }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shown, key = { it.id }) { r ->
                ProdCard(r, rows) { pickFor = r.id; picker.launch("image/*") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    query = ""
                    rows.add(0, Prod("p" + System.currentTimeMillis(), "", "", "", 0L, 0L, 0L, "", false))
                },
                modifier = Modifier.weight(1f)
            ) { Text("إضافة منتج") }
            Button(
                onClick = { publish() },
                enabled = !busy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("نشر", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun ProdCard(r: Prod, rows: SnapshotStateList<Prod>, onPick: () -> Unit) {
    val i = rows.indexOfFirst { it.id == r.id }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (r.hidden) Color(0xFFE3E7E2) else Color.White).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = r.name,
            onValueChange = { v -> upd(rows, r.id) { it.copy(name = v) } },
            label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = if (r.lbp == 0L) "" else r.lbp.toString(),
                onValueChange = { v -> upd(rows, r.id) { it.copy(lbp = v.filter { c -> c.isDigit() }.take(12).toLongOrNull() ?: 0L) } },
                label = { Text("السعر (ل.ل)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true, modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = r.unit,
                onValueChange = { v -> upd(rows, r.id) { it.copy(unit = v) } },
                label = { Text("الوحدة") }, singleLine = true, modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = r.cat,
            onValueChange = { v -> upd(rows, r.id) { it.copy(cat = v) } },
            label = { Text("القسم (مثل: محروقات)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPick) {
                Text(
                    if (r.pending != null) "صورة جديدة جاهزة" else if (r.img.isNotBlank()) "تغيير الصورة" else "إضافة صورة",
                    color = Green, fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                if (i > 0) { val a = rows[i]; rows[i] = rows[i - 1]; rows[i - 1] = a }
            }) { Text("▲", color = Ink) }
            TextButton(onClick = {
                if (i >= 0 && i < rows.size - 1) { val a = rows[i]; rows[i] = rows[i + 1]; rows[i + 1] = a }
            }) { Text("▼", color = Ink) }
        }
        Row {
            TextButton(onClick = { upd(rows, r.id) { it.copy(hidden = !it.hidden) } }) {
                Text(if (r.hidden) "إظهار" else "إخفاء", color = Muted)
            }
            TextButton(onClick = { rows.removeAll { it.id == r.id } }) { Text("حذف", color = Red) }
        }
    }
}
