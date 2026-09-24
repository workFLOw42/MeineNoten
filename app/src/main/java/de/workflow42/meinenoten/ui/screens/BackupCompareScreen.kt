package de.workflow42.meinenoten.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.workflow42.meinenoten.R
import de.workflow42.meinenoten.data.BackupLogic
import de.workflow42.meinenoten.model.*
import de.workflow42.meinenoten.ui.util.formatSetlistDate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupCompareScreen(
    analysisResult: BackupAnalysisResult,
    currentUserId: String,
    onApplyImport: (replaceAll: Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    /** Blocking; called off the main thread. `fromBackup` picks the copy in the backup. */
    loadPreview: (song: Song, fromBackup: Boolean) -> ScorePreview? = { _, _ -> null },
) {
    var replaceAll by remember { mutableStateOf(false) }
    var showReplaceAllConfirmDialog by remember { mutableStateOf(false) }

    // Previews only live as long as the screen: they are of no use afterwards.
    DisposableEffect(analysisResult) {
        onDispose { previewCache.clear() }
    }

    val manifest = analysisResult.manifest
    val askForIdentity = remember(analysisResult, currentUserId) {
        BackupLogic.shouldAskForIdentity(manifest, currentUserId)
    }
    val authorLabel = manifest.authorName.ifBlank { stringResource(R.string.compare_identity_unknown_name) }

    // Reads the snapshot state of every choice, so it follows each tap on a chip or switch.
    val missingBySong = BackupLogic.songsMissingFromSelectedSetlists(
        analysisResult.songs,
        analysisResult.setlists,
    )

    val differentMetaSongs = remember(analysisResult) {
        analysisResult.songs.filter { it.category == SongMatchCategory.SAME_FILE_DIFFERENT_METADATA }
    }
    val possibleOtherSongs = remember(analysisResult) {
        analysisResult.songs.filter { it.category == SongMatchCategory.POSSIBLE_OTHER_VERSION }
    }
    val newSongs = remember(analysisResult) {
        analysisResult.songs.filter { it.category == SongMatchCategory.NEW }
    }
    val identicalSongs = remember(analysisResult) {
        analysisResult.songs.filter { it.category == SongMatchCategory.IDENTICAL }
    }

    if (askForIdentity && !analysisResult.identityQuestionAnswered) {
        AlertDialog(
            onDismissRequest = { analysisResult.identityQuestionAnswered = true },
            title = { Text(stringResource(R.string.compare_identity_title)) },
            text = {
                val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date(manifest.createdAt))
                val origin = listOf(manifest.deviceName, dateStr).filter { it.isNotBlank() }.joinToString(" · ")
                Text(stringResource(R.string.compare_identity_msg, authorLabel, origin))
            },
            confirmButton = {
                TextButton(onClick = {
                    analysisResult.adoptAuthorIdentity = true
                    analysisResult.identityQuestionAnswered = true
                }) {
                    Text(stringResource(R.string.compare_identity_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    analysisResult.adoptAuthorIdentity = false
                    analysisResult.identityQuestionAnswered = true
                }) {
                    Text(stringResource(R.string.compare_identity_no))
                }
            },
        )
    }

    if (showReplaceAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceAllConfirmDialog = false },
            title = { Text(stringResource(R.string.compare_quick_replace_all)) },
            text = { Text(stringResource(R.string.compare_replace_all_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        replaceAll = true
                        analysisResult.songs.forEach { it.action = SongImportAction.TAKE_BACKUP }
                        analysisResult.setlists.forEach { it.importSetlist = true }
                        if (analysisResult.settingsInBackup != null) analysisResult.importSettings = true
                        showReplaceAllConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.compare_replace_all_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceAllConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    ) { innerPadding ->
        // Summary, quick choice and the import button stay fixed; only the per-song
        // decisions scroll underneath. One button that is always in reach replaces the
        // former pair at top and bottom.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HeaderCard(manifest = manifest, songCount = analysisResult.songs.size, setlistCount = analysisResult.setlists.size)

                if (analysisResult.missingFiles.isNotEmpty()) {
                    MissingFilesBanner(missingFiles = analysisResult.missingFiles)
                }

                QuickChoiceRow(
                    replaceAll = replaceAll,
                    onSelectSuggested = {
                        replaceAll = false
                        analysisResult.songs.forEach { item ->
                            item.action = when (item.category) {
                                SongMatchCategory.IDENTICAL -> SongImportAction.KEEP_OWN
                                SongMatchCategory.SAME_FILE_DIFFERENT_METADATA -> SongImportAction.KEEP_OWN
                                SongMatchCategory.POSSIBLE_OTHER_VERSION -> SongImportAction.KEEP_OWN
                                SongMatchCategory.NEW -> SongImportAction.TAKE_BACKUP
                            }
                            item.mergeForeignNotes = true
                        }
                        analysisResult.setlists.forEach { it.importSetlist = true }
                        analysisResult.importSettings = false
                    },
                    onSelectNewOnly = {
                        replaceAll = false
                        analysisResult.songs.forEach { item ->
                            item.action = if (item.category == SongMatchCategory.NEW) SongImportAction.TAKE_BACKUP else SongImportAction.KEEP_OWN
                        }
                        analysisResult.setlists.forEach { it.importSetlist = it.localSetlist == null }
                        analysisResult.importSettings = false
                    },
                    onRequestReplaceAll = {
                        showReplaceAllConfirmDialog = true
                    }
                )

                Button(
                    onClick = { onApplyImport(replaceAll) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                ) {
                    Text(
                        text = stringResource(R.string.compare_import_now),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Marks where the fixed part ends and the scrolling list begins.
            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (differentMetaSongs.isNotEmpty()) {
                item {
                    CategoryHeader(stringResource(R.string.compare_cat_different_meta, differentMetaSongs.size))
                }
                items(differentMetaSongs) { item ->
                    SongDiffCard(
                        item = item,
                        tempDir = analysisResult.tempDir,
                        loadPreview = loadPreview,
                        onActionChanged = { newAction ->
                            item.action = newAction
                        },
                        onMergeNotesChanged = { merge ->
                            item.mergeForeignNotes = merge
                        }
                    )
                }
            }

            if (possibleOtherSongs.isNotEmpty()) {
                item {
                    CategoryHeader(stringResource(R.string.compare_cat_possible_other, possibleOtherSongs.size))
                }
                items(possibleOtherSongs) { item ->
                    SongDiffCard(
                        item = item,
                        tempDir = analysisResult.tempDir,
                        loadPreview = loadPreview,
                        onActionChanged = { newAction ->
                            item.action = newAction
                        },
                        onMergeNotesChanged = { merge ->
                            item.mergeForeignNotes = merge
                        }
                    )
                }
            }

            if (newSongs.isNotEmpty()) {
                item {
                    CategoryHeader(stringResource(R.string.compare_cat_new, newSongs.size))
                }
                items(newSongs) { item ->
                    NewSongCard(
                        item = item,
                        neededBySetlists = missingBySong[item.backupSong.id].orEmpty(),
                        onActionChanged = { newAction ->
                            item.action = newAction
                        }
                    )
                }
            }

            if (identicalSongs.isNotEmpty()) {
                item {
                    CategoryHeader(stringResource(R.string.compare_cat_identical, identicalSongs.size))
                }
                items(identicalSongs) { item ->
                    IdenticalSongCard(
                        item = item,
                        onActionChanged = { newAction ->
                            item.action = newAction
                        }
                    )
                }
            }

            if (analysisResult.setlists.isNotEmpty()) {
                item {
                    CategoryHeader(stringResource(R.string.compare_cat_setlists, analysisResult.setlists.size))
                }
                items(analysisResult.setlists) { setlistItem ->
                    val missingCount = if (setlistItem.importSetlist) {
                        setlistItem.backupSetlist.songIds.distinct().count { it in missingBySong }
                    } else 0
                    SetlistImportCard(
                        item = setlistItem,
                        missingSongCount = missingCount,
                        onToggleImport = {
                            setlistItem.importSetlist = !setlistItem.importSetlist
                        }
                    )
                }
            }

            if (askForIdentity) {
                item {
                    IdentityCard(
                        authorLabel = authorLabel,
                        adopt = analysisResult.adoptAuthorIdentity,
                        onToggle = { analysisResult.adoptAuthorIdentity = !analysisResult.adoptAuthorIdentity },
                    )
                }
            }

            if (analysisResult.settingsInBackup != null) {
                item {
                    CategoryHeader(stringResource(R.string.compare_cat_settings))
                }
                item {
                    SettingsImportCard(
                        importSettings = analysisResult.importSettings,
                        onToggle = {
                            analysisResult.importSettings = !analysisResult.importSettings
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun HeaderCard(manifest: BackupManifest, songCount: Int, setlistCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.GERMAN).format(Date(manifest.createdAt))
            val titleStr = manifest.title.ifBlank { stringResource(R.string.settings_section_backup) }
            val authorStr = manifest.authorName.ifBlank { stringResource(R.string.app_name) }
            val deviceStr = manifest.deviceName.ifBlank { "Tablet" }

            Text(
                text = titleStr,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.compare_header_info, titleStr, deviceStr, authorStr, dateStr),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.compare_header_counts, songCount, setlistCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun MissingFilesBanner(missingFiles: List<String>) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.error_missing_song_file, missingFiles.first()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickChoiceRow(
    replaceAll: Boolean,
    onSelectSuggested: () -> Unit,
    onSelectNewOnly: () -> Unit,
    onRequestReplaceAll: () -> Unit,
) {
    Column {
        Text(
            text = "Schnellwahl",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FilterChip(
                selected = !replaceAll,
                onClick = onSelectSuggested,
                label = { Text(stringResource(R.string.compare_quick_suggested)) }
            )
            FilterChip(
                selected = false,
                onClick = onSelectNewOnly,
                label = { Text(stringResource(R.string.compare_quick_new_only)) }
            )
            FilterChip(
                selected = replaceAll,
                onClick = onRequestReplaceAll,
                label = { Text(stringResource(R.string.compare_quick_replace_all)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                )
            )
        }
    }
}

@Composable
private fun CategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SongDiffCard(
    item: SongComparisonItem,
    tempDir: File,
    loadPreview: (song: Song, fromBackup: Boolean) -> ScorePreview?,
    onActionChanged: (SongImportAction) -> Unit,
    onMergeNotesChanged: (Boolean) -> Unit,
) {
    val backup = item.backupSong
    val local = item.localSong

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = backup.displayTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (local != null) {
                Spacer(modifier = Modifier.height(4.dp))
                DiffRow("Titel", local.title, backup.title)
                if (local.artist != backup.artist) DiffRow("Künstler", local.artist, backup.artist)
                if (local.genre != backup.genre) DiffRow("Genre", local.genre, backup.genre)
                if (local.version != backup.version) DiffRow("Version", local.version, backup.version)

                if (local.hasFile || backup.hasFile) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScorePreviewBox(
                            label = stringResource(R.string.compare_preview_mine),
                            song = local,
                            cacheKey = "local|${local.id}",
                            load = { loadPreview(local, false) },
                            modifier = Modifier.weight(1f),
                        )
                        ScorePreviewBox(
                            label = stringResource(R.string.compare_preview_backup),
                            song = backup,
                            cacheKey = "${tempDir.path}|${backup.id}",
                            load = { loadPreview(backup, true) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChoiceChip(
                    selected = item.action == SongImportAction.KEEP_OWN,
                    label = stringResource(R.string.compare_choice_keep_own),
                    onClick = { onActionChanged(SongImportAction.KEEP_OWN) }
                )
                ChoiceChip(
                    selected = item.action == SongImportAction.TAKE_BACKUP,
                    label = stringResource(R.string.compare_choice_take_backup),
                    onClick = { onActionChanged(SongImportAction.TAKE_BACKUP) }
                )
                ChoiceChip(
                    selected = item.action == SongImportAction.KEEP_BOTH,
                    label = stringResource(R.string.compare_choice_keep_both),
                    onClick = { onActionChanged(SongImportAction.KEEP_BOTH) }
                )
            }

            if (item.action == SongImportAction.KEEP_OWN && backup.notes.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Checkbox(
                        checked = item.mergeForeignNotes,
                        onCheckedChange = onMergeNotesChanged
                    )
                    Text(
                        text = stringResource(R.string.compare_merge_notes),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun NewSongCard(
    item: SongComparisonItem,
    neededBySetlists: List<String>,
    onActionChanged: (SongImportAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
      Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.backupSong.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.backupSong.artist.isNotBlank()) {
                    Text(
                        text = item.backupSong.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ChoiceChip(
                    selected = item.action == SongImportAction.TAKE_BACKUP,
                    label = stringResource(R.string.action_import),
                    onClick = { onActionChanged(SongImportAction.TAKE_BACKUP) }
                )
                ChoiceChip(
                    selected = item.action == SongImportAction.SKIP,
                    label = stringResource(R.string.compare_choice_skip),
                    onClick = { onActionChanged(SongImportAction.SKIP) }
                )
            }
        }
        if (neededBySetlists.isNotEmpty()) {
            NeededBySetlistHint(
                setlistTitles = neededBySetlists,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
      }
    }
}

/** „Setlist *Erntedank* enthält dieses Lied“ – shown under a deselected song. */
@Composable
private fun NeededBySetlistHint(setlistTitles: List<String>, modifier: Modifier = Modifier) {
    val text = if (setlistTitles.size == 1) {
        stringResource(R.string.compare_needed_by_setlist, setlistTitles.first())
    } else {
        stringResource(R.string.compare_needed_by_setlists, setlistTitles.joinToString(", ") { "„$it“" })
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun IdenticalSongCard(
    item: SongComparisonItem,
    onActionChanged: (SongImportAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.backupSong.displayTitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ChoiceChip(
                selected = item.action == SongImportAction.KEEP_OWN,
                label = stringResource(R.string.compare_choice_skip),
                onClick = { onActionChanged(SongImportAction.KEEP_OWN) }
            )
        }
    }
}

@Composable
private fun SetlistImportCard(
    item: SetlistImportItem,
    missingSongCount: Int,
    onToggleImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.backupSetlist.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (item.backupSetlist.date.isNotBlank()) {
                    Text(
                        text = formatSetlistDate(item.backupSetlist.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (missingSongCount > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.compare_setlist_incomplete, missingSongCount, missingSongCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Switch(
                checked = item.importSetlist,
                onCheckedChange = { onToggleImport() }
            )
        }
    }
}

/**
 * Switch for „Ich bin ‹Name›“: the answer to the question asked on opening, changeable
 * until the import is started.
 */
@Composable
private fun IdentityCard(
    authorLabel: String,
    adopt: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.compare_identity_switch, authorLabel),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = adopt, onCheckedChange = { onToggle() })
        }
    }
}

/** Previews already rendered, so scrolling back does not open the PDF again. */
private val previewCache = ConcurrentHashMap<String, ScorePreview>()

/**
 * First page of one side, with page count and file size underneath. Two of these side by
 * side answer „are these the same notes?“ at a glance, which the title cannot.
 */
@Composable
private fun ScorePreviewBox(
    label: String,
    song: Song,
    cacheKey: String,
    load: () -> ScorePreview?,
    modifier: Modifier = Modifier,
) {
    var preview by remember(cacheKey) { mutableStateOf(previewCache[cacheKey]) }
    var loading by remember(cacheKey) { mutableStateOf(preview == null && song.hasFile) }

    LaunchedEffect(cacheKey) {
        if (preview != null || !song.hasFile) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) { runCatching(load).getOrNull() }
        if (loaded != null) previewCache[cacheKey] = loaded
        preview = loaded
        loading = false
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = preview?.bitmap
            when {
                bitmap != null -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> Text(
                    text = stringResource(R.string.compare_preview_none),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.DarkGray,
                )
            }
        }
        val info = preview
        if (info != null) {
            val sizeStr = Formatter.formatShortFileSize(LocalContext.current, info.fileSize)
            Text(
                text = if (info.pageCount != null) {
                    stringResource(R.string.compare_preview_pages, info.pageCount, sizeStr)
                } else sizeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SettingsImportCard(
    importSettings: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.compare_cat_settings),
                style = MaterialTheme.typography.bodyMedium
            )
            Switch(
                checked = importSettings,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun DiffRow(label: String, localVal: String, backupVal: String) {
    if (localVal == backupVal) return
    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = localVal.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = " ↔ ",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = backupVal.ifBlank { "-" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
        } else null
    )
}
