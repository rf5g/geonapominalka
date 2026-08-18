package com.example.geonapominalka.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.geonapominalka.GeoApp
import com.example.geonapominalka.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Обрабатывает нажатия кнопок "Выполнено" / "Отложить" прямо в уведомлении,
 * без открытия приложения (п.1.5, 4 ТЗ).
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(Constants.EXTRA_REMINDER_ID, -1L)
        if (reminderId == -1L) return

        val app = GeoApp.from(context)
        val notificationId = Constants.REMINDER_NOTIFICATION_ID_BASE + reminderId.toInt()

        // goAsync() позволяет завершить suspend-работу до того, как система убьёт ресивер
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (intent.action) {
                    Constants.ACTION_DONE -> {
                        // Задача выполнена: статус done, удаляется из активных.
                        // Если это была последняя активная задача — GeoApp остановит сервис.
                        app.reminderRepository.markDone(reminderId)
                    }
                    Constants.ACTION_SNOOZE -> {
                        // Остаётся активной, но lastNotificationTime уже было выставлено
                        // при показе уведомления — повторно не покажем 10 минут (п.1.5 ТЗ).
                        app.reminderRepository.setZoneState(reminderId, true)
                    }
                }
            } finally {
                NotificationManagerCompat.from(context).cancel(notificationId)
                pendingResult.finish()
            }
        }
    }
}
