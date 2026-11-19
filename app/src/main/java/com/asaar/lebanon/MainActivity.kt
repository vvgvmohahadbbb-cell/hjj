package com.asaar.lebanon

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

val Green = Color(0xFF1F4D3A)
val Gold = Color(0xFFE0A94A)
val Ink = Color(0xFF14201B)
val Muted = Color(0xFF66736C)
val Bg = Color(0xFFF4F6F2)
val Red = Color(0xFFC8382E)
val Down = Color(0xFF2E8B57)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val req = PeriodicWorkRequestBuilder<PriceWorker>(3, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("prices", ExistingPeriodicWorkPolicy.KEEP, req)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, onPrimary = Color.White, secondary = Gold)) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Home() }
            }
        }
    }
}

fun emoji(id: String) = when (id) {
    "bread" -> "🍞"
    "benzine95", "benzine98" -> "⛽"
    "diesel" -> "🛢️"
    "gas" -> "🔥"
    else -> "🛒"
}

fun shareAll(ctx: Context, p: Prices) {
    val sb = StringBuilder("أسعار لبنان - ${p.updated}\n")
    p.items.forEach {
        if (it.lbp > 0) sb.append("${emoji(it.id)} ${it.name}: ${fmt(it.lbp)} ل.ل (${usd(it.lbp, p.rate)}\$)\n")
    }
    sb.append("سعر الدولار: ${fmt(p.rate)} ل.ل")
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, sb.toString())
    }
    ctx.startActivity(Intent.createChooser(i, "شارك الأسعار"))
}

@Composable
fun Home() {
    val ctx = LocalContext.current
    var prices by remember { mutableStateOf(Repo.cached(ctx)?.let { runCatching { Repo.parse(it) }.getOrNull() }) }
    var online by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var sel by remember { mutableStateOf<Item?>(null) }
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("") }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        val s = withContext(Dispatchers.IO) { Repo.fetch() }
        if (s != null) {
            runCatching { Repo.parse(s) }.getOrNull()?.let { prices = it; Repo.save(ctx, "ui", s) }
            online = true
        } else online = false
        loading = false
    }

    val p = prices
    Column(Modifier.fillMaxSize().background(Bg)) {
        Header(p, online) { if (p != null) shareAll(ctx, p) }
        if (p == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (loading) CircularProgressIndicator(color = Green)
                else Text("تعذّر تحميل الأسعار. تأكد من اتصال الإنترنت وافتح التطبيق من جديد.", color = Muted, modifier = Modifier.padding(32.dp))
            }
        } else {
            val cats = p.items.map { it.cat }.filter { it.isNotBlank() }.distinct()
            val shown = p.items.filter {
                (cat.isBlank() || it.cat == cat) && (query.isBlank() || it.name.contains(query.trim(), true))
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("ابحث عن منتج") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Green
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            if (cats.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf("") + cats) { c ->
                        val on = c == cat
                        Text(
                            if (c.isBlank()) "الكل" else c,
                            color = if (on) Color.White else Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (on) Green else Color.White)
                                .clickable { cat = c }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            if (shown.isEmpty()) {
                Text("ما في نتائج. جرّب كلمة ثانية.", color = Muted, modifier = Modifier.padding(24.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shown, span = { GridItemSpan(if (it.id == "bread") maxLineSpan else 1) }) {
                    ItemCard(it, p.rate) { sel = it }
                }
            }
        }
    }
    val s = sel
    if (s != null && p != null) CalcDialog(s, p.rate) { sel = null }
}

@Composable
fun Header(p: Prices?, online: Boolean, onShare: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(Green, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("أسعار لبنان", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (p != null) {
                Text("آخر تحديث: ${p.updated}", color = Color(0xCCFFFFFF), fontSize = 13.sp)
                Text("الدولار: ${fmt(p.rate)} ل.ل" + if (!online) "  (بدون إنترنت)" else "", color = Color(0xCCFFFFFF), fontSize = 13.sp)
            }
        }
        if (p != null) {
            Button(onClick = onShare, colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)) {
                Text("شارك واتساب", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ItemCard(i: Item, rate: Long, onClick: () -> Unit) {
    val hero = i.id == "bread"
    val shape = RoundedCornerShape(22.dp)
    val base = Modifier.fillMaxWidth().clip(shape)
        .background(if (hero) Color(0xFFFFEFC9) else Color.White)
        .clickable { onClick() }.padding(16.dp)
    if (hero) {
        Row(base, verticalAlignment = Alignment.CenterVertically) {
            ItemArt(i, Modifier.size(130.dp))
            Spacer(Modifier.width(16.dp))
            PriceBlock(i, rate, true, Alignment.Start)
        }
    } else {
        Column(base, horizontalAlignment = Alignment.CenterHorizontally) {
            ItemArt(i, Modifier.size(92.dp))
            Spacer(Modifier.height(8.dp))
            PriceBlock(i, rate, false, Alignment.CenterHorizontally)
        }
    }
}

@Composable
fun PriceBlock(i: Item, rate: Long, big: Boolean, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(i.name, fontWeight = FontWeight.Bold, fontSize = if (big) 20.sp else 15.sp, color = Ink)
        if (i.lbp <= 0) {
            Text("السعر غير محدد", color = Muted, fontSize = 13.sp)
        } else {
            Text("${fmt(i.lbp)} ل.ل", fontSize = if (big) 28.sp else 18.sp, fontWeight = FontWeight.ExtraBold, color = Green)
            Text("≈ ${usd(i.lbp, rate)} \$  لكل ${i.unit}", color = Muted, fontSize = 12.sp)
            val d = i.lbp - i.prev
            if (i.prev > 0 && d != 0L) {
                val up = d > 0
                Text(
                    (if (up) "▲ ارتفع " else "▼ انخفض ") + fmt(abs(d)),
                    color = if (up) Red else Down, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CalcDialog(i: Item, rate: Long, onDismiss: () -> Unit) {
    var qty by remember { mutableStateOf(1) }
    var money by remember { mutableStateOf("") }
    val total = i.lbp * qty
    val m = money.toLongOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق", color = Green) } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemArt(i, Modifier.size(52.dp))
                Spacer(Modifier.width(10.dp))
                Text(i.name, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                if (i.lbp <= 0) {
                    Text("السعر غير محدد حالياً.", color = Muted)
                } else {
                    Text("الكمية (${i.unit})", color = Muted, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { if (qty > 1) qty-- }) { Text("−", fontSize = 20.sp) }
                        Text(qty.toString(), Modifier.padding(horizontal = 22.dp), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = { qty++ }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("+", fontSize = 20.sp) }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("المجموع", color = Muted, fontSize = 13.sp)
                    Text("${fmt(total)} ل.ل", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Green)
                    Text("≈ ${usd(total, rate)} \$", color = Muted)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = money,
                        onValueChange = { money = it.filter { c -> c.isDigit() }.take(12) },
                        label = { Text("معي مبلغ بالليرة") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    if (m != null && m > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "بتشتري ${String.format(Locale.US, "%.1f", m.toDouble() / i.lbp)} ${i.unit}",
                            fontWeight = FontWeight.Bold, color = Ink
                        )
                    }
                }
            }
        }
    )
}
