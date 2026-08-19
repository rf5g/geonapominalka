package com.example.geonapominalka.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.geonapominalka.databinding.ActivityLogBinding
import com.example.geonapominalka.util.AppLogger
import kotlinx.coroutines.launch

/**
 * Экран "Консоль" — визуальный контроль работы сервиса геолокации в реальном
 * времени: интервал опроса, полученные координаты, входы/выходы из геозон,
 * отправленные уведомления. Обновляется живьём, пока сервис работает.
 */
class LogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLogBinding
    private lateinit var adapter: LogAdapter
    private lateinit var layoutManager: LinearLayoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(com.example.geonapominalka.R.string.title_log)

        adapter = LogAdapter()
        layoutManager = LinearLayoutManager(this)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        binding.fabClear.setOnClickListener { AppLogger.clear() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppLogger.entries.collect { entries ->
                    adapter.submitList(entries)
                    binding.emptyView.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                    if (entries.isNotEmpty()) {
                        binding.recyclerView.scrollToPosition(entries.size - 1)
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
