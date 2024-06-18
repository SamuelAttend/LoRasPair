package com.example.loraspair.ui.chat

import android.app.AlertDialog
import android.app.Service
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.loraspair.App
import com.example.loraspair.DeviceCommandsManager
import com.example.loraspair.R
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.adapters.ListMessage
import com.example.loraspair.adapters.ListMessageAdapter
import com.example.loraspair.connection.BluetoothConnectionThread
import com.example.loraspair.connection.BluetoothListenersManager
import com.example.loraspair.connection.BluetoothService
import com.example.loraspair.database.OutgoingMessage
import com.example.loraspair.databinding.FragmentChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Date

// Фрагмент чата
class ChatFragment : Fragment(), OnSharedPreferenceChangeListener,
    BluetoothListenersManager.BluetoothListener {
    private lateinit var binding: FragmentChatBinding // Привязка к интерфейсу фрагмента чата
    private lateinit var sharedPreferencesChat: SharedPreferences // Хранилище примитивных данных чата
    private lateinit var sharedPreferencesDevice: SharedPreferences // Хранилище примитивных данных девайса
    private var messageStart: Int = 0 // Индекс первого непрогруженного сообщения
    private var messageIsLast = false // Больше сообщений для запроса нет
    private var signIsChanged =
        false // Пользователь, с которым в данный момент ведётся диалог, сменился

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentChatBinding.inflate(
            inflater,
            container,
            false
        ) // Установка привязки к интерфейсу фрагмента чата

        sharedPreferencesChat =
            App.self.getSharedPreferences(
                SharedPreferencesConstants.CHAT,
                Service.MODE_PRIVATE
            ) // Запрос хранилища примитивных данных чата
        sharedPreferencesDevice =
            App.self.getSharedPreferences(
                SharedPreferencesConstants.DEVICE,
                Service.MODE_PRIVATE
            ) // Запрос хранилища примитивных данных девайса

        updateChatHeader() // Обновление шапки чата

        BluetoothListenersManager.addListener(this) // Добавление фрагмента к слушателям Bluetooth

        initMessages() // Настройка списка сообщений
        initSwipeRefresh() // Настройка свайпа

        initSendMessageButton() // Настройка кнопки отправки сообщений
        initDeleteChatButton() // Настройка кнопки удаления чата

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        BluetoothListenersManager.removeListener(this) // Удаление фрагмента из слушателей Bluetooth
    }

    override fun onResume() {
        super.onResume()
        sharedPreferencesChat.registerOnSharedPreferenceChangeListener(this) // Добавление слушателя изменения значений хранилища примитивных данных чата
    }

    override fun onPause() {
        super.onPause()
        sharedPreferencesChat.unregisterOnSharedPreferenceChangeListener(this) // Удаление слушателя изменения значений хранилища примитивных данных чата
    }

    private fun loadMessages() { // Загрузка сообщений
        lifecycleScope.launch {// Корутина жизненного цикла
            val adapter = binding.messages.adapter as ListMessageAdapter

            if (signIsChanged) { // Если пользователь, с которым в данный момент ведётся диалог, сменился
                adapter.submitList(null) // Очистка списока сообщений
                signIsChanged = false
            }

            val userDao = App.database.userDao()
            val user = userDao.getUserByUserSign(getChatSign()) // Запрос пользователя по позывному
            if (user != null) {
                val userId = user.user_id

                val messages = mutableListOf<ListMessage>() // Список сообщений
                userDao.getLastIncomingMessagesFromUserByUserId(
                    userId,
                    messageStart,
                    10
                ) // Запрос 10 последних непрогруженных входящих сообщений
                    .forEach {
                        messages.add(ListMessage.fromIncomingMessage(it)) // Добавление входящего сообщения к списку сообщений
                    }
                userDao.getLastOutgoingMessagesToUserByUserId(
                    userId,
                    messageStart,
                    10
                ) // Запрос 10 последних непрогруженных исходящих сообщений
                    .forEach {
                        messages.add(ListMessage.fromOutgoingMessage(it)) // Добавление исходящего сообщения к списку сообщений
                    }

                if (messages.isEmpty()) { // Если список сообщений пуст
                    messageIsLast = true // Больше сообщений для запроса нет
                } else { // Если список сообщений не пуст
                    adapter.submitList(messages.sortedBy { it.date_time.time } + adapter.currentList
                    ) { // Отображение запрошенных сообщений перед имеющимися
                        if (messageStart == 0) { // Если осуществлялся первый запрос сообщений
                            scrollAllTheWayDown() // Прокрутка списка в самый низ
                        }
                    }
                }
            }
            binding.swipeRefresh.isRefreshing = false // Обновление списка сообщений завершено
        }
    }

    private fun initMessages() { // Настройка списка сообщений
        binding.messages.layoutManager = LinearLayoutManager(App.self.applicationContext)
        binding.messages.adapter = ListMessageAdapter() // Установка адаптера списка сообщений

        loadMessages() // Загрузка сообщений
    }

    private fun initSwipeRefresh() { // Настройка свайпа
        binding.swipeRefresh.setOnRefreshListener { // Добавление отслеживания свайпа для обновления
            if (!messageIsLast) { // Если сообщения для запроса ещё есть
                messageStart += 10 // Смещение индекса первого непрогруженного сообщения на 10
                loadMessages() // Загрузка сообщений
            } else { // Если больше сообщений для запроса нет
                binding.swipeRefresh.isRefreshing =
                    false // Обновление списка сообщений не требуется
            }
        }
    }

    private fun initSendMessageButton() { // Настройка кнопки отправки сообщений
        binding.messageInput.isEnabled =
            BluetoothService.self?.isConnected == true // Ввод сообщения разрешен только при соединении с девайсом
        binding.messageInput.setEndIconOnClickListener {// Установка слушателя нажатия на икноку отправки сообщения
            val senderSign =
                sharedPreferencesDevice.getString(SharedPreferencesConstants.DEVICE_SIGN, "") ?: ""
            val receiverSign = getChatSign()
            val text = binding.message.text.toString()

            val messages = DeviceCommandsManager.Requests.generateTextMessages(
                text,
                senderSign,
                receiverSign
            ) // Составление текстового сообщения девайсу

            if (messages.isNotEmpty()) {
                MainScope().launch {// Корутина для UI
                    val adapter = binding.messages.adapter as ListMessageAdapter
                    var start = 0
                    for (index in 3..<text.length) {
                        if (text[index] == ']' && text[index - 3] == '[') { // Если найдена конструкция '[..]' (большинство эмодзи занимают два символа)
                            val stickerEmoji =
                                text.slice(index - 2..index - 1) // Взятие символов между '[' и ']'

                            if (App.database.stickerDao()
                                    .checkStickerExistsByStickerEmoji(stickerEmoji)
                            ) { // Если изображение найдено по идентификатору в базе данных
                                App.database.userDao()
                                    .getUserByUserSignOrInsertUser(receiverSign)
                                    ?.let { user -> // Запрос пользователя из базы данных
                                        val outgoingMessage =
                                            OutgoingMessage( // Формирование исходящего сообщения
                                                0,
                                                user.user_id,
                                                false,
                                                Date(),
                                                text.slice(start..<index - 3).trim(),
                                                stickerEmoji
                                            )
                                        App.database.outgoingMessageDao()
                                            .insertAllOutgoingMessages(outgoingMessage) // Добавление исходящего сообщения в базу данных
                                        adapter.submitList(
                                            adapter.currentList + ListMessage.fromOutgoingMessage(
                                                outgoingMessage
                                            ) // Добавление исходящего сообщения в список сообщений
                                        ) {
                                            scrollAllTheWayDown() // Прокрутка списка в самый низ
                                        }
                                        start = index + 1
                                    }
                            }
                        }
                    }
                    if (start != text.length) {
                        App.database.userDao().getUserByUserSignOrInsertUser(receiverSign)
                            ?.let { user -> // Запрос пользователя из базы данных
                                val outgoingMessage =
                                    OutgoingMessage( // Формирование исходящего сообщения
                                        0,
                                        user.user_id,
                                        false,
                                        Date(),
                                        text.slice(start..<text.length).trim(),
                                        null
                                    )
                                App.database.outgoingMessageDao()
                                    .insertAllOutgoingMessages(outgoingMessage) // Добавление исходящего сообщения в базу данных
                                adapter.submitList(
                                    adapter.currentList + ListMessage.fromOutgoingMessage(
                                        outgoingMessage
                                    ) // Добавление исходящего сообщения в список сообщений
                                ) {
                                    scrollAllTheWayDown() // Прокрутка списка в самый низ
                                }
                            }
                    }
                    binding.message.text?.clear() // Очистка поля ввода текста сообщения
                }

                Thread { // Создание потока для отправки сообщений
                    BluetoothService.self?.communicateDevice(messages) // Отправка списка сообщений девайсу
                }.start() // Запуск потока
            }
        }
    }

    private fun initDeleteChatButton() { // Настройка кнопки удаления чата
        binding.deleteChatButton.setOnClickListener {// Установка слушателя нажатия на кнопку удаления чата
            lifecycleScope.launch {
                val sign = getChatSign()
                val dialogBuilder =
                    AlertDialog.Builder(context) // Инициализация построителя диалогового окна удаления чата
                dialogBuilder.setTitle("${getString(R.string.delete_chat_dialog_message)} ${sign}?")
                dialogBuilder.setPositiveButton( // Добавление логики нажатия на кнопку OK
                    getString(android.R.string.ok)
                ) { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch { // Корутина ввода-вывода данных
                        val userDao = App.database.userDao()
                        val user = userDao.getUserByUserSign(sign)
                        if (user != null) {
                            with(userDao) {
                                deleteAllIncomingMessagesFromUserByUserId(user.user_id) // Удаление всех входящих сообщений от пользователя, с которым в данный момент ведётся диалог из базы данных
                                deleteAllOutgoingMessagesToUserByUserId(user.user_id) // Удаление всех исходящих сообщений от пользователя, с которым в данный момент ведётся диалог из базы данных
                                deleteUsers(user) // Удаление пользователя, с которым в данный момент ведётся диалог, из базы данных
                            }
                            with(sharedPreferencesChat.edit()) {
                                putString(
                                    SharedPreferencesConstants.CHAT_SIGN,
                                    ""
                                ) // Очистка позывного пользователя, с которым в данный момент ведётся диалог, из хранилища примитивных данных чата
                                apply()
                            }
                        }
                    }
                }
                dialogBuilder.setNegativeButton(getString(android.R.string.cancel)) { _, _ -> }

                val dialog = dialogBuilder.create() // Инициализация диалогового окна удаления чата
                dialog.show() // Отображение диалогового окна удаления чата
            }
        }
    }

    private fun scrollAllTheWayDown() { // Прокрутка списка в самый низ
        val adapter = binding.messages.adapter as ListMessageAdapter
        binding.messages.scrollToPosition(adapter.itemCount - 1) // Прокрутка списка сообщений к самому последнему сообщению
    }

    private fun updateChatHeader() { // Обновление шапки чата
        val sign =
            getChatSign() // Запрос позывного пользователя, с которым в данный момент ведётся диалог
        if (sign.isEmpty()) { // Если позывной пользователя, с которым в данный момент ведётся диалог, пуст
            binding.sign.text =
                getString(R.string.default_chat_sign) // Установка текста об отсутствии выбранного пользователя
            binding.deleteChatButton.visibility = View.GONE // Скрытие кнопки удаления чата
        } else {
            binding.sign.text =
                sign // Установка позывного пользователя, с которым в данный момент ведётся диалог, в шапке чат
            binding.deleteChatButton.visibility = View.VISIBLE // Отображение кнопки удаления чата
        }
    }

    private fun getChatSign(): String { // Запрос позывного пользователя, с которым в данный момент ведётся диалог
        return sharedPreferencesChat.getString( // Запрос позывного пользователя, с которым в данный момент ведётся диалог, из хранилища примитивных данных чата
            SharedPreferencesConstants.CHAT_SIGN,
            ""
        ) ?: ""
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?
    ) { // Вызывается при изменении значений хранилища примитивных данных чата
        if (key == SharedPreferencesConstants.CHAT_SIGN) {
            updateChatHeader() // Обновление шапки чата

            messageStart = 0 // Установка индекса первого непрогруженного сообщения на значение 0
            messageIsLast = false // Сообщения для запроса имеются
            signIsChanged = true // Пользователь, с которым в данный момент ведётся диалог, сменился
            loadMessages() // Загрузка сообщений
        }
    }

    override fun onBluetoothInfoReceived(info: String) { // Вызывается для оповещения о новом статусе подключения
        val messageInput = binding.messageInput
        activity?.runOnUiThread {
            when (info) {
                BluetoothConnectionThread.Constants.BLUETOOTH_CONNECTED -> { // Если произведено успешное подключение к девайсу
                    messageInput.isEnabled = true // Ввод сообщения разрешен
                }

                BluetoothConnectionThread.Constants.BLUETOOTH_NOT_CONNECTED, BluetoothConnectionThread.Constants.BLUETOOTH_CONNECTING -> { // Если производится попытка подключения к девайсу или произошло отключение от девайса
                    messageInput.isEnabled = false // Ввод сообщения запрещен
                }
            }
        }

    }

    override fun onBluetoothMessageReceived(variant: DeviceCommandsManager.Message.Variant) { // Вызывается для оповещения о новом входящем сообщении
        when (variant) {
            DeviceCommandsManager.Message.Variant.TEXT -> { // Если сообщение является текстовым
                val sign = getChatSign()
                if (DeviceCommandsManager.Message.text.from == sign) {
                    lifecycleScope.launch {
                        val adapter = binding.messages.adapter as ListMessageAdapter
                        val userDao = App.database.userDao()
                        val user = userDao.getUserByUserSign(sign)
                        if (user != null) {
                            userDao.getLastIncomingMessagesFromUserByUserId(
                                user.user_id,
                                0,
                                1
                            ).forEach {
                                adapter.submitList(
                                    adapter.currentList + ListMessage.fromIncomingMessage(
                                        it
                                    )
                                ) // Запрос последнего сообщения пользователя, с которым в данный момент ведётся диалог, из базы данных и отображение в списке сообщений
                            }
                        }
                    }


                }
            }

            DeviceCommandsManager.Message.Variant.GPS -> {} // Если сообщение является сообщением о координатах
        }
    }
}