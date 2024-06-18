package com.example.loraspair.adapters

// Структура данных команды для списка команд
data class ListCommand(
    var name: String, // Название команды
    var value: String, // Значение команды
    val isMutable : Boolean // Является ли команда стандартной или пользовательской
)
