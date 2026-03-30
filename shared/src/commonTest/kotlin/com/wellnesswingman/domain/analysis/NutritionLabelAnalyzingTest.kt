package com.wellnesswingman.domain.analysis

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class NutritionLabelAnalyzingTest {

    @Test
    fun `nutrition label analyzing contract can be implemented`() = runTest {
        val analyzer: NutritionLabelAnalyzing = object : NutritionLabelAnalyzing {
            override fun hasConfiguredApiKey(): Boolean = false

            override suspend fun analyzeLabelImage(
                imageBytes: ByteArray,
                sourceImagePath: String?
            ): NutritionLabelExtraction {
                return NutritionLabelExtraction(sourceImagePath = sourceImagePath)
            }
        }

        assertFalse(analyzer.hasConfiguredApiKey())
        assertEquals(
            NutritionLabelExtraction(sourceImagePath = "/tmp/test.jpg"),
            analyzer.analyzeLabelImage(byteArrayOf(1), "/tmp/test.jpg")
        )
    }
}
