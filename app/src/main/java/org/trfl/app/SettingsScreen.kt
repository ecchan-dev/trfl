package org.trfl.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The Settings tab. Stateless: values come in, changes go out through the callbacks, which
 * are responsible for persisting them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    boardColors: Boolean, onBoardColorsChange: (Boolean) -> Unit,
    chanTheme: ChanTheme, onChanThemeChange: (ChanTheme) -> Unit,
    themeMenuOpen: Boolean, onThemeMenuOpenChange: (Boolean) -> Unit,
    chanAppearance: String, onChanAppearanceChange: (String) -> Unit,
    adult: Boolean, onAdultChange: (Boolean) -> Unit,
    onOpenDownloads: () -> Unit, onOpenDonation: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
        item { SettingsToggle("4chan colors", "Off follows your phone’s theme and wallpaper colors",  boardColors) { onBoardColorsChange(it) } }
        if (boardColors) item {
            Box {
                ListItem(headlineContent = { Text("Theme") }, supportingContent = { Text(chanTheme.label) },
                    trailingContent = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.clickable { onThemeMenuOpenChange(true) })
                DropdownMenu(expanded = themeMenuOpen, onDismissRequest = { onThemeMenuOpenChange(false) }) {
                    ChanTheme.entries.forEach { theme ->
                        DropdownMenuItem(text = { Text(theme.label) },
                            trailingIcon = { if (theme == chanTheme) Icon(Icons.Default.Check, null) },
                            onClick = {
                                onChanThemeChange(theme)
                                onThemeMenuOpenChange(false)
                            })
                    }
                }
            }
        }
        if (boardColors) item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("4chan appearance", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf("System", "Light", "Dark").forEachIndexed { index, mode ->
                        SegmentedButton(selected = chanAppearance == mode, onClick = {
                            onChanAppearanceChange(mode)
                        }, shape = SegmentedButtonDefaults.itemShape(index, 3)) { Text(mode) }
                    }
                }
            }
        }
        item { WatchNotificationSetting() }
        if (!BuildConfig.SFW_ONLY) item { SettingsToggle("18+ boards", "Show adult boards in the directory", adult) { onAdultChange(it) } }
        item { ListItem(headlineContent = { Text("Downloads") }, supportingContent = { Text("Progress, cancellation and retry") }, modifier = Modifier.clickable { onOpenDownloads() }) }
        item { ListItem(headlineContent = { Text("Donate") }, supportingContent = { Text("Ethereum") }, modifier = Modifier.clickable { onOpenDonation() }) }
        item { ListItem(headlineContent = { Text("Trfl ${BuildConfig.VERSION_NAME}") }, supportingContent = { Text(BuildConfig.COMMIT.take(12)) }) }
    }
}
