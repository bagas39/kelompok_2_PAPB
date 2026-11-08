package com.example.cloble.data.local.converter

import androidx.room.TypeConverter
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationType

class CloBleTypeConverters {

    @TypeConverter
    fun fromItemStatus(status: ItemStatus?): String? = status?.name

    @TypeConverter
    fun toItemStatus(value: String?): ItemStatus? =
        value?.let { runCatching { ItemStatus.valueOf(it) }.getOrDefault(ItemStatus.AVAILABLE) }

    @TypeConverter
    fun fromInterestStatus(status: InterestStatus?): String? = status?.name

    @TypeConverter
    fun toInterestStatus(value: String?): InterestStatus? =
        value?.let { runCatching { InterestStatus.valueOf(it) }.getOrDefault(InterestStatus.PENDING) }

    @TypeConverter
    fun fromMatchStatus(status: MatchStatus?): String? = status?.name

    @TypeConverter
    fun toMatchStatus(value: String?): MatchStatus? =
        value?.let { runCatching { MatchStatus.valueOf(it) }.getOrDefault(MatchStatus.ACTIVE) }

    @TypeConverter
    fun fromNotificationType(type: NotificationType?): String? = type?.name

    @TypeConverter
    fun toNotificationType(value: String?): NotificationType? =
        value?.let { runCatching { NotificationType.valueOf(it) }.getOrDefault(NotificationType.INCOMING_INTEREST) }
}
