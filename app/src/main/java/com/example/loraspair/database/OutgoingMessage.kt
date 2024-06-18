package com.example.loraspair.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

// Структура данных исходящего сообщения базы данных
@Entity(
    tableName = "outgoing_message",
    indices = [
        Index(
            value = ["message_id"], unique = true
        )
    ],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["user_id"],
            childColumns = ["receiver_id"]
        ),
        ForeignKey(
            entity = Sticker::class,
            parentColumns = ["sticker_emoji"],
            childColumns = ["sticker_emoji"]
        )
    ]
)
data class OutgoingMessage(
    @PrimaryKey(autoGenerate = true) val message_id: Long, // Идентификатор
    @ColumnInfo val receiver_id: Long, // Идентификатор получателя
    @ColumnInfo val status: Boolean, // Статус сообщения
    @ColumnInfo val date_time: Date, // Дата и время
    @ColumnInfo val text: String, // Текст сообщения
    @ColumnInfo val sticker_emoji: String? // Идентификатор привязанного изображения к сообщению
)
