package com.zacco.rytm.phone.today

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zacco.rytm.domain.body.OcrLine
import com.zacco.rytm.domain.body.ScaleLayout
import com.zacco.rytm.domain.body.ScaleReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Odczyt i surowe linie z OCR (do skopiowania, gdy odczyt się myli). */
data class ScaleScan(val reading: ScaleReading?, val dump: String)

/**
 * Rozpoznanie tekstu ze zrzutu ekranu wagi (ML Kit, model w aplikacji, bez wysyłania obrazu).
 * Długi zrzut (cały ekran pomiaru przewinięty) dzielimy na zachodzące na siebie kawałki: na jednym wysokim
 * obrazie drobny tekst kafelków rozpoznaje się gorzej.
 */
object ScaleOcr {
    suspend fun read(context: Context, uri: Uri): ScaleScan {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val lines = mutableListOf<OcrLine>()
            for ((image, offsetY) in tiles(context, uri)) {
                val result = recognizer.process(image).await()
                result.textBlocks.flatMap { it.lines }.forEach { l ->
                    val b = l.boundingBox ?: return@forEach
                    lines += OcrLine(l.text, b.left, b.top + offsetY, b.right, b.bottom + offsetY)
                }
            }
            return ScaleScan(ScaleLayout.parse(lines), ScaleLayout.dump(ScaleLayout.readingOrder(lines)))
        } finally {
            recognizer.close()
        }
    }

    private suspend fun tiles(context: Context, uri: Uri): List<Pair<InputImage, Int>> = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || h <= w * 2) return@withContext listOf(InputImage.fromFilePath(context, uri) to 0)
        val tileH = (w * 1.6).toInt()
        val step = (tileH * 0.8).toInt()
        val decoder = context.contentResolver.openInputStream(uri)?.use { s ->
            if (Build.VERSION.SDK_INT >= 31) BitmapRegionDecoder.newInstance(s) else @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(s, false)
        } ?: return@withContext listOf(InputImage.fromFilePath(context, uri) to 0)
        try {
            val out = mutableListOf<Pair<InputImage, Int>>()
            var top = 0
            while (top < h) {
                val bottom = minOf(h, top + tileH)
                val bmp = decoder.decodeRegion(Rect(0, top, w, bottom), null)
                out += InputImage.fromBitmap(bmp, 0) to top
                if (bottom == h) break
                top += step
            }
            out
        } finally {
            decoder.recycle()
        }
    }
}
