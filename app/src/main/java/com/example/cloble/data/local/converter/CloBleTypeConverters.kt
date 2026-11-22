package com.example.cloble.data.local.converter

import androidx.room.TypeConverter
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus

class CloBleTypeConverters {

    @TypeConverter
    fun fromItemStatus(status: ItemStatus?): String? = status?.name

    @TypeConverter
    fun toItemStatus(value: String?): ItemStatus? =
        value?.let { runCatching { ItemStatus.valueOf(it) }.getOrDefault(ItemStatus.AVAILABLE) }

    @TypeConverter
    fun fromMatchStatus(status: MatchStatus?): String? = status?.name

    @TypeConverter
    fun toMatchStatus(value: String?): MatchStatus? =
        value?.let { runCatching { MatchStatus.valueOf(it) }.getOrDefault(MatchStatus.ACTIVE) }
}
