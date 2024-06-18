package com.example.loraspair.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Интерфейс управления пользователями в базе данных
@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM user WHERE user_sign LIKE :userSign")
    suspend fun getUserByUserSign(userSign: String): User?

    @Query("SELECT * FROM user WHERE user_id LIKE :userId")
    suspend fun getUserByUserId(userId: Long): User?

    @Query("SELECT * FROM user where rowid LIKE :rowId")
    suspend fun getUserByRowId(rowId: Long): User?

    @Query("SELECT * FROM incoming_message WHERE sender_id LIKE :userId AND to_all LIKE 0 ORDER BY message_id DESC LIMIT :start, :amount")
    suspend fun getLastIncomingMessagesFromUserByUserId(
        userId: Long,
        start: Int,
        amount: Int
    ): List<IncomingMessage>

    @Query("DELETE FROM incoming_message WHERE sender_id LIKE :userId")
    suspend fun deleteAllIncomingMessagesFromUserByUserId(userId: Long)

    @Query("SELECT * FROM outgoing_message WHERE receiver_id LIKE :userId ORDER BY message_id DESC LIMIT :start, :amount")
    suspend fun getLastOutgoingMessagesToUserByUserId(
        userId: Long,
        start: Int,
        amount: Int
    ): List<OutgoingMessage>

    @Query("DELETE FROM outgoing_message WHERE receiver_id LIKE :userId")
    suspend fun deleteAllOutgoingMessagesToUserByUserId(userId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUsers(vararg user: User): List<Long>

    @Delete
    suspend fun deleteUsers(vararg user: User)
    suspend fun getUserByUserSignOrInsertUser(userSign: String): User? {
        return getUserByUserSign(userSign) ?: getUserByRowId(
            insertUsers(
                User(
                    0,
                    userSign
                )
            ).first()
        )
    }
}