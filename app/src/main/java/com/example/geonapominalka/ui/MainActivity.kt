package com.example.geonapominalka.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.R
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.databinding.ActivityMainBinding
import com.example.geonapominalka.util.Constants
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var viewModel: MainViewModel
    private lateinit var myLocationOverlay: MyLocationNewOverlay

    // reminder.id -> Marker, чтобы удалять/сопоставлять при клике
    private val markerByReminderId = HashMap<Long, Marker>()
    private var currentTileSourceIndex = 0

    private val tileSources: List<ITileSource> by lazy { com.example.geonapominalka.util.TileSources.all }

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

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
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

        requestRuntimePermissions()
    }

    private fun setupMap() {
        map = binding.mapView
        map.setTileSource(com.example.geonapominalka.util.TileSources.cartoLight)
        map.setMultiTouchControls(true)
        map.controller.setZoom(14.0)
        map.controller.setCenter(GeoPoint(55.7558, 37.6173)) // старт по умолчанию, сместится к геолокации

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

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)

        viewModel.mapType.observe(this) { type ->
            currentTileSourceIndex = type.coerceIn(0, tileSources.size - 1)
            map.setTileSource(tileSources[currentTileSourceIndex])
        }

        viewModel.activeReminders.observe(this) { reminders -> renderMarkers(reminders) }
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
                R.id.nav_exit -> finishAffinity()
            }
            binding.drawerLayout.closeDrawers()
            true
        }
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

    private fun toggleMapType() {
        currentTileSourceIndex = (currentTileSourceIndex + 1) % tileSources.size
        map.setTileSource(tileSources[currentTileSourceIndex])
        viewModel.setMapType(currentTileSourceIndex)
    }

    private fun searchAddress(query: String) {
        if (query.isBlank()) return
        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(this, Locale.getDefault())
            val results = geocoder.getFromLocationName(query, 1)
            val first = results?.firstOrNull()
            if (first != null) {
                map.controller.animateTo(GeoPoint(first.latitude, first.longitude))
                map.controller.setZoom(15.0)
            } else {
                Toast.makeText(this, R.string.msg_address_not_found, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.msg_address_not_found, Toast.LENGTH_SHORT).show()
        }
    }

    /** п.1.2 ТЗ: долгое нажатие открывает диалог с кнопкой "Напомнить здесь". */
    private fun showCreateReminderDialog(point: GeoPoint) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_create_title)
            .setMessage(getString(R.string.dialog_create_message, point.latitude, point.longitude))
            .setPositiveButton(R.string.action_remind_here) { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, TaskEditActivity::class.java).apply {
                    putExtra(Constants.EXTRA_INITIAL_LAT, point.latitude)
                    putExtra(Constants.EXTRA_INITIAL_LNG, point.longitude)
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.action_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    /** п.1.4 ТЗ: клик по маркеру -> подтверждение удаления. */
    private fun onMarkerClicked(reminderId: Long) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setMessage(R.string.dialog_delete_message)
            .setPositiveButton(R.string.action_yes) { dialog, _ ->
                val reminder = viewModel.activeReminders.value?.find { it.id == reminderId }
                reminder?.let { viewModel.deleteReminder(it) }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_no) { dialog, _ -> dialog.dismiss() }
            .show()
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
            requestRuntimePermissions()
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
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestRuntimePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        requestPermissions.launch(permissions.toTypedArray())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestBackgroundPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestBackgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
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
