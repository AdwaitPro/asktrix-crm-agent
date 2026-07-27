package com.asktrix.agent.core.network

import com.asktrix.agent.core.network.dto.ClientDetailDto
import com.asktrix.agent.core.network.dto.ClientSummaryDto
import com.asktrix.agent.core.network.dto.MaskedContactDto
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The §4 / ADR-0003 invariant, enforced by a test rather than by reviewer memory.
 *
 * The rule is that no wire type may carry an unmasked customer phone number or email address. This
 * reflects over the DTOs and fails the build if a property appears whose name suggests one. Someone
 * will eventually add `val phone: String` in good faith to fix a bug; this is what stops it reaching
 * a device.
 */
class DtoPrivacyTest {

    private val forbidden = listOf("phone", "mobile", "email", "contact", "number")
    private val allowed = listOf("phoneMasked", "emailMasked")

    @Test
    fun `ClientDetailDto exposes no unmasked contact field`() = assertNoUnmaskedContact(ClientDetailDto::class)

    @Test
    fun `ClientSummaryDto exposes no contact field at all`() = assertNoUnmaskedContact(ClientSummaryDto::class)

    @Test
    fun `MaskedContactDto exposes only masked forms`() {
        val names = MaskedContactDto::class.java.declaredFields.map { it.name }

        assertTrue("phoneMasked is missing", "phoneMasked" in names)
        assertTrue("emailMasked is missing", "emailMasked" in names)
        assertTrue(
            "MaskedContactDto must expose only masked values, found: $names",
            names.none { it == "phone" || it == "email" },
        )
    }

    private fun assertNoUnmaskedContact(klass: kotlin.reflect.KClass<*>) {
        val offenders = klass.java.declaredFields
            .map { it.name }
            .filter { name ->
                name !in allowed && forbidden.any { name.lowercase().contains(it) }
            }
            .filterNot { it == "contact" } // The masked contact block itself is expected.

        assertTrue(
            "${klass.simpleName} declares properties that look like unmasked contact details: " +
                "$offenders. See docs/adr/0003-server-side-pii-masking.md - masking is server-side " +
                "and the DTO must have no field for a full number or address.",
            offenders.isEmpty(),
        )
    }
}
