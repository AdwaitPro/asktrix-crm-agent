package com.asktrix.agent.core.designsystem.component

/**
 * Semantic tone for a status.
 *
 * The design system deliberately does not know about `ProcessStatus` - that type lives in the data
 * layer, and `:core:designsystem` must not depend on it. Callers map their own domain state onto a
 * tone, which keeps this component reusable and the dependency pointing inward.
 */
enum class StatusTone { POSITIVE, WARNING, NEGATIVE, PENDING, NEUTRAL }
