package com.example.loraspair.adapters

import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.loraspair.App
import com.example.loraspair.R
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.databinding.UserBinding

// Адаптер списка пользователей
class ListUserAdapter : ListAdapter<ListUser, ListUserAdapter.Holder>(Comparator()) {
    val sharedPreferences: SharedPreferences =
        App.self.getSharedPreferences(
            SharedPreferencesConstants.CHAT,
            Context.MODE_PRIVATE
        ) // Запрос хранилища примитивных данных чата

    data class User( // Структура данных для пользователя, с которым в данный момент ведётся диалог в чате
        val data: ListUser, // Структура данных пользователя для списка пользователей
        val binding: UserBinding // Привязка к интерфейсу пользователя
    )

    private var currentUser: User? =
        null // Инициализация пользователя, с которым в данный момент ведётся диалог в чате со значенеим null

    class Holder( // Держатель элемента списка, используемый для хранения внешнего вида и прочих необходимых данных элемента списка
        view: View, // Внешний вид элемента списка
        private val listUserAdapter: ListUserAdapter // Объект адаптера списка пользователей
    ) : RecyclerView.ViewHolder(view) { // Наследование от стандартного держателя элемента списка
        private val binding = UserBinding.bind(view) // Привязка к интерфейсу элемента списка
        private var data: ListUser? =
            null // Данные элемента списка (пользователь списка пользователей)

        init { // Конструктор класса
            binding.root.setOnClickListener { _ -> // Добавление отслеживания нажатия на элемент списка
                data?.let {// Если данные элемента списка не равны null
                    listUserAdapter.setUserCurrent(
                        User(
                            it,
                            binding
                        )
                    ) // Обновление пользователя, с которым в данный момент ведётся диалог
                }
            }
        }

        fun bind(item: ListUser) = // Вызывается когда элемент списка становится виден пользователю
            with(binding) {// Использование области видимости привязки к интерфейсу элемента списка
                data = item // Установка данных элемента списка на актуальные
                sign.text = item.sign // Установка позывного пользователя на соответствующий
                if (item.isCurrent) { // Если с данным пользователем в данный момент ведётся диалог
                    listUserAdapter.currentUser = User(
                        item,
                        binding
                    ) // Установка пользователя, с которым в данный момент ведётся диалог
                    binding.root.setBackgroundColor( // Выделение пользователя, с которым в данный момент ведётся диалог, цветом
                        ContextCompat.getColor(
                            App.self.applicationContext,
                            R.color.chosen_sign_background
                        )
                    )
                } else { // Если с данным пользователем в данный момент не ведётся диалог
                    binding.root.setBackgroundColor(0) // Снятие цветового выделения пользователя
                }
            }
    }

    class Comparator : DiffUtil.ItemCallback<ListUser>() { // Сравнитель пользователей списка
        override fun areItemsTheSame(
            old: ListUser,
            new: ListUser
        ): Boolean { // Вызывается для проверки того, представляют ли два объекта один и тот же элемент списка
            return old == new
        }

        override fun areContentsTheSame(
            old: ListUser,
            new: ListUser
        ): Boolean { // Вызывается для проверки того, имеют ли два элемента списка одинаковые данные
            return old == new
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder { // Вызывается, когда нужно инициализировать новый держатель элемента списка
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.user, parent, false) // Установка внешнего вида элемента списка
        return Holder(view, this) // Инициализация держателя элемента списка
    }

    override fun onBindViewHolder(holder: Holder, position: Int) { // Вызывается при установке или обновлении элемента списка в определённой позиции списка
        holder.bind(getItem(position)) // Обновление и отображение элемента списка в определённой позиции
    }

    fun setUserCurrent(user: User) { // Обновление пользователя, с которым в данный момент ведётся диалог
        currentUser?.let {// Если с каким-то пользователем уже вёлся диалог
            it.binding.root.setBackgroundColor(0) // Снять цветовое выделение с пользователя
            it.data.isCurrent = false // С данным пользователем уже не ведётся диалог
        }
        currentUser = user // Установка пользователя, с которым в данный момент ведётся диалог
        currentUser?.let {// Если пользователь, с которым в данный момент ведётся диалог, не равен null
            it.binding.root.setBackgroundColor( // Выделение пользователя, с которым в данный момент ведётся диалог, цветом
                ContextCompat.getColor(
                    App.self.applicationContext,
                    R.color.chosen_sign_background
                )
            )
            it.data.isCurrent = true // С данным пользователем ведётся диалог

            with(sharedPreferences.edit()) {// Обновление позывного пользователя, с которым в данный момент ведётся диалог, в хранилище примитивных данных чата
                putString(SharedPreferencesConstants.CHAT_SIGN, it.data.sign)
                apply()
            }
        }
    }
}