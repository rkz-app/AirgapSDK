package app.rkz.airgapsdk.consumer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.rkz.airgap.AirgapDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------

sealed class ScannerState {
    object Idle       : ScannerState()
    object Scanning   : ScannerState()
    object Processing : ScannerState()
    data class Success(val data: ByteArray) : ScannerState()
    data class Error(val message: String)   : ScannerState()
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

class QRScannerViewModel : ViewModel() {

    private val _state = MutableStateFlow<ScannerState>(ScannerState.Idle)
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private val _scannedChunks = MutableStateFlow<Set<Int>>(emptySet())
    val scannedChunks: StateFlow<Set<Int>> = _scannedChunks.asStateFlow()

    private val _totalChunks = MutableStateFlow(0)
    val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

    private val _progress = MutableStateFlow(0.0)
    val progress: StateFlow<Double> = _progress.asStateFlow()

    private var decoder: AirgapDecoder? = null

    // -----------------------------------------------------------------------

    fun startScanning() {
        _state.value        = ScannerState.Scanning
        _scannedChunks.value = emptySet()
        _totalChunks.value  = 0
        _progress.value     = 0.0
        decoder             = AirgapDecoder()
    }

    fun processQRCode(code: String) {
        if (_state.value !is ScannerState.Scanning) return
        val dec = decoder ?: return

        try {
            val result = dec.processQrString(code)

            _scannedChunks.value += result.chunkNumber.toInt()
            _totalChunks.value   = result.totalChunks.toInt()

            if (_totalChunks.value > 0) {
                _progress.value = _scannedChunks.value.size.toDouble() / _totalChunks.value
            }

            if (dec.isComplete) {
                _state.value = ScannerState.Processing
                completeScanning()
            }
        } catch (e: Exception) {
            _state.value = ScannerState.Error(e.message ?: "Unknown error")
        }
    }

    private fun completeScanning() {
        val dec = decoder ?: run {
            _state.value = ScannerState.Error("Decoder not initialized")
            return
        }
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.Default) { dec.getData() }
                _state.value = ScannerState.Success(data)
            } catch (e: Exception) {
                _state.value = ScannerState.Error("Failed to decode data: ${e.message}")
            }
        }
    }

    fun reset() {
        _state.value         = ScannerState.Idle
        _scannedChunks.value = emptySet()
        _totalChunks.value   = 0
        _progress.value      = 0.0
        decoder              = null
    }
}
