package com.zacco.rytm.wear.tile

import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.material3.titleCard
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import com.zacco.rytm.wear.MainActivity
import com.zacco.rytm.wear.data.WatchStore
import com.zacco.rytm.wear.ui.Labels

/** Kafelek „Dziś”: gotowość, plan dnia i pora snu. */
class TodayTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val ctx = this@TodayTileService
        val s = WatchStore.current(ctx)
        val layout = if (s == null) {
            primaryLayout(
                titleSlot = { text("Rytm".layoutString) },
                mainSlot = { text("Otwórz Rytm na telefonie, żeby wysłać plan.".layoutString, maxLines = 3) },
                bottomSlot = { textEdgeButton(onClick = TileActions.open(ctx, MainActivity.SCREEN_HOME)) { text("Otwórz".layoutString) } },
            )
        } else {
            val stale = WatchStore.isStale(s)
            val title = when {
                stale -> "Dane z wczoraj"
                s.calibrating -> "Gotowość ${s.readinessScore}*"
                else -> "Gotowość ${s.readinessScore}"
            }
            val detail = s.planWarning ?: "Sen do ${s.bedtime}"
            val needsCheckIn = !stale && !s.checkInDone
            primaryLayout(
                titleSlot = { text(title.layoutString) },
                mainSlot = {
                    titleCard(
                        onClick = TileActions.open(ctx, MainActivity.SCREEN_HOME),
                        title = { text(s.planTitle.layoutString, maxLines = 2) },
                        content = {
                            text(
                                "${Labels.intensity(s.planIntensity)} · $detail".layoutString,
                                typography = Typography.BODY_SMALL,
                                maxLines = 2,
                            )
                        },
                        height = expand(),
                    )
                },
                bottomSlot = {
                    if (needsCheckIn) {
                        textEdgeButton(onClick = TileActions.open(ctx, MainActivity.SCREEN_CHECK_IN)) { text("Check-in".layoutString) }
                    } else if (s.toRate.isNotEmpty()) {
                        textEdgeButton(onClick = TileActions.open(ctx, MainActivity.SCREEN_RPE)) { text("Oceń RPE".layoutString) }
                    } else {
                        textEdgeButton(onClick = TileActions.open(ctx, MainActivity.SCREEN_HOME)) { text("Otwórz".layoutString) }
                    }
                },
            )
        }
        return tile(Timeline.fromLayoutElement(layout))
    }
}
