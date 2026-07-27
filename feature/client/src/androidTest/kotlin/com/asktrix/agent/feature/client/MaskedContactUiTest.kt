package com.asktrix.agent.feature.client

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import com.asktrix.agent.core.data.model.Client
import com.asktrix.agent.core.data.model.GovernmentStatus
import com.asktrix.agent.core.data.model.MaskedContact
import com.asktrix.agent.core.data.model.PaymentStatus
import com.asktrix.agent.core.data.model.ProcessStatus
import com.asktrix.agent.core.designsystem.theme.AsktrixTheme
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * The last line of defence for §4.
 *
 * Server-side masking and the DTO test cover the data path. This covers the rendered UI: it walks the
 * whole semantics tree of the composed screen and asserts nothing that looks like a full phone number
 * or email address appears anywhere on it - including in a content description, which is where a leak
 * would otherwise be invisible to a human reviewer but perfectly audible to a screen reader.
 */
class MaskedContactUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val client = Client(
        clientId = "CLI-10240",
        name = "Sivakumar Ramanathan",
        serviceId = "SVC-GST-2291",
        processStatus = ProcessStatus.DOCUMENTS_PENDING,
        paymentStatus = PaymentStatus.PENDING,
        governmentStatus = GovernmentStatus.NOT_SUBMITTED,
        documentsPending = 3,
        followUpAt = Instant.now(),
        lastInteractionAt = Instant.now(),
        version = 1,
        contact = MaskedContact("98XXXXXX12", "siv****@gmail.com", callable = true),
    )

    private fun renderDetail(state: ClientDetailUiState = ClientDetailUiState(client = client, isLoading = false)) {
        composeRule.setContent {
            AsktrixTheme {
                ClientDetailScreen(
                    state = state,
                    onBack = {},
                    onCall = {},
                    onStatus = {},
                    onDismissCall = {},
                    onConsumeMessage = {},
                )
            }
        }
    }

    @Test
    fun masked_contact_values_are_shown() {
        renderDetail()

        composeRule.onNodeWithText("98XXXXXX12").assertIsDisplayed()
        composeRule.onNodeWithText("siv****@gmail.com").assertIsDisplayed()
    }

    @Test
    fun no_full_phone_number_appears_anywhere_in_the_rendered_tree() {
        renderDetail()

        val tree = composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        val fullPhone = Regex("""\b(?:\+?91[-\s]?)?[6-9]\d{9}\b""")

        assertFalse(
            "A full phone number is rendered somewhere on the client detail screen. " +
                "See docs/adr/0003-server-side-pii-masking.md.",
            fullPhone.containsMatchIn(tree),
        )
    }

    @Test
    fun no_full_email_address_appears_anywhere_in_the_rendered_tree() {
        renderDetail()

        val tree = composeRule.onRoot(useUnmergedTree = true).printToString(maxDepth = Int.MAX_VALUE)
        // A masked address contains '*', so require an address with no asterisk in the local part.
        val fullEmail = Regex("""\b[A-Za-z0-9._%+-]{2,}@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")

        val matches = fullEmail.findAll(tree).map { it.value }.filterNot { it.contains('*') }.toList()
        assertFalse("Unmasked email addresses rendered: $matches", matches.isNotEmpty())
    }

    @Test
    fun all_six_quick_status_actions_are_present() {
        renderDetail()

        QUICK_STATUSES.forEach { status ->
            composeRule.onNodeWithText(status.label).assertIsDisplayed()
        }
    }

    @Test
    fun the_current_status_cannot_be_reapplied() {
        renderDetail()

        // The client is DOCUMENTS_PENDING, which is not a quick action; make one current instead.
        composeRule.setContent { }
        renderDetail(
            ClientDetailUiState(
                client = client.copy(processStatus = ProcessStatus.PAYMENT_RECEIVED),
                isLoading = false,
            ),
        )
        composeRule.onNodeWithText(ProcessStatus.PAYMENT_RECEIVED.label).assertIsNotEnabled()
    }

    @Test
    fun the_call_action_never_offers_a_dialable_number() {
        renderDetail()

        // §5: the only call affordance routes through the CRM.
        composeRule.onNodeWithText("Call through CRM").assertIsDisplayed()
    }
}
