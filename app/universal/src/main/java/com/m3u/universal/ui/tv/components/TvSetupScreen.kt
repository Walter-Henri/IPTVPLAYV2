package com.m3u.universal.ui.tv.components

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.core.foundation.ui.PremiumColors
import com.m3u.core.foundation.ui.tvFocusHighlight
import com.m3u.universal.ui.common.StartupState
import com.m3u.universal.ui.common.StartupViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun TvSetupScreen(
    viewModel: StartupViewModel,
    state: StartupState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    val titleFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val importLinkFocus = remember { FocusRequester() }
    val importFileFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        titleFocus.requestFocus()
    }

    val needsStoragePermission = Build.VERSION.SDK_INT < 33
    val storagePermission = if (needsStoragePermission) rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE) else null

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                val finalTitle = if (title.isBlank()) "Minha lista" else title
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                viewModel.importFromFile(finalTitle, uri.toString())
            }
        }
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "M3U PLAY PREMIUM - TV",
                color = PremiumColors.Accent,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "A melhor experiência IPTV na sua TV. Importe sua lista para começar.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Spacer(Modifier.height(16.dp))

            androidx.compose.material3.OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Nome da lista (ex: Canais VIP)") },
                modifier = Modifier
                    .width(400.dp)
                    .focusRequester(titleFocus)
                    .tvFocusHighlight()
                    .focusProperties { next = urlFocus }
            )
            
            androidx.compose.material3.OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("URL M3U8 (http://...)") },
                modifier = Modifier
                    .width(400.dp)
                    .focusRequester(urlFocus)
                    .tvFocusHighlight()
                    .focusProperties { 
                        previous = titleFocus
                        next = importLinkFocus 
                    }
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        val finalTitle = if (title.isBlank()) "Minha lista" else title
                        viewModel.importFromUrl(finalTitle, url)
                    },
                    enabled = !state.isImporting && url.isNotBlank(),
                    modifier = Modifier
                        .focusRequester(importLinkFocus)
                        .tvFocusHighlight()
                        .focusProperties {
                            previous = urlFocus
                            next = importFileFocus
                        }
                ) {
                    androidx.compose.material3.Icon(Icons.Default.Link, null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Importar URL")
                }
                
                Button(
                    onClick = {
                        val sp = storagePermission
                        if (needsStoragePermission && sp != null && sp.status != PermissionStatus.Granted) {
                            sp.launchPermissionRequest()
                        } else {
                            filePicker.launch(arrayOf("*/*"))
                        }
                    },
                    enabled = !state.isImporting,
                    modifier = Modifier
                        .focusRequester(importFileFocus)
                        .tvFocusHighlight()
                        .focusProperties { previous = importLinkFocus }
                ) {
                    androidx.compose.material3.Icon(Icons.Default.UploadFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Importar Arquivo")
                }
            }
            
            if (state.isImporting) {
                CircularProgressIndicator(color = PremiumColors.Accent)
            }
            
            state.error?.let { errorMsg ->
                Text(errorMsg, color = Color.Red, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
