package app.minimaldialer.dialer

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.minimaldialer.calling.domain.AudioRoute
import app.minimaldialer.calling.domain.CallAudio
import app.minimaldialer.calling.domain.CallController
import app.minimaldialer.calling.domain.CallSnapshot
import app.minimaldialer.calling.domain.PlaceCallResult
import app.minimaldialer.contacts.domain.ContactsRepository
import app.minimaldialer.core.designsystem.MinimalDialerTheme
import app.minimaldialer.core.model.Contact
import app.minimaldialer.core.model.ContactNumber
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialerScreenTest {
    @get:Rule val compose = createComposeRule()

    private val placed = mutableListOf<String>()

    private val calls = object : CallController {
        override val calls: StateFlow<List<CallSnapshot>> = MutableStateFlow(emptyList())
        override val audio: StateFlow<CallAudio> = MutableStateFlow(CallAudio())
        override fun placeCall(number: String): PlaceCallResult { placed += number; return PlaceCallResult.Placed }
        override fun answer(callId: String) = Unit
        override fun reject(callId: String) = Unit
        override fun hangUp(callId: String) = Unit
        override fun setOnHold(callId: String, hold: Boolean) = Unit
        override fun setMuted(muted: Boolean) = Unit
        override fun setRoute(route: AudioRoute) = Unit
        override fun playDtmf(callId: String, digit: Char) = Unit
        override fun stopDtmf(callId: String) = Unit
        override fun swap() = Unit
    }

    private val contacts = object : ContactsRepository {
        val list = listOf(Contact(1, "k", "Mohan Anand", listOf(ContactNumber("+919845012345", isPrimary = true))))
        override fun contacts(): Flow<List<Contact>> = flowOf(list)
        override suspend fun findByNumber(number: String): Contact? = null
        override suspend fun setStarred(contactId: Long, starred: Boolean) = true
    }

    private fun setUp(): DialerViewModel {
        val vm = DialerViewModel(contacts, calls)
        compose.setContent {
            MinimalDialerTheme { DialerScreen(vm, PaddingValues(), onOpenSettings = {}) }
        }
        return vm
    }

    @Test fun typingShowsNumberAndCallPlacesIt() {
        setUp()
        listOf("5", "5", "5", "0", "1").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithContentDescription("Number: 55501").assertIsDisplayed()
        compose.onNodeWithContentDescription("Call").performClick()
        compose.runOnIdle { assertEquals(listOf("55501"), placed) }
    }

    @Test fun longPressBackspaceClears() {
        setUp()
        listOf("1", "2", "3").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithContentDescription("Delete").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("Number: ").assertIsDisplayed()
    }

    @Test fun t9MatchSuggestsContact() {
        setUp()
        listOf("6", "6", "4").forEach { compose.onNodeWithContentDescription(it).performClick() }
        compose.onNodeWithContentDescription("Number: 664").assertIsDisplayed()
        compose.waitUntil(2_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Mohan Anand")).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
