package com.datenote.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.local.ScheduleTypeWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.ui.components.ReorderableItem
import com.datenote.app.ui.components.moveItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class ScheduleTypesState(
    val types: List<ScheduleTypeWithSteps> = emptyList(),
)

class ScheduleTypesViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleTypesState())
    val state: StateFlow<ScheduleTypesState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeScheduleTypes().collect { types -> _state.value = ScheduleTypesState(types) }
        }
    }

    fun delete(id: Long, onFinished: () -> Unit) {
        viewModelScope.launch {
            repository.deleteScheduleType(id)
            onFinished()
        }
    }

    class Factory(private val repository: ScheduleRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleTypesViewModel(repository) as T
    }
}

data class ScheduleTypeEditorState(
    val isLoading: Boolean = true,
    val id: Long = 0,
    val name: String = "",
    val steps: List<ScheduleTypeDraftStep> = emptyList(),
    val error: ScheduleTypeEditorError? = null,
)

data class ScheduleTypeDraftStep(val id: Long, val title: String) : ReorderableItem {
    override val stableId: Long
        get() = id
}

enum class ScheduleTypeEditorError {
    NAME_REQUIRED,
    NAME_TOO_LONG,
    STEP_REQUIRED,
    DUPLICATE_NAME,
}

class ScheduleTypeEditorViewModel(
    private val repository: ScheduleRepository,
    private val typeId: Long?,
) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleTypeEditorState())
    val state: StateFlow<ScheduleTypeEditorState> = _state.asStateFlow()

    // Negative ids keep unsaved rows distinct from database ids while dragging.
    private var nextLocalStepId = -1L

    init {
        viewModelScope.launch {
            val type = typeId?.let { repository.getScheduleType(it) }
            _state.value = ScheduleTypeEditorState(
                isLoading = false,
                id = type?.type?.id ?: 0L,
                name = type?.type?.name.orEmpty(),
                steps = type?.orderedSteps?.map { ScheduleTypeDraftStep(it.id, it.title) }.orEmpty(),
            )
        }
    }

    fun setName(value: String) = update { copy(name = value, error = null) }

    fun setStep(index: Int, value: String) = update {
        copy(steps = steps.mapIndexed { current, old -> if (current == index) old.copy(title = value) else old }, error = null)
    }

    fun addStep() = update { copy(steps = steps + ScheduleTypeDraftStep(nextLocalStepId--, ""), error = null) }

    fun removeStep(index: Int) = update {
        copy(steps = steps.filterIndexed { current, _ -> current != index }, error = null)
    }

    fun moveStep(fromIndex: Int, toIndex: Int) = update {
        copy(steps = steps.moveItem(fromIndex, toIndex), error = null)
    }

    fun save(onSaved: () -> Unit) {
        val current = _state.value
        val name = current.name.trim()
        val steps = current.steps.map { it.title.trim() }
        val error = when {
            name.isEmpty() -> ScheduleTypeEditorError.NAME_REQUIRED
            name.codePointCount(0, name.length) > 20 -> ScheduleTypeEditorError.NAME_TOO_LONG
            steps.any(String::isEmpty) -> ScheduleTypeEditorError.STEP_REQUIRED
            else -> null
        }
        if (error != null) {
            _state.value = current.copy(error = error)
            return
        }
        viewModelScope.launch {
            val result = runCatching {
                repository.saveScheduleType(current.id, name, steps)
            }
            if (result.isSuccess) onSaved()
            else _state.value = _state.value.copy(error = ScheduleTypeEditorError.DUPLICATE_NAME)
        }
    }

    private fun update(transform: ScheduleTypeEditorState.() -> ScheduleTypeEditorState) {
        _state.value = _state.value.transform()
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val typeId: Long?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleTypeEditorViewModel(repository, typeId) as T
    }
}
