package com.example.core.item

/**
 * Registry of all feature modules that provide Settings Items.
 * Serves as the domain-level aggregation point.
 */
object CategoryItemRegistry {

    private val providers = mutableMapOf<String, CategoryItemProvider>()

    fun register(provider: CategoryItemProvider) {
        providers[provider.categoryId.lowercase()] = provider
    }

    fun getProvider(categoryId: String?): CategoryItemProvider? {
        if (categoryId == null) return null
        return providers[categoryId.lowercase()]
    }

    fun getAllProviders(): Collection<CategoryItemProvider> = providers.values

    fun clear() {
        providers.clear()
    }
}
