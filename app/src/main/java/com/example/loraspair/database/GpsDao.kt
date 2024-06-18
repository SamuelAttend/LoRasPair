package com.example.loraspair.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

// Интерфейс управления координатами в базе данных

@Dao
interface GpsDao {
    @Query("SELECT * FROM gps")
    suspend fun getAllGps(): List<Gps>

    @Query("SELECT user_id, date_time, latitude, longitude, altitude, rx_message, gps_message FROM (SELECT *, max(date_time) FROM gps GROUP BY user_id)")
    suspend fun getLastGpsOfAllUsers(): List<Gps>

    @Query("SELECT * FROM gps WHERE user_id LIKE :userId ORDER BY date_time DESC LIMIT :start, :amount")
    suspend fun getLastGpsFromUserByUserId(
        userId: Long,
        start: Int,
        amount: Int
    ): List<Gps>

    @Query("DELETE FROM gps")
    suspend fun deleteAllGps()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllGps(vararg gps: Gps): List<Long>

    @Delete
    suspend fun deleteGps(vararg gps: Gps)
}