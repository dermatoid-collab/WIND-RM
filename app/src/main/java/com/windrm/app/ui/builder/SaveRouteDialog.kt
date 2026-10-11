package com.windrm.app.ui.builder

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.windrm.app.R

/** Name for the route, plus what to do with its GPX: a copy in the chosen folder (on by default, unless the file is already there) and/or the share sheet. */
@Composable
internal fun SaveRouteDialog(
    initialName: String,
    folderName: String?,
    /** Off for a file that already sits in the GPX folder, where a copy would only duplicate it. */
    folderCopyByDefault: Boolean = true,
    mode: BuilderMode = BuilderMode.NEW,
    onDismiss: () -> Unit,
    onConfirm: (name: String, toFolder: Boolean, share: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var toFolder by remember { mutableStateOf(folderName != null && folderCopyByDefault) }
    var share by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when (mode) {
                        BuilderMode.NEW -> R.string.builder_name_title
                        BuilderMode.EDIT -> R.string.builder_name_title_edit
                        BuilderMode.DUPLICATE -> R.string.builder_name_title_copy
                    },
                ),
            )
        },
        text = {
            Column {
                // Saving over a route and saving a copy look alike, so the dialog says which one this is.
                if (mode != BuilderMode.NEW) {
                    Text(
                        stringResource(if (mode == BuilderMode.EDIT) R.string.builder_name_hint_edit else R.string.builder_name_hint_copy),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.builder_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (folderName != null) {
                    CheckRow(toFolder, { toFolder = it }, stringResource(R.string.builder_gpx_to_folder, folderName))
                } else {
                    Text(
                        stringResource(R.string.builder_no_folder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                CheckRow(share, { share = it }, stringResource(R.string.builder_export_gpx))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, toFolder && folderName != null, share) }, enabled = name.isNotBlank()) {
                Text(
                    stringResource(
                        when (mode) {
                            BuilderMode.NEW -> R.string.builder_save
                            BuilderMode.EDIT -> R.string.builder_save_changes
                            BuilderMode.DUPLICATE -> R.string.builder_save_copy
                        },
                    ),
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
