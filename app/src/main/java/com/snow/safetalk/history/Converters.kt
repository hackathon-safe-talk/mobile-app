package com.snow.safetalk.history

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(data: String): List<String> {
        return try {
            json.decodeFromString(data)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromMessageSource(source: MessageSource): String = source.name

    @TypeConverter
    fun toMessageSource(name: String): MessageSource = try { MessageSource.valueOf(name) } catch (e: Exception) { MessageSource.MANUAL }

    @TypeConverter
    fun fromRiskLabel(label: RiskLabel): String = label.name

    @TypeConverter
    fun toRiskLabel(name: String): RiskLabel = try { RiskLabel.valueOf(name) } catch (e: Exception) { RiskLabel.SAFE }
}
