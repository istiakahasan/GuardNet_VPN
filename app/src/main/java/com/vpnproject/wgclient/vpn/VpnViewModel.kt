package com.vpnproject.wgclient.vpn

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vpnproject.wgclient.data.ConfigManager
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    /** [since] is wall-clock millis when the tunnel came up. */
    data class Connected(val since: Long) : ConnectionState
}

data class UiState(
    val connection: ConnectionState = ConnectionState.Disconnected,
    val hasConfig: Boolean = false,
    val server: String? = null,
    val rxBytes: Long = 0,
    val txBytes: Long = 0
)

class VpnViewModel(app: Application) : AndroidViewModel(app) {

    private val configManager = ConfigManager(app)
    private val backend = GoBackend(app)

    private val tunnel = object : Tunnel {
        override fun getName() = "wg0"
        override fun onStateChange(newState: Tunnel.State) {
            // Also fires if the system drops the VPN (e.g. another VPN app takes over).
            if (newState == Tunnel.State.UP) markUp() else markDown()
        }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** One-shot messages shown in a Snackbar. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var statsJob: Job? = null

    init {
        refreshConfigInfo()
        // Re-sync if the VPN is already up (e.g. after the process was kept alive).
        viewModelScope.launch(Dispatchers.IO) {
            if (runCatching { backend.getState(tunnel) }.getOrNull() == Tunnel.State.UP) markUp()
        }
    }

    private fun refreshConfigInfo() {
        val config = configManager.load()
        _state.update { it.copy(hasConfig = configManager.hasConfig(), server = config?.serverName()) }
    }

    private fun Config.serverName(): String? =
        peers.firstOrNull()?.endpoint?.orElse(null)?.host

    fun importConfig(text: String) {
        if (configManager.save(text) != null) {
            refreshConfigInfo()
            say("Config imported. You're ready to connect.")
        } else {
            say("That config isn't valid. Make sure it's a complete WireGuard .conf file.")
        }
    }

    fun removeConfig() {
        disconnect()
        configManager.delete()
        refreshConfigInfo()
    }

    fun connect() {
        if (_state.value.connection != ConnectionState.Disconnected) return
        val config = configManager.load()
        if (config == null) {
            say("Config invalid. Please import it again.")
            return
        }
        _state.update { it.copy(connection = ConnectionState.Connecting) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backend.setState(tunnel, Tunnel.State.UP, config)
                markUp()
            } catch (e: Exception) {
                markDown()
                say("Couldn't connect. Check your internet and server details.")
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
            markDown()
        }
    }

    fun permissionDenied() = say("VPN permission denied. The VPN can't start without it.")

    fun say(message: String) {
        _messages.trySend(message)
    }

    private fun markUp() {
        if (_state.value.connection is ConnectionState.Connected) return
        _state.update { it.copy(connection = ConnectionState.Connected(System.currentTimeMillis())) }
        statsJob?.cancel()
        statsJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { backend.getStatistics(tunnel) }.getOrNull()?.let { s ->
                    _state.update { it.copy(rxBytes = s.totalRx(), txBytes = s.totalTx()) }
                }
                delay(1000)
            }
        }
    }

    private fun markDown() {
        statsJob?.cancel()
        _state.update { it.copy(connection = ConnectionState.Disconnected, rxBytes = 0, txBytes = 0) }
    }
}
