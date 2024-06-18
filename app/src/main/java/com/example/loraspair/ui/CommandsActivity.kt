package com.example.loraspair.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loraspair.App
import com.example.loraspair.DeviceCommandsManager
import com.example.loraspair.adapters.ListCommand
import com.example.loraspair.adapters.ListCommandAdapter
import com.example.loraspair.R
import com.example.loraspair.databinding.ActivityCommandsBinding

// Активность управления командами
class CommandsActivity : AppCompatActivity(), ListCommandAdapter.OnTextChangedListener {
    private lateinit var binding: ActivityCommandsBinding // Привязка к интерфейсу активности управления командами
    private lateinit var itemTouchHelper: ItemTouchHelper // Инструменты для поддержки свайпа списком

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding =
            ActivityCommandsBinding.inflate(layoutInflater) // Установка привязки к интерфейсу активности управления командами
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true) // Установка стандартной шапки
        supportActionBar?.title = getString(R.string.action_commands) // Установка названия шапки

        initSwipeRefresh() // Настройка свайпа

        binding.commandAdd.setOnClickListener {// Установка слушателя нажатия на кнопку добавления пользовательской команды
            val dialog = Dialog(this) // Инициализация диалогового окна добавления пользовательской команды
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE) // Скрытие названия диалогового окна
            dialog.setCancelable(true) // Установка возможности закрытия диалогового окна кнопкой "Назад"
            dialog.setContentView(R.layout.dialog_add_command) // Установка интерфейса диалогового окна

            val saveCommandButton = dialog.findViewById<Button>(R.id.save_command_button)
            saveCommandButton.setOnClickListener {// Установка слушателя нажатия на кнопку сохранения пользовательской команды
                if (DeviceCommandsManager.NamedCommands.addCommand(
                        dialog.findViewById<AutoCompleteTextView>(R.id.command_name).text.toString(),
                        dialog.findViewById<AutoCompleteTextView>(R.id.command_value).text.toString(),
                    ) // Если добавление пользовательской команды прошло успешно
                ) {
                    dialog.currentFocus?.let { view ->
                        val imm =
                            getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(view.windowToken, 0)
                    } // Закрытие экранной клавиатуры
                    dialog.dismiss() // Закрытие диалогового окна
                    updateCommands() // Обновление списка команд
                }
            }

            dialog.show() // Отображение диалогового окна добавления пользовательской команды
        }

        binding.commands.layoutManager = LinearLayoutManager(App.self.applicationContext)
        binding.commands.adapter = ListCommandAdapter(this)
        val adapter = binding.commands.adapter as ListCommandAdapter

        val simpleItemTouchCallback =
            object : ItemTouchHelper.SimpleCallback(
                0,
                ItemTouchHelper.RIGHT
            ) { // Установка коллбэка при свайпе элемента списка команд
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    return false
                }

                override fun onSwiped(
                    viewHolder: RecyclerView.ViewHolder,
                    direction: Int
                ) { // Вызывается по окончании свайпа
                    val position = viewHolder.adapterPosition // Запрос позиции элемента в списке
                    val listCommand = adapter.getItemAt(position) // Запрос команды списка
                    if (DeviceCommandsManager.NamedCommands.removeCommand(listCommand.name)) { // Если удаление команды прошло успешно
                        updateCommands() // Обновление списка команд
                    } else {
                        undoSwipe() // Отмена свайпа
                    }
                }

            }
        itemTouchHelper =
            ItemTouchHelper(simpleItemTouchCallback) // Установка инструментов для поддержки свайпа списком
        itemTouchHelper.attachToRecyclerView(binding.commands) // Привязка инструментов для поддержки свайпа списком

        binding.swipeRefresh.isRefreshing = true // Остановка анимации обновления списка команд
        updateCommands() // Обновление списка команд
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean { // Вызывается при нажатии на элемент в шапке
        when (item.itemId) {
            android.R.id.home -> { // Если нажата кнопка "Домой"
                finish() // Закрытие активности управления командами
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onTextChanged(item: ListCommand) { // Вызывается для оповещения отслеживателя изменений в значениях команд в списке
        DeviceCommandsManager.NamedCommands.alterCommand(item.name, item.value) // Изменение значения команды
    }

    private fun updateCommands() { // Обновление списка команд
        val adapter = binding.commands.adapter as ListCommandAdapter
        adapter.submitList(null) // Очистка списка команд
        adapter.submitList(
            DeviceCommandsManager.NamedCommands.default.entries.map {
                ListCommand(
                    it.key,
                    it.value,
                    false
                )
            }
                    + DeviceCommandsManager.NamedCommands.custom.entries.map {
                ListCommand(
                    it.key,
                    it.value,
                    true
                )
            }
        ) // Добавление стандартных и пользовательских команд в список
        binding.swipeRefresh.isRefreshing = false // Остановка анимации обновления списка команд
    }

    private fun initSwipeRefresh() { // Настройка свайпа
        binding.swipeRefresh.setOnRefreshListener { // Добавление отслеживания свайпа для обновления
            currentFocus?.let { view ->
                val imm =
                    getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            } // Закрытие экранной клавиатуры
            updateCommands() // Обновление списка команд
        }
    }

    private fun undoSwipe() { // Отмена свайпа
        itemTouchHelper.attachToRecyclerView(null) // Отвязка инструментов для поддержки свайпа списком
        itemTouchHelper.attachToRecyclerView(binding.commands) // Привязка инструментов для поддержки свайпа списком
    }
}