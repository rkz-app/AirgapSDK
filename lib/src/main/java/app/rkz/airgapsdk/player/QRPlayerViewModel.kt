package app.rkz.airgapsdk.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rkz.airgap.AirgapEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


enum class PlayButtonState { PLAY, PAUSE, REPLAY }

sealed class QRPlayerState {
    object Initial : QRPlayerState()
    object BuildingQR : QRPlayerState()
    data class QRError(val message: String) : QRPlayerState()
    object Paused : QRPlayerState()
    object Playing : QRPlayerState()
}


class QRPlayerViewModel(private val data: ByteArray) : ViewModel() {

    private val _state = MutableStateFlow<QRPlayerState>(QRPlayerState.Initial)
    val state: StateFlow<QRPlayerState> = _state.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _images = MutableStateFlow<List<Bitmap>>(emptyList())
    val images: StateFlow<List<Bitmap>> = _images.asStateFlow()

    var chunkSize: Int = 460

    val isPlaying: Boolean get() = _state.value == QRPlayerState.Playing

    val isLastIndex: Boolean
        get() = _images.value.isNotEmpty() && _currentIndex.value == _images.value.size - 1

    val playButtonState: PlayButtonState
        get() = when (_state.value) {
            is QRPlayerState.Paused -> if (isLastIndex) PlayButtonState.REPLAY else PlayButtonState.PLAY
            is QRPlayerState.Playing -> PlayButtonState.PAUSE
            else -> PlayButtonState.PLAY
        }


    fun play() {
        if (_images.value.isEmpty()) return
        if (isLastIndex) _currentIndex.value = 0
        _state.value = QRPlayerState.Playing
    }

    fun pause() {
        _state.value = QRPlayerState.Paused
    }

    fun forward() {
        _currentIndex.value = minOf(_images.value.size - 1, _currentIndex.value + 1)
    }

    fun backward() {
        _currentIndex.value = maxOf(0, _currentIndex.value - 1)
    }

    fun nextFrame() {
        if (_images.value.isEmpty()) return
        if (_currentIndex.value < _images.value.size - 1) {
            _currentIndex.value++
        } else {
            _state.value = QRPlayerState.Paused
        }
    }


    fun assemble() {
        _state.value = QRPlayerState.BuildingQR
        viewModelScope.launch {
            val result = buildQRsAsync()
            result.fold(
                onSuccess = { bitmaps ->
                    _images.value = bitmaps
                    _currentIndex.value = 0
                    _state.value = QRPlayerState.Paused
                },
                onFailure = { err ->
                    _state.value = QRPlayerState.QRError(err.message ?: "Unknown error")
                }
            )
        }
    }

    private suspend fun buildQRsAsync(): Result<List<Bitmap>> = withContext(Dispatchers.Default) {
        try {
           val encoder = AirgapEncoder(data, chunkSize, qrSize = 1200)
           val count = encoder.chunkCount
           val bytes: List<ByteArray> = (0 until count).map { i -> encoder.generatePng(i)}
            val bitmaps = bytes.map { BitmapFactory.decodeByteArray(it, 0, it.size) }
           Result.success(bitmaps)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resetToInitial() {
        _state.value = QRPlayerState.Initial
        _currentIndex.value = 0
    }
}
