package com.zacco.rytm.data.repo

import android.content.Context
import com.zacco.rytm.domain.coach.DayOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Plany dni zmienione w rozmowie z trenerem. Trzymamy ostatnie 2 tygodnie. */
class DayOverrideStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("day_overrides", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(DayOverride.serializer())

    private val _all = MutableStateFlow(load())
    val all: StateFlow<List<DayOverride>> = _all.asStateFlow()

    fun set(o: DayOverride) = save(_all.value.filterNot { it.date == o.date } + o)

    fun clear(date: LocalDate) = save(_all.value.filterNot { it.date == date.toString() })

    fun forDate(date: LocalDate): DayOverride? = _all.value.lastOrNull { it.date == date.toString() }

    private fun save(list: List<DayOverride>) {
        val from = LocalDate.now().minusDays(KEEP_DAYS).toString()
        val kept = list.filter { it.date >= from }.sortedBy { it.date }
        prefs.edit().putString(KEY, json.encodeToString(serializer, kept)).apply()
        _all.value = kept
    }

    private fun load(): List<DayOverride> =
        prefs.getString(KEY, null)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY = "list"
        const val KEEP_DAYS = 14L
    }
}
