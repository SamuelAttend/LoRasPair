package com.example.loraspair.adapters

import android.content.Context
import com.example.loraspair.App
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.database.User

// Структура данных пользователя для списка пользователей
data class ListUser(
    val sign: String, // Позывной пользователя
    var isCurrent: Boolean // Ведётся ли с этим пользователем в данный момент диалог в чате
) {
    companion object { // Статические поля и методы
        private val sharedPreferences =
            App.self.getSharedPreferences( // Запрос хранилища примитивных данных чата
                SharedPreferencesConstants.CHAT, // Название хранилища
                Context.MODE_PRIVATE // Тип доступа к хранилищу
            )

        fun fromUser(user: User): ListUser { // Приведение структуры данных пользователя базы данных к структуре данных пользователя для списка пользователей
            return ListUser( // Возврат структуры данных пользователя для списка пользователей
                user.user_sign,
                sharedPreferences.getString(
                    SharedPreferencesConstants.CHAT_SIGN, ""
                ) == user.user_sign
            )
        }
    }
}
