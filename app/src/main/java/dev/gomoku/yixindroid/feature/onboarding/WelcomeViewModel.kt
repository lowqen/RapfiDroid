package dev.gomoku.yixindroid.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gomoku.yixindroid.data.prefs.OnboardingStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val store: OnboardingStore,
) : ViewModel() {

    /** Null until the store has answered — see [WelcomeGate] for why that matters. */
    val seen: StateFlow<Boolean?> = store.welcomeSeen.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    fun onSeen() {
        viewModelScope.launch { store.markWelcomeSeen() }
    }

    /** Settings ▸ ⓘ offers this: the guide is the one screen people go back for. */
    fun onReplay() {
        viewModelScope.launch { store.resetWelcome() }
    }
}
