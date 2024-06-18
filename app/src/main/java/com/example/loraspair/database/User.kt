package com.example.loraspair.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Структура данных пользователя базы данных
@Entity(
    tableName = "user",
    indices = [
        Index(
            "user_id", unique = true
        ),
        Index(
            "user_sign", unique = true
        )
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true) val user_id: Long,
    @ColumnInfo val user_sign: String
)
