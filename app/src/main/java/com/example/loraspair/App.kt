package com.example.loraspair

import android.app.Application
import com.example.loraspair.database.AppDatabase

// Приложение
class App : Application() {
    companion object { // Статические поля и методы
        lateinit var self: Application // Экземпляр приложения
            private set

        lateinit var database: AppDatabase // Экземпляр базы данных
            private set
    }

    override fun onCreate() {
        super.onCreate()
        self = this // Установка экземпляра приложения
        database = AppDatabase.getDatabase() // Установка экземпляра базы данных
    }
}