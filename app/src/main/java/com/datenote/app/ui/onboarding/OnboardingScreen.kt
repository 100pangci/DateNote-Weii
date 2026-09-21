package com.datenote.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.datenote.app.R
import com.datenote.app.ui.components.AppOutlinedTextField
import com.datenote.app.ui.components.AppPrimaryButton
import com.datenote.app.ui.theme.AppSpacing

@Composable
fun OnboardingScreen(
    initialNickname: String = "",
    onNicknameSaved: (String) -> Unit,
) {
    var nickname by rememberSaveable { mutableStateOf(initialNickname) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val trimmed = nickname.trim()
    val length = trimmed.codePointCount(0, trimmed.length)
    val error = when {
        submitted && trimmed.isEmpty() -> stringResource(R.string.nickname_required)
        submitted && length > 12 -> stringResource(R.string.nickname_too_long)
        else -> null
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.ScreenHorizontal, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(AppSpacing.Section))
        Text(
            text = stringResource(R.string.onboarding_question),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(AppSpacing.Section))
        AppOutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it; submitted = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.nickname_label)) },
            placeholder = { Text(stringResource(R.string.nickname_placeholder)) },
            supportingText = {
                Text(error ?: stringResource(R.string.nickname_supporting))
            },
            isError = error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Spacer(Modifier.height(AppSpacing.Content))
        AppPrimaryButton(
            onClick = {
                submitted = true
                if (trimmed.isNotEmpty() && length <= 12) onNicknameSaved(trimmed)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.start_using)) }
        Spacer(Modifier.height(AppSpacing.Content))
        Text(
            text = stringResource(R.string.app_subtitle),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
