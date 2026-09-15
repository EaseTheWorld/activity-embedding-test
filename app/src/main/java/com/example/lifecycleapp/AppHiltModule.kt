package com.example.lifecycleapp

import com.example.common.ui.settings.HardwarePropertyStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Top-level application Hilt module providing singletons for vehicle hardware storage.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppHiltModule {

    @Provides
    @Singleton
    fun provideHardwarePropertyStorage(): HardwarePropertyStorage {
        VehicleHardwareSimulator.init()
        return VehicleHardwareSimulator.hardwareStorage
    }
}