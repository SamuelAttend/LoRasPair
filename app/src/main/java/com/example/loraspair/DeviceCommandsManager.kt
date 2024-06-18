package com.example.loraspair

import android.app.Service
import com.example.loraspair.connection.BluetoothListenersManager
import com.example.loraspair.database.Gps
import com.example.loraspair.database.IncomingMessage
import kotlinx.coroutines.runBlocking
import java.util.Date

// Менеджер команд
@OptIn(ExperimentalStdlibApi::class)
class DeviceCommandsManager {
    object Constants { // Статические поля в пространстве имён Constants
        val PREAMBLE = byteArrayOf(0xfe.toByte(), 0xfe.toByte())
        val TRANSCEIVER_ADDRESS = 0x9a.toByte()
        val CONTROLLER_ADDRESS = 0xe0.toByte()
        val END = 0xfd.toByte()

        val TEXT_SIGNS_PREAMBLE =
            byteArrayOf(0x24.toByte(), 0x24.toByte(), 0x4d.toByte(), 0x73.toByte(), 0x67.toByte())
        val TEXT_COMMA = 0x2c.toByte()
        val TEXT_TX_SIGNS_END = byteArrayOf(
            0x30.toByte(),
            0x30.toByte(),
            0x31.toByte(),
            0x31.toByte(),
            0x45.toByte(),
            0x30.toByte()
        )
        val TEXT_RX_SIGNS_END =
            byteArrayOf(0x30.toByte(), 0x30.toByte(), 0x31.toByte(), 0x31.toByte())
        val TEXT_CRC = 0x00.toByte() // TEMP
        val TEXT_TX_END = byteArrayOf(0x0d.toByte(), 0x00.toByte())
        val TEXT_RX_END_FIRST = 0x00.toByte()
        val TEXT_RX_END_LAST = byteArrayOf(0x0d.toByte(), 0x00.toByte())

        const val TEXT_TX_MAX_BYTES = 30
        const val TEXT_TX_FIRST_CONST_BYTES = 14

        val OTHER_SIGNS_PREAMBLE = byteArrayOf(0x00.toByte(), 0x00.toByte())

        enum class Cmd(val value: Byte) {
            TEST_MSG(0x12.toByte()),
            MY_ID(0x19.toByte()),
            MY_CONNECTION_TEST(0x20.toByte()),
            MY_CONNECTION_TEST_ALT(0x24.toByte()),
            OK(0xfb.toByte()),
            MY_STATUS(0x1c.toByte()),
            MY_OPERATING_MODE(0x04.toByte()),
            MY_GPS(0x24.toByte()),
            SIGN(0x1f.toByte()),
            TEXT(0x22.toByte()),
            GPS(0x20.toByte())
        }

        enum class SuppCmd(val value: Byte) {
            TEXT_TX(0x00.toByte()),
            TEXT_RX(0x01.toByte()),

            GPS_SIGN(0x00.toByte()),
            GPS_RX_MESSAGE(0x01.toByte()),
            GPS_RX_STATUS(0x02.toByte()),
            GPS_DATA(0x03.toByte()),
            GPS_MESSAGE(0x04.toByte())
        }

        enum class SubCmd(val value: Byte) {
            TEXT_RX_AUTO_OUTPUT_SWITCH(0x00.toByte()),
            TEXT_RX_READ(0x01.toByte()),

            GPS_SWITCH(0x00.toByte()),
            GPS_OUTPUT(0x01.toByte()),
            GPS_READ(0x02.toByte())
        }

        enum class DataCmd(val value: Byte) {
            TEXT_RX_AUTO_OUTPUT_OFF(0x00.toByte()),
            TEXT_RX_AUTO_OUTPUT_ON(0x01.toByte()),
        }
    }

    object NamedCommands { // Статические поля в пространстве имён NamedCommands
        private val sharedPreferences = App.self.getSharedPreferences(
            SharedPreferencesConstants.COMMANDS,
            Service.MODE_PRIVATE
        ) // Запрос хранилища примитивных данных пользовательских команд

        val default: Map<String, String> = mapOf(
            "GPS POSITION ONCE" to "CGPS1",
            "GPS 15MIN EVERY 30SEC" to "CGPS2",
            "GPS 15MIN EVERY 1MIN" to "CGPS3",
            "GPS OFF" to "CGPSOFF",
            "SNR ONLY" to "CSNR",
            "MY GPS ONCE" to "MYGPS1",
            "MY GPS OFF" to "MYGPSOFF"
        ) // Стандартные команды

        @Suppress("UNCHECKED_CAST")
        val custom: Map<String, String>
            get() = sharedPreferences.all as Map<String, String>

        fun addCommand(
            name: String,
            value: String
        ): Boolean { // Добавление пользовательской команды
            if (name.isEmpty()) {
                return false
            }
            if (default.containsKey(name) or custom.containsKey(name)) {
                return false
            }
            with(sharedPreferences.edit()) {// Добавление пользовательской команды в хранилище примитивных данных команд
                putString(name, value)
                return commit()
            }
        }

        fun removeCommand(name: String): Boolean { // Удаление пользовательской команды
            if (!custom.containsKey(name)) {
                return false
            }
            with(sharedPreferences.edit()) {// Удаление пользовательской команды из хранилища примитивных данных команд
                remove(name)
                return commit()
            }
        }

        fun alterCommand(
            name: String,
            value: String
        ): Boolean { // Изменение пользовательской команды
            if (!custom.containsKey(name)) {
                return false
            }
            with(sharedPreferences.edit()) {// Обновление пользовательской команды в хранилище примитивных данных команд
                remove(name)
                putString(name, value)
                return commit()
            }
        }

        fun clearCommands(): Boolean { // Удаление всех пользовательских команд
            with(sharedPreferences.edit()) {// Удаление всех пользовательских команд из хранилища примитивных данных команд
                clear()
                return commit()
            }
        }
    }

    object Requests { // Статические поля в пространстве имён Requests
        fun generateSimpleRequestMessage(
            cmd: Constants.Cmd,
            suppCmd: Constants.SuppCmd? = null,
            subCmd: Constants.SubCmd? = null,
            dataCmd: Constants.DataCmd? = null
        ): ByteArray { // Составление простого запроса девайсу
            val message = arrayListOf(
                Constants.PREAMBLE[0],
                Constants.PREAMBLE[1],
                Constants.TRANSCEIVER_ADDRESS,
                Constants.CONTROLLER_ADDRESS,
                cmd.value
            )

            suppCmd?.let { message.add(it.value) }
            subCmd?.let { message.add(it.value) }
            dataCmd?.let { message.add(it.value) }

            message.add(Constants.END)

            return message.toByteArray()
        }

        fun generateNamedRequestMessages(
            name: String,
            from: String,
            to: String
        ): List<ByteArray> { // Составление запроса пользовательской команды девайсу
            if (name.isEmpty() or from.isEmpty() or to.isEmpty()) {
                return emptyList()
            }

            if (NamedCommands.default.containsKey(name) or NamedCommands.custom.containsKey(name)) {
                val value = NamedCommands.default[name] ?: NamedCommands.custom[name] as String
                return generateTextMessages(
                    value,
                    from,
                    to
                ) // Составление текстового сообщения девайсу
            }

            return emptyList()
        }

        fun generateTextMessages(
            text: String,
            from: String,
            to: String
        ): List<ByteArray> { // Составление текстового сообщения девайсу
            if (text.isEmpty() or from.isEmpty() or to.isEmpty()) {
                return emptyList()
            }

            val textByteArray = text.toByteArray(Charsets.UTF_8)
            var textByteArraySlice = textByteArray.sliceArray(
                0..<minOf(
                    Constants.TEXT_TX_MAX_BYTES - (Constants.TEXT_TX_FIRST_CONST_BYTES + from.length + to.length),
                    textByteArray.size
                )
            )

            if (textByteArraySlice.size == textByteArray.size) {
                return listOf(
                    byteArrayOf(
                        *Constants.PREAMBLE,
                        Constants.TRANSCEIVER_ADDRESS,
                        Constants.CONTROLLER_ADDRESS,
                        Constants.Cmd.TEXT.value,
                        Constants.SuppCmd.TEXT_TX.value,
                        *Constants.TEXT_SIGNS_PREAMBLE,
                        Constants.TEXT_COMMA,
                        *from.toByteArray(Charsets.US_ASCII),
                        Constants.TEXT_COMMA,
                        *to.toByteArray(Charsets.US_ASCII),
                        Constants.TEXT_COMMA,
                        *Constants.TEXT_TX_SIGNS_END,
                        *textByteArraySlice,
                        Constants.TEXT_CRC,
                        *Constants.TEXT_TX_END,
                        Constants.END
                    )
                )
            } // Если текст сообщения помещается в одну посылку, то осуществляется возврат списка из одного сообщения

            val messages: MutableList<ByteArray> = mutableListOf(
                byteArrayOf(
                    *Constants.PREAMBLE,
                    Constants.TRANSCEIVER_ADDRESS,
                    Constants.CONTROLLER_ADDRESS,
                    Constants.Cmd.TEXT.value,
                    Constants.SuppCmd.TEXT_TX.value,
                    *Constants.TEXT_SIGNS_PREAMBLE,
                    Constants.TEXT_COMMA,
                    *from.toByteArray(Charsets.US_ASCII),
                    Constants.TEXT_COMMA,
                    *to.toByteArray(Charsets.US_ASCII),
                    Constants.TEXT_COMMA,
                    *Constants.TEXT_TX_SIGNS_END,
                    *textByteArraySlice,
                    Constants.END
                )
            )

            var start = textByteArraySlice.size
            while (start != textByteArray.size) {
                textByteArraySlice = textByteArray.sliceArray(
                    start..<minOf(
                        start + Constants.TEXT_TX_MAX_BYTES,
                        textByteArray.size
                    )
                )
                start += textByteArraySlice.size
                messages.add(
                    byteArrayOf(
                        *Constants.PREAMBLE,
                        Constants.TRANSCEIVER_ADDRESS,
                        Constants.CONTROLLER_ADDRESS,
                        Constants.Cmd.TEXT.value,
                        Constants.SuppCmd.TEXT_TX.value,
                        *textByteArraySlice,
                        *(if (textByteArraySlice.size == Constants.TEXT_TX_MAX_BYTES)
                            byteArrayOf(Constants.END)
                        else
                            byteArrayOf(
                                Constants.TEXT_CRC,
                                *Constants.TEXT_TX_END,
                                Constants.END
                            ))
                    )
                )
            } // Если текст сообщения достаточно длинный, то осуществляется его разбивка на несколько сообщений

            return messages
        }
    }

    object Responses { // Статические поля в пространстве имён Responses
        private val sharedPreferences = App.self.getSharedPreferences(
            SharedPreferencesConstants.DEVICE,
            Service.MODE_PRIVATE
        ) // Запрос хранилища примитивных данных девайса

        fun handleResponseMessage(message: ByteArray) { // Обработка принятых данных
            val ranges = mutableListOf<Pair<Int, Int>>()
            var start = 0
            for (index in 1..<message.size) {
                if (message[index - 1] == Constants.PREAMBLE[0] && message[index] == Constants.PREAMBLE[1]) {
                    start = index + 1
                }
                if (message[index] == Constants.END) {
                    ranges.add(Pair(start, index - 1))
                }
            } // Поиск преабмул и окончаний в принимаемых данных

            ranges.forEach { // Для каждого полученного сообщения
                try {
                    if (message[0 + it.first] != Constants.CONTROLLER_ADDRESS || message[1 + it.first] != Constants.TRANSCEIVER_ADDRESS) { // Сообщение должно иметь адреса контроллера и трансивера
                        return@forEach
                    }
                    val cmd = message[2 + it.first]
                    when (cmd) {
                        Constants.Cmd.TEXT.value -> { // Если сообщение является текстовым
                            if (message[3 + it.first] != Constants.SuppCmd.TEXT_RX.value || message[4 + it.first] != Constants.SubCmd.TEXT_RX_READ.value) {
                                return@forEach
                            }

                            var dataLeftIndex = 5 + it.first
                            var dataRightIndex = it.second

                            var crcLeftIndex = 0
                            var crcRightIndex = 0

                            if (message.sliceArray((5 + it.first)..<(5 + it.first + 5))
                                    .contentEquals(Constants.TEXT_SIGNS_PREAMBLE)
                            ) { // Если это первое сообщение из нескольких
                                if (message[10 + it.first] != Constants.TEXT_COMMA) {
                                    return@forEach
                                }
                                Message.text =
                                    Message.Text() // Инициализация структуры данных нового текстового сообщения

                                val fromLeftCommaIndex = 10 + it.first
                                var fromRightCommaIndex = fromLeftCommaIndex + 1
                                while (message[fromRightCommaIndex] != Constants.TEXT_COMMA) {
                                    fromRightCommaIndex++
                                }
                                Message.text.from =
                                    message.sliceArray(fromLeftCommaIndex + 1..<fromRightCommaIndex)
                                        .toString(Charsets.US_ASCII) // Установка позывного отправителя

                                val toLeftCommaIndex = fromRightCommaIndex
                                var toRightCommaIndex = toLeftCommaIndex + 1
                                while (message[toRightCommaIndex] != Constants.TEXT_COMMA) {
                                    toRightCommaIndex++
                                }
                                Message.text.to =
                                    message.sliceArray(toLeftCommaIndex + 1..<toRightCommaIndex)
                                        .toString(Charsets.US_ASCII) // Установка позывного получателя

                                if (!message.sliceArray(toRightCommaIndex + 1..<toRightCommaIndex + 1 + 4)
                                        .contentEquals(Constants.TEXT_RX_SIGNS_END)
                                ) {
                                    return@forEach
                                }

                                crcLeftIndex = it.second - 2
                                crcRightIndex = it.second - 1

                                dataLeftIndex = toRightCommaIndex + 1 + 4
                                dataRightIndex = crcLeftIndex - 1

                                if (message[it.second] != Constants.TEXT_RX_END_FIRST) {
                                    return@forEach
                                }
                            }

                            if (message.sliceArray(it.second - 1..it.second)
                                    .contentEquals(Constants.TEXT_RX_END_LAST)
                            ) { // Если это последнее сообщение из нескольких
                                crcLeftIndex = it.second - 2
                                crcRightIndex = it.second - 1

                                dataRightIndex = crcLeftIndex - 1

                                Message.text.value += message.sliceArray(dataLeftIndex..dataRightIndex) // Добавление данных к полю значения структуры данных текстового сообщения

                                runBlocking {// Блокирующая корутина
                                    var start = 0
                                    for (index in 3..<Message.text.value.size) {
                                        if (Message.text.value[index] == ']'.code.toByte() && Message.text.value[index - 3] == '['.code.toByte()) { // Если найдена конструкция '[..]' (большинство эмодзи занимают два символа)
                                            val stickerEmoji =
                                                Message.text.value.sliceArray(index - 2..index - 1)
                                                    .toString(Charsets.UTF_8) // Взятие символов между '[' и ']'

                                            if (App.database.stickerDao()
                                                    .checkStickerExistsByStickerEmoji(stickerEmoji)
                                            ) { // Если изображение найдено по идентификатору в базе данных
                                                App.database.userDao()
                                                    .getUserByUserSignOrInsertUser(
                                                        Message.text.from
                                                    )
                                                    ?.let { user -> // Запрос пользователя из базы данных
                                                        App.database.incomingMessageDao()
                                                            .insertIncomingMessages(
                                                                IncomingMessage(
                                                                    0,
                                                                    user.user_id,
                                                                    Message.text.to == "CQCQCQ",
                                                                    Date(),
                                                                    Message.text.value.sliceArray(
                                                                        start..<index - 3
                                                                    ).toString(Charsets.UTF_8)
                                                                        .trim(),
                                                                    stickerEmoji
                                                                )
                                                            ) // Добавление входящего сообщения в базу данных
                                                        start = index + 1
                                                    }
                                            }
                                        }
                                    }
                                    if (start != Message.text.value.size) {
                                        App.database.userDao()
                                            .getUserByUserSignOrInsertUser(
                                                Message.text.from
                                            )?.let { user -> // Запрос пользователя из базы данных
                                                App.database.incomingMessageDao()
                                                    .insertIncomingMessages(
                                                        IncomingMessage(
                                                            0,
                                                            user.user_id,
                                                            Message.text.to == "CQCQCQ",
                                                            Date(),
                                                            Message.text.value.sliceArray(start..<Message.text.value.size)
                                                                .toString(Charsets.UTF_8).trim(),
                                                            null
                                                        )
                                                    ) // Добавление входящего сообщения в базу данных
                                            }
                                    }
                                }

                                BluetoothListenersManager.getListeners()
                                    .forEach {
                                        it.onBluetoothMessageReceived(Message.Variant.TEXT)
                                    } // Сообщение всем слушателям Bluetooth информации о новом входящем сообщении

                            } else {
                                Message.text.value += message.sliceArray(dataLeftIndex..dataRightIndex) // Добавление данных к полю значения структуры данных текстового сообщения
                            }
                        }

                        Constants.Cmd.GPS.value -> { // Если сообщение является сообщением о координатах
                            val suppCmd = message[3 + it.first]
                            when (suppCmd) {
                                Constants.SuppCmd.GPS_SIGN.value -> { // Если в сообщении указываются позывные отправителя и получателя
                                    val subCmd = message[4 + it.first]
                                    if (subCmd != Constants.SubCmd.GPS_OUTPUT.value) {
                                        return@forEach
                                    }
                                    if (!message.sliceArray(5 + it.first..6 + it.first)
                                            .contentEquals(Constants.OTHER_SIGNS_PREAMBLE)
                                    ) {
                                        return@forEach
                                    }

                                    with(
                                        message.sliceArray(7 + it.first..it.second)
                                            .toString(Charsets.US_ASCII).trim()
                                            .split("\\s+".toRegex())
                                    ) {
                                        Message.gps = Message.GPS(
                                            from = this.first(),
                                            to = this.last()
                                        ) // Инициализация структуры данных нового сообщения о координатах со значениями позывных
                                    }
                                }

                                Constants.SuppCmd.GPS_RX_MESSAGE.value -> { // Если в сообщении указывается RX-сообщение
                                    val subCmd = message[4 + it.first]
                                    if (subCmd != Constants.SubCmd.GPS_OUTPUT.value) {
                                        return@forEach
                                    }

                                    Message.gps.rxMessage =
                                        message.sliceArray(5 + it.first..<5 + it.first + 20)
                                            .toString(Charsets.US_ASCII) // Установка данных в поле RX-сообщения структуры данных сообщения о координатах
                                }

                                Constants.SuppCmd.GPS_DATA.value -> { // Если в сообщении указываются геоданные
                                    val subCmd = message[4 + it.first]
                                    if (subCmd != Constants.SubCmd.GPS_OUTPUT.value) {
                                        return@forEach
                                    }

                                    message.sliceArray(17 + it.first..21 + it.first).toHexString()
                                        .let {
                                            with(Message.gps.lat) {
                                                this.degrees = it.slice(0..1).toInt() *
                                                        (if (it.slice(9..9)
                                                                .toInt() == 0
                                                        ) -1 else 1)
                                                this.minutes = it.slice(2..6).toFloat() / 1000.0f
                                            }
                                        } // Установка данных в поле широты структуры данных сообщения о координатах

                                    message.sliceArray(22 + it.first..27 + it.first).toHexString()
                                        .let {
                                            with(Message.gps.lon) {
                                                this.degrees = it.slice(1..3).toInt() *
                                                        (if (it.slice(11..11)
                                                                .toInt() == 0
                                                        ) -1 else 1)
                                                this.minutes = it.slice(4..8).toFloat() / 1000.0f
                                            }
                                        } // Установка данных в поле долготы структуры данных сообщения о координатах

                                    message.sliceArray(28 + it.first..31 + it.first).toHexString()
                                        .let {
                                            Message.gps.alt =
                                                it.slice(0..5).toFloat() / 10.0f *
                                                        (if (it.slice(7..7)
                                                                .toInt() == 0
                                                        ) 1.0f else -1.0f)
                                        } // Установка данных в поле высоты структуры данных сообщения о координатах
                                }

                                Constants.SuppCmd.GPS_MESSAGE.value -> { // Если в сообщении указывается GPS-сообщение
                                    val subCmd = message[4 + it.first]
                                    if (subCmd != Constants.SubCmd.GPS_OUTPUT.value) {
                                        return@forEach
                                    }

                                    Message.gps.gpsMessage =
                                        message.sliceArray(15 + it.first..it.second)
                                            .toString(Charsets.US_ASCII) // Установка данных в поле GPS-сообщения структуры данных сообщения о координатах
                                }

                                Constants.SuppCmd.GPS_RX_STATUS.value -> { // Если в сообщении указывается RX-статус
                                    runBlocking {// Блокирующая корутина
                                        App.database.userDao()
                                            .getUserByUserSignOrInsertUser(
                                                Message.gps.from
                                            )?.let { user -> // Запрос пользователя из базы данных
                                                App.database.gpsDao().insertAllGps(
                                                    Gps(
                                                        user.user_id,
                                                        Date(),
                                                        Message.gps.lat,
                                                        Message.gps.lon,
                                                        Message.gps.alt,
                                                        Message.gps.rxMessage,
                                                        Message.gps.gpsMessage
                                                    )
                                                ) // Добавление данных координат в базу данных
                                            }
                                    }

                                    BluetoothListenersManager.getListeners()
                                        .forEach {
                                            it.onBluetoothMessageReceived(Message.Variant.GPS)
                                        } // Сообщение всем слушателям Bluetooth информации о новом входящем сообщении
                                }
                            }
                        }

                        Constants.Cmd.OK.value -> { // Если сообщение является сообщением подтверждения
                        }

                        Constants.Cmd.SIGN.value -> { // Если сообщение является сообщением позывного девайса
                            with(sharedPreferences.edit()) {// Добавление позывного девайса в хранилище примитивных данных девайса
                                putString(
                                    SharedPreferencesConstants.DEVICE_SIGN,
                                    message.sliceArray(4 + it.first..<4 + it.first + 8)
                                        .toString(Charsets.US_ASCII).trim()
                                )
                                apply()
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    object Message { // Статические поля в пространстве имён Message
        enum class Variant { // Тип входящего сообщения
            TEXT,
            GPS
        }

        data class Text(
            var from: String = String(),
            var to: String = String(),
            var value: ByteArray = byteArrayOf()
        ) // Структура данных текстового сообщения

        data class GPS(
            var from: String = String(),
            var to: String = String(),
            var lat: DMM = DMM(),
            var lon: DMM = DMM(),
            var alt: Float = 0.0f,
            var rxMessage: String = String(),
            var gpsMessage: String = String()
        ) // Структура данных сообщения о координатах
        {
            data class DMM(
                var degrees: Int = 0,
                var minutes: Float = 0.0f
            ) // Структура данных формата DMM
        }

        var text: Text = Text() // Инициализация структуры данных нового текстового сообщения
        var gps: GPS = GPS() // Инициализация структуры данных нового сообщения о координатах
    }
}