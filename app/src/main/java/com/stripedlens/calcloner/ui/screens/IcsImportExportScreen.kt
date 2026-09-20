package com.stripedlens.calcloner.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stripedlens.calcloner.CalendarInfo
import com.stripedlens.calcloner.domain.ics.ExportFormat
import com.stripedlens.calcloner.ui.components.UnifiedDateRangeCard
import com.stripedlens.calcloner.ui.theme.TitaniumMint
import com.stripedlens.calcloner.viewmodel.MainUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IcsImportExportScreen(
    uiState: MainUiState,
    onNavigateBack: () -> Unit,
    onSelectExportCalendar: (calendarId: Long, selected: Boolean) -> Unit,
    onSelectAllExportCalendars: (selectAll: Boolean) -> Unit,
    onSetExportFormat: (ExportFormat) -> Unit,
    onSetExportAsZip: (Boolean) -> Unit,
    onSetDateWindow: (daysPast: Int, daysFuture: Int) -> Unit,
    onExportToFolder: (folderUri: Uri) -> Unit,
    onExportToZip: (zipUri: Uri) -> Unit,
    onDismissExportResult: () -> Unit,
    onPickImportFile: (fileUri: Uri, fileName: String) -> Unit,
    onSelectImportTargetCalendar: (CalendarInfo) -> Unit,
    onExecuteImport: () -> Unit,
    onDismissImportResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Export, 1 = Import

    // SAF Launchers
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { onExportToFolder(it) }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { onExportToZip(it) }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = queryFileName(context, it)
            onPickImportFile(it, fileName)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Calendar Files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "ICS & CSV IMPORT / EXPORT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TitaniumMint.Mint400
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Mode Tabs (Export Calendars vs Import File)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = TitaniumMint.Mint400,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = TitaniumMint.Mint400,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Export Calendars",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )

                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Import ICS File",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
            }

            // Tab Content
            if (selectedTab == 0) {
                ExportTabContent(
                    uiState = uiState,
                    onSelectCalendar = onSelectExportCalendar,
                    onSelectAllCalendars = onSelectAllExportCalendars,
                    onSetFormat = onSetExportFormat,
                    onSetAsZip = onSetExportAsZip,
                    onSetDateWindow = onSetDateWindow,
                    onTriggerExport = {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                        if (uiState.icsExportAsZip) {
                            zipLauncher.launch("CalCloner_Export_$timestamp.zip")
                        } else {
                            folderLauncher.launch(null)
                        }
                    }
                )
            } else {
                ImportTabContent(
                    uiState = uiState,
                    onPickFile = { importFileLauncher.launch(arrayOf("*/*", "text/calendar", "text/x-vcalendar")) },
                    onSelectTargetCalendar = onSelectImportTargetCalendar,
                    onExecuteImport = onExecuteImport
                )
            }
        }
    }

    // Export Result Dialog
    uiState.icsExportResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissExportResult,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400
                    )
                    Text("Export Complete", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Successfully exported ${result.totalEvents} event(s) across ${result.fileCount} file(s).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = result.destinationSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Completed in ${result.durationMs} ms",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TitaniumMint.Mint400
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismissExportResult,
                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumMint.Mint500)
                ) {
                    Text("Done", color = Color(0xFF003824), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Import Result Dialog
    uiState.icsImportResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissImportResult,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400
                    )
                    Text("Import Complete", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Target Calendar: ${result.calendarName}",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• ${result.importedCount} event(s) imported successfully\n• ${result.skippedCount} duplicate event(s) skipped",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Completed in ${result.durationMs} ms",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = TitaniumMint.Mint400
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onDismissImportResult,
                    colors = ButtonDefaults.buttonColors(containerColor = TitaniumMint.Mint500)
                ) {
                    Text("Done", color = Color(0xFF003824), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ExportTabContent(
    uiState: MainUiState,
    onSelectCalendar: (Long, Boolean) -> Unit,
    onSelectAllCalendars: (Boolean) -> Unit,
    onSetFormat: (ExportFormat) -> Unit,
    onSetAsZip: (Boolean) -> Unit,
    onSetDateWindow: (Int, Int) -> Unit,
    onTriggerExport: () -> Unit
) {
    val sampleDate = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Format Selection ───────────────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "EXPORT FILE FORMAT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TitaniumMint.Mint400
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FormatOptionCard(
                            title = "iCalendar (.ics)",
                            subtitle = "RFC 5545 standard with reminders & color",
                            selected = uiState.icsExportFormat == ExportFormat.ICS,
                            onClick = { onSetFormat(ExportFormat.ICS) },
                            modifier = Modifier.weight(1f)
                        )
                        FormatOptionCard(
                            title = "Spreadsheet (.csv)",
                            subtitle = "Standard CSV table for Excel & Sheets",
                            selected = uiState.icsExportFormat == ExportFormat.CSV,
                            onClick = { onSetFormat(ExportFormat.CSV) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── 2. Packaging Options (Folder vs ZIP) ──────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DESTINATION PACKAGING",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TitaniumMint.Mint400
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PackagingOptionCard(
                            icon = Icons.Default.Folder,
                            title = "Folder (Default)",
                            subtitle = "Individual files saved directly to folder",
                            selected = !uiState.icsExportAsZip,
                            onClick = { onSetAsZip(false) },
                            modifier = Modifier.weight(1f)
                        )
                        PackagingOptionCard(
                            icon = Icons.Default.Archive,
                            title = "ZIP Archive",
                            subtitle = "Single .zip containing files + manifest",
                            selected = uiState.icsExportAsZip,
                            onClick = { onSetAsZip(true) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── 3. Date Range Window ──────────────────────────────────────────────
        item {
            UnifiedDateRangeCard(
                daysPast = uiState.icsDaysPast,
                daysFuture = uiState.icsDaysFuture,
                onRangeChanged = onSetDateWindow,
                title = "EXPORT DATE RANGE",
                description = "Define which events to export. Choose All Events for a complete backup."
            )
        }

        // ── 4. Multi-Calendar Selection Checklist ─────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "CALENDARS TO EXPORT",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TitaniumMint.Mint400
                            )
                            Text(
                                text = "${uiState.icsSelectedCalendarIds.size} of ${uiState.availableCalendars.size} selected",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val allSelected = uiState.availableCalendars.isNotEmpty() &&
                                uiState.icsSelectedCalendarIds.size == uiState.availableCalendars.size
                        TextButton(
                            onClick = { onSelectAllCalendars(!allSelected) }
                        ) {
                            Text(
                                text = if (allSelected) "Deselect All" else "Select All",
                                fontSize = 12.sp,
                                color = TitaniumMint.Mint400,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        uiState.availableCalendars.forEach { cal ->
                            val isChecked = uiState.icsSelectedCalendarIds.contains(cal.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSelectCalendar(cal.id, !isChecked) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { onSelectCalendar(cal.id, it) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = TitaniumMint.Mint500,
                                        checkmarkColor = Color(0xFF003824)
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(Color(cal.color ?: 0xFF00D47E.toInt()), shape = CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = cal.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = cal.accountName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 5. Filename Pattern Preview ───────────────────────────────────────
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "FILENAME FORMAT PREVIEW",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "<Calendar Name> - Export - $sampleDate.${uiState.icsExportFormat.extension}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TitaniumMint.Mint400
                    )
                    Text(
                        text = "Individual files are created per calendar. Date and time formatted down to the second.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── 6. Export Execution Button & Status ────────────────────────────────
        item {
            val canExport = uiState.icsSelectedCalendarIds.isNotEmpty() && !uiState.icsIsOperating

            Button(
                onClick = onTriggerExport,
                enabled = canExport,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TitaniumMint.Mint500,
                    disabledContainerColor = TitaniumMint.Mint500.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (uiState.icsIsOperating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF003824),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.icsProgressText ?: "Exporting...",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF003824)
                    )
                } else {
                    Icon(
                        imageVector = if (uiState.icsExportAsZip) Icons.Default.Archive else Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFF003824)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.icsExportAsZip) "Choose ZIP Save Location" else "Choose Output Folder & Export",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF003824)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportTabContent(
    uiState: MainUiState,
    onPickFile: () -> Unit,
    onSelectTargetCalendar: (CalendarInfo) -> Unit,
    onExecuteImport: () -> Unit
) {
    var showTargetCalendarMenu by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. File Selection Card ────────────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. SELECT ICS FILE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TitaniumMint.Mint400
                    )

                    Surface(
                        onClick = onPickFile,
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = TitaniumMint.Mint400,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = uiState.icsImportFileName ?: "Choose .ics Calendar File",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (uiState.icsImportFileName != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (uiState.icsImportParsedEvents != null) {
                                        "${uiState.icsImportParsedEvents.size} event(s) parsed and ready"
                                    } else {
                                        "Tap to browse device storage (.ics files only)"
                                    },
                                    fontSize = 12.sp,
                                    color = if (uiState.icsImportParsedEvents != null) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2. Target Calendar Selection ──────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "2. DESTINATION CALENDAR",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TitaniumMint.Mint400
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            onClick = { showTargetCalendarMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uiState.icsImportTargetCalendar != null) {
                                    val cal = uiState.icsImportTargetCalendar
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(Color(cal.color ?: 0xFF00D47E.toInt()), shape = CircleShape)
                                        )
                                        Column {
                                            Text(
                                                text = cal.displayName,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = cal.accountName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = "Select Destination Calendar",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = TitaniumMint.Mint400
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTargetCalendarMenu,
                            onDismissRequest = { showTargetCalendarMenu = false }
                        ) {
                            uiState.availableCalendars.forEach { cal ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .background(Color(cal.color ?: 0xFF00D47E.toInt()), shape = CircleShape)
                                            )
                                            Column {
                                                Text(cal.displayName, fontWeight = FontWeight.Medium)
                                                Text(
                                                    cal.accountName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        onSelectTargetCalendar(cal)
                                        showTargetCalendarMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 3. Safety & Rollback Guarantee Banner ─────────────────────────────
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = TitaniumMint.Mint500.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, TitaniumMint.Mint500.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Safe Import & Tagging Guarantee",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• All imported events are stamped with [CalCloner-ID: import:<id>]. You can purge them at any time using the Delete Tool.\n• Duplicate events with matching title and start time will be automatically skipped to prevent double-booking.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── 4. Import Button ──────────────────────────────────────────────────
        item {
            val canImport = uiState.icsImportParsedEvents != null &&
                    uiState.icsImportTargetCalendar != null &&
                    !uiState.icsIsOperating

            Button(
                onClick = onExecuteImport,
                enabled = canImport,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TitaniumMint.Mint500,
                    disabledContainerColor = TitaniumMint.Mint500.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                if (uiState.icsIsOperating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF003824),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.icsProgressText ?: "Importing events...",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF003824)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = Color(0xFF003824)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import Events into Target",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF003824)
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) TitaniumMint.Mint500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = if (selected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurface
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PackagingOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) TitaniumMint.Mint500.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (selected) TitaniumMint.Mint400 else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = TitaniumMint.Mint400,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String {
    var result = "calendar.ics"
    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                val name = cursor.getString(nameIndex)
                if (!name.isNullOrBlank()) {
                    result = name
                }
            }
        }
    } catch (_: Exception) {}
    return result
}
