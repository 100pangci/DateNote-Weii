package com.datenote.app.ui.reminder

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.datenote.app.R

@Composable
fun NotificationUnavailableDialog(
    onEnable: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.reminder_saved_without_notification_title)) },
        text = { Text(stringResource(R.string.reminder_saved_without_notification_message)) },
        dismissButton = { TextButton(onClick = onLater) { Text(stringResource(R.string.reminder_later)) } },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(R.string.reminder_enable_notification)) } },
    )
}
