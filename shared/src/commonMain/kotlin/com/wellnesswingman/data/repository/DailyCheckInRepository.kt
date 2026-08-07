package com.wellnesswingman.data.repository

import com.wellnesswingman.data.model.CheckInSlot
import com.wellnesswingman.data.model.DailyCheckIn
import kotlinx.datetime.LocalDate

/**
 * Repository interface for subjective daily check-ins.
 */
interface DailyCheckInRepository {
    suspend fun getAllCheckIns(): List<DailyCheckIn>
    suspend fun getCheckInsForDate(date: LocalDate): List<DailyCheckIn>
    suspend fun getCheckIn(date: LocalDate, slot: CheckInSlot): DailyCheckIn?
    suspend fun getCheckInsForDateRange(startDate: LocalDate, endDate: LocalDate): List<DailyCheckIn>
    suspend fun getCheckInByExternalId(externalId: String): DailyCheckIn?

    /**
     * Saves a check-in, replacing any existing answer for the same date and slot.
     * Returns the row ID of the stored check-in.
     */
    suspend fun saveCheckIn(checkIn: DailyCheckIn): Long

    /** Attaches a health-chat conversation to an existing check-in. */
    suspend fun attachConversation(date: LocalDate, slot: CheckInSlot, conversationExternalId: String)

    suspend fun deleteCheckIn(date: LocalDate, slot: CheckInSlot)
    suspend fun deleteOldCheckIns(beforeDate: LocalDate)

    /** Insert-or-replace by primary key, used by the import path. */
    suspend fun upsertCheckIn(checkIn: DailyCheckIn)
}
