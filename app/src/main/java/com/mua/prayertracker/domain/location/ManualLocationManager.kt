package com.mua.prayertracker.domain.location

import android.content.Context
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Manual location support, with every step in one dedicated place:
 *
 *  Step 1 - Model + validation: [GeoPoint] is the single coordinate type used by
 *           the app; it rejects NaN/Infinity and out-of-range coordinates.
 *  Step 2 - Persistence: [ManualLocationStorage] saves/loads/clears the manually
 *           set location in SharedPreferences, surviving app restarts. Corrupted
 *           or out-of-range stored values are treated as "not set" and wiped.
 *  Step 3 - Resolution: [resolveLocation] picks the effective location, with a
 *           valid manual location always taking precedence over the device one,
 *           and tags the result with its [LocationSource] so the UI can render
 *           whether the location was set manually or not.
 *  Step 4 - Distance: [distanceBetweenMeters] computes the great-circle
 *           (haversine) distance between the manually set location and the
 *           current device location; [formatDistance] renders it for the UI.
 */

/**
 * Step 1: A latitude/longitude pair in decimal degrees.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

    /** Stable, locale-independent display form (e.g. "23.7800, 90.4000"). */
    fun formatted(): String =
        String.format(Locale.US, "%.4f, %.4f", latitude, longitude)

    companion object {
        /**
         * Parse user input into a valid point.
         * Returns null for non-numeric, non-finite, or out-of-range values.
         */
        fun parse(latitudeText: String, longitudeText: String): GeoPoint? {
            val latitude = latitudeText.trim().toDoubleOrNull() ?: return null
            val longitude = longitudeText.trim().toDoubleOrNull() ?: return null
            return GeoPoint(latitude, longitude).takeIf { it.isValid }
        }
    }
}

/**
 * Where the effective location came from.
 */
enum class LocationSource {
    DEVICE,
    MANUAL
}

/**
 * The location actually used by the app, tagged with its origin.
 */
data class ResolvedLocation(
    val point: GeoPoint,
    val source: LocationSource
) {
    val isManual: Boolean get() = source == LocationSource.MANUAL
}

/**
 * Step 2: Persist the manually set location across app restarts.
 * Values are stored as strings to avoid float precision loss.
 */
class ManualLocationStorage(context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Load the persisted manual location.
     * Returns null when nothing is set; corrupted or invalid entries are
     * cleared and also reported as null.
     */
    fun load(): GeoPoint? {
        val latitude = preferences.getString(KEY_LATITUDE, null)?.toDoubleOrNull()
        val longitude = preferences.getString(KEY_LONGITUDE, null)?.toDoubleOrNull()
        if (latitude == null || longitude == null) {
            if (preferences.contains(KEY_LATITUDE) || preferences.contains(KEY_LONGITUDE)) {
                clear()
            }
            return null
        }

        val point = GeoPoint(latitude, longitude)
        if (!point.isValid) {
            clear()
            return null
        }
        return point
    }

    /**
     * Persist a manual location. Invalid points are rejected.
     * @return true when the point was saved.
     */
    fun save(point: GeoPoint): Boolean {
        if (!point.isValid) return false
        preferences.edit()
            .putString(KEY_LATITUDE, point.latitude.toString())
            .putString(KEY_LONGITUDE, point.longitude.toString())
            .apply()
        return true
    }

    /** Remove the manual location so the device location is used again. */
    fun clear() {
        preferences.edit()
            .remove(KEY_LATITUDE)
            .remove(KEY_LONGITUDE)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "manual_location"
        const val KEY_LATITUDE = "latitude"
        const val KEY_LONGITUDE = "longitude"
    }
}

/**
 * Step 3: Choose the effective location.
 * A valid manual location always wins over the device location; when neither
 * is available the result is null and callers fall back to placeholders.
 */
fun resolveLocation(manual: GeoPoint?, device: GeoPoint?): ResolvedLocation? = when {
    manual != null && manual.isValid -> ResolvedLocation(manual, LocationSource.MANUAL)
    device != null && device.isValid -> ResolvedLocation(device, LocationSource.DEVICE)
    else -> null
}

private const val EARTH_RADIUS_METERS = 6_371_000.0

/**
 * Step 4a: Great-circle distance between two points using the haversine
 * formula. Returns null when either point is missing or invalid. The
 * intermediate value is clamped to [0, 1] so floating-point rounding near
 * identical or antipodal points can never produce NaN.
 */
fun distanceBetweenMeters(from: GeoPoint?, to: GeoPoint?): Double? {
    if (from == null || to == null || !from.isValid || !to.isValid) return null

    val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
    val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
    val fromLatitude = Math.toRadians(from.latitude)
    val toLatitude = Math.toRadians(to.latitude)

    val a = sin(latitudeDelta / 2.0) * sin(latitudeDelta / 2.0) +
            cos(fromLatitude) * cos(toLatitude) *
            sin(longitudeDelta / 2.0) * sin(longitudeDelta / 2.0)
    val clamped = a.coerceIn(0.0, 1.0)

    return 2.0 * EARTH_RADIUS_METERS * asin(sqrt(clamped))
}

/**
 * Step 4b: Human-readable distance ("850 m", "12.3 km").
 * Returns null for missing, negative, or non-finite input.
 */
fun formatDistance(meters: Double?): String? = when {
    meters == null || !meters.isFinite() || meters < 0.0 -> null
    meters < 1000.0 -> "${meters.roundToInt()} m"
    else -> String.format(Locale.US, "%.1f km", meters / 1000.0)
}
