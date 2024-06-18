package com.example.loraspair.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

// Структура данных входящего сообщения базы данных

@Entity(
    tableName = "incoming_message",
    indices = [
        Index(
            value = ["message_id"], unique = true
        )
    ],
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["user_id"],
            childColumns = ["sender_id"]
        ),
        ForeignKey(
            entity = Sticker::class,
            parentColumns = ["sticker_emoji"],
            childColumns = ["sticker_emoji"]
        )
    ]
)
data class IncomingMessage(
    @PrimaryKey(autoGenerate = true) val message_id: Long, // Идентификатор
    @ColumnInfo val sender_id: Long, // Идентификатор отправителя
    @ColumnInfo val to_all: Boolean, // Адресовано ли сообщение всем
    @ColumnInfo val date_time: Date, // Дата и время
    @ColumnInfo val text: String, // Текст сообщения
    @ColumnInfo val sticker_emoji: String? // Идентификатор привязанного изображения к сообщению
)
