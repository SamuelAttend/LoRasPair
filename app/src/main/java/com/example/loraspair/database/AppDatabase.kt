package com.example.loraspair.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.loraspair.App

// База данных пользователей, входящих и исходящих сообщений, координат и изображений
@Database(
    entities = [User::class, IncomingMessage::class, OutgoingMessage::class, Gps::class, Sticker::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun incomingMessageDao(): IncomingMessageDao
    abstract fun outgoingMessageDao(): OutgoingMessageDao
    abstract fun gpsDao(): GpsDao
    abstract fun stickerDao(): StickerDao

    companion object { // Статические поля и методы
        @Volatile
        private var INSTANCE: AppDatabase? = null // Инициализация экземпляра базы данных со значением null

        fun getDatabase(): AppDatabase { // Получение экземпляра базы данных, реализация паттерна Синглтон
            if (INSTANCE == null) {
                synchronized(AppDatabase::class) {
                    if (INSTANCE == null) {
                        INSTANCE = Room.databaseBuilder(
                            App.self.applicationContext,
                            AppDatabase::class.java,
                            "database.db"
                        ).createFromAsset("database/stickers.db").build()
                    }
                }
            }
            return INSTANCE!!
        }
    }
}