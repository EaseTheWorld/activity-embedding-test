package com.example.feature.door

import com.example.common.ui.settings.InMemorySettingRepository
import com.example.common.ui.settings.SettingRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for Door domain settings.
 */
interface DoorSettingRepository {
    val unlockOnParkRepository: SettingRepository<Boolean>
}

/**
 * Reference/Default implementation of [DoorSettingRepository].
 */
@Singleton
class DoorSettingRepositoryImpl @Inject constructor() : DoorSettingRepository {
    override val unlockOnParkRepository: SettingRepository<Boolean> =
        InMemorySettingRepository(initialValue = true)
}

/**
 * Clean Architecture UseCase for mutating UnlockOnPark setting with validation.
 */
class SetUnlockOnParkUseCase @Inject constructor(
    private val repository: DoorSettingRepository
) {
    suspend operator fun invoke(enabled: Boolean) {
        repository.unlockOnParkRepository.save(enabled)
    }
}