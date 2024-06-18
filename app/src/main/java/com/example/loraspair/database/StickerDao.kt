package com.example.loraspair.database

import androidx.room.Dao
import androidx.room.Query

// Интерфейс управления изображениями в базе данных
@Dao
interface StickerDao {
    @Query("SELECT * FROM sticker")
    suspend fun getAllStickers(): List<Sticker>

    @Query("SELECT EXISTS (SELECT * FROM sticker WHERE sticker_emoji LIKE :stickerEmoji)")
    suspend fun checkStickerExistsByStickerEmoji(stickerEmoji: String) : Boolean

    @Query("SELECT * FROM sticker WHERE sticker_emoji LIKE :stickerEmoji")
    suspend fun getStickerByStickerEmoji(stickerEmoji: String) : Sticker
}