package com.spyboy.camxploit.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.spyboy.camxploit.CameraDatabase
import com.spyboy.camxploit.CameraMonitorService
import com.spyboy.camxploit.FeedCheck
import com.spyboy.camxploit.FeedHealth
import com.spyboy.camxploit.FeedLibrary
import com.spyboy.camxploit.SavedCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private val libraryGreen = Color(0xFF39FF14)
private val libraryCyan = Color(0xFF00FFFF)
private val libraryCard = Color(0xFF151515)
private val libraryMuted = Color(0xFF9E9E9E)

/**
 * The saved-camera tab is a feed library, not a bare IP list: search, filters,
 * favorites, thumbnails, rename/delete, and a manual re-check that refreshes
 * expiring public tokens before marking a feed online.
 */
@Composable
fun SavedFeedLibrary(
    onPlay: (SavedCamera) -> Unit,
    onOpenConsole: (SavedCamera) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val dao = remember { CameraDatabase.getDatabase(context).cameraDao() }
    val cameras by dao.getAllCameras().collectAsState(initial = emptyList())

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var isRechecking by remember { mutableStateOf(false) }
    var recheckProgress by remember { mutableStateOf(0 to 0) }
    var monitorRunning by remember { mutableStateOf(CameraMonitorService.isRunning) }
    var renameTarget by remember { mutableStateOf<SavedCamera?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<SavedCamera?>(null) }

    fun feedLike(camera: SavedCamera): Boolean =
        camera.isFeed || camera.pageUrl.isNotBlank() || camera.sourceLabel.isNotBlank() ||
            camera.brand.equals("Public", true)

    val filtered = cameras.filter { camera ->
        val haystack = listOf(
            camera.nickname, camera.ip, camera.streamUrl, camera.pageUrl, camera.sourceLabel
        ).joinToString(" ").lowercase()
        val matchesQuery = query.isBlank() || haystack.contains(query.trim().lowercase())
        val matchesFilter = when (filter) {
            "FEEDS" -> feedLike(camera)
            "FAVORITES" -> camera.isFavorite
            "LAN" -> !feedLike(camera)
            else -> true
        }
        matchesQuery && matchesFilter
    }.sortedWith(
        compareByDescending<SavedCamera> { it.isFavorite }
            .thenByDescending { it.isOnline }
            .thenByDescending { it.lastVerified }
    )

    val online = cameras.count { it.isOnline }
    val feedCount = cameras.count(::feedLike)

    fun recheckAll() {
        if (isRechecking || cameras.isEmpty()) return
        scope.launch {
            isRechecking = true
            val snapshot = cameras
            var checked = 0
            recheckProgress = 0 to snapshot.size
            snapshot.forEach { camera ->
                val status = runCatching { FeedHealth.check(camera) }
                    .getOrElse { FeedCheck(false, "Unavailable") }
                val now = System.currentTimeMillis()
                dao.updateCamera(
                    camera.copy(
                        isOnline = status.online,
                        lastSeen = if (status.online) now else camera.lastSeen,
                        lastVerified = now,
                        verification = status.verification,
                        contentType = status.contentType.ifBlank { camera.contentType },
                        streamUrl = status.resolvedUrl.ifBlank { camera.streamUrl }
                    )
                )
                checked += 1
                recheckProgress = checked to snapshot.size
                delay(250)
            }
            isRechecking = false
            Toast.makeText(
                context,
                "Checked $checked saved camera${if (checked == 1) "" else "s"}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun toggleMonitor() {
        val intent = Intent(context, CameraMonitorService::class.java)
        val ok = runCatching {
            if (monitorRunning) {
                context.stopService(intent)
            } else {
                ContextCompat.startForegroundService(context, intent)
            }
        }.isSuccess
        if (ok) {
            monitorRunning = !monitorRunning
        } else {
            Toast.makeText(context, "Could not change monitor state", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 12.dp)
            .padding(top = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Videocam, null, tint = libraryGreen, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SAVED FEEDS",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    "$feedCount feeds · ${cameras.size - feedCount} LAN · $online online",
                    color = libraryMuted,
                    fontSize = 10.sp
                )
            }
            TextButton(onClick = ::toggleMonitor) {
                Text(
                    if (monitorRunning) "MONITOR ON" else "MONITOR OFF",
                    color = if (monitorRunning) libraryGreen else Color.Gray,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search saved cameras…", color = Color.DarkGray, fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, null, tint = Color.DarkGray, modifier = Modifier.size(16.dp))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = libraryCard,
                unfocusedContainerColor = libraryCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = libraryGreen,
                unfocusedBorderColor = Color.DarkGray
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("ALL", "FEEDS", "FAVORITES", "LAN").forEach { label ->
                FilterChip(
                    selected = filter == label,
                    onClick = { filter = label },
                    label = {
                        Text(
                            label,
                            fontSize = 9.sp,
                            color = if (filter == label) Color.Black else Color.White
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = libraryGreen,
                        containerColor = libraryCard
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = ::recheckAll, enabled = !isRechecking && cameras.isNotEmpty()) {
                Icon(
                    Icons.Default.Refresh,
                    null,
                    tint = if (isRechecking) Color.DarkGray else libraryCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (isRechecking) "CHECKING" else "RE-CHECK ALL",
                    color = if (isRechecking) Color.DarkGray else libraryCyan,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isRechecking) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = libraryGreen,
                trackColor = Color(0xFF222222)
            )
            Text(
                "${recheckProgress.first}/${recheckProgress.second} checked",
                color = Color.Gray,
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 3.dp)
            )
        }

        Spacer(Modifier.height(6.dp))

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Videocam,
                        null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(42.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (cameras.isEmpty()) "No saved feeds yet" else "No cameras match this view",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Text(
                        "Sniff a browser page, then tap the save icon on a verified feed.",
                        color = Color.DarkGray,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(filtered, key = { it.id }) { camera ->
                    SavedFeedCard(
                        camera = camera,
                        isFeed = feedLike(camera),
                        onPlay = { onPlay(camera) },
                        onToggleFavorite = {
                            scope.launch { dao.setFavorite(camera.id, !camera.isFavorite) }
                        },
                        onRename = {
                            renameTarget = camera
                            renameText = camera.nickname
                        },
                        onDelete = { deleteTarget = camera },
                        onCopy = {
                            clipboard.setText(AnnotatedString(camera.streamUrl.ifBlank { camera.ip }))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        onOpenPage = {
                            if (camera.pageUrl.isNotBlank()) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(camera.pageUrl))
                                    )
                                }
                            }
                        },
                        onOpenConsole = { onOpenConsole(camera) }
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename camera") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renameText.trim()
                        if (name.isNotEmpty()) {
                            scope.launch { dao.renameCamera(target.id, name) }
                        }
                        renameTarget = null
                    }
                ) { Text("SAVE") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("CANCEL") }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete saved feed?") },
            text = { Text(target.nickname) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { FeedLibrary.delete(context, target) }
                        deleteTarget = null
                    }
                ) { Text("DELETE", color = Color(0xFFFF5252)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("CANCEL") }
            }
        )
    }
}

@Composable
private fun SavedFeedCard(
    camera: SavedCamera,
    isFeed: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onOpenPage: () -> Unit,
    onOpenConsole: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val hasPlayableUrl = camera.streamUrl.isNotBlank() || camera.remoteUrl?.isNotBlank() == true
    val statusColor = when {
        camera.isOnline -> libraryGreen
        camera.lastVerified > 0 -> Color(0xFFFF5252)
        else -> Color.Gray
    }
    val statusText = when {
        camera.isOnline -> "ONLINE"
        camera.lastVerified > 0 -> "OFFLINE"
        else -> "UNCHECKED"
    }
    val thumbnailPath = camera.thumbnailPath
    val thumbnailModel: Any? = when {
        !thumbnailPath.isNullOrBlank() && File(thumbnailPath).exists() -> File(thumbnailPath)
        !camera.thumbnailUrl.isNullOrBlank() -> camera.thumbnailUrl
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (hasPlayableUrl) onPlay() else onOpenConsole() },
        colors = CardDefaults.cardColors(containerColor = libraryCard),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (camera.isFavorite) libraryGreen.copy(alpha = 0.35f) else Color(0xFF262626)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(86.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailModel != null) {
                    AsyncImage(
                        model = thumbnailModel,
                        contentDescription = "Saved feed preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Videocam,
                        null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        camera.nickname,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            if (camera.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (camera.isFavorite) "Remove favorite" else "Favorite",
                            tint = if (camera.isFavorite) Color(0xFFFFC107) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        camera.streamType.ifBlank { "FEED" }.uppercase(),
                        color = libraryCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (isFeed) "FEED" else "LAN",
                        color = Color.Gray,
                        fontSize = 8.sp
                    )
                }
                Text(
                    camera.streamUrl.ifBlank { camera.pageUrl.ifBlank { camera.ip } },
                    color = Color(0xFF9E9E9E),
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${camera.sourceLabel.ifBlank { if (isFeed) "Public feed" else "LAN camera" }} · " +
                        relativeTime(camera.lastVerified) +
                        if (camera.verification.isNotBlank()) " · ${camera.verification}" else "",
                    color = Color.Gray,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onPlay,
                    enabled = hasPlayableUrl,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        "Play",
                        tint = if (hasPlayableUrl) libraryGreen else Color.DarkGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            "More",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy URL", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            }
                        )
                        if (camera.pageUrl.isNotBlank()) {
                            DropdownMenuItem(
                                text = { Text("Open source page", fontSize = 12.sp) },
                                onClick = {
                                    menuExpanded = false
                                    onOpenPage()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Open console", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Terminal, null) },
                            onClick = {
                                menuExpanded = false
                                onOpenConsole()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", fontSize = 12.sp, color = Color(0xFFFF5252)) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252))
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "never checked"
    val diff = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "just checked"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
