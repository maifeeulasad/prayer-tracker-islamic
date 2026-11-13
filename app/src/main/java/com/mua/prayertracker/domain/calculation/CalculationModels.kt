package com.mua.prayertracker.domain.calculation

import com.mua.prayertracker.domain.model.PrayerType

/**
 * The strategy layer of the prayer-time engine. The three independent
 * dimensions that used to be interleaved in one procedural function —
 * calculation authority, juristic school, and high-latitude handling —
 * are modelled as separate strategies that each contribute to ONE value
 * object, [CalculationParameters]. The engine consumes only that object,
 * so no madhab x method x latitude cross product exists anywhere:
 *
 *   CalculationMethod.parameters()   -> angles / intervals of the authority
 *     .copy(madhab = ..., highLatitudeRule = ..., offsets...)  -> user choices
 *   PrayerTimeEngine.calculate(parameters)                     -> times
 *
 * Adding a new method, madhab or rule is one enum constant; nothing else
 * changes.
 */

/**
 * Juristic school (madhab) strategy — decides the Asr shadow factor.
 *
 * Asr begins when an object's shadow (beyond its noon shadow) equals the
 * object's length (standard: Shafi'i, Maliki, Hanbali — factor 1) or
 * twice its length (Hanafi — factor 2).
 *
 * References:
 * - PrayTimes, "Calculation Methods" (Asr juristic methods):
 *   https://praytimes.org/wiki/Calculation_Methods
 * - Adhan library calculation parameters guide ("Madhab"):
 *   https://github.com/batoulapps/adhan-js/blob/master/METHODS.md
 */
enum class Madhab(val shadowFactor: Int) {
    SHAFI(shadowFactor = 1),
    HANAFI(shadowFactor = 2),
}

/** Night fractions a high-latitude rule allows for Fajr and Isha. */
data class NightPortions(
    val fajrFraction: Double,
    val ishaFraction: Double,
)

/**
 * High-latitude rule strategy. Above roughly 48° latitude, twilight can
 * persist all night in summer, making the Fajr/Isha depression angles
 * unsolvable or placing them at impractical hours. Each rule bounds Fajr
 * and Isha to a fraction of the night (sunset -> sunrise):
 *
 * - [MIDDLE_OF_NIGHT]: Fajr no earlier, and Isha no later, than the
 *   middle of the night (fraction 1/2).
 * - [SEVENTH_OF_NIGHT]: Isha within the first seventh of the night, Fajr
 *   within the last seventh (fraction 1/7); the Adhan guide recommends
 *   this rule above 48°.
 * - [TWILIGHT_ANGLE]: the fraction is angle/60 of the night (e.g. an 18°
 *   angle allows 18/60 = 0.3 of the night) — the "angle-based" method.
 *
 * References:
 * - PrayTimes, "Calculation Methods" (higher-latitude adjustments):
 *   https://praytimes.org/wiki/Calculation_Methods
 * - Adhan library parameters guide ("HighLatitudeRule"):
 *   https://github.com/batoulapps/adhan-js/blob/master/METHODS.md
 */
enum class HighLatitudeRule {
    MIDDLE_OF_NIGHT {
        override fun nightPortions(parameters: CalculationParameters) =
            NightPortions(fajrFraction = 1.0 / 2.0, ishaFraction = 1.0 / 2.0)
    },
    SEVENTH_OF_NIGHT {
        override fun nightPortions(parameters: CalculationParameters) =
            NightPortions(fajrFraction = 1.0 / 7.0, ishaFraction = 1.0 / 7.0)
    },
    TWILIGHT_ANGLE {
        override fun nightPortions(parameters: CalculationParameters) =
            NightPortions(
                fajrFraction = parameters.fajrAngle / 60.0,
                ishaFraction = parameters.ishaAngle / 60.0,
            )
    };

    /** The fractions of the night this rule allows for Fajr and Isha. */
    abstract fun nightPortions(parameters: CalculationParameters): NightPortions
}

/**
 * Everything the engine needs to compute one day's times. Built from a
 * [CalculationMethod]'s base values plus the user's own choices; the
 * engine never looks at the method again.
 *
 * @param fajrAngle Solar depression angle for dawn, degrees below horizon.
 * @param maghribAngle Solar depression angle for Maghrib; 0 means Maghrib
 *   is at sunset (every method except Tehran/Jafari-style ones).
 * @param ishaAngle Solar depression angle for nightfall; ignored when
 *   [ishaIntervalMinutes] > 0.
 * @param ishaIntervalMinutes Fixed Isha offset after Maghrib (Umm al-Qura
 *   style); 0 means angle-based Isha.
 * @param madhab Asr juristic strategy.
 * @param highLatitudeRule Bounds Fajr/Isha to a night fraction when set;
 *   when null, the engine still falls back to [HighLatitudeRule.MIDDLE_OF_NIGHT]
 *   for times that are mathematically unsolvable.
 * @param elevationMeters Observer elevation; lowers the sunrise/sunset
 *   horizon by 0.0347 * sqrt(elevation) degrees.
 * @param offsetsMinutes Per-prayer manual adjustments.
 */
data class CalculationParameters(
    val fajrAngle: Double,
    val maghribAngle: Double = 0.0,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int = 0,
    val madhab: Madhab = Madhab.HANAFI,
    val highLatitudeRule: HighLatitudeRule? = null,
    val elevationMeters: Double = 0.0,
    val offsetsMinutes: Map<PrayerType, Int> = emptyMap(),
)

/**
 * Calculation authority strategy — each constant yields its published
 * twilight parameters as a [CalculationParameters] base.
 *
 * Published parameters (per the PrayTimes method table and the Adhan
 * parameters guide, cross-checked against the salah crate docs):
 *
 * | Method                           | Fajr  | Isha            | Maghrib |
 * |----------------------------------|-------|-----------------|---------|
 * | Karachi (Univ. Islamic Sciences) | 18°   | 18°             | sunset  |
 * | Muslim World League              | 18°   | 17°             | sunset  |
 * | ISNA (North America)             | 15°   | 15°             | sunset  |
 * | Egyptian General Authority       | 19.5° | 17.5°           | sunset  |
 * | Umm al-Qura, Makkah              | 18.5° | 90 min interval | sunset  |
 * | Tehran (Inst. of Geophysics)     | 17.7° | 14°             | 4.5°    |
 *
 * Known limitation: Umm al-Qura uses a 120-minute Isha interval during
 * Ramadan; that needs a Hijri calendar and is not applied here.
 *
 * References:
 * - PrayTimes, "Calculation Methods": https://praytimes.org/wiki/Calculation_Methods
 * - Adhan parameters guide: https://github.com/batoulapps/adhan-js/blob/master/METHODS.md
 * - salah crate method table: https://docs.rs/salah/latest/salah/enum.Method.html
 */
enum class CalculationMethod {
    KARACHI {
        override fun parameters() = CalculationParameters(fajrAngle = 18.0, ishaAngle = 18.0)
    },
    MUSLIM_WORLD_LEAGUE {
        override fun parameters() = CalculationParameters(fajrAngle = 18.0, ishaAngle = 17.0)
    },
    ISNA {
        override fun parameters() = CalculationParameters(fajrAngle = 15.0, ishaAngle = 15.0)
    },
    EGYPT {
        override fun parameters() = CalculationParameters(fajrAngle = 19.5, ishaAngle = 17.5)
    },
    MAKKAH {
        override fun parameters() = CalculationParameters(
            fajrAngle = 18.5,
            ishaAngle = 0.0,
            ishaIntervalMinutes = 90,
        )
    },
    TEHRAN {
        override fun parameters() = CalculationParameters(
            fajrAngle = 17.7,
            maghribAngle = 4.5,
            ishaAngle = 14.0,
        )
    };

    /** This authority's published base parameters. */
    abstract fun parameters(): CalculationParameters
}
