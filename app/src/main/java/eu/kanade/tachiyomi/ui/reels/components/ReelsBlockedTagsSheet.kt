package eu.kanade.tachiyomi.ui.reels.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

/**
 * Blocked-tags editor (contract v20, AnimeBlockedTagsSource): the account's never-show tag
 * list. Replace-semantics: the saved set is whatever remains when "Save" is pressed.
 */
@Composable
fun ReelsBlockedTagsSheet(
    initialTags: List<String>,
    isLoading: Boolean,
    error: String?,
    onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var tags by remember(initialTags) { mutableStateOf(initialTags) }
    var input by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(MR.strings.reels_blocked_tags),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(top = 8.dp),
                        )
                        TextButton(onClick = { onSave(tags) }) {
                            Text(stringResource(MR.strings.action_save))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(MR.strings.action_cancel))
                        }
                    }
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    if (error != null) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            label = { Text(stringResource(MR.strings.reels_blocked_tags_add)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                val tag = input.trim().removePrefix("#").lowercase()
                                if (tag.isNotBlank() && tag !in tags) {
                                    tags = tags + tag
                                }
                                input = ""
                            },
                        ) {
                            Text(stringResource(MR.strings.reels_blocked_tags_add))
                        }
                    }
                    when {
                        isLoading -> LoadingScreen()
                        tags.isEmpty() ->
                            Text(
                                text = stringResource(MR.strings.reels_blocked_tags_empty),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        else ->
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(tags, key = { it }) { tag ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "#$tag",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 14.sp,
                                            modifier = Modifier.weight(1f),
                                        )
                                        IconButton(onClick = { tags = tags - tag }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Close,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }
            }
        }
    }
}
