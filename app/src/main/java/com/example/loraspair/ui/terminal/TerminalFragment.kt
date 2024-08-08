package com.example.loraspair.ui.terminal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.loraspair.App
import com.example.loraspair.DeviceCommandsManager
import com.example.loraspair.R
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.connection.BluetoothConnectionThread
import com.example.loraspair.connection.BluetoothListenersManager
import com.example.loraspair.connection.BluetoothService
import com.example.loraspair.databinding.FragmentTerminalBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Фрагмент терминала (отправки команд)
class TerminalFragment : Fragment(),
    BluetoothListenersManager.BluetoothListener, OnSharedPreferenceChangeListener {
    private lateinit var binding: FragmentTerminalBinding // Привязка к интерфейсу фрагмента отправки команд
    private lateinit var sharedPreferences: SharedPreferences // Хранилище примитивных данных девайса

    object Constants {
        const val ACTION = "com.example.loraspair.USER_ADDED"
    }

    private val userAddBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(Constants.ACTION)) {
                updateData()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentTerminalBinding.inflate(
            inflater,
            container,
            false
        )

        sharedPreferences =
            App.self.applicationContext.getSharedPreferences(
                SharedPreferencesConstants.DEVICE,
                Context.MODE_PRIVATE
            ) // Запрос хранилища примитивных данных девайса

        BluetoothListenersManager.addListener(this) // Добавление сервиса к слушателям Bluetooth
        updateData() // Обновление данных фрагмента

        initSendCommandButton() // Настройка кнопки отправки команды

        val filter = IntentFilter()
        filter.addAction(Constants.ACTION)
        activity?.registerReceiver(userAddBroadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        binding.dropdownCommands.addTextChangedListener {// Добавление слушателя изменения текста в поле названия команды
            binding.commandValue.setText(
                DeviceCommandsManager.NamedCommands.default[binding.dropdownCommands.text.toString()]
                    ?: DeviceCommandsManager.NamedCommands.custom[binding.dropdownCommands.text.toString()]
            ) // Установка соответствующего значения в поле значения команды
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.unregisterReceiver(userAddBroadcastReceiver)
        BluetoothListenersManager.removeListener(this) // Удаление фрагмента из слушателей Bluetooth
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this) // Добавление слушателя изменения значений хранилища примитивных данных девайса
        updateData()
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this) // Удаление слушателя изменения значений хранилища примитивных данных девайса
    }

    private fun updateData() { // Обновление данных фрагмента
        binding.dropdownFrom.setText(
            sharedPreferences.getString(
                SharedPreferencesConstants.DEVICE_SIGN,
                ""
            )
        ) // Установка значения поля позывного отправителя

        lifecycleScope.launch { // Корутина жизненного цикла
            val signsArrayAdapter = ArrayAdapter(
                App.self.applicationContext,
                R.layout.dropdown_item,
                R.id.dropdown_item,
                App.database.userDao().getAllUsers().map { user -> user.user_sign }
            )
            binding.dropdownTo.setAdapter(signsArrayAdapter) // Установка значений выпадающего списка позывных получателей
        }

        val commandsArrayAdapter = ArrayAdapter(
            App.self.applicationContext,
            R.layout.dropdown_item,
            R.id.dropdown_item,
            DeviceCommandsManager.NamedCommands.default.keys.toList()
                    + DeviceCommandsManager.NamedCommands.custom.keys.toList()
        )
        binding.dropdownCommands.setAdapter(commandsArrayAdapter) // Установка значений выпадающего списка названий команд

        binding.commandValue.setText(
            DeviceCommandsManager.NamedCommands.default[binding.dropdownCommands.text.toString()]
                ?: DeviceCommandsManager.NamedCommands.custom[binding.dropdownCommands.text.toString()]
        ) // Установка соответствующего значения в поле значения команды
    }

    private fun initSendCommandButton() { // Настройка кнопки отправки команды
        val sendCommandButton = binding.sendCommandButton
        sendCommandButton.isEnabled = BluetoothService.self?.isConnected == true
        sendCommandButton.setOnClickListener {// Установка слушателя нажатия на кнопку отправки команды
            val commandName = binding.dropdownCommands.text.toString()
            val senderSign =
                sharedPreferences.getString(SharedPreferencesConstants.DEVICE_SIGN, "") ?: ""
            val receiverSign = binding.dropdownTo.text.toString()

            val messages = DeviceCommandsManager.Requests.generateNamedRequestMessages(
                commandName,
                senderSign,
                receiverSign
            ) // Формирование посылок для отправки

            Thread {
                BluetoothService.self?.communicateDevice(messages)
                printToTerminal("Command '$commandName' is being sent to $receiverSign")
            }.start() // Отправка посылок
        }
    }

    private fun printToTerminal(value: String) { // Вывод текста в терминал
        activity?.runOnUiThread {
            binding.terminal.append(
                "${
                    SimpleDateFormat(
                        "HH:mm",
                        Locale.US
                    ).format(Date())
                } >> ${value}\n\n"
            )
        }
    }

    override fun onBluetoothInfoReceived(info: String) { // Вызывается для оповещения о новом статусе подключения
        val sendCommandButton = binding.sendCommandButton
        activity?.runOnUiThread {
            when (info) {
                BluetoothConnectionThread.Constants.BLUETOOTH_CONNECTED -> { // Если произведено успешное подключение к девайсу
                    sendCommandButton.isEnabled = true // Кнопка отправки команды доступна
                }

                BluetoothConnectionThread.Constants.BLUETOOTH_NOT_CONNECTED, BluetoothConnectionThread.Constants.BLUETOOTH_CONNECTING -> { // Если производится попытка подключения к девайсу или произошло отключение от девайса
                    sendCommandButton.isEnabled = false // Кнопка отправки команды не доступна
                }
            }
        }
    }

    override fun onBluetoothMessageReceived(variant: DeviceCommandsManager.Message.Variant) { // Вызывается для оповещения о новом входящем сообщении
        when (variant) {
            DeviceCommandsManager.Message.Variant.TEXT -> { // Если сообщение является текстовым
                if (DeviceCommandsManager.Message.text.from == "MYLORA") { // Если сообщение является системным
                    printToTerminal(
                        DeviceCommandsManager.Message.text.value.toString(Charsets.UTF_8)
                    ) // Вывод текста сообщения в терминал
                }
            }

            DeviceCommandsManager.Message.Variant.GPS -> { // Если сообщение является сообщением о координатах
                printToTerminal(
                    "GPS from ${
                        DeviceCommandsManager.Message.gps.from
                    }: (${
                        DeviceCommandsManager.Message.gps.lat
                    }, ${
                        DeviceCommandsManager.Message.gps.lon
                    }, ${
                        DeviceCommandsManager.Message.gps.alt
                    })"
                ) // Вывод данных сообщения в терминал
            }
        }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?
    ) { // Вызывается при изменении значений хранилища примитивных данных девайса
        if (key == SharedPreferencesConstants.DEVICE_SIGN) { // Если произошло измнение позывного девайса
            binding.dropdownFrom.setText(
                sharedPreferences?.getString(
                    SharedPreferencesConstants.DEVICE_SIGN,
                    ""
                )
            ) // Установка значения поля позывного отправителя
        }
    }
}