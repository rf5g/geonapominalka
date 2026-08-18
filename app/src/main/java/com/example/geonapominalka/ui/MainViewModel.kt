package com.example.geonapominalka.ui

import androidx.lifecycle.*
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.data.ReminderRepository
import com.example.geonapominalka.data.SettingsRepository
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: ReminderRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val activeReminders: LiveData<List<Reminder>> = repository.observeActive().asLiveData()
    val mapType: LiveData<Int> = settingsRepository.mapType.asLiveData()

    fun deleteReminder(reminder: Reminder) = viewModelScope.launch {
        repository.delete(reminder)
    }

    fun setMapType(type: Int) = viewModelScope.launch {
        settingsRepository.setMapType(type)
    }

    class Factory(
        private val repository: ReminderRepository,
        private val settingsRepository: SettingsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(repository, settingsRepository) as T
        }
    }
}
