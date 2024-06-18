package com.example.loraspair.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Интерфейс управления входящими сообщениями в базе данных
@Dao
interface IncomingMessageDao {
    @Query("SELECT * FROM incoming_message WHERE to_all LIKE 1")
    suspend fun getLastIncomingMessagesToAll(): List<IncomingMessage>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIncomingMessages(vararg incomingMessage: IncomingMessage): List<Long>

    @Delete
    suspend fun deleteIncomingMessages(vararg incomingMessage: IncomingMessage)
}