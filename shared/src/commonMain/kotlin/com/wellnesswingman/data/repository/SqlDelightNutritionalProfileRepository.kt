package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SqlDelightNutritionalProfileRepository(
    private val database: WellnessWingmanDatabase
) : NutritionalProfileRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val queries = database.nutritionalProfileQueries

    override suspend fun getAll(): List<NutritionalProfile> = withContext(Dispatchers.IO) {
        queries.getAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun getById(profileId: Long): NutritionalProfile? = withContext(Dispatchers.IO) {
        queries.getById(profileId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getByExternalId(externalId: String): NutritionalProfile? = withContext(Dispatchers.IO) {
        queries.getByExternalId(externalId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            return@withContext emptyList()
        }

        val contains = "%$normalized%"
        val prefix = "$normalized%"
        queries.searchByName(
            query = contains,
            aliases = contains,
            exact = normalized,
            prefix = prefix,
            limit = limit.toLong()
        ).executeAsList().map { it.toDomain() }
    }

    override suspend fun insert(profile: NutritionalProfile): Long = withContext(Dispatchers.IO) {
        queries.insert(
            externalId = profile.externalId,
            primaryName = profile.primaryName,
            aliases = encodeAliases(profile.aliases),
            servingSize = profile.servingSize,
            calories = profile.calories,
            protein = profile.protein,
            carbohydrates = profile.carbohydrates,
            fat = profile.fat,
            fiber = profile.fiber,
            sugar = profile.sugar,
            sodium = profile.sodium,
            saturatedFat = profile.saturatedFat,
            transFat = profile.transFat,
            cholesterol = profile.cholesterol,
            rawJson = profile.rawJson,
            sourceImagePath = profile.sourceImagePath,
            createdAt = profile.createdAt.toEpochMilliseconds(),
            updatedAt = profile.updatedAt.toEpochMilliseconds()
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun update(profile: NutritionalProfile) = withContext(Dispatchers.IO) {
        queries.update(
            primaryName = profile.primaryName,
            aliases = encodeAliases(profile.aliases),
            servingSize = profile.servingSize,
            calories = profile.calories,
            protein = profile.protein,
            carbohydrates = profile.carbohydrates,
            fat = profile.fat,
            fiber = profile.fiber,
            sugar = profile.sugar,
            sodium = profile.sodium,
            saturatedFat = profile.saturatedFat,
            transFat = profile.transFat,
            cholesterol = profile.cholesterol,
            rawJson = profile.rawJson,
            sourceImagePath = profile.sourceImagePath,
            updatedAt = profile.updatedAt.toEpochMilliseconds(),
            profileId = profile.profileId
        )
    }

    override suspend fun delete(profileId: Long) = withContext(Dispatchers.IO) {
        queries.deleteById(profileId)
    }

    override suspend fun upsert(profile: NutritionalProfile) = withContext(Dispatchers.IO) {
        queries.upsert(
            profileId = profile.profileId,
            externalId = profile.externalId,
            primaryName = profile.primaryName,
            aliases = encodeAliases(profile.aliases),
            servingSize = profile.servingSize,
            calories = profile.calories,
            protein = profile.protein,
            carbohydrates = profile.carbohydrates,
            fat = profile.fat,
            fiber = profile.fiber,
            sugar = profile.sugar,
            sodium = profile.sodium,
            saturatedFat = profile.saturatedFat,
            transFat = profile.transFat,
            cholesterol = profile.cholesterol,
            rawJson = profile.rawJson,
            sourceImagePath = profile.sourceImagePath,
            createdAt = profile.createdAt.toEpochMilliseconds(),
            updatedAt = profile.updatedAt.toEpochMilliseconds()
        )
    }

    private fun encodeAliases(aliases: List<String>): String = json.encodeToString(aliases)

    private fun decodeAliases(raw: String): List<String> {
        return runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    private fun com.wellnesswingman.db.NutritionalProfile.toDomain(): NutritionalProfile {
        return NutritionalProfile(
            profileId = profileId,
            externalId = externalId,
            primaryName = primaryName,
            aliases = decodeAliases(aliases),
            servingSize = servingSize,
            calories = calories,
            protein = protein,
            carbohydrates = carbohydrates,
            fat = fat,
            fiber = fiber,
            sugar = sugar,
            sodium = sodium,
            saturatedFat = saturatedFat,
            transFat = transFat,
            cholesterol = cholesterol,
            rawJson = rawJson,
            sourceImagePath = sourceImagePath,
            createdAt = Instant.fromEpochMilliseconds(createdAt),
            updatedAt = Instant.fromEpochMilliseconds(updatedAt)
        )
    }
}
