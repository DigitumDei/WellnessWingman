package com.wellnesswingman.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NutritionalProfile(
    val profileId: Long = 0,
    val externalId: String,
    val primaryName: String,
    val aliases: List<String> = emptyList(),
    val servingSize: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val carbohydrates: Double? = null,
    val fat: Double? = null,
    val fiber: Double? = null,
    val sugar: Double? = null,
    val sodium: Double? = null,
    val saturatedFat: Double? = null,
    val transFat: Double? = null,
    val cholesterol: Double? = null,
    val rawJson: String? = null,
    val sourceImagePath: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class NutritionalProfileLookupResult(
    @SerialName("profileId")
    val profileId: Long,
    @SerialName("primaryName")
    val primaryName: String,
    @SerialName("aliases")
    val aliases: List<String> = emptyList(),
    @SerialName("servingSize")
    val servingSize: String? = null,
    @SerialName("nutrition")
    val nutrition: NutritionalProfileNutrition = NutritionalProfileNutrition(),
    @SerialName("source")
    val source: String = "exact"
)

@Serializable
data class NutritionalProfileNutrition(
    @SerialName("totalCalories")
    val totalCalories: Double? = null,
    @SerialName("protein")
    val protein: Double? = null,
    @SerialName("carbohydrates")
    val carbohydrates: Double? = null,
    @SerialName("fat")
    val fat: Double? = null,
    @SerialName("fiber")
    val fiber: Double? = null,
    @SerialName("sugar")
    val sugar: Double? = null,
    @SerialName("sodium")
    val sodium: Double? = null,
    @SerialName("saturatedFat")
    val saturatedFat: Double? = null,
    @SerialName("transFat")
    val transFat: Double? = null,
    @SerialName("cholesterol")
    val cholesterol: Double? = null
)
