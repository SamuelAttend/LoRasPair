package com.example.loraspair.adapters

import android.bluetooth.BluetoothDevice

// Структура данных девайса для списка девайсов
data class ListDevice(
    val device: BluetoothDevice, // Bluetooth-девайс
    val isEnabled: Boolean // Является ли девайс выбранным
)
