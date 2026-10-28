package app.minimaldialer.dialer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.minimaldialer.calling.domain.CallController
import app.minimaldialer.calling.domain.PlaceCallResult
import app.minimaldialer.contacts.domain.ContactMatch
import app.minimaldialer.contacts.domain.ContactSearch
import app.minimaldialer.contacts.domain.ContactsRepository
import app.minimaldialer.core.model.PhoneNumbers
import app.minimaldialer.dialer.domain.DialInput
import app.minimaldialer.dialer.domain.DialKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class DialerUiState(
    val input: DialInput = DialInput.EMPTY,
    /** Contact whose number equals what was typed, shown above the number. */
    val exactMatchName: String? = null,
    val suggestions: List<ContactMatch> = emptyList(),
)

sealed interface DialerMessage {
    data object NeedsCallPermission : DialerMessage
    data class CallFailed(val reason: String) : DialerMessage
}

class DialerViewModel(
    contacts: ContactsRepository,
    private val calls: CallController,
) : ViewModel() {

    private val input = MutableStateFlow(DialInput.EMPTY)
    private val _message = MutableStateFlow<DialerMessage?>(null)
    val message: StateFlow<DialerMessage?> = _message

    val state: StateFlow<DialerUiState> = combine(input, contacts.contacts().onStart { emit(emptyList()) }) { typed, all ->
        val text = typed.text
        DialerUiState(
            input = typed,
            exactMatchName = if (text.length >= 3) {
                all.firstOrNull { c -> c.numbers.any { PhoneNumbers.looselyEqual(it.number, text) } }?.displayName
            } else {
                null
            },
            suggestions = if (text.isEmpty()) emptyList() else ContactSearch.search(all, text, limit = 3),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DialerUiState())

    fun press(key: DialKey) = input.update { it.press(key) }
    fun longPress(key: DialKey) = input.update { it.longPress(key) }
    fun backspace() = input.update { it.backspace() }
    fun clear() = input.update { it.clear() }
    fun setNumber(raw: String) = input.update { it.replaceWith(raw) }

    /** Places a call to the typed number, or to [number] (a tapped suggestion). */
    fun call(number: String? = null) {
        val target = number ?: input.value.dialString()
        when (val result = calls.placeCall(target)) {
            PlaceCallResult.Placed -> input.value = DialInput.EMPTY
            PlaceCallResult.MissingPermission -> _message.value = DialerMessage.NeedsCallPermission
            PlaceCallResult.EmptyNumber -> Unit
            is PlaceCallResult.Failed -> _message.value = DialerMessage.CallFailed(result.message)
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private inline fun MutableStateFlow<DialInput>.update(f: (DialInput) -> DialInput) {
        value = f(value)
    }
}
