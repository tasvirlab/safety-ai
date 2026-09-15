package com.azizbek.safetyai.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.azizbek.safetyai.data.local.NotificationEntity
import com.azizbek.safetyai.data.repository.NotificationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class NotificationsViewModel(repository: NotificationRepository) : ViewModel() {
    val notifications: StateFlow<List<NotificationEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(repository: NotificationRepository) = viewModelFactory {
            initializer { NotificationsViewModel(repository) }
        }
    }
}
