package com.asaar.lebanon

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// بعد ما ترفع المشروع، بدّل USER و REPO باسم حسابك واسم المستودع
const val PRICES_URL = "https://raw.githubusercontent.com/vvgvmohahadbbb-cell/hjj/main/prices.json"

data class Item(val id: String, val name: String, val unit: String, val lbp: Long, val prev: Long, val cat: String = "", val img: String = "")
data class Prices(val updated: String, val rate: Long, val items: List<Item>)

fun fmt(n: Long): String = String.format(Locale.US, "%,d", n)
fun usd(lbp: Long, rate: Long): String =
    String.format(Locale.US, "%.2f", if (rate > 0) lbp.toDouble() / rate else 0.0)

object Repo {
    private fun sp(c: Context) = c.getSharedPreferences("prices", Context.MODE_PRIVATE)

    fun parse(s: String): Prices {
        val o = JSONObject(s)
        val a = o.getJSONArray("items")
        val list = (0 until a.length()).map { a.getJSONObject(it) }
            .filter { !it.optBoolean("hidden", false) }
            .map { i ->
                Item(i.getString("id"), i.getString("name"), i.optString("unit", ""), i.optLong("lbp"),
                    i.optLong("prev"), i.optString("cat", ""), i.optString("img", ""))
            }
        return Prices(o.optString("updated"), o.optLong("rate", 89500), list)
    }

    fun fetch(): String? = try {
        val c = URL("$PRICES_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.inputStream.bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        null
    }

    fun loadImage(c: Context, img: String): Bitmap? = try {
        val f = File(c.filesDir, "img_" + img.replace("/", "_"))
        if (!f.exists()) {
            val base = PRICES_URL.substringBeforeLast("/") + "/"
            val conn = URL(base + img).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            f.writeBytes(conn.inputStream.use { it.readBytes() })
        }
        BitmapFactory.decodeFile(f.path)
    } catch (e: Exception) {
        null
    }

    fun cached(c: Context): String? = sp(c).getString("ui", null) ?: sp(c).getString("seen", null)
    fun seen(c: Context): String? = sp(c).getString("seen", null)
    fun save(c: Context, key: String, s: String) = sp(c).edit().putString(key, s).apply()
}

class PriceWorker(private val ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val s = Repo.fetch() ?: return Result.retry()
        val new = runCatching { Repo.parse(s) }.getOrNull() ?: return Result.success()
        val old = Repo.seen(ctx)?.let { runCatching { Repo.parse(it) }.getOrNull() }
        Repo.save(ctx, "seen", s)
        if (old != null) {
            val changes = new.items.mapNotNull { n ->
                val o = old.items.find { it.id == n.id }
                if (o != null && n.lbp > 0 && o.lbp != n.lbp)
                    "${n.name}: من ${fmt(o.lbp)} إلى ${fmt(n.lbp)} ل.ل" else null
            }
            if (changes.isNotEmpty()) notifyChange(changes)
        }
        return Result.success()
    }

    private fun notifyChange(lines: List<String>) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("prices", "تغيّر الأسعار", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val n = NotificationCompat.Builder(ctx, "prices")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("تغيّرت الأسعار")
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setAutoCancel(true)
            .build()
        try { nm.notify(1, n) } catch (e: SecurityException) { }
    }
}
