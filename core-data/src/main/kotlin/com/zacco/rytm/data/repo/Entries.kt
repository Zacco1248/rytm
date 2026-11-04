package com.zacco.rytm.data.repo

import com.zacco.rytm.data.db.RytmDatabase
import com.zacco.rytm.data.profile.ProfileStore
import com.zacco.rytm.domain.model.Source
import com.zacco.rytm.domain.time.RytmTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

enum class EntryKind { WEIGH_IN, NUTRITION, ACTIVITY, CHECK_IN, ALCOHOL, SLEEP }

/** Jeden wpis na liście „Wpisy” (do poprawienia albo usunięcia). */
data class Entry(
    val kind: EntryKind,
    /** Id ważenia albo aktywności; dla dni (żywienie, check-in, alkohol) data. */
    val id: String,
    val date: LocalDate,
    val time: Instant?,
    val title: String,
    val detail: String,
    /** Przyszło z Health Connect: po usunięciu może wrócić przy pełnej synchronizacji. */
    val fromHealthConnect: Boolean,
    /** Trening z dziennikiem serii (usuwa też serie). */
    val hasSets: Boolean = false,
    val rpe: Int? = null,
    /** Koniec (sen: pobudka). */
    val end: Instant? = null,
)

/** Przegląd, usuwanie i eksport wpisów. */
class EntriesRepository(private val db: RytmDatabase, private val profiles: ProfileStore) {

    fun observe(today: LocalDate, days: Long = 30): Flow<List<Entry>> {
        val from = today.minusDays(days - 1)
        return combine(
            db.weighIns().observe(from, today),
            db.nutrition().observe(from, today),
            db.activities().observe(from, today),
            db.checkIns().observe(from, today),
            db.dayNotes().observe(from, today),
        ) { w, n, a, c, notes -> Parts(w, n, a, c, notes) }.combine(db.dailyMetrics().observe(from, today)) { p, metrics ->
            val (w, n, a, c, notes) = p
            val list = mutableListOf<Entry>()
            metrics.filter { it.sleepManual && it.sleepStart != null && it.sleepEnd != null }.forEach { e ->
                val hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                val from = e.sleepStart!!.atZone(RytmTime.zone).format(hm)
                val to = e.sleepEnd!!.atZone(RytmTime.zone).format(hm)
                val min = e.sleepMin ?: 0
                list += Entry(
                    EntryKind.SLEEP, e.date.toString(), e.date, e.sleepStart, "Sen (ręcznie)",
                    "$from–$to · ${min / 60} h ${min % 60} min", fromHealthConnect = false, end = e.sleepEnd,
                )
            }
            w.forEach { e ->
                list += Entry(
                    EntryKind.WEIGH_IN, e.id, e.date, e.time, "Ważenie",
                    listOfNotNull(e.weightKg?.let { "${kg(it)} kg" }, e.bodyFatPct?.let { "tłuszcz ${kg(it)}%" }).joinToString(" · "),
                    fromHealthConnect = e.origin != OWN,
                )
            }
            n.forEach { e ->
                list += Entry(
                    EntryKind.NUTRITION, e.date.toString(), e.date, null, "Żywienie",
                    "${e.kcal} kcal · B ${e.proteinG.toInt()} g", fromHealthConnect = !e.manual,
                )
            }
            a.forEach { e ->
                list += Entry(
                    EntryKind.ACTIVITY, e.id, e.date, e.start, e.title ?: e.notes ?: e.type.name,
                    "${e.durationMin} min" + (e.rpe?.let { " · RPE $it" } ?: "") + (e.kcal?.let { " · $it kcal" } ?: ""),
                    fromHealthConnect = e.source == Source.HEALTH_CONNECT,
                    hasSets = e.origin == OWN,
                    rpe = e.rpe,
                )
            }
            c.forEach { e ->
                list += Entry(EntryKind.CHECK_IN, e.date.toString(), e.date, e.createdAt, "Check-in", "energia ${e.energy} · nastrój ${e.mood}", false)
            }
            notes.filter { it.alcoholDrinks > 0 }.forEach { e ->
                list += Entry(EntryKind.ALCOHOL, e.date.toString(), e.date, null, "Alkohol", "${e.alcoholDrinks} porc.", false)
            }
            list.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.time ?: Instant.EPOCH })
        }
    }

    suspend fun deleteWeighIn(id: String) = db.weighIns().delete(id)

    suspend fun delete(e: Entry) {
        when (e.kind) {
            EntryKind.WEIGH_IN -> db.weighIns().delete(e.id)
            EntryKind.NUTRITION -> db.nutrition().delete(e.date)
            EntryKind.ACTIVITY -> {
                db.workoutSets().deleteFor(e.id)
                db.activities().delete(e.id)
            }
            EntryKind.CHECK_IN -> db.checkIns().delete(e.date)
            EntryKind.SLEEP -> {
                val m = db.dailyMetrics().get(e.date) ?: return
                db.dailyMetrics().upsert(m.copy(sleepMin = null, sleepStart = null, sleepEnd = null, nightRestingHr = null, sleepManual = false, updatedAt = Instant.now()))
            }
            EntryKind.ALCOHOL -> {
                val note = db.dayNotes().get(e.date) ?: return
                if (!note.dayOff) db.dayNotes().delete(e.date) else db.dayNotes().upsert(note.copy(alcoholDrinks = 0, updatedAt = Instant.now()))
            }
        }
    }

    /** Wszystkie dane w jednym pliku JSON (kopia albo przeniesienie). Bez klucza AI. */
    suspend fun exportJson(today: LocalDate = LocalDate.now(RytmTime.zone)): String {
        val from = LocalDate.of(2000, 1, 1)
        val to = today.plusDays(7)
        val acts = db.activities().observe(from, to).first()
        val metrics = db.dailyMetrics().observe(from, to).first()
        val nutrition = db.nutrition().observe(from, to).first()
        val weighIns = db.weighIns().observe(from, to).first()
        val checkIns = db.checkIns().observe(from, to).first()
        val habits = db.habitLogs().observe(from, to).first()
        val notes = db.dayNotes().observe(from, to).first()
        val sets = db.workoutSets().observe(from, to).first()
        val root: JsonObject = buildJsonObject {
            put("app", "Rytm")
            put("exportedAt", Instant.now().toString())
            profiles.current()?.let { p ->
                put("profile", Json.parseToJsonElement(Json.encodeToString(com.zacco.rytm.domain.profile.PersonalProfile.serializer(), p)))
            }
            putJsonArray("activities") {
                acts.forEach { e ->
                    add(obj("id" to e.id, "type" to e.type.name, "start" to e.start.toString(), "durationMin" to e.durationMin, "rpe" to e.rpe,
                        "avgHr" to e.avgHr, "kcal" to e.kcal, "source" to e.source.name, "title" to e.title, "notes" to e.notes))
                }
            }
            putJsonArray("dailyMetrics") {
                metrics.forEach { e ->
                    add(obj("date" to e.date.toString(), "sleepMin" to e.sleepMin, "sleepStart" to e.sleepStart?.toString(),
                        "sleepEnd" to e.sleepEnd?.toString(), "sleepManual" to e.sleepManual, "nightRestingHr" to e.nightRestingHr,
                        "dayRestingHr" to e.dayRestingHr, "steps" to e.steps, "totalKcal" to e.totalKcal))
                }
            }
            putJsonArray("nutrition") {
                nutrition.forEach { e ->
                    add(obj("date" to e.date.toString(), "kcal" to e.kcal, "proteinG" to e.proteinG, "fatG" to e.fatG, "carbsG" to e.carbsG, "manual" to e.manual))
                }
            }
            putJsonArray("weighIns") {
                weighIns.forEach { e ->
                    add(obj("time" to e.time.toString(), "weightKg" to e.weightKg, "bodyFatPct" to e.bodyFatPct, "origin" to e.origin))
                }
            }
            putJsonArray("checkIns") {
                checkIns.forEach { e ->
                    add(obj("date" to e.date.toString(), "energy" to e.energy, "mood" to e.mood, "sore" to e.soreAreas.joinToString(",") { it.name }, "hrv" to e.hrvRmssd))
                }
            }
            putJsonArray("habits") { habits.forEach { e -> add(obj("habit" to e.habitId, "date" to e.date.toString(), "value" to e.value)) } }
            putJsonArray("dayNotes") {
                notes.forEach { e -> add(obj("date" to e.date.toString(), "dayOff" to e.dayOff, "alcoholDrinks" to e.alcoholDrinks)) }
            }
            putJsonArray("sets") {
                sets.forEach { e ->
                    add(obj("workoutId" to e.workoutId, "exercise" to e.exercise, "set" to e.setIndex, "reps" to e.reps,
                        "weightKg" to e.weightKg, "rir" to e.rir, "date" to e.date.toString()))
                }
            }
        }
        return Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), root)
    }

    private data class Parts(
        val w: List<com.zacco.rytm.data.db.WeighInEntity>,
        val n: List<com.zacco.rytm.data.db.NutritionDayEntity>,
        val a: List<com.zacco.rytm.data.db.ActivityEntity>,
        val c: List<com.zacco.rytm.data.db.CheckInEntity>,
        val notes: List<com.zacco.rytm.data.db.DayNoteEntity>,
    )

    private fun obj(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
        pairs.associate { (k, v) ->
            k to when (v) {
                null -> kotlinx.serialization.json.JsonNull
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else -> JsonPrimitive(v.toString())
            }
        },
    )

    private fun kg(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, v).replace('.', ',')

    private companion object {
        const val OWN = "com.zacco.rytm"
    }
}

