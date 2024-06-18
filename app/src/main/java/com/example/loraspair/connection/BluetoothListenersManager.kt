package com.example.loraspair.connection

import com.example.loraspair.DeviceCommandsManager

// Менеджер слушателей Bluetooth
class BluetoothListenersManager {
    interface BluetoothListener { // Интерфейс слушателя Bluetooth
        fun onBluetoothInfoReceived(info: String) // Вызывается для оповещения о новом статусе подключения
        fun onBluetoothMessageReceived(variant: DeviceCommandsManager.Message.Variant) // Вызывается для оповещения о новом входящем сообщении
    }

    companion object { // Статические поля и методы
        private val listeners = mutableSetOf<BluetoothListener>() // Множество слушателей Bluetooth

        fun addListener(listener: BluetoothListener) { // Добавление нового слушателя Bluetooth
            listeners.add(listener)
        }

        fun removeListener(listener: BluetoothListener) { // Удаление слушателя Bluetooth
            listeners.remove(listener)
        }

        fun getListeners() : Set<BluetoothListener> { // Получение всех слушателей Bluetooth
            return listeners.toSet()
        }
    }
}