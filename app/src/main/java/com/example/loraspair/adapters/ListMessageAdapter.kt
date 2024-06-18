package com.example.loraspair.adapters

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.loraspair.R
import com.example.loraspair.databinding.MessageBinding
import java.text.SimpleDateFormat
import java.util.Locale

// Адаптер списка сообщений
class ListMessageAdapter :
    ListAdapter<ListMessage, ListMessageAdapter.Holder>(Comparator()) { // Наследование от адаптера списка
    class Holder( // Держатель элемента списка, используемый для хранения внешнего вида и прочих необходимых данных элемента списка
        view: View // Внешний вид элемента списка
    ) : RecyclerView.ViewHolder(view) { // Наследование от стандартного держателя элемента списка
        private val binding = MessageBinding.bind(view) // Привязка к интерфейсу элемента списка
        private var data: ListMessage? = null // Данные элемента списка (сообщение списка сообщений)

        fun bind(item: ListMessage) =
            // Вызывается когда элемент списка становится виден пользователю
            with(binding) {// Использование области видимости привязки к интерфейсу элемента списка
                val mine =
                    item.status != null // Сообщение является отправляемым, когда у него есть статус
                data = item // Установка данных элемента списка на актуальные

                messageSign.text =
                    item.sign // Установка позывного отправителя сообщения списка на соответствующее

                if (item.text.isNotEmpty()) { // Если в сообщении есть текст
                    messageText.visibility = View.VISIBLE // Показ раздела для текста сообщения
                    messageText.text = item.text // Установка текста сообщения на соответствующий
                } else { // Если в вообщении нет текста
                    messageText.visibility = View.GONE // Скрытие раздела для текста сообщения
                }

                if (item.sticker != null) { // Если в сообщении есть изображение
                    messageSticker.visibility =
                        View.VISIBLE // Показ раздела для изображения сообщения
                    messageSticker.setImageBitmap(item.sticker) // Установка изображения сообщения на соответствующее
                } else { // Если в вообщении нет изображения
                    messageSticker.visibility =
                        View.GONE // Скрытие раздела для изображения сообщения
                }

                messageTime.text =
                    SimpleDateFormat( // Установка времени сообщения в соответствующем формате
                        "HH:mm",
                        Locale.US
                    ).format(item.date_time)

                messageDate.text =
                    SimpleDateFormat( // Установка даты сообщения в соответствующем формате
                        "dd.MM.yyyy",
                        Locale.US
                    ).format(item.date_time)

                with( // Использование области видимости параметров линейной схемы
                    LinearLayout.LayoutParams( // Задание параметров линейной схемы
                        ViewGroup.LayoutParams.WRAP_CONTENT, // Задание оборачивания контента по ширине
                        ViewGroup.LayoutParams.WRAP_CONTENT // Задание оборачивания контента по высоте
                    )
                ) {
                    gravity = Gravity.START // Установка позиции элементов схемы в начало
                    if (mine) { // Если сообщение является исходящим
                        messageTime.layoutParams =
                            this // Установка позиции времени сообщения в начало
                        messageDate.layoutParams = this // Установка позиции даты сообщения в начало
                    } else { // Если сообщение является входящим
                        messageSign.layoutParams =
                            this // Установка позиции позывного отправителя сообщения в начало
                    }
                }
                with( // Использование области видимости параметров линейной схемы
                    LinearLayout.LayoutParams( // Задание параметров линейной схемы
                        ViewGroup.LayoutParams.WRAP_CONTENT, // Задание оборачивания контента по ширине
                        ViewGroup.LayoutParams.WRAP_CONTENT // Задание оборачивания контента по высоте
                    )
                ) {
                    gravity = Gravity.END // Установка позиции элементов схемы в конец
                    if (mine) { // Если сообщение является исходящим
                        messageSign.layoutParams =
                            this // Установка позиции позывного отправителя сообщения в конец
                    } else {
                        messageTime.layoutParams =
                            this // Установка позиции времени сообщения в конец
                        messageDate.layoutParams = this // Установка позиции даты сообщения в конец
                    }
                }

                binding.messageCard.updateLayoutParams<LayoutParams> {// Обновление параметров схемы сообщения
                    if (mine) { // Если сообщение является исходящим
                        endToEnd =
                            ConstraintSet.PARENT_ID // Приклепление сообщения к правой части экрана
                        startToStart =
                            ConstraintSet.UNSET // Отклепление сообщения от левой части экрана
                    } else { // Если сообщение является входящим
                        startToStart =
                            ConstraintSet.PARENT_ID // Приклепление сообщения к левой части экрана
                        endToEnd =
                            ConstraintSet.UNSET // Отклепление сообщения от правой части экрана
                    }
                }
            }
    }

    class Comparator : DiffUtil.ItemCallback<ListMessage>() { // Сравнитель сообщений списка
        override fun areItemsTheSame(
            old: ListMessage,
            new: ListMessage
        ): Boolean { // Вызывается для проверки того, представляют ли два объекта один и тот же элемент списка
            return old == new
        }

        override fun areContentsTheSame(
            old: ListMessage,
            new: ListMessage
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
                .inflate(R.layout.message, parent, false) // Установка внешнего вида элемента списка
        return Holder(view) // Инициализация держателя элемента списка
    }

    override fun onBindViewHolder(
        holder: Holder,
        position: Int
    ) { // Вызывается при установке или обновлении элемента списка в определённой позиции списка
        holder.bind(getItem(position)) // Обновление и отображение элемента списка в определённой позиции
    }
}