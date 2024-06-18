package com.example.loraspair.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.loraspair.R
import com.example.loraspair.databinding.CommandBinding

// Адаптер списка команд
class ListCommandAdapter(
    private val onTextChangedListener: OnTextChangedListener, // Имплементация интерфейса для отслеживания изменений в значениях команд элементов списка
) :
    ListAdapter<ListCommand, ListCommandAdapter.Holder>(Comparator()) { // Наследование от адаптера списка

    class Holder(
        // Держатель элемента списка, используемый для хранения внешнего вида и прочих необходимых данных элемента списка
        view: View, // Внешний вид элемента списка
        private val onTextChangedListener: OnTextChangedListener, // Имплементация интерфейса для отслеживания изменений в значениях команд элементов списка
    ) : RecyclerView.ViewHolder(view) { // Наследование от стандартного держателя элемента списка
        private val binding = CommandBinding.bind(view) // Привязка к интерфейсу элемента списка
        private var data: ListCommand? = null // Данные элемента списка (команда списка команд)

        init { // Конструктор класса
            binding.commandValue.addTextChangedListener { // Добавление отслеживания изменения текста в поле значения команды элемента списка
                data?.let { // Если данные элемента списка не равны null
                    it.value =
                        binding.commandValue.text.toString() // Значение команды в данных элемента списка меняется на введённое в поле значения команды списка
                    onTextChangedListener.onTextChanged(it) // Оповещение отслеживателя изменений в значениях команд в списке
                }
            }
        }

        fun bind(item: ListCommand) =
            // Вызывается когда элемент списка становится виден пользователю
            with(binding) { // Использование области видимости привязки к интерфейсу элемента списка
                data = item // Установка данных элемента списка на актуальные
                commandName.setText(item.name) // Установка названия команды элемента списка на соответствующее
                commandValue.setText(item.value) // Установка значения команды элемента списка на соответствующее
                commandValue.isEnabled =
                    item.isMutable // Если команда элемента списка является пользовательской, то разрешить изменение значения команды элемента списка, иначе — запретить
            }
    }

    fun getItemAt(position: Int): ListCommand { // Получить команду списка в указанной позиции
        return getItem(position)
    }

    class Comparator : DiffUtil.ItemCallback<ListCommand>() { // Сравнитель команд списка
        override fun areItemsTheSame(
            old: ListCommand,
            new: ListCommand
        ): Boolean { // Вызывается для проверки того, представляют ли два объекта один и тот же элемент списка
            return old == new
        }

        override fun areContentsTheSame(
            old: ListCommand,
            new: ListCommand
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
                .inflate(R.layout.command, parent, false) // Установка внешнего вида элемента списка
        return Holder(view, onTextChangedListener) // Инициализация держателя элемента списка
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int
    ) { // Вызывается при установке или обновлении элемента списка в определённой позиции списка
        holder.bind(getItem(position)) // Обновление и отображение элемента списка в определённой позиции
    }

    interface OnTextChangedListener { // Интерфейс для отслеживания изменений в значениях команд элементов списка
        fun onTextChanged(item: ListCommand) // Вызывается для оповещения отслеживателя изменений в значениях команд в списке
    }
}