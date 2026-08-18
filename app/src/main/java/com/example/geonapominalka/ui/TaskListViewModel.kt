package com.example.geonapominalka.ui

import androidx.lifecycle.*
import com.example.geonapominalka.data.Reminder
import com.example.geonapominalka.data.ReminderRepository
import kotlinx.coroutines.launch

class TaskListViewModel(private val repository: ReminderRepository) : ViewModel() {

    private val _showDone = MutableLiveData(false)

    val activeReminders: LiveData<List<Reminder>> = repository.observeActive().asLiveData()
    val doneReminders: LiveData<List<Reminder>> = repository.observeDone().asLiveData()
    val showDone: LiveData<Boolean> = _showDone

    fun toggleShowDone() {
        _showDone.value = _showDone.value != true
    }

    fun delete(reminder: Reminder) = viewModelScope.launch {
        repository.delete(reminder)
    }

    class Factory(private val repository: ReminderRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = TaskListViewModel(repository) as T
    }
}
