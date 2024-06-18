package com.example.loraspair.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.example.loraspair.DeviceCommandsManager
import java.util.Date

// Структура данных координат базы данных

@Entity(
    tableName = "gps",
    primaryKeys = ["user_id", "date_time"]
)
data class Gps(
    @ColumnInfo val user_id: Long, // Идентификатор пользователя
    @ColumnInfo val date_time: Date, // Дата и время
    @ColumnInfo val latitude: DeviceCommandsManager.Message.GPS.DMM, // Широта
    @ColumnInfo val longitude: DeviceCommandsManager.Message.GPS.DMM, // Долгота
    @ColumnInfo val altitude: Float, // Высота
    @ColumnInfo val rx_message: String, // RX-сообщение
    @ColumnInfo val gps_message: String // GPS-сообщение
)
