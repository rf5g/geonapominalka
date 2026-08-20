package com.example.geonapominalka.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.geonapominalka.databinding.ActivityMapPickerBinding
import com.example.geonapominalka.util.Constants
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

/**
 * Отдельный экран карты для кнопки "Выбрать на карте" (п.1.3, 4 ТЗ).
 * Пользователь тапает по карте, точка отмечается маркером,
 * подтверждение возвращает координаты вызывающему экрану.
 */
class MapPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMapPickerBinding
    private lateinit var map: MapView
    private var pickedLatLng: GeoPoint? = null
    private var pickedMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        map = binding.mapView
        map.setTileSource(com.example.geonapominalka.util.TileSources.cartoLight)
        map.setMultiTouchControls(true)

        val initialLat = intent.getDoubleExtra(Constants.EXTRA_INITIAL_LAT, 0.0)
        val initialLng = intent.getDoubleExtra(Constants.EXTRA_INITIAL_LNG, 0.0)
        val initial = GeoPoint(initialLat, initialLng)
        placeMarker(initial)
        map.controller.setZoom(15.0)
        map.controller.setCenter(initial)

        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p ?: return false
                placeMarker(p)
                return true
            }
            override fun longPressHelper(p: GeoPoint?) = false
        }
        map.overlays.add(MapEventsOverlay(mapEventsReceiver))

        binding.btnConfirm.setOnClickListener {
            val point = pickedLatLng
            if (point != null) {
                setResult(RESULT_OK, Intent().apply {
                    putExtra(Constants.EXTRA_PICKED_LAT, point.latitude)
                    putExtra(Constants.EXTRA_PICKED_LNG, point.longitude)
                })
            }
            finish()
        }
    }

    private fun placeMarker(point: GeoPoint) {
        pickedMarker?.let { map.overlays.remove(it) }
        val marker = Marker(map).apply {
            position = point
            icon = androidx.core.content.ContextCompat.getDrawable(this@MapPickerActivity, com.example.geonapominalka.R.drawable.ic_marker_pin)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        map.overlays.add(marker)
        pickedMarker = marker
        pickedLatLng = point
        binding.btnConfirm.isEnabled = true
        map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
