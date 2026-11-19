package com.asaar.lebanon.admin

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val OWNER = "vvgvmohahadbbb-cell"
const val REPO = "hjj"

data class Prod(
    val id: String,
    val name: String,
    val unit: String,
    val cat: String,
    val lbp: Long,
    val orig: Long,
    val prev: Long,
    val img: String,
    val hidden: Boolean,
    val pending: ByteArray? = null
)

object Store {
    fun prefs(ctx: Context): SharedPreferences = try {
        val mk = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            ctx, "secure", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("plain", Context.MODE_PRIVATE)
    }
}

object Gh {
    private fun call(method: String, path: String, token: String, body: String? = null): Pair<Int, String> {
        val c = URL("https://api.github.com/repos/$OWNER/$REPO/$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 15000
        c.readTimeout = 25000
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.setRequestProperty("User-Agent", "asaar-admin")
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = c.responseCode
        val stream = if (code < 400) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    }

    private fun err(code: Int): String = when (code) {
        401 -> "الـ Token غلط أو منتهي"
        403 -> "ما في صلاحية كتابة على المستودع"
        404 -> "المستودع أو الملف غير موجود، أو الـ Token ما بيشمل هالمستودع"
        409, 422 -> "تعارض بالملف. اضغط تحديث وجرّب من جديد"
        else -> "خطأ $code من GitHub"
    }

    fun getPrices(token: String): Result<Pair<String, String>> = runCatching {
        val (code, txt) = call("GET", "contents/prices.json?ref=main", token)
        if (code != 200) error(err(code))
        val o = JSONObject(txt)
        val raw = String(Base64.decode(o.getString("content"), Base64.DEFAULT), Charsets.UTF_8)
        o.getString("sha") to raw
    }

    fun put(path: String, token: String, bytes: ByteArray, message: String, sha: String?): Result<Unit> = runCatching {
        val b = JSONObject()
            .put("message", message)
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("branch", "main")
        if (sha != null) b.put("sha", sha)
        val (code, _) = call("PUT", "contents/$path", token, b.toString())
        if (code !in 200..201) error(err(code))
    }
}

fun parseRows(json: String): Pair<Long, List<Prod>> {
    val o = JSONObject(json)
    val a = o.getJSONArray("items")
    val l = (0 until a.length()).map {
        val i = a.getJSONObject(it)
        Prod(
            i.getString("id"), i.getString("name"), i.optString("unit", ""), i.optString("cat", ""),
            i.optLong("lbp"), i.optLong("lbp"), i.optLong("prev"), i.optString("img", ""),
            i.optBoolean("hidden", false)
        )
    }
    return o.optLong("rate", 89500) to l
}

fun buildJson(rate: Long, rows: List<Prod>): String {
    val arr = JSONArray()
    rows.forEach { r ->
        arr.put(
            JSONObject()
                .put("id", r.id)
                .put("name", r.name.trim())
                .put("unit", r.unit.trim())
                .put("cat", r.cat.trim())
                .put("lbp", r.lbp)
                .put("prev", if (r.lbp != r.orig) r.orig else r.prev)
                .put("img", r.img)
                .put("hidden", r.hidden)
        )
    }
    return JSONObject()
        .put("updated", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
        .put("rate", rate)
        .put("items", arr)
        .toString(2)
}

fun committed(r: Prod): Prod = r.copy(prev = if (r.lbp != r.orig) r.orig else r.prev, orig = r.lbp)
