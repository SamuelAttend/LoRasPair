package com.example.loraspair.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.loraspair.R
import com.example.loraspair.databinding.DeviceBinding

// Адаптер списка девайсов
class ListDeviceAdapter(
    private val onClickListener: OnClickListener, // Имплементация интерфейса для отслеживания нажатия на элемент списка
    private val isBounded: Boolean // Является ли список девайсов списком сопряжённых устройств или устройств поблизости
) :
    ListAdapter<ListDevice, ListDeviceAdapter.Holder>(Comparator()) { // Наследование от адаптера списка
    private var currentCheckBox: CheckBox? =
        null // Чек-бокс выбранного в данный момент девайса списка девайсов

    class Holder( // Держатель элемента списка, используемый для хранения внешнего вида и прочих необходимых данных элемента списка
        view: View, // Внешний вид элемента списка
        private val listDeviceAdapter: ListDeviceAdapter, // Объект адаптера списка девайсов
        onClickListener: OnClickListener, // Имплементация интерфейса для отслеживания нажатия на элемент списка
        isBounded: Boolean // Является ли список девайсов списком сопряжённых устройств или устройств поблизости
    ) : RecyclerView.ViewHolder(view) { // Наследование от стандартного держателя элемента списка
        private val binding = DeviceBinding.bind(view) // Привязка к интерфейсу элемента списка
        private var data: ListDevice? = null // Данные элемента списка (девайс списка девайсов)

        init { // Конструктор класса
            val checkBox = binding.checkBox // Ссылка на чек-бокс текущего элемента списка
            if (!isBounded) { // Если список является списком устройств поблизости
                checkBox.visibility = View.GONE // Скрыть чек-бокс текущего элемента списка
                binding.root.setOnClickListener { // Добавление отслеживания нажатия на элемент списка
                    try {
                        data?.device?.createBond() // Установить сопряжение с девайсом
                    } catch (_: SecurityException) {
                    }
                }
            } else { // Если список является списком сопряжённых устройств
                checkBox.setOnClickListener { // Добавление отслеживания нажатия на чек-бокс элемента списка
                    data?.let { onClickListener.onClick(it) } // Если данные элемента списка не равны null, то производится оповещение отслеживателя нажатия на элемент списка
                    listDeviceAdapter.setCheckBox(checkBox) // Установка чек-бокса выбранного в данный момент девайса списка
                }
            }

        }

        fun bind(item: ListDevice) = // Вызывается когда элемент списка становится виден пользователю
            with(binding) { // Использование области видимости привязки к интерфейсу элемента списка
                try {
                    data = item // Установка данных элемента списка на актуальные
                    name.text =
                        item.device.name // Установка названия девайса элемента списка на соответствующее
                    mac.text =
                        item.device.address // Установка MAC-адреса элемента списка на соответствующее
                    if (item.isEnabled) { // Если девайс является выбранным
                        listDeviceAdapter.setCheckBox(checkBox) // Установка чек-бокса выбранного в данный момент девайса списка
                    }
                } catch (_: SecurityException) {
                }
            }
    }

    class Comparator : DiffUtil.ItemCallback<ListDevice>() { // Сравнитель девайсов списка
        override fun areItemsTheSame(
            old: ListDevice,
            new: ListDevice
        ): Boolean { // Вызывается для проверки того, представляют ли два объекта один и тот же элемент списка
            return old == new
        }

        override fun areContentsTheSame(
            old: ListDevice,
            new: ListDevice
        ): Boolean { // Вызывается для проверки того, имеют ли два элемента списка одинаковые данные
            return old == new
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): Holder { // Вызывается, когда нужно инициализировать новый держатель элемента списка
        val view =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.device, parent, false) // Установка внешнего вида элемента списка
        return Holder(
            view,
            this,
            onClickListener,
            isBounded
        ) // Инициализация держателя элемента списка
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int
    ) { // Вызывается при установке или обновлении элемента списка в определённой позиции списка
        holder.bind(getItem(position)) // Обновление и отображение элемента списка в определённой позиции
    }

    fun setCheckBox(checkBox: CheckBox) { // Установка чек-бокса выбранного в данный момент девайса списка
        currentCheckBox?.isChecked = false // Снятие галочки с предыдущего чек-бокса
        currentCheckBox = checkBox // Установка принятого чек-бокса выбранным
        currentCheckBox?.isChecked = true // Установка галочки выбранного чек-бокса
    }

    interface OnClickListener { // Интерфейс для отслеживания нажатия на элемент списка
        fun onClick(item: ListDevice) // Вызывается для оповещения отслеживателя нажатия на элемент списка
    }
}