package com.example.common.ui.settings

import com.example.core.item.ItemViewModelBinding
import com.example.core.item.ItemViewModelRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Core Hilt module for Settings system.
 * Collects all feature-provided [ItemViewModelBinding] multibindings and instantiates
 * the centralized [ItemViewModelRegistry].
 */
@Module
@InstallIn(SingletonComponent::class)
object CommonSettingsModule {

    @Provides
    @Singleton
    fun provideCoroutineScope(): kotlinx.coroutines.CoroutineScope {
        return AppScope.scope
    }

    @Provides
    @Singleton
    fun provideItemViewModelRegistry(
        bindings: Set<@JvmSuppressWildcards ItemViewModelBinding<*>>
    ): ItemViewModelRegistry {
        return ItemViewModelRegistry(bindings)
    }
}