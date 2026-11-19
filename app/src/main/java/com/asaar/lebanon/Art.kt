package com.asaar.lebanon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ItemArt(i: Item, m: Modifier) {
    if (i.img.isNotBlank()) {
        RemoteImage(i.img, m)
        return
    }
    when (i.id) {
        "bread" -> BreadArt(m)
        "benzine95" -> FuelArt(Color(0xFF2E8B57), m)
        "benzine98" -> FuelArt(Color(0xFFC8382E), m)
        "diesel" -> FuelArt(Color(0xFFD9A21B), m)
        "gas" -> GasArt(m)
        else -> BoxArt(m)
    }
}

@Composable
fun RemoteImage(img: String, m: Modifier) {
    val ctx = LocalContext.current
    var bmp by remember(img) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(img) {
        val b = withContext(Dispatchers.IO) { Repo.loadImage(ctx, img) }
        if (b != null) bmp = b.asImageBitmap()
    }
    val b = bmp
    if (b != null) Image(b, null, m, contentScale = ContentScale.Fit) else BoxArt(m)
}

@Composable
fun BreadArt(m: Modifier) = Canvas(m) {
    val w = size.width; val h = size.height
    drawOval(Color(0x22000000), Offset(w * .12f, h * .84f), Size(w * .76f, h * .1f))
    val bag = Path().apply {
        moveTo(w * .22f, h * .30f); lineTo(w * .78f, h * .30f)
        quadraticBezierTo(w * .94f, h * .55f, w * .85f, h * .86f)
        quadraticBezierTo(w * .5f, h * .92f, w * .15f, h * .86f)
        quadraticBezierTo(w * .06f, h * .55f, w * .22f, h * .30f)
        close()
    }
    drawPath(bag, Brush.verticalGradient(listOf(Color(0xFFF4C975), Color(0xFFD6902E))))
    repeat(3) { k ->
        val x = w * (.33f + .17f * k)
        drawPath(
            Path().apply { moveTo(x, h * .44f); quadraticBezierTo(x + w * .07f, h * .6f, x - w * .01f, h * .78f) },
            Color(0x66803F0A), style = Stroke(w * .035f, cap = StrokeCap.Round)
        )
    }
    drawPath(
        Path().apply {
            moveTo(w * .38f, h * .30f); lineTo(w * .28f, h * .13f); lineTo(w * .5f, h * .24f)
            lineTo(w * .72f, h * .13f); lineTo(w * .62f, h * .30f); close()
        }, Color(0xFF1F4D3A)
    )
    drawOval(Color(0x55FFFFFF), Offset(w * .24f, h * .38f), Size(w * .09f, h * .3f))
}

@Composable
fun FuelArt(c: Color, m: Modifier) = Canvas(m) {
    val w = size.width; val h = size.height
    drawRoundRect(Color(0x22000000), Offset(w * .1f, h * .88f), Size(w * .7f, h * .07f), CornerRadius(w * .04f))
    drawRoundRect(c, Offset(w * .2f, h * .1f), Size(w * .46f, h * .78f), CornerRadius(w * .06f))
    drawRoundRect(Color(0xFFEFF5F0), Offset(w * .27f, h * .18f), Size(w * .32f, h * .22f), CornerRadius(w * .03f))
    drawLine(Color(0xFF1F4D3A), Offset(w * .31f, h * .29f), Offset(w * .55f, h * .29f), w * .035f, StrokeCap.Round)
    drawRoundRect(Color(0x44FFFFFF), Offset(w * .27f, h * .5f), Size(w * .32f, h * .1f), CornerRadius(w * .02f))
    drawRoundRect(Color(0x44000000), Offset(w * .14f, h * .84f), Size(w * .58f, h * .06f), CornerRadius(w * .02f))
    drawPath(
        Path().apply { moveTo(w * .66f, h * .36f); cubicTo(w * .92f, h * .36f, w * .9f, h * .6f, w * .82f, h * .66f) },
        Color(0xFF222222), style = Stroke(w * .035f, cap = StrokeCap.Round)
    )
    drawRoundRect(Color(0xFF222222), Offset(w * .77f, h * .64f), Size(w * .1f, h * .16f), CornerRadius(w * .02f))
}

@Composable
fun GasArt(m: Modifier) = Canvas(m) {
    val w = size.width; val h = size.height
    val c = Color(0xFF2B6CB0)
    drawOval(Color(0x22000000), Offset(w * .2f, h * .86f), Size(w * .6f, h * .08f))
    drawRoundRect(c, Offset(w * .28f, h * .28f), Size(w * .44f, h * .6f), CornerRadius(w * .14f))
    drawRoundRect(c, Offset(w * .34f, h * .18f), Size(w * .32f, h * .2f), CornerRadius(w * .1f))
    drawRect(Color(0xFF9AA39E), Offset(w * .46f, h * .08f), Size(w * .08f, h * .12f))
    drawLine(Color(0xFF333333), Offset(w * .38f, h * .08f), Offset(w * .62f, h * .08f), w * .04f, StrokeCap.Round)
    drawRect(Color(0x44FFFFFF), Offset(w * .28f, h * .54f), Size(w * .44f, h * .1f))
    drawOval(Color(0x44FFFFFF), Offset(w * .33f, h * .36f), Size(w * .07f, h * .3f))
    drawRoundRect(Color(0xFF333333), Offset(w * .3f, h * .86f), Size(w * .4f, h * .04f), CornerRadius(w * .02f))
}

@Composable
fun BoxArt(m: Modifier) = Canvas(m) {
    val w = size.width; val h = size.height
    drawRoundRect(Color(0xFFB98A52), Offset(w * .2f, h * .25f), Size(w * .6f, h * .55f), CornerRadius(w * .05f))
    drawRect(Color(0x44FFFFFF), Offset(w * .2f, h * .25f), Size(w * .6f, h * .12f))
}
