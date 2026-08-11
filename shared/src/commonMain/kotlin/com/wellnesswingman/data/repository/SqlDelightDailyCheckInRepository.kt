package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.CheckInInputSource
import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import com.wellnesswingman.db.WellnessWingmanDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/**
 * SQLDelight implementation of DailyCheckInRepository.
 */
class SqlDelightDailyCheckInRepository(
    private val database: WellnessWingmanDatabase
) : DailyCheckInRepository {

    private val queries = database.dailyCheckInQueries

    override suspend fun getAllCheckIns(): List<DailyCheckIn> = withContext(Dispatchers.IO) {
        queries.getAllCheckIns().executeAsList().map { it.toDailyCheckIn() }
    }

    override suspend fun getCheckInsForDate(date: LocalDate): List<DailyCheckIn> =
        withContext(Dispatchers.IO) {
            queries.getCheckInsForDate(date.toEpochDays().toLong())
                .executeAsList().map { it.toDailyCheckIn() }
        }

    override suspend fun getCheckIn(date: LocalDate, slot: CheckInSlot): DailyCheckIn? =
        withContext(Dispatchers.IO) {
            queries.getCheckInForDateAndSlot(
                checkInDate = date.toEpochDays().toLong(),
                slot = slot.toStorageString()
            ).executeAsOneOrNull()?.toDailyCheckIn()
        }

    override suspend fun getCheckInsForDateRange(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyCheckIn> = withContext(Dispatchers.IO) {
        queries.getCheckInsForDateRange(
            startDate.toEpochDays().toLong(),
            endDate.toEpochDays().toLong()
        ).executeAsList().map { it.toDailyCheckIn() }
    }

    override suspend fun getCheckInByExternalId(externalId: String): DailyCheckIn? =
        withContext(Dispatchers.IO) {
            queries.getCheckInByExternalId(externalId).executeAsOneOrNull()?.toDailyCheckIn()
        }

    override suspend fun saveCheckIn(checkIn: DailyCheckIn): Long = withContext(Dispatchers.IO) {
        queries.upsertCheckInForDateAndSlot(
            externalId = checkIn.externalId,
            checkInDate = checkIn.checkInDate.toEpochDays().toLong(),
            slot = checkIn.slot.toStorageString(),
            capturedAt = checkIn.capturedAt.toEpochMilliseconds(),
            responseText = checkIn.responseText,
            inputSource = checkIn.inputSource.toStorageString(),
            conversationExternalId = checkIn.conversationExternalId
        )
        queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun attachConversation(
        date: LocalDate,
        slot: CheckInSlot,
        conversationExternalId: String
    ) = withContext(Dispatchers.IO) {
        queries.updateConversationExternalId(
            conversationExternalId = conversationExternalId,
            checkInDate = date.toEpochDays().toLong(),
            slot = slot.toStorageString()
        )
    }

    override suspend fun deleteCheckIn(date: LocalDate, slot: CheckInSlot) =
        withContext(Dispatchers.IO) {
            queries.deleteCheckInForDateAndSlot(
                checkInDate = date.toEpochDays().toLong(),
                slot = slot.toStorageString()
            )
        }

    override suspend fun deleteOldCheckIns(beforeDate: LocalDate) = withContext(Dispatchers.IO) {
        queries.deleteOldCheckIns(beforeDate.toEpochDays().toLong())
    }

    override suspend fun upsertCheckIn(checkIn: DailyCheckIn) = withContext(Dispatchers.IO) {
        queries.upsertCheckIn(
            checkInId = checkIn.checkInId,
            externalId = checkIn.externalId,
            checkInDate = checkIn.checkInDate.toEpochDays().toLong(),
            slot = checkIn.slot.toStorageString(),
            capturedAt = checkIn.capturedAt.toEpochMilliseconds(),
            responseText = checkIn.responseText,
            inputSource = checkIn.inputSource.toStorageString(),
            conversationExternalId = checkIn.conversationExternalId
        )
    }

    /**
     * Maps SQLDelight DailyCheckIn to domain DailyCheckIn.
     *
     * An unrecognised slot means the row was written by something other than this app, and
     * silently dropping it would hide the corruption rather than surface it.
     */
    private fun com.wellnesswingman.db.DailyCheckIn.toDailyCheckIn(): DailyCheckIn {
        val parsedSlot = CheckInSlot.fromString(slot)
            ?: error("Unrecognised check-in slot '$slot' for checkInId=$checkInId")

        return DailyCheckIn(
            checkInId = checkInId,
            externalId = externalId,
            checkInDate = LocalDate.fromEpochDays(checkInDate.toInt()),
            slot = parsedSlot,
            capturedAt = Instant.fromEpochMilliseconds(capturedAt),
            responseText = responseText,
            inputSource = CheckInInputSource.fromString(inputSource),
            conversationExternalId = conversationExternalId
        )
    }
}
