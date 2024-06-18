package com.example.loraspair.connection

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.example.loraspair.DeviceCommandsManager
import java.io.IOException
import java.util.UUID

// Поток Bluetooth-соединения
class BluetoothConnectionThread(
    device: BluetoothDevice // Bluetooth-девайс
) : Thread() { // Наследование от стандартного потока
    private var socket: BluetoothSocket? = null // Инициализация Bluetooth-сокета со значением null
    private val uuid: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Bluetooth-идентификатор

    object Constants { // Статические поля в пространстве имён Constants
        const val BLUETOOTH_CONNECTED = "bluetooth_connected"
        const val BLUETOOTH_NOT_CONNECTED = "bluetooth_not_connected"
        const val BLUETOOTH_CONNECTING = "bluetooth_connecting"
    }

    @Volatile
    private var isActive: Boolean = true // Является ли поток активным

    @Volatile
    var isConnected: Boolean = false // Соединён ли поток с девайсом
        private set

    init { // Конструктор класса
        try {
            socket = device.createRfcommSocketToServiceRecord(uuid) // Создание Bluetooth-сокета
        } catch (_: IOException) {
        } catch (_: SecurityException) {
        }
    }

    override fun run() { // Запуск потока
        super.run()
        BluetoothListenersManager.getListeners()
            .forEach { it.onBluetoothInfoReceived(Constants.BLUETOOTH_CONNECTING) } // Сообщение всем слушателям Bluetooth информации о попытке подключения к девайсу
        while (isActive) { // Если поток активен
            try {
                socket?.connect() // Подключение к девайсу, при неудаче вызывает исключение IOException
                readMessage() // Чтение принимаемых данных сокета
            } catch (_: IOException) {
            } catch (_: SecurityException) {
            }
            sleep(500) // Приостановка потока на 500 мс
        }
    }

    fun close() { // Закрытие потока
        try {
            BluetoothListenersManager.getListeners()
                .forEach { it.onBluetoothInfoReceived(Constants.BLUETOOTH_NOT_CONNECTED) } // Сообщение всем слушателям Bluetooth информации об отключении от девайса
            isActive = false // Поток не активен
            socket?.close() // Закрытие Bluetooth-сокета
            socket = null // Установка сокета на значение null
        } catch (_: IOException) {
        }
    }

    private fun readMessage() { // Чтение принимаемых данных сокета
        isConnected = true // Сокет соединён с девайсом
        BluetoothListenersManager.getListeners()
            .forEach { it.onBluetoothInfoReceived(Constants.BLUETOOTH_CONNECTED) } // Сообщение всем слушателям Bluetooth информации об успешном подключении к девайсу
        val buffer = ByteArray(256) // Инициализация буфера принимаемых данных Bluetooth
        while (isActive) { // Пока поток активен
            try {
                val size = socket?.inputStream?.read(buffer)
                    ?: 0 // Чтение данных с входящего потока сокета Bluetooth и запись в буфер, при ошибке вызывает исключение IOException
                val message = buffer.copyOf(size) // Обрезка буфера согласно размеру принятых данных
                DeviceCommandsManager.Responses.handleResponseMessage(message) // Обработка принятых данных
            } catch (e: IOException) {
                break // Выход из цикла ожидания данных
            }
        }
        isConnected = false // Сокет не соединён с девайсом
        if (isActive) { // Если поток активен
            BluetoothListenersManager.getListeners().forEach {
                it.onBluetoothInfoReceived(
                    Constants.BLUETOOTH_CONNECTING
                )
            } // Сообщение всем слушателям Bluetooth информации о попытке подключения к девайсу
        }
    }

    @Synchronized
    fun writeMessage(message: ByteArray) { // Отправка данных по сокету
        try {
            socket?.outputStream?.write(message) // Запись данных в исходящий поток сокета
        } catch (_: IOException) {
        }
    }
}