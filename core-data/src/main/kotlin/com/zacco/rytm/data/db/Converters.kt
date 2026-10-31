package com.zacco.rytm.data.db

import androidx.room.TypeConverter
import com.zacco.rytm.domain.model.BodyArea
import com.zacco.rytm.domain.model.LoadedArea
import java.time.Instant
import java.time.LocalDate

/** Daty jako tekst ISO (sortuje się poprawnie), chwile jako milisekundy, zbiory enumów jako CSV. */
class Converters {
    @TypeConverter fun dateToString(d: LocalDate?): String? = d?.toString()
    @TypeConverter fun stringToDate(s: String?): LocalDate? = s?.let(LocalDate::parse)

    @TypeConverter fun instantToLong(i: Instant?): Long? = i?.toEpochMilli()
    @TypeConverter fun longToInstant(l: Long?): Instant? = l?.let(Instant::ofEpochMilli)

    @TypeConverter fun loadedToString(s: Set<LoadedArea>): String = s.joinToString(",") { it.name }
    @TypeConverter fun stringToLoaded(s: String): Set<LoadedArea> =
        s.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { LoadedArea.valueOf(it) }.getOrNull() }.toSet()

    @TypeConverter fun bodyToString(s: Set<BodyArea>): String = s.joinToString(",") { it.name }
    @TypeConverter fun stringToBody(s: String): Set<BodyArea> =
        s.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { BodyArea.valueOf(it) }.getOrNull() }.toSet()
}
