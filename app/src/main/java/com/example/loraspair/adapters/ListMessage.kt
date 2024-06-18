package com.example.loraspair.adapters

import android.app.Service
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.loraspair.App
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.database.IncomingMessage
import com.example.loraspair.database.OutgoingMessage
import kotlinx.coroutines.runBlocking
import java.util.Date

// Структура данных сообщения для списка сообщений
data class ListMessage(
    val message_id: Long, // Идентификатор сообщения
    val sign: String, // Позывной отправителя сообщения
    val text: String, // Текст сообщения
    val sticker: Bitmap? = null, // Изображение сообщения
    val date_time: Date, // Дата и время сообщения
    val status: Boolean? = null // Статус сообщения
) {
    companion object { // Статические поля и методы
        private val sharedPreferences = App.self.getSharedPreferences( // Запрос хранилища примитивных данных девайса
            SharedPreferencesConstants.DEVICE, // Название хранилища
            Service.MODE_PRIVATE // Тип доступа к хранилищу
        )
        private val userDao = App.database.userDao() // Интерфейс управления пользователями в базе данных
        private val stickerDao = App.database.stickerDao() // Интерфейс управления изображениями (стикерами) в базе данных

        fun fromIncomingMessage(incomingMessage: IncomingMessage): ListMessage { // Приведение структуры данных входящего сообщения к структуре данных сообщения для списка сообщений
            with(incomingMessage) {// Использование области видимости входящего сообщения
                var stickerBitMap: Bitmap? = null // Инициализация изображения со значением null
                var sign = String() // Инициализация позывного отправителя со значением пустой строки

                runBlocking { // Блокирующая корутина (сопрограмма)
                    sticker_emoji?.let { // Если у входящего сообщения есть приклеплённое изображение
                        val stickerByteArray =
                            stickerDao.getStickerByStickerEmoji(it).sticker // Получение массива байтов изображения
                        stickerBitMap = BitmapFactory.decodeByteArray( // Установка изображения, преобразованного из массива байтов изображения
                            stickerByteArray, // Массив байтов изображения
                            0, // Отступ массива байтов изображения
                            stickerByteArray.size // Размер массива байтов изображения
                        )
                    }
                    userDao.getUserByUserId(sender_id)?.let {user -> // Если пользователь-отправитель не равен null
                        sign = user.user_sign // Установка позывного отправителя
                    }
                }

                return ListMessage( // Возврат структуры данных сообщения для списка сообщений
                    message_id,
                    sign,
                    text,
                    stickerBitMap,
                    date_time
                )
            }
        }

        fun fromOutgoingMessage(outgoingMessage: OutgoingMessage): ListMessage { // Приведение структуры данных исходящего сообщения к структуре данных сообщения для списка сообщений
            with(outgoingMessage) {// Использование области видимости исходящего сообщения
                var stickerBitMap: Bitmap? = null // Инициализация изображения со значением null
                val sign: String =
                    sharedPreferences.getString(SharedPreferencesConstants.DEVICE_SIGN, "") ?: "" // Инициализация позывного отправителя со значением позывного девайса

                runBlocking {// Блокирующая корутина
                    sticker_emoji?.let {// Если у входящего сообщения есть приклеплённое изображение
                        val stickerByteArray =
                            stickerDao.getStickerByStickerEmoji(it).sticker // Получение массива байтов изображения
                        stickerBitMap = BitmapFactory.decodeByteArray( // Установка изображения, преобразованного из массива байтов изображения
                            stickerByteArray, // Массив байтов изображения
                            0, // Отступ массива байтов изображения
                            stickerByteArray.size // Размер массива байтов изображения
                        )
                    }
                }

                return ListMessage( // Возврат структуры данных сообщения для списка сообщений
                    message_id,
                    sign,
                    text,
                    stickerBitMap,
                    date_time,
                    status
                )
            }
        }
    }
}
