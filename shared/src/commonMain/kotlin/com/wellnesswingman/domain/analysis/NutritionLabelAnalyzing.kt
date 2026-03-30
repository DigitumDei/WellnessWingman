package com.wellnesswingman.domain.analysis

interface NutritionLabelAnalyzing {
    fun hasConfiguredApiKey(): Boolean

    suspend fun analyzeLabelImage(
        imageBytes: ByteArray,
        sourceImagePath: String? = null
    ): NutritionLabelExtraction
}
