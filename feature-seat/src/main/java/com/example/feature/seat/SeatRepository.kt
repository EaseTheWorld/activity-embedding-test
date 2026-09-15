package com.example.feature.seat

import com.example.common.ui.settings.InMemorySettingRepository
import com.example.common.ui.settings.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for Seat domain local persistence settings.
 */
interface SeatSettingRepository {
    val easyEntryExitRepository: SettingRepository<Boolean>
}

/**
 * Reference/Default implementation of [SeatSettingRepository].
 */
@Singleton
class SeatSettingRepositoryImpl @Inject constructor() : SeatSettingRepository {
    override val easyEntryExitRepository: SettingRepository<Boolean> =
        InMemorySettingRepository(initialValue = true)
}