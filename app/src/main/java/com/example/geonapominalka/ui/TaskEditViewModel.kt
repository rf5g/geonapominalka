package com.example.geonapominalka.ui

import androidx.lifecycle.*
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.data.ReminderRepository
import kotlinx.coroutines.launch

class TaskEditViewModel(private val repository: ReminderRepository) : ViewModel() {

    private val _reminder = MutableLiveData<Reminder?>()
    val reminder: LiveData<Reminder?> = _reminder

    private val _saved = MutableLiveData<Boolean>()
    val saved: LiveData<Boolean> = _saved

    fun load(id: Long) = viewModelScope.launch {
        _reminder.value = repository.getById(id)
    }

    fun setInitialCoordinates(lat: Double, lng: Double) {
        _reminder.value = Reminder(latitude = lat, longitude = lng, name = "")
    }

    /** Обновляет координаты после выбора новой точки на карте ("Выбрать на карте", п.1.3). */
    fun updateCoordinates(lat: Double, lng: Double) {
        val current = _reminder.value ?: return
        _reminder.value = current.copy(latitude = lat, longitude = lng)
    }

    fun save(name: String, description: String?, radius: Int) = viewModelScope.launch {
        val current = _reminder.value ?: return@launch
        if (name.isBlank()) return@launch

        if (current.id == 0L) {
            repository.add(current.copy(name = name, description = description, radius = radius))
        } else {
            repository.update(current.copy(name = name, description = description, radius = radius))
        }
        _saved.value = true
    }

    fun delete() = viewModelScope.launch {
        _reminder.value?.let { if (it.id != 0L) repository.delete(it) }
        _saved.value = true
    }

    class Factory(private val repository: ReminderRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskEditViewModel(repository) as T
    }
}
