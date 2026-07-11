package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option

internal fun positiveInt(path: String?, value: Int): Int {
    require(value > 0) { "$path must be greater than 0" }
    return value
}

internal fun nonNegativeInt(path: String?, value: Int): Int {
    require(value >= 0) { "$path must not be negative" }
    return value
}

private var validationInitialized = false
private var revertingInvalidUpdate = false
private val positiveOptions by lazy { listOf(
    CoreConfig.LAZY_TICKER_SECONDS,
    EffectConfig.EFFECT_DURATION_SECONDS,
    SelectionConfig.SELECTION_MAX_POINTS,
    SelectionConfig.SELECTION_MIN_POINTS,
    SelectionConfig.SELECTION_DISPLAY_LINE_STEP,
    SelectionConfig.SELECTION_DISPLAY_PILLAR_STEP
) }
private val nonNegativeOptions by lazy { listOf(
    TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS,
    EntryExitConfig.ENTRY_EXIT_REGION_DELAY_SECONDS,
    PermissionConfig.PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS,
    PermissionConfig.PERMISSION_FLY_DISABLE_FALL_IMMUNITY_SECONDS
) }

fun initializeConfigValidation() {
    if (validationInitialized) return
    validationInitialized = true

    positiveOptions.forEach { validateOnChange(it, ::positiveInt) }
    nonNegativeOptions.forEach { validateOnChange(it, ::nonNegativeInt) }

    listOf(
        CoreConfig.LAZY_TICKER_SECONDS,
        EffectConfig.EFFECT_DURATION_SECONDS,
        SelectionConfig.SELECTION_MAX_POINTS,
        SelectionConfig.SELECTION_MIN_POINTS
    ).forEach { validateRelationsOnChange(it) }

    validateCurrentConfig()
}

private fun validateCurrentConfig() {
    positiveOptions.forEach { positiveInt(it.key, it.value) }
    nonNegativeOptions.forEach { nonNegativeInt(it.key, it.value) }
    validateCurrentRelations()
}

private fun validateOnChange(option: Option<Int>, validator: (String?, Int) -> Int) {
    option.changeEvents.register { changed, oldValue, _ ->
        rollbackInvalidUpdate(changed, oldValue) { validator(changed.key, changed.value) }
    }
}

private fun validateRelationsOnChange(option: Option<Int>) {
    option.changeEvents.register { changed, oldValue, _ ->
        rollbackInvalidUpdate(changed, oldValue) { validateCurrentRelations() }
    }
}

private fun rollbackInvalidUpdate(option: Option<Int>, oldValue: Int?, validate: () -> Unit) {
    if (revertingInvalidUpdate) return
    try {
        validate()
    } catch (error: IllegalArgumentException) {
        revertingInvalidUpdate = true
        try {
            option.setValue(oldValue)
        } finally {
            revertingInvalidUpdate = false
        }
        throw error
    }
}

private fun validateCurrentRelations() = validateConfigRelations(
    SelectionConfig.SELECTION_MIN_POINTS.value,
    SelectionConfig.SELECTION_MAX_POINTS.value,
    EffectConfig.EFFECT_DURATION_SECONDS.value,
    CoreConfig.LAZY_TICKER_SECONDS.value
)

internal fun validateConfigRelations(minPoints: Int, maxPoints: Int, effectSeconds: Int, lazySeconds: Int) {
    require(minPoints <= maxPoints) {
        "core.selection.min_points must not exceed core.selection.max_points"
    }
    require(effectSeconds > lazySeconds) {
        "core.effect.duration_seconds must be greater than core.lazy_ticker_seconds"
    }
}
