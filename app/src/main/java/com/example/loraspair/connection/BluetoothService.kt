package com.example.loraspair.connection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.loraspair.App
import com.example.loraspair.DeviceCommandsManager
import com.example.loraspair.R
import com.example.loraspair.SharedPreferencesConstants

// Сервис Bluetooth подключения
class BluetoothService : Service(), BluetoothListenersManager.BluetoothListener {
    object Constants { // Статические поля в пространстве имён Constants
        const val CHANNEL_ID = "LoRa's Pair Bluetooth Channel"
        const val CHANNEL_NAME = "Connected Device"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "intent.STOP_BLUETOOTH_SERVICE"

        const val BLUETOOTH_SERVICE_STARTED = "bluetooth_service_started"
        const val BLUETOOTH_SERVICE_STOPPED = "bluetooth_service_stopped"
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter // Адаптер Bluetooth
    private var bluetoothConnectionThread: BluetoothConnectionThread? =
        null // Ининциализация потока Bluetooth-соединения со значением null
    private val bluetoothReceiver =
        object : BroadcastReceiver() { // Инициалация получателя сигналов от адаптера Bluetooth
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state =
                            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        when (state) { // Если Bluetooth был включен, выключен или выключается
                            BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                                stopSelf() // Остановка работы сервиса
                            }
                        }
                    }
                }
            }
        }

    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var notificationManager: NotificationManager // Менеджер уведомлений
    private lateinit var notificationBuilder: NotificationCompat.Builder // Построитель уведомлений

    class StopBluetoothServiceReceiver :
        BroadcastReceiver() { // Получатель сигнала об отключении сервиса
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(Constants.ACTION_STOP)) {
                val stopBluetoothServiceIntent = Intent(context, BluetoothService::class.java)
                context?.stopService(stopBluetoothServiceIntent) // Остановка работы сервиса
            }
        }
    }

    companion object { // Статические поля и методы
        var self: BluetoothService? = null // Поле для получения экземпляра сервиса
    }

    override fun onCreate() { // При инициализации сервиса
        super.onCreate()

        self = this // Установка экземпляра сервиса

        BluetoothListenersManager.getListeners()
            .forEach { it.onBluetoothInfoReceived(Constants.BLUETOOTH_SERVICE_STARTED) } // Сообщение всем слушателям Bluetooth информации о запуске сервиса
        BluetoothListenersManager.addListener(this) // Добавление сервиса к слушателям Bluetooth

        sharedPreferences =
            App.self.getSharedPreferences(
                SharedPreferencesConstants.DEVICE,
                MODE_PRIVATE
            ) // Запрос хранилища примитивных данных девайса

        val notificationChannel = NotificationChannel(
            Constants.CHANNEL_ID,
            Constants.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ) // Инициализация канала уведомлений

        notificationManager =
            App.self.getSystemService(NotificationManager::class.java) // Установка менеджера уведомлений
        notificationManager.createNotificationChannel(notificationChannel) // Создание канала уведомлений

        initBluetoothAdapter() // Настройка адаптера Bluetooth
        initBluetoothReceiver() // Настройка получателя Bluetooth

        initNotification() // Настройка уведомлений
        connectDevice() // Соединение с девайсом
    }

    override fun onDestroy() {
        super.onDestroy()

        disconnectDevice() // Отсоединение от девайса
        unregisterReceiver(bluetoothReceiver) // Отвязка получателя сигналов от адаптера Bluetooth
        stopForeground(STOP_FOREGROUND_REMOVE) // Информирование об остановке работы сервиса

        BluetoothListenersManager.removeListener(this) // Удаление сервиса из слушателей Bluetooth
        BluetoothListenersManager.getListeners()
            .forEach { it.onBluetoothInfoReceived(Constants.BLUETOOTH_SERVICE_STOPPED) } // Сообщение всем слушателям Bluetooth информации об остановке сервиса

        self = null // Установка экземпляра сервиса на значение null
    }

    override fun onBind(intent: Intent?): IBinder {
        return Binder()
    }

    private fun initBluetoothAdapter() { // Настройка адаптера Bluetooth
        val bluetoothManager =
            getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager // Запрос менеджера Bluetooth
        bluetoothAdapter = bluetoothManager.adapter // Установка адаптера Bluetooth
    }

    private fun initBluetoothReceiver() { // Настройка получателя Bluetooth
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(
            bluetoothReceiver,
            filter
        ) // Установка интересующих фильтров для получателя Bluetooth
    }

    private fun initNotification() { // Настройка уведомлений
        val stopBluetoothServiceIntent =
            Intent(
                App.self.applicationContext,
                StopBluetoothServiceReceiver::class.java
            ) // Инициализация команды отключения сервиса
        stopBluetoothServiceIntent.setAction(Constants.ACTION_STOP) // Установка соответствующего действия команды

        val stopBluetoothServicePendingIntent = PendingIntent.getBroadcast(
            App.self.applicationContext,
            Constants.NOTIFICATION_ID,
            stopBluetoothServiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) // Инициализация обёртки над командой отключения сервиса

        val stopBluetoothServiceAction = NotificationCompat.Action.Builder(
            R.drawable.ic_delete,
            getString(R.string.bt_service_stop),
            stopBluetoothServicePendingIntent
        ).build() // Инициализация кнопки отключения сервиса

        notificationBuilder = NotificationCompat.Builder(
            this,
            Constants.CHANNEL_ID
        ) // Установка параметров в построитель уведомлений
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(stopBluetoothServiceAction)

        val notification = notificationBuilder.build() // Инициализация уведомления
        startForeground(Constants.NOTIFICATION_ID, notification) // Отображения уведоамления
    }

    private fun updateNotification() { // Обновление уведомления
        val name = sharedPreferences.getString(
            SharedPreferencesConstants.DEVICE_NAME,
            ""
        ) ?: "" // Запрос названия девайса из хранилища примитивных данных девайса
        val mac = sharedPreferences.getString(
            SharedPreferencesConstants.DEVICE_MAC,
            ""
        ) ?: "" // Запрос MAC-адреса девайса из хранилища примитивных данных девайса

        val notification = notificationBuilder
            .setContentTitle(
                if (bluetoothConnectionThread?.isConnected == true) name else "$name [${
                    getString(
                        R.string.bt_service_connecting
                    ).uppercase()
                }]"
            )
            .setContentText(mac)
            .build() // Инициализация нового уведомления

        notificationManager.notify(
            Constants.NOTIFICATION_ID,
            notification
        ) // Оповещение менеджера уведомлений о новом уведомлении
    }

    fun connectDevice() { // Соединение с девайсом
        val mac = sharedPreferences.getString(SharedPreferencesConstants.DEVICE_MAC, "")
            ?: "" // Запрос MAC-адреса девайса из хранилища примитивных данных девайса

        disconnectDevice() // Отсоединение от девайса
        if (bluetoothAdapter.isEnabled && mac.isNotEmpty()) { // Если адаптер Bluetooth доступен и MAC-адрес девайса не пуст
            try {
                bluetoothConnectionThread =
                    BluetoothConnectionThread(bluetoothAdapter.getRemoteDevice(mac)) // Создание потока Bluetooth-соединения
                bluetoothAdapter.cancelDiscovery() // Остановка поиска устройств
                bluetoothConnectionThread?.start() // Запуск потока Bluetooth-соединения
            } catch (_: SecurityException) {
            }
        }
    }

    @Synchronized
    fun communicateDevice(messages: List<ByteArray>) { // Отправка списка сообщений девайсу
        messages.forEach { message ->
            bluetoothConnectionThread?.writeMessage(message) // Отправка данных по сокету
            Thread.sleep(50) // Приостановка потока на 50 мс
        }
    }

    fun disconnectDevice() { // Отсоединение от девайса
        bluetoothConnectionThread?.close() // Закрытие потока Bluetooth-соединения
        bluetoothConnectionThread = null // Установка потока Bluetooth-соединения на значение null
    }

    val isConnected: Boolean // Соединён ли поток с девайсом
        get() {
            return bluetoothConnectionThread?.isConnected == true
        }

    override fun onBluetoothInfoReceived(info: String) { // Вызывается для оповещения о новом статусе подключения
        when (info) {
            BluetoothConnectionThread.Constants.BLUETOOTH_CONNECTING -> { // Если производится попытка подключения к девайсу
                updateNotification() // Обновление уведомления
            }

            BluetoothConnectionThread.Constants.BLUETOOTH_CONNECTED -> { // Если произведено успешное подключение к девайсу
                updateNotification() // Обновление уведомления
                bluetoothConnectionThread?.writeMessage(
                    DeviceCommandsManager.Requests.generateSimpleRequestMessage(
                        DeviceCommandsManager.Constants.Cmd.SIGN // Запрос позывного девайса
                    )
                )
            }
        }
    }

    override fun onBluetoothMessageReceived(variant: DeviceCommandsManager.Message.Variant) { // Вызывается для оповещения о новом входящем сообщении
    }

}