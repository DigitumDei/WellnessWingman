package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.NutritionalProfile

interface NutritionalProfileRepository {
    suspend fun getAll(): List<NutritionalProfile>
    suspend fun getById(profileId: Long): NutritionalProfile?
    suspend fun getByExternalId(externalId: String): NutritionalProfile?
    suspend fun searchByName(query: String, limit: Int = 5): List<NutritionalProfile>
    suspend fun insert(profile: NutritionalProfile): Long
    suspend fun update(profile: NutritionalProfile)
    suspend fun delete(profileId: Long)
    suspend fun upsert(profile: NutritionalProfile)
}
