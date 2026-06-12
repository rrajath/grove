package com.rrajath.grove.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Capture template picker bottom sheet (design spec §7). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturePickerSheet(
    onDismiss: () -> Unit,
    onPickTemplate: (CaptureTemplate) -> Unit,
    onManage: () -> Unit,
    viewModel: CaptureViewModel = viewModel(factory = CaptureViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val templates by viewModel.templates.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(Modifier.padding(bottom = 26.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Capture to…",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp, color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Manage",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = c.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onManage)
                        .padding(6.dp),
                )
            }
            templates.forEach { template ->
                TemplateRow(template, onClick = { onPickTemplate(template) })
            }
        }
    }
}

@Composable
private fun TemplateRow(template: CaptureTemplate, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    val tileColors = listOf(
        c.accent to c.accentSoft,
        c.green to c.greenSoft,
        c.amber to c.amberSoft,
        c.blue to c.blueSoft,
    )
    val (fg, bg) = tileColors[kotlin.math.abs(template.id.hashCode()) % tileColors.size]
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Text(template.icon, fontFamily = PlexMono, fontSize = 17.sp, color = fg)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                template.name,
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp, color = c.ink,
            )
            Text(
                "${template.targetFile} · ${template.location.describe()}",
                fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink2,
            )
        }
        Text("›", fontFamily = PlexMono, fontSize = 16.sp, color = c.ink3)
    }
}
