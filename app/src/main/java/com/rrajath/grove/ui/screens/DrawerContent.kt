package com.rrajath.grove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.search.SavedSearch
import com.rrajath.grove.ui.components.BrandMark
import com.rrajath.grove.ui.nav.Routes
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Navigation drawer per design spec §3. */
@Composable
fun GroveDrawerContent(
    currentRoute: String?,
    vaultPath: String,
    savedSearches: List<SavedSearch>,
    onNavigate: (String) -> Unit,
    onDeleteSavedSearch: (SavedSearch) -> Unit,
) {
    val c = MaterialTheme.grove
    var deleteTarget by remember { mutableStateOf<SavedSearch?>(null) }
    Column(Modifier.fillMaxWidth()) {
        // Header
        Column(Modifier.padding(22.dp)) {
            BrandMark(tileSize = 42.dp)
            Spacer(Modifier.height(10.dp))
            Text("Grove", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = c.ink)
            Text(vaultPath, fontFamily = PlexMono, fontSize = 12.sp, color = c.ink2)
        }
        HorizontalDivider(color = c.line)
        Spacer(Modifier.height(8.dp))

        DrawerItem("⌕", "Search", active = false) { onNavigate(Routes.search()) }
        DrawerItem("✦", "Notebooks", active = currentRoute == Routes.NOTEBOOKS) { onNavigate(Routes.NOTEBOOKS) }

        SectionLabel("SEARCHES")
        savedSearches.forEach { search ->
            DrawerItem(
                "⌖", search.name, active = false,
                onLongClick = { deleteTarget = search },
            ) { onNavigate(Routes.search(search.query)) }
        }

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        DrawerItem("▤", "Agenda", active = false) { onNavigate(Routes.search("ad.7")) }
        DrawerItem("⚙", "Settings", active = false) { onNavigate(Routes.SETTINGS) }
    }

    deleteTarget?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = c.surface,
            title = {
                Text(
                    "Delete \"${target.name}\"?",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.ink,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    onDeleteSavedSearch(target)
                    deleteTarget = null
                }) { Text("Delete", color = c.red, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = c.ink2)
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontFamily = PlexSans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 1.sp,
        color = MaterialTheme.grove.ink3,
        modifier = Modifier.padding(start = 22.dp, top = 16.dp, bottom = 6.dp),
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(
    glyph: String,
    label: String,
    active: Boolean,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) c.accentSoft else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(30.dp)) {
            Text(glyph, fontFamily = PlexMono, fontSize = 18.sp, color = if (active) c.accent else c.ink2)
        }
        Text(
            label,
            fontFamily = PlexSans,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 14.5.sp,
            color = if (active) c.accent else c.ink,
        )
    }
}
