package com.example.loraspair.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Интерфейс управления исходящими сообщениями в базе данных
@Dao
interface OutgoingMessageDao {
    @Query("SELECT * FROM outgoing_message WHERE receiver_id LIKE 'CQCQCQ'")
    suspend fun getAllOutgoingMessagesToAll(): List<OutgoingMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllOutgoingMessages(vararg outgoingMessage: OutgoingMessage): List<Long>

    @Delete
    suspend fun deleteOutgoingMessages(vararg outgoingMessage: OutgoingMessage)
}