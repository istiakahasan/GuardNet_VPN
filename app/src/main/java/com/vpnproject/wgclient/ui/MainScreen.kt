package com.vpnproject.wgclient.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import com.vpnproject.wgclient.vpn.ConnectionState
import com.vpnproject.wgclient.vpn.UiState
import com.vpnproject.wgclient.R
import com.vpnproject.wgclient.vpn.VpnViewModel
import com.vpnproject.wgclient.ui.theme.connectButtonColors
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: VpnViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showPaste by remember { mutableStateOf(false) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    // System VPN permission dialog
    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) vm.connect() else vm.permissionDenied()
    }

    // File picker (Storage Access Framework)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
            }.getOrNull()
            if (text != null) vm.importConfig(text) else vm.say("Couldn't read that file.")
        }
    }

    fun requestConnect() {
        val intent = VpnService.prepare(context)
        if (intent != null) vpnPermission.launch(intent) else vm.connect()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (ui.hasConfig) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Replace config file") },
                                    onClick = { menuOpen = false; picker.launch(arrayOf("*/*")) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Paste config") },
                                    onClick = { menuOpen = false; showPaste = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove config") },
                                    onClick = { menuOpen = false; showRemoveConfirm = true }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            AnimatedContent(
                targetState = ui.hasConfig,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "onboarding"
            ) { hasConfig ->
                if (!hasConfig) {
                    Onboarding(
                        onImport = { picker.launch(arrayOf("*/*")) },
                        onPaste = { showPaste = true }
                    )
                } else {
                    ConnectContent(
                        ui = ui,
                        onToggle = {
                            when (ui.connection) {
                                is ConnectionState.Disconnected -> requestConnect()
                                is ConnectionState.Connected -> showDisconnectConfirm = true
                                is ConnectionState.Connecting -> Unit
                            }
                        }
                    )
                }
            }
        }
    }

    if (showPaste) {
        PasteDialog(
            onDismiss = { showPaste = false },
            onConfirm = { showPaste = false; vm.importConfig(it) }
        )
    }
    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect VPN?") },
            text = { Text("Your traffic will no longer be protected.") },
            confirmButton = {
                TextButton(onClick = { showDisconnectConfirm = false; vm.disconnect() }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) { Text("Stay connected") }
            }
        )
    }
    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove config?") },
            text = { Text("You'll need to import a config again to connect.") },
            confirmButton = {
                TextButton(onClick = { showRemoveConfirm = false; vm.removeConfig() }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun Onboarding(onImport: () -> Unit, onPaste: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text("Welcome", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add your WireGuard config to get started. You can import the .conf file or paste its contents.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Import Config")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onPaste, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Paste Config")
        }
    }
}

@Composable
private fun ConnectContent(
    ui: UiState,
    onToggle: () -> Unit
) {
    val conn = ui.connection
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        StatusCard(ui)
        Spacer(Modifier.weight(1f))
        ConnectButton(conn, onToggle)
        Spacer(Modifier.weight(1f))
        StatsRow(ui)
    }
}

@Composable
private fun StatusCard(ui: UiState) {
    val conn = ui.connection
    val label = when (conn) {
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Connected -> "Connected"
    }
    val detail = when (conn) {
        is ConnectionState.Connected -> "via ${ui.server ?: "your server"}"
        is ConnectionState.Connecting -> ui.server ?: "Please wait"
        is ConnectionState.Disconnected -> "Tap the button to connect"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedContent(
                targetState = label,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "status"
            ) { Text(it, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.height(4.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (conn is ConnectionState.Connected) {
                Spacer(Modifier.height(8.dp))
                ConnectedTimer(conn.since)
            }
        }
    }
}

@Composable
private fun ConnectedTimer(since: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val s = ((now - since) / 1000).coerceAtLeast(0)
    Text(
        String.format(Locale.US, "Connected for %02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60),
        style = MaterialTheme.typography.bodyLarge
    )
}

@Composable
private fun ConnectButton(conn: ConnectionState, onClick: () -> Unit) {
    val colors = connectButtonColors(conn)
    val container by animateColorAsState(colors.container, tween(400), label = "container")
    val content by animateColorAsState(colors.content, tween(400), label = "content")
    val connecting = conn is ConnectionState.Connecting
    val stateText = when (conn) {
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Connected -> "Connected"
    }
    val action = when (conn) {
        is ConnectionState.Disconnected -> "Connect"
        is ConnectionState.Connecting -> "Connecting"
        is ConnectionState.Connected -> "Disconnect"
    }

    Box(contentAlignment = Alignment.Center) {
        Surface(
            onClick = onClick,
            enabled = !connecting, // prevents double taps while connecting
            shape = CircleShape,
            color = container,
            contentColor = content,
            shadowElevation = 8.dp,
            modifier = Modifier
                .size(200.dp)
                .semantics {
                    contentDescription = "$action VPN"
                    stateDescription = stateText
                    role = Role.Button
                }
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(8.dp))
                Text(action.uppercase(), style = MaterialTheme.typography.labelLarge)
            }
        }
        if (connecting) {
            CircularProgressIndicator(
                modifier = Modifier.size(224.dp),
                color = container,
                strokeWidth = 6.dp
            )
        }
    }
}

@Composable
private fun StatsRow(ui: UiState) {
    val active = ui.connection is ConnectionState.Connected
    Row(Modifier.fillMaxWidth().height(72.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        androidx.compose.animation.AnimatedVisibility(visible = active) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat("Download", formatBytes(ui.rxBytes))
                Stat("Upload", formatBytes(ui.txBytes))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste config") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("WireGuard config") },
                modifier = Modifier.fillMaxWidth().height(220.dp)
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
}
