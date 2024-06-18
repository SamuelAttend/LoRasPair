package com.example.loraspair.ui.settings

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.loraspair.App
import com.example.loraspair.adapters.ListDeviceAdapter
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.databinding.FragmentDevicesBinding

// Фрагмент девайсов
abstract class DevicesFragment(private val isBounded: Boolean) :
    Fragment(), ListDeviceAdapter.OnClickListener {
    abstract inner class BluetoothReceiver : BroadcastReceiver() // Абстрактный класс получателя Bluetooth

    protected lateinit var binding: FragmentDevicesBinding // Привязка к интерфейсу фрагмента девайсов
    protected lateinit var bluetoothAdapter: BluetoothAdapter // Адаптер Bluetooth
    protected abstract val bluetoothReceiver: BluetoothReceiver // Получатель Bluetooth
    protected lateinit var sharedPreferences: SharedPreferences // Хранилище примитивных данных девайса

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDevicesBinding.inflate(
            inflater,
            container,
            false
        ) // Установка привязки к интерфейсу фрагмента девайсов

        sharedPreferences =
            App.self.getSharedPreferences(
                SharedPreferencesConstants.DEVICE,
                Context.MODE_PRIVATE
            ) // Запрос хранилища примитивных данных девайса

        initBluetoothAdapter() // Настройка адаптера Bluetooth
        initBluetoothReceiver() // Настройка получателя Bluetooth

        initBluetoothDevices() // Настройка списка девайсов
        initSwipeRefresh() // Настройка свайпа

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activity?.unregisterReceiver(bluetoothReceiver) // Отвязка получателя сигналов от адаптера Bluetooth
    }

    private fun initBluetoothAdapter() { // Настройка адаптера Bluetooth
        val bluetoothManager =
            activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager // Запрос менеджера Bluetooth
        bluetoothAdapter = bluetoothManager.adapter // Установка адаптера Bluetooth
    }

    private fun initBluetoothReceiver() { // Настройка получателя Bluetooth
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        activity?.registerReceiver(bluetoothReceiver, filter) // Установка интересующих фильтров для получателя Bluetooth
    }

    private fun initBluetoothDevices() { // Настройка списка девайсов
        binding.devices.layoutManager = LinearLayoutManager(App.self.applicationContext)
        binding.devices.adapter = ListDeviceAdapter(this, isBounded) // Установка адаптера списка девасов

        binding.swipeRefresh.isRefreshing = true // Запуск анимации обновления списка девайсов
        updateBluetoothDevices() // Обновление списка девайсов
    }

    private fun initSwipeRefresh() { // Настройка свайпа
        binding.swipeRefresh.setOnRefreshListener { // Добавление отслеживания свайпа для обновления
            updateBluetoothDevices() // Обновление списка девайсов
        }
    }

    abstract fun updateBluetoothDevices() // Абстрактный метод обновления списка девайсов
}