package com.example.loraspair.ui.map

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.drawable.Drawable
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.loraspair.App
import com.example.loraspair.DeviceCommandsManager
import com.example.loraspair.R
import com.example.loraspair.SharedPreferencesConstants
import com.example.loraspair.connection.BluetoothListenersManager
import com.example.loraspair.database.Gps
import com.example.loraspair.databinding.FragmentMapBinding
import com.example.loraspair.ui.BoundingBoxView
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.RangeSlider.OnSliderTouchListener
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.cachemanager.CacheManager.CacheManagerCallback
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.ItemizedIconOverlay
import org.osmdroid.views.overlay.OverlayItem
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import kotlin.math.abs


// Фрагмент карты
class MapFragment : Fragment(), OnSharedPreferenceChangeListener,
    BoundingBoxView.BoundingBoxListener, OnTouchListener, MapListener,
    BluetoothListenersManager.BluetoothListener, LocationListener {
    object Constants { // Статические поля в пространстве имён Constants
        const val DEFAULT_ZOOM = 3.0
        const val MY_LOCATION_ZOOM = 14.0
        const val MIN_ZOOM = 1.0
        const val MAX_ZOOM = 20.0
        const val MIN_SAVE_ZOOM = 1.0
        const val MAX_SAVE_ZOOM = 17.0
        const val TILE_SIZE = 23780
        const val MAP_DATABASE_EXTENSION = ".sqlite"
    }

    private lateinit var binding: FragmentMapBinding // Привязка к интерфейсу фрагмента карты
    private lateinit var cacheManager: CacheManager // Менеджер сохранения карт
    private lateinit var sharedPreferencesMap: SharedPreferences // Хранилище примитивных данных карты
    private lateinit var sharedPreferencesChat: SharedPreferences // Хранилище примитивных данных чата

    private var boundingBox = BoundingBox() // Зона для сохранения карты
    private var minSaveZoom =
        Constants.MIN_SAVE_ZOOM.toInt() // Минимальное приближение для загрузки
    private var maxSaveZoom =
        Constants.MAX_SAVE_ZOOM.toInt() // Максимальное приближение для загрузки
    private lateinit var tilesDirectory: File // Путь для сохранения карт в формате базы данных

    private var overlayMarker: Drawable? = null // Маркер для точек на карте
    private val overlayItems = mutableListOf<OverlayItem>() // Список точек на карте
    private lateinit var itemizedIconOverlay: ItemizedIconOverlay<OverlayItem> // Обёртка над списком точек на карте
    private lateinit var myLocationOverlay: MyLocationNewOverlay // Моя позиция на карте
    private lateinit var myLocationRuler: Polyline // Линейка от центра экрана до моей позиции на карте
    private lateinit var locationManager: LocationManager
    private val gpsListener = object : GnssStatus.Callback() {
        override fun onStarted() {
            super.onStarted()
            updateMyLocationRuler()
        }

        override fun onStopped() {
            super.onStopped()
            updateMyLocationRuler()
        }
    }
    private var gpsHandler: Handler? = null
    private lateinit var gpsMyLocationProvider: GpsMyLocationProvider

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        binding = FragmentMapBinding.inflate(
            inflater,
            container,
            false
        ) // Установка привязки к интерфейсу фрагмента карты

        sharedPreferencesMap =
            App.self.getSharedPreferences(
                SharedPreferencesConstants.MAP,
                Context.MODE_PRIVATE
            ) // Запрос хранилища примитивных данных карты

        sharedPreferencesChat = App.self.getSharedPreferences(
            SharedPreferencesConstants.CHAT,
            Context.MODE_PRIVATE
        ) // Запрос хранилища примитивных данных чата

        gpsMyLocationProvider = GpsMyLocationProvider(context)

        initTilesDirectory() // Настройка пути для сохранения карт в формате базы данных

        initMap() // Настройка карты

        initSetTileSourceButton() // Настройка кнопки выбора источника карт
        initMapSettingsButton() // Настройка карты
        initViewMapSaving() // Настройка панели для сохранения карты
        initSaveMapButton() // Настройка кнопки открытия панели для сохранения карты
        initZoomButtons() // Настройка кнопок приближения и удаления
        initCacheInfoButton() // Настройка кнопки вывода диалогового окна с информацией о кэше

        initMapOverlays() // Настройка оверлея карты

        BluetoothListenersManager.addListener(this) // Добавление фрагмента к слушателям Bluetooth

        locationManager = App.self.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ContextCompat.checkSelfPermission(
                App.self.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
        locationManager.registerGnssStatusCallback(gpsListener, gpsHandler)
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000L, 0.0f, this)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        locationManager.removeUpdates(this)
        locationManager.unregisterGnssStatusCallback(gpsListener)
        BluetoothListenersManager.removeListener(this) // Удаление фрагмента из слушателей Bluetooth
        binding.boundingBox.listener =
            null // Удаление слушателя изменения зоны для сохранения карты
        binding.map.removeMapListener(this) // Удаление слушателя взаимодействия с картой
        myLocationOverlay.disableMyLocation() // Отключение обновления моей позиции на карте
    }

    override fun onResume() {
        super.onResume()
        sharedPreferencesMap.registerOnSharedPreferenceChangeListener(this) // Добавление слушателя изменения значений хранилища примитивных данных карты
        sharedPreferencesChat.registerOnSharedPreferenceChangeListener(this) // Добавление слушателя изменения значений хранилища примитивных данных чата
    }

    override fun onPause() {
        super.onPause()
        sharedPreferencesChat.unregisterOnSharedPreferenceChangeListener(this) // Удаление слушателя изменения значений хранилища примитивных данных чата
        sharedPreferencesMap.unregisterOnSharedPreferenceChangeListener(this) // Удаление слушателя изменения значений хранилища примитивных данных карты
    }

    private fun updateEstimatedData() { // Обновление информации о загрузке участка карты
        val tilesAmount: Int =
            cacheManager.possibleTilesInArea(
                boundingBox,
                minSaveZoom,
                maxSaveZoom
            ) // Запрос приблизительного количества изображений карт в участке (может вернуться отрицательное значение, я предполагаю, что это оверфлоу, поэтому привожу к типу Long)
        val positiveTilesAmount =
            if (tilesAmount < 0) (Int.MAX_VALUE.toLong() + abs(tilesAmount).toLong()) else tilesAmount.toLong() // Приведение количества изображений карт в участке к типу Long
        binding.viewMapSaving.estimatedTilesAmount.text = getString(
            R.string.map_download_info,
            minSaveZoom,
            maxSaveZoom,
            positiveTilesAmount,
            ((positiveTilesAmount * Constants.TILE_SIZE).toFloat() / 1_000_000)
        ) // Отображение информации о загрузке участка карты
    }

    private fun initTilesDirectory() { // Настройка пути для сохранения карт в формате базы данных
        val osmDirectory = File(context?.getExternalFilesDir(null), "osmdroid")
        if (!osmDirectory.exists()) { // Создание папки 'osmdroid' во внешнем общем хранилище
            osmDirectory.mkdir()
        }
        tilesDirectory = File(osmDirectory, "tiles")
        if (!tilesDirectory.exists()) { // Создание папки 'tiles' в папке 'osmdroid'
            tilesDirectory.mkdir()
        }
        val config = Configuration.getInstance()
        config.osmdroidBasePath =
            tilesDirectory // Установка стандартного пути для хранения участков карты в папке 'tiles'
    }

    private fun initMap() { // Настройка карты
        with(binding.map) { // Использование области видимости карты
            setTileSource(TileSourceFactory.MAPNIK) // Установка источника карт на MAPNIK (OpenStreetMap)
            setUseDataConnection(false) // Установка запрета на кэширование карт

            setMultiTouchControls(true) // Установка поддержки множественного нажатия
            isVerticalMapRepetitionEnabled = false // Выключение вертикального повторения карты
            isHorizontalMapRepetitionEnabled = true // Включение горизонтального повторения карты
            setScrollableAreaLimitLatitude(
                MapView.getTileSystem().maxLatitude,
                MapView.getTileSystem().minLatitude,
                0
            ) // Установка пределов прокрутки вдоль широты
            controller.setZoom(Constants.DEFAULT_ZOOM) // Установка стандартного значения приближения карты
            minZoomLevel = Constants.MIN_ZOOM // Установка минимального значения приближения карты
            maxZoomLevel = Constants.MAX_ZOOM // Установка максимального значения приближения карты
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER) // Скрытие стандартных кнопок приближения и удаления карты

            setTileSource(getCurrentTileSource()) // Установка источника карт на текущий
            setUseDataConnection((getCurrentMapMode() == SharedPreferencesConstants.MAP_MODE_ONLINE)) // Установка разрешения на кэширование карт в зависимости от текущего режима карты

            setOnTouchListener(this@MapFragment) // Установка слушателя нажатия на карту
            addMapListener(this@MapFragment) // Установка слушателя передвижения и приближения карты

            binding.currentZoomLevel.text =
                getString(
                    R.string.current_zoom_level,
                    zoomLevelDouble.toInt(),
                    Constants.MAX_ZOOM.toInt()
                )
            cacheManager = CacheManager(this) // Установка менеджера кэша

            binding.boundingBox.listener =
                this@MapFragment // Установка слушателя изменения размеров выбранного участка карты для загрузки

            viewTreeObserver.addOnGlobalLayoutListener(object :
                OnGlobalLayoutListener { // Установка слушателя изменения макета дочерних элементов
                override fun onGlobalLayout() {
                    updateBoundingBox() // Обновление участка карты для загрузки
                    viewTreeObserver.removeOnGlobalLayoutListener(this) // Удаление слушателя изменения макета дочерних элементов
                }
            })
        }
    }

    private fun initSetTileSourceButton() { // Настройка кнопки выбора источника карт
        binding.setTileSourceButton.setOnClickListener {// Установка слушателя нажатия на кнопку выбора источника карт
            lifecycleScope.launch {// Корутина жизненного цикла
                val dialog =
                    Dialog(requireContext()) // Инициализация диалогового окна выбора источника карт
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE) // Скрытие названия диалогового окна
                dialog.setCancelable(true) // Установка возможности закрытия диалогового окна кнопкой "Назад"
                dialog.setContentView(R.layout.dialog_tile_source) // Установка интерфейса диалогового окна

                val openStreetMapRadioButton =
                    dialog.findViewById<RadioButton>(R.id.open_street_map_radio_button)
                val openTopoRadioButton =
                    dialog.findViewById<RadioButton>(R.id.open_topo_radio_button)

                when (getCurrentTileSourceName()) {
                    SharedPreferencesConstants.MAP_TILE_SOURCE_OpenStreetMap -> {
                        openStreetMapRadioButton.isChecked = true
                    }

                    SharedPreferencesConstants.MAP_TILE_SOURCE_OpenTopo -> {
                        openTopoRadioButton.isChecked = true
                    }
                }

                dialog.setOnDismissListener {// Установка слушателя закрытия диалогового окна
                    if (openStreetMapRadioButton.isChecked) { // Если выбран источник OpenStreetMap
                        with(sharedPreferencesMap.edit()) {// Сохранение источника карт в хранилище примитивных данных карты
                            putString(
                                SharedPreferencesConstants.MAP_TILE_SOURCE,
                                SharedPreferencesConstants.MAP_TILE_SOURCE_OpenStreetMap
                            )
                            apply()
                        }
                    } else if (openTopoRadioButton.isChecked) { // Если выбран источник OpenTopo
                        with(sharedPreferencesMap.edit()) { // Сохранение источника карт в хранилище примитивных данных карты
                            putString(
                                SharedPreferencesConstants.MAP_TILE_SOURCE,
                                SharedPreferencesConstants.MAP_TILE_SOURCE_OpenTopo
                            )
                            apply()
                        }
                    }
                }

                dialog.show() // Отображение диалогового окна выбора источника карт
            }
        }
    }

    private fun initMapSettingsButton() { // Настройка карты
        binding.mapSettingsButton.setOnClickListener {
            lifecycleScope.launch {// Корутина жизненного цикла
                val dialog =
                    Dialog(requireContext()) // Инициализация диалогового окна настроек карты
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCancelable(true)
                dialog.setContentView(R.layout.dialog_map_settings)

                val offlineModeCheckBox = dialog.findViewById<CheckBox>(R.id.offline_mode_check_box)
                offlineModeCheckBox.isChecked =
                    (getCurrentMapMode() == SharedPreferencesConstants.MAP_MODE_OFFLINE)
                offlineModeCheckBox.setOnCheckedChangeListener { _, isChecked -> // Переключение режимов карты (Онлайн/Офлайн)
                    with(sharedPreferencesMap.edit()) {// Сохранение режима карты в хранилище примитивных данных карты
                        putString(
                            SharedPreferencesConstants.MAP_MODE,
                            if (isChecked) SharedPreferencesConstants.MAP_MODE_OFFLINE else SharedPreferencesConstants.MAP_MODE_ONLINE
                        )
                        apply()
                    }
                }

                val rulerCheckBox = dialog.findViewById<CheckBox>(R.id.ruler_check_box)
                rulerCheckBox.isChecked =
                    sharedPreferencesMap.getBoolean(SharedPreferencesConstants.MAP_RULER, false)
                rulerCheckBox.setOnCheckedChangeListener { _, isChecked -> // Переключение линейки
                    with(sharedPreferencesMap.edit()) {// Сохранение состояния линейки в хранилище примитивных данных карты
                        putBoolean(
                            SharedPreferencesConstants.MAP_RULER,
                            isChecked
                        )
                        apply()
                    }
                }

                val deleteOfflineMapsButton =
                    dialog.findViewById<Button>(R.id.delete_offline_maps_button)
                deleteOfflineMapsButton.setOnClickListener { // Удаление всех сохранённых карт
                    MainScope().launch {// Корутина для UI
                        val files = tilesDirectory.listFiles()
                        files?.forEach { file -> file.delete() }
                        SqlTileWriter().purgeCache()
                        updateMapTiles() // Обновление карты
                    }
                }

                val deleteGpsDataButton = dialog.findViewById<Button>(R.id.delete_gps_data_button)
                deleteGpsDataButton.setOnClickListener {// Удаление всех данных координат
                    MainScope().launch {// Корутина для UI
                        App.database.gpsDao().deleteAllGps()
                        overlayItems.clear()
                        itemizedIconOverlay.removeAllItems()
                        binding.map.invalidate() // Переотрисовка карты
                    }
                }

                dialog.show() // Отображение диалогового окна настроек карты
            }
        }
    }

    private fun initViewMapSaving() { // Настройка панели для сохранения карты
        with(binding.viewMapSaving) {
            levelSlider.addOnSliderTouchListener(object :
                OnSliderTouchListener { // Установка слушателя изменения значения ползунка уровней приближения
                override fun onStartTrackingTouch(slider: RangeSlider) {
                }

                override fun onStopTrackingTouch(slider: RangeSlider) { // Вызывается при прекращении изменения значений ползунка уровней приближения
                    val values = slider.values // Взятие данных с ползунка
                    minSaveZoom = values[0].toInt()
                    maxSaveZoom = values[1].toInt()
                    updateEstimatedData() // Обновление информации о загрузке участка карты
                }

            })
            cancelButton.setOnClickListener {// Установка слушателя нажатия на кнопку отмены загрузки участка карты
                binding.viewMapSaving.root.visibility = View.GONE
                binding.boundingBox.visibility = View.GONE
                binding.saveMapButton.visibility = View.VISIBLE
                binding.cacheInfoButton.visibility = View.VISIBLE
                binding.zoomInButton.visibility = View.VISIBLE
                binding.zoomOutButton.visibility = View.VISIBLE
                binding.setTileSourceButton.visibility = View.VISIBLE
                binding.mapSettingsButton.visibility = View.VISIBLE
            }
            okButton.setOnClickListener {// Установка слушателя нажатия на кнопку подтверждения загрузки участка карты
                if (getCurrentTileSourceName() == SharedPreferencesConstants.MAP_TILE_SOURCE_OpenStreetMap) {
                    return@setOnClickListener
                }

                try {
                    val path =
                        tilesDirectory.absolutePath + File.separator + boundingBox.toString() + Constants.MAP_DATABASE_EXTENSION // Формирование пути загрузки участка карты
                    val writer = SqliteArchiveTileWriter(path)
                    cacheManager = CacheManager(binding.map, writer)
                    cacheManager.downloadAreaAsync(
                        context,
                        boundingBox,
                        minSaveZoom,
                        maxSaveZoom,
                        object : CacheManagerCallback {
                            override fun onTaskComplete() {
                                Toast.makeText(context, "Download complete!", Toast.LENGTH_LONG)
                                    .show()
                                writer.onDetach()
                                updateMapTiles() // Обновление карты
                            }

                            override fun updateProgress(
                                progress: Int,
                                currentZoomLevel: Int,
                                zoomMin: Int,
                                zoomMax: Int
                            ) {
                            }

                            override fun downloadStarted() {
                                Toast.makeText(context, "Download started!", Toast.LENGTH_LONG)
                                    .show()
                            }

                            override fun setPossibleTilesInArea(total: Int) {
                            }

                            override fun onTaskFailed(errors: Int) {
                                Toast.makeText(
                                    context,
                                    "Download failed!: ERROR: $errors",
                                    Toast.LENGTH_LONG
                                ).show()
                                writer.onDetach()
                            }
                        })
                } catch (e: Exception) {
                    Log.e("DATABASE", e.message, e)
                }
            }
        }
    }

    private fun initSaveMapButton() { // Настройка кнопки открытия панели для сохранения карты
        binding.saveMapButton.setOnClickListener {// Установка слушателя нажатия на кнопку открытия панели для сохранения карты
            if (getCurrentTileSourceName() == SharedPreferencesConstants.MAP_TILE_SOURCE_OpenStreetMap) {
                Toast.makeText(
                    context,
                    "Bulk download from this source is prohibited",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            binding.saveMapButton.visibility = View.GONE
            binding.cacheInfoButton.visibility = View.GONE
            binding.zoomInButton.visibility = View.GONE
            binding.zoomOutButton.visibility = View.GONE
            binding.setTileSourceButton.visibility = View.GONE
            binding.mapSettingsButton.visibility = View.GONE
            binding.viewMapSaving.root.visibility = View.VISIBLE
            binding.boundingBox.visibility = View.VISIBLE
            updateBoundingBox() // Обновление участка карты для загрузки
            updateEstimatedData() // Обновление информации о загрузке участка карты
        }
    }

    private fun initZoomButtons() { // Настройка кнопок приближения и удаления
        val map = binding.map
        binding.zoomInButton.setOnClickListener {
            map.controller.zoomIn()
        }
        binding.zoomOutButton.setOnClickListener {
            map.controller.zoomOut()
        }
    }

    private fun initCacheInfoButton() { // Настройка кнопки вывода диалогового окна с информацией о кэше
        binding.cacheInfoButton.setOnClickListener {
            Toast.makeText(activity, "Calculating...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {// Корутина жизненного цикла
                val dialogBuilder = AlertDialog.Builder(context)
                dialogBuilder
                    .setTitle("Cache Info")
                    .setMessage(
                        "Cache Capacity (bytes): " + cacheManager.cacheCapacity() + "\n" +
                                "Cache Usage (bytes): " + cacheManager.currentCacheUsage()
                    )

                dialogBuilder.setItems(
                    arrayOf<CharSequence>(
                        resources.getString(android.R.string.cancel)
                    )
                ) { dialog, _ -> dialog.dismiss() }

                val dialog = dialogBuilder.create()
                dialog.show()
            }
        }
    }

    private fun getCurrentTileSource(): ITileSource? { // Возврат текущего источника карт
        val tileSourceName = getCurrentTileSourceName() // Возврат названия текущего источника карт
        when (tileSourceName) {
            SharedPreferencesConstants.MAP_TILE_SOURCE_OpenStreetMap -> {
                return TileSourceFactory.MAPNIK
            }

            SharedPreferencesConstants.MAP_TILE_SOURCE_OpenTopo -> {
                return TileSourceFactory.OpenTopo
            }
        }
        return null
    }

    private fun getCurrentTileSourceName(): String { // Возврат названия текущего источника карт
        val tileSourceName = sharedPreferencesMap.getString(
            SharedPreferencesConstants.MAP_TILE_SOURCE,
            ""
        ) ?: ""
        if (tileSourceName.isEmpty()) {
            with(sharedPreferencesMap.edit()) { // Сохранение стандартного источника карт в хранилище примитивных данных карты
                putString(
                    SharedPreferencesConstants.MAP_TILE_SOURCE,
                    SharedPreferencesConstants.MAP_TILE_SOURCE_OpenStreetMap
                )
                apply()
            }
            return SharedPreferencesConstants.MAP_TILE_SOURCE_OpenStreetMap
        }
        return tileSourceName
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?
    ) { // Вызывается при изменении значений хранилища примитивных данных карты
        when (key) {
            SharedPreferencesConstants.MAP_TILE_SOURCE -> {
                binding.map.setTileSource(getCurrentTileSource())
            }

            SharedPreferencesConstants.MAP_MODE -> {
                updateMapTiles() // Обновление карты
            }

            SharedPreferencesConstants.MAP_RULER -> {
                updateMyLocationRuler() // Обновление линейки
            }

            SharedPreferencesConstants.CHAT_SIGN -> {
                val sign =
                    sharedPreferencesChat.getString(SharedPreferencesConstants.CHAT_SIGN, "") ?: ""
                lifecycleScope.launch {// Корутина жизненного цикла
                    val user = App.database.userDao().getUserByUserSign(sign)
                    user?.let {
                        val list =
                            App.database.gpsDao().getLastGpsFromUserByUserId(it.user_id, 0, 1)
                        if (list.isNotEmpty()) {
                            val gps = list.first()
                            activity?.runOnUiThread {
                                with(binding.map.controller) {
                                    val geoPoint = GeoPoint(
                                        (gps.latitude.degrees + (gps.latitude.minutes / 60.0f)).toDouble(),
                                        (gps.longitude.degrees + (gps.longitude.minutes / 60.0f)).toDouble(),
                                        gps.altitude.toDouble()
                                    )
                                    zoomTo(Constants.MY_LOCATION_ZOOM)
                                    setCenter(geoPoint)
                                    animateTo(geoPoint)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onBoundingBoxResized() { // Вызывается при изменении размеров участка карты для загрузки
        updateBoundingBox() // Обновление участка карты для загрузки
        updateEstimatedData() // Обновление информации о загрузке участка карты
    }

    private fun updateBoundingBox() { // Обновление участка карты для загрузки
        val boundingBoxPoints =
            binding.boundingBox.boundingBoxPoints // Запрос левой верхней и правой нижней точек участка в пикселях
        val topLeftPoint = boundingBoxPoints.first
        val bottomRightPoint = boundingBoxPoints.second

        val projection = binding.map.projection
        val projectedTopLeftPoint = projection.fromPixels(
            topLeftPoint.x,
            topLeftPoint.y
        ) // Перевод левой верхней точки в систему координат карты
        val projectedBottomRightPoint =
            projection.fromPixels(
                bottomRightPoint.x,
                bottomRightPoint.y
            ) // Перевод правой нижней точки в систему координат карты
        boundingBox = BoundingBox(
            Math.max(projectedTopLeftPoint.latitude, projectedBottomRightPoint.latitude),
            Math.max(projectedTopLeftPoint.longitude, projectedBottomRightPoint.longitude),
            Math.min(projectedTopLeftPoint.latitude, projectedBottomRightPoint.latitude),
            Math.min(projectedTopLeftPoint.longitude, projectedBottomRightPoint.longitude),
        ) // Установка участка карты для загрузки
    }

    private fun getCurrentMapMode(): String { // Возврат текущего режима карты
        val mapMode = sharedPreferencesMap.getString(SharedPreferencesConstants.MAP_MODE, "") ?: ""
        if (mapMode.isEmpty()) {
            with(sharedPreferencesMap.edit()) {// Сохранение стандартного режима карты в хранилище примитивных данных карты
                putString(
                    SharedPreferencesConstants.MAP_MODE,
                    SharedPreferencesConstants.MAP_MODE_ONLINE
                )
                apply()
            }
            return SharedPreferencesConstants.MAP_MODE_ONLINE
        }
        return mapMode
    }

    private fun updateMapTiles() { // Обновление карты
        val map = binding.map
        val tileProvider = MapTileProviderBasic(context)
        tileProvider.tileSource = getCurrentTileSource()
        tileProvider.setUseDataConnection(getCurrentMapMode() == SharedPreferencesConstants.MAP_MODE_ONLINE)
        map.tileProvider = tileProvider
    }

    private fun initMapOverlays() { // Настройка оверлея карты
        val map = binding.map

        myLocationOverlay = MyLocationNewOverlay(
            gpsMyLocationProvider,
            map
        ) // Инициализация моей позиции на карте
        myLocationOverlay.enableMyLocation() // Включение обновления моей позиции на карте
        myLocationOverlay.runOnFirstFix {
            activity?.runOnUiThread {
                with(map.controller) {
                    val location = myLocationOverlay.myLocation
                    location?.let {
                        val geoPoint = GeoPoint(it)
                        zoomTo(Constants.MY_LOCATION_ZOOM)
                        setCenter(geoPoint)
                        animateTo(geoPoint)
                        updateMyLocationRuler() // Обновление линейки
                    }
                }
            }
        }
        val directionIcon = BitmapFactory.decodeResource(
            resources,
            org.osmdroid.library.R.drawable.twotone_navigation_black_48
        )
        myLocationOverlay.setDirectionIcon(directionIcon)

        myLocationRuler = Polyline(map)
        with(myLocationRuler.paint) {
            pathEffect = DashPathEffect(floatArrayOf(10.0f, 20.0f), 0.0f)
            color = Color.RED
        }

        overlayMarker = ContextCompat.getDrawable(
            App.self.applicationContext,
            org.osmdroid.library.R.drawable.marker_default
        ) // Установка стандартной иконки для отображения поверх точек координат

        lifecycleScope.launch {// Корутина жизненного цикла
            App.database.gpsDao().getLastGpsOfAllUsers()
                .forEach { gps -> // Запрос координат всех пользователей
                    App.database.userDao().getUserByUserId(gps.user_id)
                        ?.let { user -> // Запрос пользователя, которому принадлежат координаты
                            val overlayItem = gpsToOverlayItem(
                                gps,
                                user.user_sign
                            ) // Создание точки из данных координат
                            overlayItem.setMarker(overlayMarker) // Установка маркера точки на стандартную иконку
                            overlayItems.add(overlayItem) // Добавление точки в список точек на карте
                        }
                }
            itemizedIconOverlay = ItemizedIconOverlay(
                overlayItems,
                object : ItemizedIconOverlay.OnItemGestureListener<OverlayItem> {
                    override fun onItemSingleTapUp(index: Int, item: OverlayItem): Boolean {
                        Toast.makeText(
                            App.self.applicationContext,
                            item.title,
                            Toast.LENGTH_SHORT
                        )
                            .show()
                        return true
                    }

                    override fun onItemLongPress(index: Int, item: OverlayItem): Boolean {
                        Toast.makeText(
                            App.self.applicationContext,
                            item.snippet,
                            Toast.LENGTH_SHORT
                        )
                            .show()
                        return true
                    }
                },
                App.self.applicationContext
            ) // Установка обёртки над списком точек на карте

            with(binding.map.overlays) {// Использование области видимости списка графических элементов карты
                clear() // Удаление всех графических элементов поверх карты
                add(CopyrightOverlay(context)) // Добавление надписи авторских прав
                add(myLocationOverlay) // Добавление моей позиции
                add(myLocationRuler) // Добавление линейки
                add(itemizedIconOverlay) // Добавление обёрки над списком точек на карте для их отображения
            }
        }
    }

    override fun onTouch(
        view: View?,
        event: MotionEvent
    ): Boolean { // Вызывается при прикосновении к карте
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                updateBoundingBox()
                updateEstimatedData()
            }
        }
        return false // Возврат статуса того, было ли касание в полной мере обработано в этой функции
    }

    override fun onScroll(event: ScrollEvent?): Boolean { // Вызывается при передвижении карты
        updateMyLocationRuler() // Обновление линейки
        return true
    }

    override fun onZoom(event: ZoomEvent?): Boolean { // Вызывается при приближении карты
        binding.currentZoomLevel.text =
            getString(
                R.string.current_zoom_level,
                event?.zoomLevel?.toInt(),
                Constants.MAX_ZOOM.toInt()
            ) // Отображение текущего уровня приближения
        return true
    }

    override fun onBluetoothInfoReceived(info: String) { // Вызывается для оповещения о новом статусе подключения
    }

    override fun onBluetoothMessageReceived(variant: DeviceCommandsManager.Message.Variant) { // Вызывается для оповещения о новом входящем сообщении
        when (variant) {
            DeviceCommandsManager.Message.Variant.TEXT -> {}
            DeviceCommandsManager.Message.Variant.GPS -> { // Если сообщение является сообщением о координатах
                lifecycleScope.launch {// Корутина жизненного цикла
                    val sign =
                        DeviceCommandsManager.Message.gps.from // Позывной отправителя полученных координат
                    val oldOverlayItem =
                        overlayItems.findLast { it.title == sign } // Запрос последней точки, принадлежащей пользователю с данным позывным, из списка точек
                    val user = App.database.userDao()
                        .getUserByUserSign(sign) // Запрос пользователя из базы данных с позывным отправителя
                    user?.let {// Если пользователь с позывным отправителя существует в базе данных
                        val gps =
                            App.database.gpsDao().getLastGpsFromUserByUserId(
                                user.user_id,
                                0,
                                1
                            ) // Запрос последних координат пользователя
                        gps.forEach {// Проверка того, существуют ли в базе данных записи о координатах пользователя
                            val newOverlayItem = gpsToOverlayItem(
                                it,
                                user.user_sign
                            ) // Инициализация новой точки последних координат пользователя
                            newOverlayItem.setMarker(overlayMarker) // Установка маркера точки на стандартную иконку
                            overlayItems.add(newOverlayItem) // Добавление точки в список точек на карте
                            itemizedIconOverlay.addItem(newOverlayItem) // Добавление точки в обёртку над списком точек на карте

                            overlayItems.remove(oldOverlayItem) // Удаление старой точки из списка точек на карте
                            itemizedIconOverlay.removeItem(oldOverlayItem) // Удаление старой точки в обёртку над списком точек на карте

                            binding.map.invalidate() // Переотрисовка карты
                        }
                    }
                }
            }
        }
    }

    private fun gpsToOverlayItem(
        gps: Gps,
        sign: String
    ): OverlayItem { // Создание точки для отрисовка из данных координат и позывного
        return OverlayItem(
            sign,
            gps.gps_message,
            GeoPoint(
                (gps.latitude.degrees + (gps.latitude.minutes / 60.0f)).toDouble(),
                (gps.longitude.degrees + (gps.longitude.minutes / 60.0f)).toDouble(),
                (gps.altitude.toDouble())
            )
        )
    }

    private fun updateMyLocationRuler() { // Обновление линейки
        if (sharedPreferencesMap.getBoolean(SharedPreferencesConstants.MAP_RULER, false)) {
            myLocationOverlay.myLocation?.let {
                myLocationRuler.setPoints(
                    listOf(
                        GeoPoint(binding.map.mapCenter),
                        it
                    )
                ) // Установка позиций линейки

                val length = myLocationRuler.distance
                binding.rulerLength.text = if (length > 1000) getString(
                    R.string.map_ruler_length_km,
                    length / 1000.0
                ) else getString(
                    R.string.map_ruler_length_m, length
                ) // Установка расстояния
                binding.rulerLength.visibility = View.VISIBLE
            }
        } else { // Сброс линейки
            myLocationRuler.setPoints(listOf())
            binding.rulerLength.visibility = View.GONE
        }
        binding.map.invalidate() // Переотрисовка карты
    }

    override fun onLocationChanged(location: Location) {
        updateMyLocationRuler()
    }
}