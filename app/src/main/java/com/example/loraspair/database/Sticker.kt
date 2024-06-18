package com.example.loraspair.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Структура данных изображения базы данных

@Entity(
    tableName = "sticker",
    indices = [
        Index(
            "sticker_emoji", unique = true
        )
    ]
)
data class Sticker(
    @PrimaryKey val sticker_emoji: String, // Идентификатор изображения
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val sticker: ByteArray // Массив байтов изображения
)
