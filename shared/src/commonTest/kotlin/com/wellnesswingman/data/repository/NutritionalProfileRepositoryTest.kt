package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.NutritionalProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NutritionalProfileRepositoryTest {

    @Test
    fun `nutritional profile repository contract can be implemented`() = runTest {
        var stored: NutritionalProfile? = null
        val repository: NutritionalProfileRepository = object : NutritionalProfileRepository {
            override fun getAllAsFlow(): Flow<List<NutritionalProfile>> = flowOf(listOfNotNull(stored))

            override suspend fun getAll(): List<NutritionalProfile> = listOfNotNull(stored)

            override suspend fun getById(profileId: Long): NutritionalProfile? = stored?.takeIf { it.profileId == profileId }

            override suspend fun getByExternalId(externalId: String): NutritionalProfile? =
                stored?.takeIf { it.externalId == externalId }

            override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> =
                listOfNotNull(stored).filter { it.primaryName.contains(query, ignoreCase = true) }.take(limit)

            override suspend fun insert(profile: NutritionalProfile): Long {
                stored = profile.copy(profileId = 1L)
                return 1L
            }

            override suspend fun update(profile: NutritionalProfile) {
                stored = profile
            }

            override suspend fun delete(profileId: Long) {
                if (stored?.profileId == profileId) stored = null
            }

            override suspend fun upsert(profile: NutritionalProfile) {
                stored = profile
            }
        }

        val profile = NutritionalProfile(
            externalId = "quest-bar",
            primaryName = "Quest Protein Bar",
            createdAt = Instant.parse("2026-03-30T10:00:00Z"),
            updatedAt = Instant.parse("2026-03-30T10:00:00Z")
        )

        assertEquals(1L, repository.insert(profile))
        assertEquals("Quest Protein Bar", repository.getById(1L)?.primaryName)
        assertEquals("quest-bar", repository.getByExternalId("quest-bar")?.externalId)
        assertEquals(listOf("Quest Protein Bar"), repository.searchByName("quest", limit = 5).map { it.primaryName })

        repository.delete(1L)

        assertNull(repository.getById(1L))
    }
}
