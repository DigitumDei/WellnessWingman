package com.wellnesswingman.ui.screens.nutrition

import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.data.repository.NutritionalProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionalProfilesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadProfiles populates success state`() = runTest(dispatcher.scheduler) {
        val now = Clock.System.now()
        val repository = FakeProfilesRepository(
            mutableListOf(
                NutritionalProfile(
                    profileId = 1L,
                    externalId = "quest-bar",
                    primaryName = "Quest Bar",
                    aliases = listOf("protein bar"),
                    createdAt = now,
                    updatedAt = now
                )
            )
        )

        val viewModel = NutritionalProfilesViewModel(repository)
        advanceUntilIdle()

        val state = assertIs<NutritionalProfilesUiState.Success>(viewModel.uiState.value)
        assertEquals(1, state.profiles.size)
        assertEquals("Quest Bar", state.profiles.single().primaryName)
    }

    @Test
    fun `deleteProfile removes item and reloads list`() = runTest(dispatcher.scheduler) {
        val now = Clock.System.now()
        val repository = FakeProfilesRepository(
            mutableListOf(
                NutritionalProfile(
                    profileId = 1L,
                    externalId = "one",
                    primaryName = "One",
                    createdAt = now,
                    updatedAt = now
                ),
                NutritionalProfile(
                    profileId = 2L,
                    externalId = "two",
                    primaryName = "Two",
                    createdAt = now,
                    updatedAt = now
                )
            )
        )
        val viewModel = NutritionalProfilesViewModel(repository)
        advanceUntilIdle()

        viewModel.deleteProfile(1L)
        advanceUntilIdle()

        val state = assertIs<NutritionalProfilesUiState.Success>(viewModel.uiState.value)
        assertEquals(listOf("Two"), state.profiles.map { it.primaryName })
    }
}

private class FakeProfilesRepository(
    private val profiles: MutableList<NutritionalProfile> = mutableListOf()
) : NutritionalProfileRepository {
    override suspend fun getAll(): List<NutritionalProfile> = profiles.toList()
    override suspend fun getById(profileId: Long): NutritionalProfile? = profiles.find { it.profileId == profileId }
    override suspend fun getByExternalId(externalId: String): NutritionalProfile? = profiles.find { it.externalId == externalId }
    override suspend fun searchByName(query: String, limit: Int): List<NutritionalProfile> = emptyList()
    override suspend fun insert(profile: NutritionalProfile): Long = 0L
    override suspend fun update(profile: NutritionalProfile) {}
    override suspend fun delete(profileId: Long) { profiles.removeAll { it.profileId == profileId } }
    override suspend fun upsert(profile: NutritionalProfile) {}
}
