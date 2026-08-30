package dev.gomoku.yixindroid.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding")

/** Whether the welcome guide has been seen. Its own store, so clearing app data
 *  brings the guide back without touching settings the user chose. */
@Singleton
class OnboardingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val seenKey = booleanPreferencesKey("welcome_seen")

    val welcomeSeen: Flow<Boolean> = context.onboardingDataStore.data.map { it[seenKey] ?: false }

    suspend fun markWelcomeSeen() {
        context.onboardingDataStore.edit { it[seenKey] = true }
    }

    suspend fun resetWelcome() {
        context.onboardingDataStore.edit { it[seenKey] = false }
    }
}
