package com.zacco.rytm.phone.today

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zacco.rytm.domain.body.ScaleReading
import com.zacco.rytm.domain.body.ScaleScreenshotParser
import kotlinx.coroutines.tasks.await

/** Rozpoznanie tekstu ze zrzutu ekranu wagi (ML Kit, model w aplikacji, bez wysyłania obrazu). */
object ScaleOcr {
    suspend fun read(context: Context, uri: Uri): ScaleReading? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(InputImage.fromFilePath(context, uri)).await()
            // Linie w kolejności bloków: tak parser najlepiej łączy wartości z etykietami.
            val text = result.textBlocks.flatMap { b -> b.lines.map { it.text } }.joinToString("\n")
            return ScaleScreenshotParser.parse(text)
        } finally {
            recognizer.close()
        }
    }
}
