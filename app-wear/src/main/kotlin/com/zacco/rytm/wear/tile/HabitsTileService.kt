package com.zacco.rytm.wear.tile

import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonDefaults.filledButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.loadAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.tile
import com.zacco.rytm.sync.WatchEvent
import com.zacco.rytm.wear.MainActivity
import com.zacco.rytm.wear.data.WatchStore

/**
 * Kafelek „Nawyki”: odhaczanie bez otwierania aplikacji. Klik niesie docelowy stan („habit:id:1”),
 * a nie „przełącz”, bo ten sam lastClickableId może przyjść w kolejnym odświeżeniu kafelka.
 */
class HabitsTileService : Material3TileService() {

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val ctx = this@HabitsTileService
        handleClick(requestParams.currentState.lastClickableId)

        val s = WatchStore.current(ctx)?.takeUnless { WatchStore.isStale(it) }
        val manual = s?.habits.orEmpty().filterNot { it.auto }.take(MAX_BUTTONS)
        val steps = s?.habits.orEmpty().firstOrNull { it.auto }

        val layout = primaryLayout(
            titleSlot = { text((steps?.let { "Kroki ${it.detail}" } ?: "Nawyki").layoutString) },
            mainSlot = {
                if (manual.isEmpty()) {
                    text((if (s == null) "Brak danych z telefonu" else "Na dziś bez nawyków").layoutString, maxLines = 2)
                } else {
                    val column = LayoutElementBuilders.Column.Builder().setWidth(expand())
                    manual.forEachIndexed { i, h ->
                        val secondary: (MaterialScope.() -> LayoutElementBuilders.LayoutElement)? =
                            if (h.detail.isNotBlank()) {
                                { text(h.detail.layoutString, maxLines = 1) }
                            } else {
                                null
                            }
                        if (i > 0) column.addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build())
                        column.addContent(
                            button(
                                onClick = clickable(action = loadAction(), id = "$PREFIX${h.id}:${if (h.done) 0 else 1}"),
                                labelContent = { text(((if (h.done) "✓ " else "") + h.name).layoutString, maxLines = 1) },
                                secondaryLabelContent = secondary,
                                width = expand(),
                                colors = if (h.done) filledButtonColors() else filledTonalButtonColors(),
                            ),
                        )
                    }
                    column.build()
                }
            },
            bottomSlot = { textEdgeButton(onClick = TileActions.open(ctx, MainActivity.SCREEN_HABITS)) { text("Więcej".layoutString) } },
        )
        return tile(Timeline.fromLayoutElement(layout))
    }

    private suspend fun handleClick(id: String) {
        if (!id.startsWith(PREFIX)) return
        val parts = id.removePrefix(PREFIX).split(":")
        if (parts.size != 2) return
        val (habitId, target) = parts
        val done = target == "1"
        val s = WatchStore.current(this) ?: return
        val habit = s.habits.firstOrNull { it.id == habitId } ?: return
        if (habit.auto || habit.done == done) return
        WatchStore.send(this, WatchEvent.HabitToggle(WatchStore.today(), habitId, done))
    }

    private companion object {
        const val PREFIX = "habit:"
        const val MAX_BUTTONS = 2
    }
}
