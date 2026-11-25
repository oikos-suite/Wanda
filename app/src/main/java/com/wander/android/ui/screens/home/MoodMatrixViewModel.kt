package com.wander.android.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wander.android.core.playback.PlayerConnection
import com.wander.android.data.repository.InstantRadioRepository
import com.wander.android.data.repository.MoodPreset
import com.wander.android.data.repository.MoodRadioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MoodMatrixUiState(
    /** Null until a mood is picked, or once its radio has started playing. */
    val playingKey: String? = null,
    /** "Surprise me" is building its station. */
    val isSurprising: Boolean = false
)

/**
 * Backs the mood radios on Search's start page. One tap on a mood starts its radio immediately —
 * the same "pick a mood, it plays" pattern YouTube Music uses — rather than picking a point and
 * then confirming with a separate button. [MoodRadioRepository] answers across every connected
 * source, never just one. "Surprise me" is the radio with no mood at all; see
 * [InstantRadioRepository].
 */
@HiltViewModel
class MoodMatrixViewModel @Inject constructor(
    private val moodRadioRepository: MoodRadioRepository,
    private val instantRadio: InstantRadioRepository,
    private val playerConnection: PlayerConnection
) : ViewModel() {

    private val _state = MutableStateFlow(MoodMatrixUiState())
    val state: StateFlow<MoodMatrixUiState> = _state.asStateFlow()

    fun playMood(preset: MoodPreset) {
        _state.value = MoodMatrixUiState(playingKey = preset.key)
        viewModelScope.launch {
            val tracks = moodRadioRepository.radioFor(preset.tempo, preset.energy, limit = QUEUE_LIMIT)
            if (tracks.isNotEmpty()) playerConnection.play(tracks)
        }
    }

    /** Starts a station with nothing to go on — no seed, no mood, no chosen playlist. */
    fun playSurprise() {
        if (_state.value.isSurprising) return
        _state.value = MoodMatrixUiState(isSurprising = true)
        viewModelScope.launch {
            val station = instantRadio.buildStation()
            _state.value = MoodMatrixUiState()
            if (station.isEmpty()) {
                playerConnection.notifyNoStation()
                return@launch
            }
            playerConnection.play(station)
            playerConnection.setRadioMode(true)
        }
    }

    private companion object {
        const val QUEUE_LIMIT = 30
    }
}
