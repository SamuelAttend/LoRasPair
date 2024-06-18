package com.example.loraspair.ui.settings

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.example.loraspair.App
import com.example.loraspair.adapters.ListDevice
import com.example.loraspair.adapters.ListDeviceAdapter
import com.example.loraspair.R
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.connection.BluetoothService
import com.example.loraspair.databinding.FragmentSettingsBinding
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener

// Фрагмент настроек подключения
class SettingsFragment : Fragment() {
    private lateinit var binding: FragmentSettingsBinding // Привязка к интерфейсу фрагмента настроек подключения
    private lateinit var bluetoothAdapter: BluetoothAdapter // Адаптер Bluetooth
    private lateinit var bluetoothLauncher: ActivityResultLauncher<Intent> // Лаунчер Bluetooth
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>> // Лаунчер разрешений
    private val bluetoothReceiver = object : BroadcastReceiver() { // Получатель Bluetooth
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> { // Если состояние Bluetooth изменилось
                    updateFragment() // Обновление фрагмента
                }
            }
        }
    }
    private lateinit var sharedPreferences: SharedPreferences // Хранилище примитивных данных девайса
    private lateinit var fragmentManager: FragmentManager // Менеджер дочерних фрагментов

    object Constants { // Статические поля в пространстве имён Constants
        const val BOUNDED_DEVICES_TAB_POS = 0
        const val NEARBY_DEVICES_TAB_POS = 1
    }

    class BoundedDevicesFragment :
        DevicesFragment(true) { // Фрагмент сопряжённых девайсов
        inner class BluetoothReceiver :
            DevicesFragment.BluetoothReceiver() { // Имплементация класса получателя Bluetooth
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> { // Если состояние Bluetooth изменилось
                        updateBluetoothDevices() // Обновление списка девайсов
                    }
                }
            }
        }

        override val bluetoothReceiver = BluetoothReceiver() // Инициализация получателя Bluetooth

        override fun updateBluetoothDevices() { // Имплементация метода обновления списка девайсов
            val adapter = binding.devices.adapter as ListDeviceAdapter
            adapter.submitList(null) // Очистка списка девайсов
            adapter.submitList(getBoundedDevices()) // Загрузка сопряжённых девайсов в список девайсов
            binding.swipeRefresh.isRefreshing =
                false // Остановка анимации обновления списка девайсов
        }

        override fun onClick(item: ListDevice) { // Вызывается при нажатии на элемент списка
            saveDevice(item) // Сохранение девайса списка в хранилище девайса
        }

        private fun saveDevice(item: ListDevice) { // Сохранение девайса списка в хранилище девайса
            try {
                with(sharedPreferences.edit()) {// Область видимости редактора хранилища примитивных данных девайсов
                    putString(SharedPreferencesConstants.DEVICE_NAME, item.device.name) // Сохранение названия девайса в хранилище примитивных данных девайса
                    putString(SharedPreferencesConstants.DEVICE_MAC, item.device.address) // Сохранение MAC-адреса девайса в хранилище примитивных данных девайса
                    putString(SharedPreferencesConstants.DEVICE_SIGN, "") // Очистка позывного девайса в хранилище примитивных данных девайса
                    apply()
                }

                BluetoothService.self?.connectDevice() // Соединение с девайсом
            } catch (_: SecurityException) {
            }
        }

        private fun getBoundedDevices(): ArrayList<ListDevice> { // Запрос списка сопряжённых девайсов
            try {
                val bondedDevices =
                    bluetoothAdapter.bondedDevices as Set<BluetoothDevice>? // Запрос множества сопряжённых девайсов у адаптера Bluetooth
                val devices = ArrayList<ListDevice>() // Инициализация списка сопряжённых девайсов
                bondedDevices?.forEach {// Заполнение списка сопряжённых девайсов
                    devices.add(
                        ListDevice(
                            it,
                            sharedPreferences.getString(
                                SharedPreferencesConstants.DEVICE_MAC,
                                ""
                            ) == it.address
                        )
                    )
                }
                return devices
            } catch (_: SecurityException) {
            }
            return ArrayList() // Если разрешения на работу с адаптером Bluetooth не предоставлены, то осуществляется возврат пустого списка
        }
    }

    class NearbyDevicesFragment :
        DevicesFragment(false) { // Фрагмент близлежащих девайсов
        inner class BluetoothReceiver : DevicesFragment.BluetoothReceiver() { // Имплементация класса получателя Bluetooth
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> { // Если какой-либо девайс обнаружен при сканировании
                        val device = IntentCompat.getParcelableExtra(
                            intent,
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice::class.java
                        ) // Запрос обнаруженного девайса
                        val listDeviceAdapter = binding.devices.adapter as ListDeviceAdapter // Запрос адаптера списка девайсов
                        val set = mutableSetOf<ListDevice>() // Инициализация множества девайсов
                        set.addAll(listDeviceAdapter.currentList) // Добавление всех девайсов списка девайсов в множество девайсов
                        device?.let {// Если обнаруженный девайс не равен null
                            set.add(
                                ListDevice(
                                    device,
                                    sharedPreferences.getString(
                                        SharedPreferencesConstants.DEVICE_MAC,
                                        ""
                                    ) == device.address
                                )
                            ) // Добавление обнаруженного девайса в множество девайсов
                        }
                        listDeviceAdapter.submitList(set.toList()) // Загрузка множества девайсов в список девайсов
                    }

                    BluetoothAdapter.ACTION_STATE_CHANGED -> { // Если состояние Bluetooth изменилось
                        updateBluetoothDevices() // Обновление списка девайсов
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> { // Если поиск близлежащих девайсов завершён
                        binding.swipeRefresh.isRefreshing = false // Остановка анимации обновления списка девайсов
                    }
                }

            }
        }

        private lateinit var locationServicesLauncher: ActivityResultLauncher<Intent> // Лаунчер включения доступа к местоположнию
        override val bluetoothReceiver = BluetoothReceiver() // Инициализация получателя Bluetooth

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            initLocationServices() // Настройка доступа к местоположению
            return super.onCreateView(inflater, container, savedInstanceState)
        }

        override fun onDestroyView() {
            super.onDestroyView()
            try {
                bluetoothAdapter.cancelDiscovery() // Остановка поиска устройств
            } catch (_: SecurityException) {
            }
        }

        override fun updateBluetoothDevices() { // Имплементация метода обновления списка девайсов
            val listDeviceAdapter = binding.devices.adapter as ListDeviceAdapter
            listDeviceAdapter.submitList(null) // Очистка списка девайсов

            val swipeRefresh = binding.swipeRefresh
            try {
                if (bluetoothAdapter.isEnabled) { // Если адаптер Bluetooth активен
                    bluetoothAdapter.startDiscovery() // Старт поиска близлежащих девайсов
                } else {
                    swipeRefresh.isRefreshing = false // Остановка анимации обновления списка девайсов
                }
            } catch (_: SecurityException) {
                swipeRefresh.isRefreshing = false // Остановка анимации обновления списка девайсов
            }
        }

        override fun onClick(item: ListDevice) {} // Вызывается при нажатии на элемент списка

        private fun initLocationServices() { // Настройка доступа к местоположению
            locationServicesLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                updateBluetoothDevices() // Обновление списка девайсов по возвращении в приложение из настроек
            } // Установка лаунчера включения доступа к местоположнию

            val locationManager =
                App.self.getSystemService(Context.LOCATION_SERVICE) as LocationManager // Запрос менеджера положения
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { // Если доступ к местоположению отключён
                locationServicesLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) // Запуск лаунчера включения доступа к местоположению
                Toast.makeText(
                    context,
                    getString(R.string.location_services_required),
                    Toast.LENGTH_LONG
                ).show() // Вывод информации о необходимости включения доступа к местоположению
            }
        }
    }

    private var devicesFragment: DevicesFragment? = null // Текущий фрагмент девайсов

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(
            inflater,
            container,
            false
        ) // Установка привязки к интерфейсу фрагмента настроек подключения
        fragmentManager = childFragmentManager // Установка менеджера дочерних фрагментов

        sharedPreferences =
            App.self.getSharedPreferences(SharedPreferencesConstants.DEVICE, Context.MODE_PRIVATE) // Запрос хранилища примитивных данных девайса

        initPermissionsLauncher() // Настройка лаунчера разрешений
        initBluetoothPermissions() // Настройка разрешений Bluetooth

        initBluetoothLauncher() // Настройка лаунчера Bluetooth
        initBluetoothAdapter() // Настройка адаптера Bluetooth
        initBluetoothReceiver() // Настройка лаунчера разрешений

        initBluetoothButton() // Настройка кнопки запуска Bluetooth
        initDevicesFragment() // Настройка фрагмента девайсов

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            bluetoothAdapter.cancelDiscovery() // Остановка поиска устройств
        } catch (_: SecurityException) {
        }
        activity?.unregisterReceiver(bluetoothReceiver) // Отвязка получателя сигналов от адаптера Bluetooth
    }

    private fun initDevicesFragment() { // Настройка фрагмента девайсов
        val tabLayout = binding.devicesHeader.tabLayout
        tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener { // Установка слушателя переключения вкладки
            override fun onTabSelected(tab: TabLayout.Tab?) { // Вызывается при переключении вкладки, принимает в качестве аргумента новую вкладку
                when (tab?.position) {
                    Constants.BOUNDED_DEVICES_TAB_POS -> {
                        devicesFragment = BoundedDevicesFragment()
                    }

                    Constants.NEARBY_DEVICES_TAB_POS -> {
                        devicesFragment = NearbyDevicesFragment()
                    }
                }

                val ft: FragmentTransaction = fragmentManager.beginTransaction() // Инициализация транзакции фрагмента девайсов
                devicesFragment?.let { ft.replace(binding.fragmentDevices.id, it) } // Замена текущего фрагмента девайсов на новый
                ft.commit() // Запуск транзакции
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) { // Вызывается при переключении вкладки, принимает в качестве аргумента старую вкладку
                val ft: FragmentTransaction = fragmentManager.beginTransaction() // Инициализация транзакции фрагмента девайсов
                devicesFragment?.let { ft.remove(it) } // Удаление текущего фрагмента девайсов
                ft.commit() // Запуск транзакции
            }

            override fun onTabReselected(tab: TabLayout.Tab?) { // Вызывается при выборе текущей же вкладки
                when (tab?.position) {
                    Constants.BOUNDED_DEVICES_TAB_POS -> {
                        devicesFragment = BoundedDevicesFragment()
                    }

                    Constants.NEARBY_DEVICES_TAB_POS -> {
                        devicesFragment = NearbyDevicesFragment()
                    }
                }

                val ft: FragmentTransaction = fragmentManager.beginTransaction() // Инициализация транзакции фрагмента девайсов
                devicesFragment?.let { ft.replace(binding.fragmentDevices.id, it) } // Замена текущего фрагмента девайсов на новый
                ft.commit() // Запуск транзакции
            }
        })
        tabLayout.selectTab(tabLayout.getTabAt(Constants.BOUNDED_DEVICES_TAB_POS)) // Установка начальной вкладки на вкладку сопряжённых девайсов
    }

    private fun initBluetoothButton() { // Настройка кнопки запуска Bluetooth
        binding.devicesHeader.bluetoothButton.setOnClickListener {// Установка слушателя нажатия на кнопку запуска Bluetooth
            bluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) // Запуск Bluetooth
        }
        updateBluetoothButton() // Обновление кнопки запуска Bluetooth
    }

    private fun updateBluetoothButton() { // Обновление кнопки запуска Bluetooth
        val bluetoothButton = binding.devicesHeader.bluetoothButton
        if (checkBluetoothPermissions()) { // Если необходимые разрешения предоставлены
            val bluetoothAdapterIsEnabled = bluetoothAdapter.isEnabled
            bluetoothButton.isEnabled = !bluetoothAdapterIsEnabled
            bluetoothButton.text =
                getString(if (bluetoothAdapterIsEnabled) R.string.bt_is_on else R.string.bt_is_off)
        } else { // Если необходимые разрешения не предоставлены
            bluetoothButton.isEnabled = false
            bluetoothButton.text = getString(R.string.bt_no_permissions)
        }
    }

    private fun checkBluetoothPermissions(): Boolean { // Проверка наличия необходимых разрешений
        context?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return (
                        ContextCompat.checkSelfPermission(
                            it,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                                &&
                                ContextCompat.checkSelfPermission(
                                    it,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                                &&
                                ContextCompat.checkSelfPermission(
                                    it,
                                    Manifest.permission.BLUETOOTH_SCAN
                                ) == PackageManager.PERMISSION_GRANTED
                        )
            }
            return ContextCompat.checkSelfPermission(
                it,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        return false
    }

    private fun initBluetoothPermissions() { // Настройка разрешений Bluetooth
        if (checkBluetoothPermissions()) { // Если необходимые разрешения предоставлены
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // Если версия Anroid больше или равна 11
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            ) // Запрос разрешений
        } else {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            ) // Запрос разрешений
        }
    }

    private fun initBluetoothAdapter() { // Настройка адаптера Bluetooth
        val bluetoothManager =
            activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager // Запрос менеджера Bluetooth
        bluetoothAdapter = bluetoothManager.adapter // Установка адаптера Bluetooth
    }

    private fun initBluetoothLauncher() { // Настройка лаунчера Bluetooth
        bluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (it.resultCode == Activity.RESULT_OK) { // Если ответ на запрос запуска Bluetooth является положительным
                updateFragment() // Обновление фрагмента
            }
        }
    }

    private fun initPermissionsLauncher() { // Настройка лаунчера разрешений
        permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            updateFragment() // Обновление фрагмента после получения ответа на запрос предоставления разрешений
        }
    }

    private fun initBluetoothReceiver() { // Настройка получателя Bluetooth
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        activity?.registerReceiver(bluetoothReceiver, filter) // Установка интересующих фильтров для получателя Bluetooth
    }

    private fun updateFragment() {
        updateBluetoothButton() // Обновление кнопки запуска Bluetooth
        devicesFragment?.updateBluetoothDevices() // Обновление списка девайсов
    }
}