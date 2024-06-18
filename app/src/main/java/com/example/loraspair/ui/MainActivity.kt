package com.example.loraspair.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI.navigateUp
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import androidx.navigation.ui.NavigationUI.setupWithNavController
import com.example.loraspair.connection.BluetoothService
import com.example.loraspair.databinding.ActivityMainBinding
import org.osmdroid.config.Configuration
import android.Manifest
import android.app.Dialog
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.SystemClock
import android.view.MenuItem
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.loraspair.App
import com.example.loraspair.DeviceCommandsManager
import com.example.loraspair.R
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.adapters.ListUser
import com.example.loraspair.adapters.ListUserAdapter
import com.example.loraspair.connection.BluetoothConnectionThread
import com.example.loraspair.connection.BluetoothListenersManager
import com.example.loraspair.database.User
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// Активность приложения
class MainActivity : AppCompatActivity(), BluetoothListenersManager.BluetoothListener,
    OnSharedPreferenceChangeListener {
    object Constants { // Статические поля в пространстве имён Constants
        const val BLUETOOTH_BUTTON_DISCONNECT = 0
        const val BLUETOOTH_BUTTON_CONNECT = 1
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter // Адаптер Bluetooth
    private lateinit var appBarConfiguration: AppBarConfiguration // Настройщик интерфейса главной активности приложения
    private lateinit var binding: ActivityMainBinding // Привязка к интерфейсу активности приложения
    private var bluetoothServiceButtonState = Constants.BLUETOOTH_BUTTON_CONNECT // Состояние кнопки запуска сервиса Bluetooth подключения
    private var bluetoothServiceButtonLastClickTime: Long = 0 // Время последнего нажатия на кнопку запуска сервиса Bluetooth подключения
    private lateinit var sharedPreferences: SharedPreferences // Хранилище примитивных данных чата

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName // Установка значения пользовательского агента http для osmdroid

        binding = ActivityMainBinding.inflate(layoutInflater) // Установка привязки к интерфейсу активности приложения
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar) // Установка тулбара для шапки

        val drawer = binding.drawerLayout
        appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_map, R.id.nav_chat, R.id.nav_terminal, R.id.nav_settings
        ).setOpenableLayout(drawer).build() // Установка разделов и бокового меню

        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = binding.appBarMain.contentMain.bottomNavView
        setupActionBarWithNavController(this, navController, appBarConfiguration)
        setupWithNavController(bottomNavigationView, navController) // Установка значений разделов

        if (!checkNotificationsPermissions()) { // Если разрешения на показ уведомлений не предоставлены
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            ) // Запрос разрешений на показ уведомлений
        }

        sharedPreferences =
            App.self.getSharedPreferences(SharedPreferencesConstants.CHAT, Service.MODE_PRIVATE) // Запрос хранилища примитивных данных чата

        initBluetoothAdapter() // Настройка адаптера Bluetooth

        BluetoothListenersManager.addListener(this) // Добавление активности к слушателям Bluetooth

        initBluetoothServiceButton() // Настройка кнопки запуска сервиса Bluetooth подключения

        initUsers() // Настройка списка пользователей
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this) // Добавление слушателя изменения значений хранилища примитивных данных чата
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this) // Удаление слушателя изменения значений хранилища примитивных данных чата
    }

    override fun onDestroy() {
        super.onDestroy()
        BluetoothListenersManager.removeListener(this) // Удаление активности из слушателей Bluetooth
    }

    private fun initBluetoothAdapter() { // Настройка адаптера Bluetooth
        val bluetoothManager =
            App.self.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager // Запрос менеджера Bluetooth
        bluetoothAdapter = bluetoothManager.adapter // Установка адаптера Bluetooth
    }

    private fun initBluetoothServiceButton() { // Настройка кнопки запуска сервиса Bluetooth подключения
        bluetoothServiceButtonState =
            if (BluetoothService.self == null) Constants.BLUETOOTH_BUTTON_CONNECT else Constants.BLUETOOTH_BUTTON_DISCONNECT
        binding.appBarMain.bluetoothServiceButton.text =
            if (BluetoothService.self == null) getString(R.string.bt_service_start) else if (BluetoothService.self?.isConnected == true) getString(
                R.string.bt_service_stop
            ) else getString(R.string.bt_service_connecting)

        binding.appBarMain.bluetoothServiceButton.setOnClickListener {// Установка слушателя нажатия на кнопку включения сервиса Bluetooth подключения
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - bluetoothServiceButtonLastClickTime < 250) { // Если прошло менее 250 мс со времени последнего нажатия на кнопку запуска сервиса Bluetooth подключения
                return@setOnClickListener // Отмена нажатия на кнопку
            }
            bluetoothServiceButtonLastClickTime = currentTime // Установка времени последнего нажатия на кнопку запуска сервиса Bluetooth на текущее

            when (bluetoothServiceButtonState) {
                Constants.BLUETOOTH_BUTTON_DISCONNECT -> { // Если в данный момент сервис Bluetooth подключения запущен
                    stopService(Intent(App.self.applicationContext, BluetoothService::class.java)) // Остановка сервиса Bluetooth подключения
                }

                Constants.BLUETOOTH_BUTTON_CONNECT -> { // Если в данный момент сервис Bluetooth подключения не запущен
                    if (bluetoothAdapter.isEnabled) { // Если адаптер Bluetooth доступен
                        startForegroundService(
                            Intent(
                                App.self.applicationContext,
                                BluetoothService::class.java
                            )
                        ) // Запуск сервиса Bluetooth подключения
                    }
                }
            }
        }
    }

    private fun initUsers() { // Настройка списка пользователей
        binding.users.layoutManager = LinearLayoutManager(App.self.applicationContext)
        binding.users.adapter = ListUserAdapter()

        updateUsers() // Обновление списка пользователей
        binding.swipeRefresh.setOnRefreshListener { // Добавление отслеживания свайпа для обновления
            updateUsers() // Обновление списка пользователей
        }
        binding.userAdd.setOnClickListener {// Установка слушателя нажатия на кнопку добавления пользователя
            val dialog = Dialog(this) // Инициализация диалогового окна добавления пользователя
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE) // Скрытие названия диалогового окна
            dialog.setCancelable(true) // Установка возможности закрытия диалогового окна кнопкой "Назад"
            dialog.setContentView(R.layout.dialog_add_user) // Установка интерфейса диалогового окна

            val applyUserButton = dialog.findViewById<Button>(R.id.apply_user_button)
            applyUserButton.setOnClickListener {// Установка слушателя нажатия на кнопку подтверждения добавления пользователя
                MainScope().launch {// Корутина для UI
                    if (App.database.userDao().insertUsers(
                            User(
                                0,
                                dialog.findViewById<AutoCompleteTextView>(R.id.user_sign).text.toString()
                            )
                        ).first() != (-1).toLong()
                    ) { // Если новый пользователь успешно добавлен в базу данных
                        dialog.currentFocus?.let { view ->
                            val imm =
                                getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.hideSoftInputFromWindow(view.windowToken, 0)
                        } // Закрытие экранной клавиатуры
                        dialog.dismiss() // Закрытие диалогового окна
                        updateUsers() // Обновление списка пользователей
                    }
                }
            }

            dialog.show() // Отображение диалогового окна добавления пользователя
        }
    }

    private fun updateUsers() { // Обновление списка пользователей
        lifecycleScope.launch {// Корутина жизненного цикла
            val adapter = binding.users.adapter as ListUserAdapter
            val users = mutableListOf<ListUser>()
            App.database.userDao().getAllUsers().forEach {
                users.add(ListUser.fromUser(it))
            }
            adapter.submitList(users) // Загрузка пользователей в список пользователей
            binding.swipeRefresh.isRefreshing = false // Остановка анимации обновления списка девайсов
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean { // Вызывается при создании панели меню
        menuInflater.inflate(R.menu.main, menu) // Установка интерфейса панели меню
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { // Вызывается при нажатии на элемент в панели меню
        when (item.itemId) {
            R.id.action_commands -> { // Если нажата кнопка "Commands"
                startActivity(Intent(this, CommandsActivity::class.java)) // Запуск активности управления командами
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean { // Настройка навигатора по активностям
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController
        return (navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp())
    }

    private fun checkNotificationsPermissions(): Boolean { // Проверка наличия необходимых разрешений
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return (ContextCompat.checkSelfPermission(
                App.self.applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED)
        }
        return true
    }

    override fun onBluetoothInfoReceived(info: String) { // Вызывается для оповещения о новом статусе подключения
        val bluetoothServiceButton = binding.appBarMain.bluetoothServiceButton
        runOnUiThread {
            when (info) {
                BluetoothService.Constants.BLUETOOTH_SERVICE_STARTED -> {
                    val bluetoothService = BluetoothService.self
                    bluetoothServiceButtonState = Constants.BLUETOOTH_BUTTON_DISCONNECT
                    bluetoothServiceButton.text =
                        if (bluetoothService?.isConnected == true) getString(R.string.bt_service_stop) else getString(
                            R.string.bt_service_connecting
                        )
                }

                BluetoothService.Constants.BLUETOOTH_SERVICE_STOPPED -> {
                    bluetoothServiceButtonState = Constants.BLUETOOTH_BUTTON_CONNECT
                    bluetoothServiceButton.text = getString(R.string.bt_service_start)
                }

                BluetoothConnectionThread.Constants.BLUETOOTH_CONNECTING -> {
                    bluetoothServiceButtonState = Constants.BLUETOOTH_BUTTON_DISCONNECT
                    bluetoothServiceButton.text = getString(R.string.bt_service_connecting)
                }

                BluetoothConnectionThread.Constants.BLUETOOTH_CONNECTED -> {
                    bluetoothServiceButtonState = Constants.BLUETOOTH_BUTTON_DISCONNECT
                    bluetoothServiceButton.text = getString(R.string.bt_service_stop)
                }

                BluetoothConnectionThread.Constants.BLUETOOTH_NOT_CONNECTED -> {
                    val bluetoothService = BluetoothService.self
                    bluetoothServiceButtonState =
                        if (bluetoothService != null) Constants.BLUETOOTH_BUTTON_DISCONNECT else Constants.BLUETOOTH_BUTTON_CONNECT
                    bluetoothServiceButton.text =
                        if (bluetoothService != null) getString(R.string.bt_service_connecting) else getString(
                            R.string.bt_service_start
                        )
                }
            }
        }
    }

    override fun onBluetoothMessageReceived(variant: DeviceCommandsManager.Message.Variant) { // Вызывается для оповещения о новом входящем сообщении
        val adapter = binding.users.adapter as ListUserAdapter
        val sign = when (variant) {
            DeviceCommandsManager.Message.Variant.TEXT -> DeviceCommandsManager.Message.text.from
            DeviceCommandsManager.Message.Variant.GPS -> DeviceCommandsManager.Message.gps.from
        }
        if (!adapter.currentList.any { it.sign == sign }) {
            adapter.submitList(adapter.currentList + ListUser(sign, false))
        } // Добавление нового пользователя-отправителя в список пользователей, если его ещё нет в списке
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) { // Вызывается при изменении значений хранилища примитивных данных чата
        if (key == SharedPreferencesConstants.CHAT_SIGN) {
            val sign = sharedPreferences?.getString(SharedPreferencesConstants.CHAT_SIGN, "") ?: ""
            if (sign.isEmpty()) {
                updateUsers()
            }
        }
    }
}