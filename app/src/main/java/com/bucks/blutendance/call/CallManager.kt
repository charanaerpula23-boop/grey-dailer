package com.bucks.blutendance.call

import android.content.Context
import android.media.AudioManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Singleton that holds the current [Call] reference, audio state, and
 * the [InCallService] reference so we can control speaker / mute / BT
 * routing through the real Telecom audio APIs.
 */
object CallManager {

    /* ── Call state ───────────────────────────────────────────── */

    private val _currentCall = MutableStateFlow<Call?>(null)
    val currentCall: StateFlow<Call?> = _currentCall

    private val _callState = MutableStateFlow(Call.STATE_DISCONNECTED)
    val callState: StateFlow<Int> = _callState

    /* ── Audio state (speaker / mute) ────────────────────────── */

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted

    private val _audioRoute = MutableStateFlow(CallAudioState.ROUTE_EARPIECE)
    val audioRoute: StateFlow<Int> = _audioRoute

    /* ── Conference / multi-call ─────────────────────────────── */

    private val _calls = MutableStateFlow<List<Call>>(emptyList())
    val calls: StateFlow<List<Call>> = _calls

    /** Kept so we can drive setMuted / setAudioRoute. */
    private var inCallService: InCallService? = null

    /* ── Callbacks ────────────────────────────────────────────── */

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            // Only update the displayed state for the current call
            if (call == _currentCall.value) {
                _callState.value = state
            }
            if (state == Call.STATE_DISCONNECTED) {
                removeCall(call)
            }
        }
    }

    /* ── Service binding ─────────────────────────────────────── */

    fun bindService(service: InCallService) {
        inCallService = service
    }

    fun unbindService() {
        inCallService = null
    }

    /** Called by DialerCallService when audio state changes. */
    fun onAudioStateChanged(state: CallAudioState) {
        _isMuted.value = state.isMuted
        _audioRoute.value = state.route
    }

    /* ── Call management ─────────────────────────────────────── */

    fun addCall(call: Call) {
        call.registerCallback(callback)
        _calls.value = _calls.value + call
        // The most-recently-added call becomes "current"
        _currentCall.value = call
        _callState.value = call.state
    }

    fun removeCall(call: Call) {
        call.unregisterCallback(callback)
        _calls.value = _calls.value - call
        if (_currentCall.value == call) {
            _currentCall.value = _calls.value.lastOrNull()
            _callState.value = _currentCall.value?.state ?: Call.STATE_DISCONNECTED
        }
    }

    /** Legacy bridge – kept for DialerCallService.onCallAdded */
    fun setCurrentCall(call: Call?) {
        if (call != null) addCall(call)
        else {
            _currentCall.value = null
            _callState.value = Call.STATE_DISCONNECTED
        }
    }

    /* ── Basic call actions ───────────────────────────────────── */

    fun answer() {
        _currentCall.value?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun hangup() {
        _currentCall.value?.disconnect()
    }

    fun hold() {
        val call = _currentCall.value ?: return
        if (call.state == Call.STATE_ACTIVE) {
            call.hold()
        }
    }

    fun unhold() {
        // Try current call first
        val call = _currentCall.value
        if (call != null && call.state == Call.STATE_HOLDING) {
            call.unhold()
            return
        }
        // Fallback: find any held call in the list and unhold it
        _calls.value.firstOrNull { it.state == Call.STATE_HOLDING }?.unhold()
    }

    /* ── DTMF ─────────────────────────────────────────────────── */

    fun sendDtmf(digit: Char) {
        _currentCall.value?.playDtmfTone(digit)
        _currentCall.value?.stopDtmfTone()
    }

    /* ── Mute ──────────────────────────────────────────────────── */

    fun toggleMute() {
        val newMuted = !_isMuted.value
        inCallService?.setMuted(newMuted)
        _isMuted.value = newMuted
    }

    /* ── Speaker / earpiece / Bluetooth ────────────────────────── */

    fun toggleSpeaker() {
        val current = _audioRoute.value
        val newRoute = if (current == CallAudioState.ROUTE_SPEAKER)
            CallAudioState.ROUTE_EARPIECE
        else
            CallAudioState.ROUTE_SPEAKER
        inCallService?.setAudioRoute(newRoute)
        _audioRoute.value = newRoute
    }

    fun setAudioRouteBluetooth() {
        inCallService?.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
        _audioRoute.value = CallAudioState.ROUTE_BLUETOOTH
    }

    val isSpeaker: Boolean get() = _audioRoute.value == CallAudioState.ROUTE_SPEAKER
    val isBluetooth: Boolean get() = _audioRoute.value == CallAudioState.ROUTE_BLUETOOTH

    /* ── Conference / merge ─────────────────────────────────────── */

    /** Merge the current call with the held call (conference). */
    fun mergeConference() {
        _currentCall.value?.conference(_calls.value.firstOrNull { it != _currentCall.value })
    }

    /** Swap between active and held call. */
    fun swapCalls() {
        val active = _calls.value.firstOrNull { it.state == Call.STATE_ACTIVE }
        val held   = _calls.value.firstOrNull { it.state == Call.STATE_HOLDING }
        active?.hold()
        held?.unhold()
    }

    /** Number of live (non-disconnected) calls. */
    val liveCallCount: Int
        get() = _calls.value.count { it.state != Call.STATE_DISCONNECTED }
}

