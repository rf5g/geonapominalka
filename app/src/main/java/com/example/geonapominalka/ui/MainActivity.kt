package com.example.geonapominalka.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.databinding.ActivityMainBinding
import com.example.geonapominalka.util.Constants
import com.example.geonapominalka.util.TileSources
import com.example.geonapominalka.util.NominatimGeocoder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var viewModel: MainViewModel
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    // reminder.id -> Marker, чтобы удалять/сопоставлять при клике
    private val markerByReminderId = HashMap<Long, Marker>()
    private var currentStyle = TileSources.MapStyle.LIGHT

    // Оверлей подписей для гибридного режима (снимок + названия/дороги поверх)
    private var labelsOverlay: TilesOverlay? = null

    // Маркер результата поиска адреса — один на экран, обновляется при новом поиске
    private var searchMarker: Marker? = null

    // Чтобы не пересобрать пользователя на карту повторно после первого раза
    // (например, если он сам куда-то проскроллил карту)
    private var hasCenteredOnUser = false

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            enableMyLocationLayer()
            requestBackgroundPermissionIfNeeded()
        } else {
            Toast.makeText(this, R.string.msg_location_required, Toast.LENGTH_LONG).show()
        }
    }

    private val requestBackgroundPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* фон - опционально, приложение продолжит работать в активном режиме */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = GeoApp.from(this)
        viewModel = ViewModelProvider(
            this,
            MainViewModel.Factory(app.reminderRepository, app.settingsRepository)
        )[MainViewModel::class.java]

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap()
        setupToolbarAndDrawer()
        setupControls()
        startCacheSizeMonitor()

        requestAllPermissionsIfNeeded()
    }

    private fun setupMap() {
        map = binding.mapView
        map.setMultiTouchControls(true)
        map.controller.setZoom(14.0)
        map.controller.setCenter(GeoPoint(55.7558, 37.6173)) // запасной центр, если геопозиция недоступна — сместится на неё сразу, как получим координаты

        // Долгий тап по карте -> создание напоминания (п.1.2 ТЗ)
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p ?: return false
                showCreateReminderDialog(p)
                return true
            }
        }
        map.overlays.add(MapEventsOverlay(mapEventsReceiver))

        // Курсор "моё местоположение" в цвете E65100 вместо стандартного синего —
        // штатные иконки заменены на кастомные точку/стрелку.
        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map).apply {
            val dot = drawableToBitmap(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location_dot)!!)
            val arrow = drawableToBitmap(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location_arrow)!!)
            setPersonIcon(dot)
            setPersonAnchor(0.5f, 0.5f)
            setDirectionIcon(arrow)
            setDirectionAnchor(0.5f, 0.5f)
        }

        viewModel.mapType.observe(this) { index -> applyMapStyle(TileSources.MapStyle.fromIndex(index)) }

        viewModel.activeReminders.observe(this) { reminders -> renderMarkers(reminders) }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) return drawable.bitmap
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Мелкая подпись с размером дискового кэша тайлов — только для отладки/визуального
     * контроля (см. обсуждение с пользователем). Обновляется раз в 5 секунд, пока экран
     * виден; ничего не делает с самим кэшем, только читает его текущий размер. Легко
     * убрать целиком в будущем: эта функция, вызов из onCreate и cacheSizeLabel в layout.
     */
    /**
     * Мелкая подпись с размером дискового кэша тайлов — только для отладки/визуального
     * контроля (см. обсуждение с пользователем). Обновляется раз в 5 секунд, пока экран
     * виден; ничего не делает с самим кэшем, только читает его текущий размер. В консоль
     * (AppLogger) не пишет — там и так важнее геоданные, размер кэша туда не должен
     * попадать регулярно, только сама метка на экране. Легко убрать целиком в будущем:
     * эта функция, вызов из onCreate и cacheSizeLabel в layout.
     */
    private fun startCacheSizeMonitor() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val sizeMb = com.example.geonapominalka.util.TileCacheInfo.currentSizeMb()
                    binding.cacheSizeLabel.text = getString(R.string.cache_size_label, sizeMb)
                    delay(5000)
                }
            }
        }
    }

    private fun setupToolbarAndDrawer() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(Gravity.START)
        }

        binding.navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_task_manager -> startActivity(Intent(this, TaskListActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.nav_log -> startActivity(Intent(this, LogActivity::class.java))
                R.id.nav_exit -> finishAffinity()
                R.id.nav_force_close -> confirmForceClose()
            }
            binding.drawerLayout.closeDrawers()
            true
        }
    }

    /**
     * Полное закрытие приложения (в отличие от обычного "Выхода"): останавливает
     * foreground-сервис геолокации явно, независимо от количества активных задач,
     * и завершает процесс, чтобы приложение не оставалось висеть в фоне, когда
     * это не нужно пользователю.
     */
    private fun confirmForceClose() {
        val hasActiveTasks = !viewModel.activeReminders.value.isNullOrEmpty()
        val message = if (hasActiveTasks) {
            R.string.dialog_force_close_message
        } else {
            R.string.dialog_force_close_message_no_tasks
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton(R.string.action_yes) { dialog, _ ->
                dialog.dismiss()
                forceCloseApp()
            }
            .setNegativeButton(R.string.action_no) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun forceCloseApp() {
        stopService(Intent(this, com.example.geonapominalka.service.LocationForegroundService::class.java))
        androidx.core.app.NotificationManagerCompat.from(this).cancelAll()
        finishAffinity()
        // killProcess гарантирует, что процесс (и все его корутины/сервисы) не останется
        // висеть в фоне после явного запроса пользователя на полное закрытие.
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun setupControls() {
        binding.btnMyLocation.setOnClickListener { moveToMyLocation() }
        binding.btnZoomIn.setOnClickListener { map.controller.zoomIn() }
        binding.btnZoomOut.setOnClickListener { map.controller.zoomOut() }
        binding.btnMapType.setOnClickListener { toggleMapType() }

        binding.searchField.setOnEditorActionListener { _, _, _ ->
            searchAddress(binding.searchField.text.toString())
            true
        }
    }

    /** Переключает стиль карты по кругу и сохраняет выбор в настройках (синхронизировано с SettingsActivity). */
    private fun toggleMapType() {
        val styles = TileSources.MapStyle.entries
        val next = styles[(currentStyle.ordinal + 1) % styles.size]
        applyMapStyle(next)
        viewModel.setMapType(next.ordinal)
    }

    /**
     * Применяет выбранный стиль карты. Для HYBRID отдельно накладывает полупрозрачный
     * слой подписей (labelsOverlay) поверх спутникового снимка — сам спутниковый
     * провайдер (Esri) слоя подписей не даёт, поэтому склеиваем два источника тайлов.
     */
    private fun applyMapStyle(style: TileSources.MapStyle) {
        currentStyle = style
        map.setTileSource(style.tileSource)

        labelsOverlay?.let { map.overlays.remove(it) }
        labelsOverlay = null

        if (style.isHybrid) {
            val provider = MapTileProviderBasic(this, TileSources.labelsOverlay)
            val overlay = TilesOverlay(provider, this).apply { loadingBackgroundColor = android.graphics.Color.TRANSPARENT }
            // Вставляем сразу после базового слоя карты (индекс 0), чтобы подписи были
            // выше снимка, но ниже маркеров и служебных оверлеев (моё местоположение и т.д.)
            map.overlays.add(0, overlay)
            labelsOverlay = overlay
        }
        map.invalidate()
    }

    /**
     * Поиск адреса с приоритетом по текущему местоположению пользователя.
     *
     * ВАЖНО: системный Android Geocoder игнорирует bounding box на многих устройствах
     * (особенно с бэкендом Google Play services) — это известное ограничение самого
     * Android, а не ошибка логики: сервис просто возвращает глобально "самый релевантный"
     * результат, который может оказаться в другой стране. Поэтому поиск идёт через
     * NominatimGeocoder (OpenStreetMap) с параметром bounded=1 — там область СТРОГО
     * ограничивает результаты, а не просто подсказывает.
     */
    private fun searchAddress(query: String) {
        if (query.isBlank()) return

        lifecycleScope.launch {
            val lastLocation = try {
                if (hasFineLocationPermission()) fusedClient.lastLocation.await() else null
            } catch (e: Exception) {
                null
            }

            val result = try {
                NominatimGeocoder.search(query, lastLocation?.latitude, lastLocation?.longitude)
            } catch (e: Exception) {
                null
            }

            if (result != null) {
                val point = GeoPoint(result.latitude, result.longitude)
                map.controller.animateTo(point)
                map.controller.setZoom(16.0)
                showSearchResultMarker(point, result.displayName)
            } else {
                Toast.makeText(this@MainActivity, R.string.msg_address_not_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Булавка результата поиска адреса — отдельная от булавок задач (синий цвет). */
    private fun showSearchResultMarker(point: GeoPoint, title: String) {
        searchMarker?.let { map.overlays.remove(it) }
        val marker = Marker(map).apply {
            position = point
            this.title = title
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_marker_pin_search)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(marker)
        searchMarker = marker
        marker.showInfoWindow()
        map.invalidate()
    }

    /**
     * п.1.2 ТЗ: долгое нажатие открывает диалог с кнопкой "Напомнить здесь".
     * Диалог показывается сразу с координатами (без задержки на сеть), адрес
     * подгружается асинхронно через reverse-геокодинг и дописывается в текст диалога,
     * когда придёт ответ. Если пользователь нажмёт "Напомнить здесь" уже после того,
     * как адрес пришёл — он попадёт в поле "Описание" на следующем экране.
     */
    private fun showCreateReminderDialog(point: GeoPoint) {
        var resolvedAddress: String? = null
        val coordinatesLine = getString(R.string.dialog_create_message, point.latitude, point.longitude)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_create_title)
            .setMessage("$coordinatesLine\n${getString(R.string.msg_resolving_address)}")
            .setPositiveButton(R.string.action_remind_here) { d, _ ->
                d.dismiss()
                val intent = Intent(this, TaskEditActivity::class.java).apply {
                    putExtra(Constants.EXTRA_INITIAL_LAT, point.latitude)
                    putExtra(Constants.EXTRA_INITIAL_LNG, point.longitude)
                    resolvedAddress?.let { putExtra(Constants.EXTRA_INITIAL_ADDRESS, it) }
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.action_cancel) { d, _ -> d.dismiss() }
            .create()
        dialog.show()

        val job = lifecycleScope.launch {
            val address = try {
                NominatimGeocoder.reverse(point.latitude, point.longitude)
            } catch (e: Exception) {
                null
            }
            if (!dialog.isShowing) return@launch
            resolvedAddress = address
            dialog.setMessage(if (address != null) "$coordinatesLine\n$address" else coordinatesLine)
        }
        dialog.setOnDismissListener { job.cancel() }
    }

    /** п.1.4 ТЗ: клик по маркеру -> подтверждение удаления. Показываем название и описание,
     *  чтобы было понятно, какую именно задачу удаляем. */
    private fun onMarkerClicked(reminderId: Long) {
        val reminder = viewModel.activeReminders.value?.find { it.id == reminderId } ?: return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_delete_message)
            .setMessage(buildDeleteConfirmationMessage(reminder.name, reminder.description))
            .setPositiveButton(R.string.action_yes) { dialog, _ ->
                viewModel.deleteReminder(reminder)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_no) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun buildDeleteConfirmationMessage(name: String, description: String?): String = buildString {
        append(getString(R.string.dialog_delete_task_name_line, name))
        if (!description.isNullOrBlank()) {
            append("\n")
            append(getString(R.string.dialog_delete_task_description_line, description))
        }
    }

    private fun renderMarkers(reminders: List<Reminder>) {
        val currentIds = reminders.map { it.id }.toSet()
        val toRemove = markerByReminderId.keys.filter { it !in currentIds }
        toRemove.forEach { id ->
            markerByReminderId[id]?.let { map.overlays.remove(it) }
            markerByReminderId.remove(id)
        }

        for (reminder in reminders) {
            val existing = markerByReminderId[reminder.id]
            if (existing == null) {
                val marker = Marker(map).apply {
                    position = GeoPoint(reminder.latitude, reminder.longitude)
                    title = reminder.name
                    snippet = getString(R.string.marker_radius_snippet, reminder.radius)
                    // Контрастная красная булавка вместо блёклой стандартной иконки —
                    // штатный маркер osmdroid плохо виден на светлых стилях карты.
                    icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_marker_pin)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ -> onMarkerClicked(reminder.id); true }
                }
                map.overlays.add(marker)
                markerByReminderId[reminder.id] = marker
            } else {
                existing.position = GeoPoint(reminder.latitude, reminder.longitude)
                existing.title = reminder.name
            }
        }
        map.invalidate()
    }

    private fun moveToMyLocation() {
        if (!hasFineLocationPermission()) {
            requestAllPermissionsIfNeeded()
            return
        }
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                map.controller.animateTo(GeoPoint(location.latitude, location.longitude))
                map.controller.setZoom(16.0)
            }
        }
    }

    private fun enableMyLocationLayer() {
        if (hasFineLocationPermission() && !map.overlays.contains(myLocationOverlay)) {
            myLocationOverlay.enableMyLocation()
            map.overlays.add(myLocationOverlay)
        }
        centerOnUserLocationIfNeeded()
    }

    /**
     * Старт карты на позиции пользователя вместо дефолтных координат: как только
     * известна последняя геопозиция, один раз перецентровываем карту на неё.
     */
    private fun centerOnUserLocationIfNeeded() {
        if (hasCenteredOnUser || !hasFineLocationPermission()) return
        fusedClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                hasCenteredOnUser = true
                map.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                map.controller.setZoom(16.0)
            }
        }
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBackgroundLocationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * При первом запуске (и при каждом последующем, пока не выдано) объясняем, зачем нужны
     * разрешения, и запрашиваем их по шагам, как требует Android 10+:
     * 1) геолокация переднего плана + уведомления + распознавание активности — одним заходом;
     * 2) фоновая геолокация — ОТДЕЛЬНЫМ системным запросом сразу после (одновременно с
     *    первым запросить нельзя — система молча проигнорирует). Перед этим шагом показываем
     *    отдельный диалог с объяснением, почему это разрешение обязательно, а не опционально:
     *    без него foreground-сервис на Android 14+ перестаёт получать координаты, как только
     *    пользователь уходит с экрана приложения — то есть уведомления о задачах просто
     *    не придут.
     */
    private fun requestAllPermissionsIfNeeded() {
        val foregroundGranted = hasFineLocationPermission() && hasNotificationPermission() && hasActivityRecognitionPermission()
        if (!foregroundGranted) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_permissions_title)
                .setMessage(R.string.dialog_permissions_message)
                .setCancelable(false)
                .setPositiveButton(R.string.action_continue) { dialog, _ ->
                    dialog.dismiss()
                    requestRuntimePermissions()
                }
                .show()
        } else {
            enableMyLocationLayer()
            requestBackgroundPermissionIfNeeded()
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        requestPermissions.launch(permissions.toTypedArray())
    }

    /**
     * Отдельный диалог именно про "Разрешить в любом режиме" — показываем на каждом запуске,
     * пока разрешение не выдано (сознательно, по просьбе — это критично для работы приложения,
     * не косметика). Кнопка "Открыть настройки" — подстраховка для устройств, где системный
     * диалог не предлагает нужный вариант напрямую (так бывает на некоторых прошивках).
     */
    private fun requestBackgroundPermissionIfNeeded() {
        if (hasBackgroundLocationPermission()) return
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_background_location_title)
            .setMessage(R.string.dialog_background_location_message)
            .setCancelable(false)
            .setPositiveButton(R.string.action_grant) { dialog, _ ->
                dialog.dismiss()
                requestBackgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            .setNeutralButton(R.string.action_open_settings) { dialog, _ ->
                dialog.dismiss()
                openAppSettings()
            }
            .setNegativeButton(R.string.action_not_now) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}
