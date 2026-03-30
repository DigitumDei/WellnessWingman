package com.wellnesswingman.data.repository

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wellnesswingman.data.model.NutritionalProfile
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightNutritionalProfileRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: WellnessWingmanDatabase
    private lateinit var repository: NutritionalProfileRepository

    @BeforeTest
    fun setup() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
        repository = SqlDelightNutritionalProfileRepository(database)
    }

    @AfterTest
    fun teardown() {
        driver.close()
    }

    @Test
    fun `insert get by id and flow round trip nutritional profile`() = runTest {
        val createdAt = Instant.parse("2026-03-30T10:15:00Z")
        val profile = profile(
            externalId = "quest-bar",
            primaryName = "Quest Protein Bar",
            aliases = listOf("protein bar", "quest bar"),
            servingSize = "1 bar",
            calories = 190.0,
            protein = 21.0,
            carbohydrates = 22.0,
            fat = 7.0,
            fiber = 14.0,
            sugar = 1.0,
            sodium = 210.0,
            saturatedFat = 2.5,
            transFat = 0.0,
            cholesterol = 5.0,
            rawJson = """{"source":"llm"}""",
            sourceImagePath = "/tmp/quest.jpg",
            createdAt = createdAt,
            updatedAt = createdAt
        )

        val id = repository.insert(profile)

        assertTrue(id > 0)

        val stored = repository.getById(id)
        assertNotNull(stored)
        assertEquals(id, stored.profileId)
        assertEquals(profile.externalId, stored.externalId)
        assertEquals(profile.primaryName, stored.primaryName)
        assertEquals(profile.aliases, stored.aliases)
        assertEquals(profile.servingSize, stored.servingSize)
        assertEquals(profile.calories, stored.calories)
        assertEquals(profile.protein, stored.protein)
        assertEquals(profile.carbohydrates, stored.carbohydrates)
        assertEquals(profile.fat, stored.fat)
        assertEquals(profile.fiber, stored.fiber)
        assertEquals(profile.sugar, stored.sugar)
        assertEquals(profile.sodium, stored.sodium)
        assertEquals(profile.saturatedFat, stored.saturatedFat)
        assertEquals(profile.transFat, stored.transFat)
        assertEquals(profile.cholesterol, stored.cholesterol)
        assertEquals(profile.rawJson, stored.rawJson)
        assertEquals(profile.sourceImagePath, stored.sourceImagePath)
        assertEquals(createdAt, stored.createdAt)
        assertEquals(createdAt, stored.updatedAt)

        val flowed = repository.getAllAsFlow().first()
        assertEquals(listOf(stored), flowed)
    }

    @Test
    fun `get all sorts by primary name ignoring case`() = runTest {
        repository.insert(profile(externalId = "b", primaryName = "banana chips"))
        repository.insert(profile(externalId = "a", primaryName = "Apple Slices"))
        repository.insert(profile(externalId = "c", primaryName = "apricot bites"))

        val all = repository.getAll()

        assertEquals(
            listOf("Apple Slices", "apricot bites", "banana chips"),
            all.map { it.primaryName }
        )
    }

    @Test
    fun `get by external id returns matching profile or null`() = runTest {
        val insertedId = repository.insert(profile(externalId = "fairlife", primaryName = "Core Power"))

        val found = repository.getByExternalId("fairlife")
        val missing = repository.getByExternalId("missing")

        assertEquals(insertedId, found?.profileId)
        assertNull(missing)
    }

    @Test
    fun `search by name handles blank exact prefix contains alias and limit cases`() = runTest {
        repository.insert(
            profile(
                externalId = "exact",
                primaryName = "Quest Protein Bar",
                aliases = listOf("protein bar")
            )
        )
        repository.insert(
            profile(
                externalId = "prefix",
                primaryName = "Quest Shake",
                aliases = listOf("ready to drink")
            )
        )
        repository.insert(
            profile(
                externalId = "alias",
                primaryName = "Chips Deluxe",
                aliases = listOf("quest protein bar dupe", "salty snack")
            )
        )

        assertEquals(emptyList<NutritionalProfile>(), repository.searchByName("   "))

        val ranked = repository.searchByName("Quest Protein Bar", limit = 2)
        assertEquals(listOf("Quest Protein Bar", "Chips Deluxe"), ranked.map { it.primaryName })

        val prefix = repository.searchByName("Quest", limit = 2)
        assertEquals(listOf("Quest Protein Bar", "Quest Shake"), prefix.map { it.primaryName })

        val alias = repository.searchByName("salty", limit = 5)
        assertEquals(listOf("Chips Deluxe"), alias.map { it.primaryName })
    }

    @Test
    fun `update persists changed values`() = runTest {
        val id = repository.insert(profile(externalId = "fairlife", primaryName = "Core Power"))

        repository.update(
            profile(
                profileId = id,
                externalId = "fairlife",
                primaryName = "Core Power Elite",
                aliases = listOf("elite shake"),
                servingSize = "414 ml",
                calories = 230.0,
                protein = 42.0,
                sourceImagePath = "/tmp/core-power.jpg",
                rawJson = """{"updated":true}""",
                updatedAt = Instant.parse("2026-03-30T12:00:00Z")
            )
        )

        val stored = repository.getById(id)
        assertNotNull(stored)
        assertEquals("Core Power Elite", stored.primaryName)
        assertEquals(listOf("elite shake"), stored.aliases)
        assertEquals("414 ml", stored.servingSize)
        assertEquals(230.0, stored.calories)
        assertEquals(42.0, stored.protein)
        assertEquals("/tmp/core-power.jpg", stored.sourceImagePath)
        assertEquals("""{"updated":true}""", stored.rawJson)
        assertEquals(Instant.parse("2026-03-30T12:00:00Z"), stored.updatedAt)
    }

    @Test
    fun `delete removes profile`() = runTest {
        val id = repository.insert(profile(externalId = "delete-me", primaryName = "Delete Me"))

        repository.delete(id)

        assertNull(repository.getById(id))
        assertEquals(emptyList(), repository.getAll())
    }

    @Test
    fun `upsert inserts new profile and replaces existing one`() = runTest {
        repository.upsert(
            profile(
                profileId = 77L,
                externalId = "upserted",
                primaryName = "Original Name",
                calories = 120.0
            )
        )

        val inserted = repository.getById(77L)
        assertNotNull(inserted)
        assertEquals("Original Name", inserted.primaryName)

        repository.upsert(
            inserted.copy(
                primaryName = "Updated Name",
                calories = 180.0,
                aliases = listOf("updated")
            )
        )

        val updated = repository.getById(77L)
        assertNotNull(updated)
        assertEquals("Updated Name", updated.primaryName)
        assertEquals(180.0, updated.calories)
        assertEquals(listOf("updated"), updated.aliases)
    }

    @Test
    fun `upsert with default profile id creates autoincremented row`() = runTest {
        repository.upsert(
            profile(
                externalId = "new-upsert",
                primaryName = "Inserted Via Upsert"
            )
        )

        val stored = repository.getByExternalId("new-upsert")

        assertNotNull(stored)
        assertTrue(stored.profileId > 0L)
        assertEquals("Inserted Via Upsert", stored.primaryName)
    }

    @Test
    fun `invalid alias json falls back to empty aliases`() = runTest {
        database.nutritionalProfileQueries.insert(
            externalId = "bad-aliases",
            primaryName = "Broken Aliases",
            aliases = "{not valid json",
            servingSize = null,
            calories = null,
            protein = null,
            carbohydrates = null,
            fat = null,
            fiber = null,
            sugar = null,
            sodium = null,
            saturatedFat = null,
            transFat = null,
            cholesterol = null,
            rawJson = null,
            sourceImagePath = null,
            createdAt = 1L,
            updatedAt = 2L
        )

        val stored = repository.getByExternalId("bad-aliases")

        assertNotNull(stored)
        assertEquals(emptyList(), stored.aliases)
        assertEquals(Instant.fromEpochMilliseconds(1L), stored.createdAt)
        assertEquals(Instant.fromEpochMilliseconds(2L), stored.updatedAt)
    }

    private fun profile(
        profileId: Long = 0L,
        externalId: String,
        primaryName: String,
        aliases: List<String> = emptyList(),
        servingSize: String? = null,
        calories: Double? = null,
        protein: Double? = null,
        carbohydrates: Double? = null,
        fat: Double? = null,
        fiber: Double? = null,
        sugar: Double? = null,
        sodium: Double? = null,
        saturatedFat: Double? = null,
        transFat: Double? = null,
        cholesterol: Double? = null,
        rawJson: String? = null,
        sourceImagePath: String? = null,
        createdAt: Instant = Instant.parse("2026-03-30T10:00:00Z"),
        updatedAt: Instant = Instant.parse("2026-03-30T10:00:00Z")
    ) = NutritionalProfile(
        profileId = profileId,
        externalId = externalId,
        primaryName = primaryName,
        aliases = aliases,
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
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
