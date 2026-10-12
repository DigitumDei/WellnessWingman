package com.wellnesswingman.domain.report

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

/**
 * Pure, deterministic transformation of a [HealthReportData] snapshot into a
 * [HealthReportDocument].
 *
 * Deterministic by construction: the only time-dependent value is the
 * [generatedAt] timestamp supplied by the caller, so identical snapshots and
 * timestamps always produce byte-identical documents. Section rendering
 * follows the data itself — an empty section is omitted entirely, and no
 * zero-value record is ever fabricated.
 *
 * Every measurement carries its unit and a provenance label, and nothing
 * beyond the typed snapshot fields is ever rendered.
 */
class HealthReportBuilder {

    fun build(data: HealthReportData, generatedAt: Instant): HealthReportDocument {
        val blocks = mutableListOf<HealthReportBlock>()

        blocks += HealthReportBlock.Heading(1, "Health report")
        blocks += HealthReportBlock.Paragraph(
            "Range: ${data.start} to ${data.end} (inclusive) — time zone: ${data.timeZoneId} — " +
                "generated: ${generatedAt.toLocalDateTime(TimeZone.of(data.timeZoneId))}"
        )
        blocks += HealthReportBlock.Divider

        data.profile?.let { appendProfile(blocks, it) }

        if (data.meals.isNotEmpty()) {
            blocks += HealthReportBlock.Heading(2, "Meals and nutrition")
            blocks += HealthReportBlock.Paragraph(
                "Nutrition values are estimates unless marked as coming from an exact nutritional profile."
            )
            data.meals.sortedBy { it.localTime }.groupBy { it.date }.forEach { (date, meals) ->
                blocks += HealthReportBlock.Heading(3, date.toString())
                meals.forEach { appendMeal(blocks, it) }
            }
            blocks += HealthReportBlock.Divider
        }

        if (data.exercise.isNotEmpty()) {
            blocks += HealthReportBlock.Heading(2, "Exercise and activity")
            blocks += HealthReportBlock.Paragraph(
                "Timestamps and notes are the user's own; metrics and insights are AI-estimated."
            )
            data.exercise.sortedBy { it.localTime }.groupBy { it.date }.forEach { (date, entries) ->
                blocks += HealthReportBlock.Heading(3, date.toString())
                entries.forEach { appendExercise(blocks, it) }
            }
            blocks += HealthReportBlock.Divider
        }

        if (data.sleep.isNotEmpty()) {
            blocks += HealthReportBlock.Heading(2, "Sleep and recovery")
            blocks += HealthReportBlock.Paragraph(
                "Timestamps and notes are the user's own; analysis values are AI-estimated."
            )
            data.sleep.sortedBy { it.localTime }.groupBy { it.date }.forEach { (date, entries) ->
                blocks += HealthReportBlock.Heading(3, date.toString())
                entries.forEach { appendSleep(blocks, it) }
            }
            blocks += HealthReportBlock.Divider
        }

        if (data.checkIns.isNotEmpty()) {
            blocks += HealthReportBlock.Heading(2, "Daily check-ins")
            blocks += HealthReportBlock.Paragraph(
                "Responses are the user's own words; extracted food and factors are AI-estimated."
            )
            data.checkIns
                .sortedWith(compareBy<ReportCheckIn> { it.date }.thenBy { it.slot })
                .forEach { checkIn ->
                    appendCheckIn(blocks, checkIn)
                }
            blocks += HealthReportBlock.Divider
        }

        if (data.weightRecords.isNotEmpty()) {
            blocks += HealthReportBlock.Heading(2, "Weight history")
            blocks += HealthReportBlock.Table(
                headers = listOf("Date", "Time", "Weight", "Source"),
                rows = data.weightRecords.sortedBy { it.recordedAt }.map { record ->
                    listOf(
                        record.date.toString(),
                        record.recordedAt.toTimeString(),
                        formatNumber(record.value) + " " + record.unit,
                        record.provenance.label()
                    )
                }
            )
            blocks += HealthReportBlock.Divider
        }

        if (data.polarDays.isNotEmpty()) {
            blocks += HealthReportBlock.Heading(2, "Polar wearable measurements")
            blocks += HealthReportBlock.Paragraph(
                "All values are Polar-measured by the wearable and synced to the app."
            )
            data.polarDays.sortedBy { it.date }.forEach { day -> appendPolarDay(blocks, day) }
            blocks += HealthReportBlock.Divider
        }

        if (data.dailySummaries.isNotEmpty()) {
            blocks += HealthReportBlock.Heading(2, "Daily summaries")
            data.dailySummaries.sortedBy { it.date }.forEach { summary ->
                appendDailySummary(blocks, summary)
            }
            blocks += HealthReportBlock.Divider
        }

        if (data.weeklySummaries.isNotEmpty()) {
            blocks += HealthReportBlock.Heading(2, "Weekly summaries")
            data.weeklySummaries.sortedBy { it.weekStartDate }.forEach { summary ->
                appendWeeklySummary(blocks, summary)
            }
            blocks += HealthReportBlock.Divider
        }

        if (blocks.size <= 3) {
            blocks += HealthReportBlock.Paragraph("No data was found in the selected range.")
        }

        return HealthReportDocument(blocks)
    }

    private fun appendProfile(blocks: MutableList<HealthReportBlock>, profile: ReportProfile) {
        if (!profile.hasContent) return
        blocks += HealthReportBlock.Heading(2, "Profile and goals")
        val rows = mutableListOf<String>()
        profile.height?.let { rows += "Height: ${formatNumber(it)} ${profile.heightUnit ?: ""}" }
        profile.currentWeight?.let { rows += "Current weight: ${formatNumber(it)} ${profile.weightUnit ?: ""}" }
        profile.sex?.let { rows += "Sex: $it" }
        profile.dateOfBirth?.let { rows += "Date of birth: $it" }
        profile.activityLevel?.let { rows += "Activity level: $it" }
        profile.goalsAndPreferences?.let { rows += "Goals and preferences: $it" }
        if (rows.isNotEmpty()) {
            blocks += HealthReportBlock.BulletList(rows)
        }
    }

    private fun appendMeal(blocks: MutableList<HealthReportBlock>, meal: ReportMealEntry) {
        val time = meal.localTime.toTimeString()
        val note = meal.userNotes?.takeIf { it.isNotBlank() }
        blocks += HealthReportBlock.Paragraph(
            buildList {
                add("Meal at $time (user-entered)")
                if (note != null) add("Note: $note (user-entered)")
            }.joinToString(" — ")
        )

        meal.foodItems.forEach { item ->
            val calories = item.calories?.let { ", ${formatNumber(it)} kcal" } ?: ""
            val portion = item.portionSize?.let { ", $it" } ?: ""
            blocks += HealthReportBlock.BulletList(
                listOf("${item.name}$portion$calories (${item.provenance.label()})")
            )
        }

        meal.nutrition?.takeIf { it.hasValues }?.let { nutrition ->
            blocks += HealthReportBlock.BulletList(
                listOf(nutritionLine(nutrition))
            )
        }

        meal.healthInsights?.let { insights ->
            insights.summary?.takeIf { it.isNotBlank() }?.let {
                blocks += HealthReportBlock.Paragraph("Summary: $it (AI-estimated)")
            }
            if (insights.positives.isNotEmpty()) {
                blocks += HealthReportBlock.BulletList(
                    insights.positives.map { "Positive: $it (AI-estimated)" }
                )
            }
            if (insights.improvements.isNotEmpty()) {
                blocks += HealthReportBlock.BulletList(
                    insights.improvements.map { "Improvement: $it (AI-estimated)" }
                )
            }
        }
    }

    private fun nutritionLine(nutrition: ReportNutrition): String {
        val parts = mutableListOf<String>()
        nutrition.totalCalories?.let { parts += "${formatNumber(it)} kcal" }
        nutrition.protein?.let { parts += "${formatNumber(it)} g protein" }
        nutrition.carbohydrates?.let { parts += "${formatNumber(it)} g carbs" }
        nutrition.fat?.let { parts += "${formatNumber(it)} g fat" }
        nutrition.fiber?.let { parts += "${formatNumber(it)} g fiber" }
        nutrition.sugar?.let { parts += "${formatNumber(it)} g sugar" }
        nutrition.sodium?.let { parts += "${formatNumber(it)} mg sodium" }
        return "Nutrition: ${parts.joinToString(", ")} (${nutrition.provenance.label()})"
    }

    private fun appendExercise(blocks: MutableList<HealthReportBlock>, entry: ReportExerciseEntry) {
        val time = entry.localTime.toTimeString()
        val type = entry.activityType?.takeIf { it.isNotBlank() }?.let { " ($it, AI-estimated)" } ?: ""
        val note = entry.userNotes?.takeIf { it.isNotBlank() }?.let { "; note: $it (user-entered)" } ?: ""
        blocks += HealthReportBlock.Paragraph("Exercise at $time (user-entered)$type$note")

        val metrics = entry.metrics
        if (metrics != null) {
            val parts = mutableListOf<String>()
            metrics.durationMinutes?.let { parts += "${formatNumber(it)} min" }
            metrics.distance?.let { parts += "${formatNumber(it)} ${metrics.distanceUnit ?: ""}" }
            metrics.averagePace?.let { parts += "pace $it" }
            metrics.calories?.let { parts += "${formatNumber(it)} kcal" }
            metrics.averageHeartRate?.let { parts += "avg HR ${formatNumber(it)} bpm" }
            metrics.maxHeartRate?.let { parts += "max HR ${formatNumber(it)} bpm" }
            metrics.steps?.let { parts += "${formatNumber(it)} steps" }
            if (parts.isNotEmpty()) {
                blocks += HealthReportBlock.BulletList(listOf("Metrics (AI-estimated): ${parts.joinToString(", ")}"))
            }
        }

        entry.insights?.summary?.takeIf { it.isNotBlank() }?.let {
            blocks += HealthReportBlock.Paragraph("Summary: $it (AI-estimated)")
        }
    }

    private fun appendSleep(blocks: MutableList<HealthReportBlock>, entry: ReportSleepEntry) {
        val time = entry.localTime.toTimeString()
        val note = entry.userNotes?.takeIf { it.isNotBlank() }?.let { "; note: $it (user-entered)" } ?: ""
        blocks += HealthReportBlock.Paragraph("Sleep at $time (user-entered)$note")

        val analysis = entry.analysis
        if (analysis != null) {
            val parts = mutableListOf<String>()
            analysis.durationHours?.let { parts += "${formatNumber(it)} hours" }
            analysis.sleepScore?.let { parts += "score ${formatNumber(it)}/100" }
            if (parts.isNotEmpty()) {
                blocks += HealthReportBlock.BulletList(
                    listOf("Analysis (AI-estimated): ${parts.joinToString(", ")}")
                )
            }
            analysis.qualitySummary?.takeIf { it.isNotBlank() }?.let {
                blocks += HealthReportBlock.Paragraph("Quality: $it (AI-estimated)")
            }
            if (analysis.recommendations.isNotEmpty()) {
                blocks += HealthReportBlock.BulletList(
                    analysis.recommendations.map { "Recommendation: $it (AI-estimated)" }
                )
            }
        }
    }

    private fun appendCheckIn(blocks: MutableList<HealthReportBlock>, checkIn: ReportCheckIn) {
        blocks += HealthReportBlock.Heading(3, "${checkIn.date} — ${checkIn.slot} check-in (user-entered)")
        blocks += HealthReportBlock.Paragraph(checkIn.responseText)

        if (checkIn.mentionedFood.isNotEmpty()) {
            blocks += HealthReportBlock.BulletList(
                checkIn.mentionedFood.map { food ->
                    val portion = food.portionSize?.let { " ($it)" } ?: ""
                    val calories = food.nutrition?.totalCalories?.let { ", ${formatNumber(it)} kcal" } ?: ""
                    "Mentioned food: ${food.name}$portion$calories (AI-estimated)"
                }
            )
        }
        if (checkIn.goodFactors.isNotEmpty()) {
            blocks += HealthReportBlock.BulletList(
                checkIn.goodFactors.map { "Helped: ${it.description}" }
            )
        }
        if (checkIn.badFactors.isNotEmpty()) {
            blocks += HealthReportBlock.BulletList(
                checkIn.badFactors.map { "Hurt: ${it.description}" }
            )
        }
    }

    private fun appendPolarDay(blocks: MutableList<HealthReportBlock>, day: ReportPolarDay) {
        blocks += HealthReportBlock.Heading(3, day.date.toString())
        val lines = mutableListOf<String>()

        day.activity?.let { activity ->
            lines += "Steps: ${activity.totalSteps}"
        }
        day.sleep?.let { sleep ->
            lines += "Sleep: ${formatNumber(sleep.durationSeconds / 3600.0)} hours " +
                "(deep ${formatNumber(sleep.deepSleepSeconds / 3600.0)} h, " +
                "rem ${formatNumber(sleep.remSleepSeconds / 3600.0)} h, " +
                "light ${formatNumber(sleep.lightSleepSeconds / 3600.0)} h); " +
                "score ${formatNumber(sleep.sleepScore)}/100"
        }
        day.nightlyRecharge?.let { recharge ->
            lines += "Nightly recharge: ANS ${formatNumber(recharge.ansStatus)} " +
                "(HRV RMSSD ${recharge.hrvRmssd} ms, baseline ${recharge.baselineRmssd} ms)"
        }
        day.trainingSessions.forEach { session ->
            lines += "Training: ${session.sportId}, ${formatNumber(session.durationSeconds / 60.0)} min, " +
                "${session.calories} kcal, avg HR ${session.averageHeartRate} bpm, " +
                (if (session.distanceMeters > 0) "${formatNumber(session.distanceMeters / 1000.0)} km" else "no distance")
        }

        if (lines.isNotEmpty()) {
            blocks += HealthReportBlock.BulletList(lines)
        }
    }

    private fun appendDailySummary(blocks: MutableList<HealthReportBlock>, summary: ReportDailySummary) {
        blocks += HealthReportBlock.Heading(3, summary.date.toString())
        summary.highlights.takeIf { it.isNotBlank() }?.let {
            blocks += HealthReportBlock.Paragraph("Highlights: $it (AI-estimated)")
        }
        summary.recommendations.takeIf { it.isNotBlank() }?.let {
            blocks += HealthReportBlock.Paragraph("Recommendations: $it (AI-estimated)")
        }
        summary.totals?.let { totals ->
            val parts = mutableListOf<String>()
            parts += "${formatNumber(totals.calories)} kcal"
            parts += "${formatNumber(totals.protein)} g protein"
            parts += "${formatNumber(totals.carbs)} g carbs"
            parts += "${formatNumber(totals.fat)} g fat"
            parts += "${formatNumber(totals.fiber)} g fiber"
            parts += "${formatNumber(totals.sugar)} g sugar"
            parts += "${formatNumber(totals.sodium)} mg sodium"
            if (totals.mentionedItemCount > 0) {
                parts += "${totals.mentionedItemCount} item(s) from check-in text (${formatNumber(totals.mentionedCalories)} kcal)"
            }
            blocks += HealthReportBlock.BulletList(listOf("Daily totals: ${parts.joinToString(", ")}"))
        }
        summary.balance?.let { balance ->
            val parts = listOfNotNull(
                balance.overall?.let { "overall: $it" },
                balance.macroBalance?.let { "macros: $it" },
                balance.timing?.let { "timing: $it" },
                balance.variety?.let { "variety: $it" }
            )
            if (parts.isNotEmpty()) {
                blocks += HealthReportBlock.BulletList(listOf("Balance: ${parts.joinToString("; ")}"))
            }
        }
        summary.userComments?.takeIf { it.isNotBlank() }?.let {
            blocks += HealthReportBlock.Paragraph("User comments: $it (user-entered)")
        }
    }

    private fun appendWeeklySummary(blocks: MutableList<HealthReportBlock>, summary: ReportWeeklySummary) {
        blocks += HealthReportBlock.Heading(3, "Week of ${summary.weekStartDate}")
        blocks += HealthReportBlock.Paragraph(
            "Entries: ${summary.totalEntries} total " +
                "(${summary.mealCount} meals, ${summary.exerciseCount} exercise, " +
                "${summary.sleepCount} sleep, ${summary.otherCount} other)"
        )
        summary.highlights.takeIf { it.isNotBlank() }?.let {
            blocks += HealthReportBlock.Paragraph("Highlights: $it (AI-estimated)")
        }
        summary.recommendations.takeIf { it.isNotBlank() }?.let {
            blocks += HealthReportBlock.Paragraph("Recommendations: $it (AI-estimated)")
        }
        summary.userComments?.takeIf { it.isNotBlank() }?.let {
            blocks += HealthReportBlock.Paragraph("User comments: $it (user-entered)")
        }
    }

    private fun ReportProvenance.label(): String = when (this) {
        ReportProvenance.USER_ENTERED -> "user-entered"
        ReportProvenance.AI_ESTIMATED -> "AI-estimated"
        ReportProvenance.EXACT_PROFILE -> "exact nutritional profile"
        ReportProvenance.POLAR_MEASURED -> "Polar-measured"
    }

    private fun formatNumber(value: Double): String {
        val rounded = round(value * 10.0) / 10.0
        val text = rounded.toString()
        return text.removeSuffix(".0")
    }

    private fun kotlinx.datetime.LocalDateTime.toTimeString(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
