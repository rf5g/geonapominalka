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

    // Пользователь мог сам проскроллить вверх, чтобы почитать более старые записи —
    // в этом случае новые записи НЕ должны дёргать список вниз (иначе читать лог
    // на лету невозможно). Автопрокрутка работает только если и так были у самого низа.
    private var userIsNearBottom = true

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

        binding.recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val itemCount = adapter.itemCount
                // "Около низа" — последняя видимая позиция в пределах пары строк от конца списка
                userIsNearBottom = itemCount == 0 || lastVisible >= itemCount - 2
            }
        })

        binding.fabClear.setOnClickListener { AppLogger.clear() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppLogger.entries.collect { entries ->
                    val wasNearBottom = userIsNearBottom
                    adapter.submitList(entries)
                    binding.emptyView.visibility = if (entries.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                    if (entries.isNotEmpty() && wasNearBottom) {
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
