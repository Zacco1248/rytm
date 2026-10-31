package com.zacco.rytm.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.zacco.rytm.wear.MainActivity
import com.zacco.rytm.wear.data.WatchStore

/** Komplikacja z gotowością (0–100) na tarczę. Odświeżana przy każdym nowym stanie z telefonu. */
class ReadinessComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, 72)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val s = WatchStore.current(this)?.takeUnless { WatchStore.isStale(it) }
            ?: return NoDataComplicationData()
        return build(request.complicationType, s.readinessScore)
    }

    private fun build(type: ComplicationType, score: Int): ComplicationData? {
        val text = PlainComplicationText.Builder(score.toString()).build()
        val description = PlainComplicationText.Builder("Gotowość $score").build()
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(score.toFloat(), 0f, 100f, description)
                    .setText(text)
                    .setTitle(PlainComplicationText.Builder("GOT").build())
                    .setTapAction(tap)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text, description)
                    .setTitle(PlainComplicationText.Builder("Gotow.").build())
                    .setTapAction(tap)
                    .build()
            else -> null
        }
    }
}
