package com.imyvm.iwg.infra.config

import com.imyvm.hoki.config.Option
import com.imyvm.iwg.domain.component.requireTeleportFallbackSearchRadius

internal fun positiveInt(path: String?, value: Int): Int {
    require(value > 0) { "$path must be greater than 0" }
    return value
}

internal fun nonNegativeInt(path: String?, value: Int): Int {
    require(value >= 0) { "$path must not be negative" }
    return value
}

internal fun finiteNonNegativeDouble(path: String?, value: Double): Double {
    require(value.isFinite() && value >= 0.0) { "$path must be finite and non-negative" }
    return value
}

internal fun finiteAspectRatio(path: String?, value: Double): Double {
    require(value.isFinite() && value > 0.0 && value <= 1.0) {
        "$path must be finite and in (0.0, 1.0]"
    }
    return value
}

private var validationInitialized = false
private var revertingInvalidUpdate = false
private val positiveOptions by lazy { listOf(
    CoreConfig.LAZY_TICKER_SECONDS,
    SelectionConfig.SELECTION_MAX_POINTS,
    SelectionConfig.SELECTION_MIN_POINTS,
    SelectionConfig.SELECTION_DISPLAY_LINE_STEP,
    SelectionConfig.SELECTION_DISPLAY_PILLAR_STEP
) }
@Suppress("DEPRECATION")
private val nonNegativeOptions by lazy { listOf(
    EntryExitConfig.ENTRY_EXIT_REGION_DELAY_SECONDS,
    PermissionConfig.PERMISSION_FLY_DISABLE_COUNTDOWN_SECONDS,
    PermissionConfig.PERMISSION_FLY_DISABLE_FALL_IMMUNITY_SECONDS
) }
private val nonNegativeDoubleOptions by lazy { listOf(
    GeoConfig.MIN_RECTANGLE_AREA,
    GeoConfig.MIN_SIDE_LENGTH,
    GeoConfig.MIN_CIRCLE_RADIUS,
    GeoConfig.MIN_POLYGON_AREA,
    GeoConfig.MIN_POLYGON_SPAN,
    GeoConfig.MIN_EDGE_LENGTH
) }

fun initializeConfigValidation() {
    if (validationInitialized) return
    validationInitialized = true

    positiveOptions.forEach { validateOnChange(it, ::positiveInt) }
    nonNegativeOptions.forEach { validateOnChange(it, ::nonNegativeInt) }
    nonNegativeDoubleOptions.forEach { validateDoubleOnChange(it, ::finiteNonNegativeDouble) }
    validateDoubleOnChange(GeoConfig.MIN_ASPECT_RATIO, ::finiteAspectRatio)
    validateOnChange(EffectConfig.EFFECT_DURATION_SECONDS) { _, seconds ->
        effectDurationTicks(seconds)
        seconds
    }
    validateOnChange(TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS) { _, value ->
        requireTeleportFallbackSearchRadius(value)
    }

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
    nonNegativeDoubleOptions.forEach { finiteNonNegativeDouble(it.key, it.value) }
    finiteAspectRatio(GeoConfig.MIN_ASPECT_RATIO.key, GeoConfig.MIN_ASPECT_RATIO.value)
    effectDurationTicks(EffectConfig.EFFECT_DURATION_SECONDS.value)
    requireTeleportFallbackSearchRadius(
        TeleportConfig.TELEPORT_POINT_FALLBACK_SEARCH_RADIUS.value
    )
    validateCurrentRelations()
}

private fun validateOnChange(option: Option<Int>, validator: (String?, Int) -> Int) {
    option.changeEvents.register { changed, oldValue, _ ->
        rollbackInvalidUpdate(changed, oldValue) { validator(changed.key, changed.value) }
    }
}

private fun validateDoubleOnChange(option: Option<Double>, validator: (String?, Double) -> Double) {
    option.changeEvents.register { changed, oldValue, _ ->
        rollbackInvalidDoubleUpdate(changed, oldValue) { validator(changed.key, changed.value) }
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

private fun rollbackInvalidDoubleUpdate(option: Option<Double>, oldValue: Double?, validate: () -> Unit) {
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
