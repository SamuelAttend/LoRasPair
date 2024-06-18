package com.example.loraspair.database

import androidx.room.TypeConverter
import com.example.loraspair.DeviceCommandsManager
import java.util.Date

// Преобразователи структур данных
class Converters {
    @TypeConverter
    fun timestampToDate(timestamp: Long?): Date? { // Число в дату
        return timestamp?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? { // Дату в число
        return date?.time
    }

    @TypeConverter
    fun dmmToDouble(dmm: DeviceCommandsManager.Message.GPS.DMM): Double { // DMM в число с плавающей запятой
        return (dmm.degrees + (dmm.minutes / 60.0f)).toDouble()
    }

    @TypeConverter
    fun doubleToDmm(double: Double): DeviceCommandsManager.Message.GPS.DMM { // Число с плавающей запятой в DMM
        val int: Int = double.toInt()
        val decimal: Float = (double - int).toFloat()
        return DeviceCommandsManager.Message.GPS.DMM(
            int,
            decimal * 60.0f
        )
    }
}