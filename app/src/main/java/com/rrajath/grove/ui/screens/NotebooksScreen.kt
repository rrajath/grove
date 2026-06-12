package com.rrajath.grove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/**
 * Notebook list home screen (design spec §2). M1 ships the chrome — app bar,
 * sync status strip, FAB — with an empty state; real notebook rows arrive in M2.
 */
@Composable
fun NotebooksScreen(
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCapture: () -> Unit,
    onOpenNotebook: (String) -> Unit,
) {
    val c = MaterialTheme.grove
    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = {
                    IconGlyph("☰", onClick = onOpenDrawer)
                },
                title = {
                    Text(
                        "Notebooks",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.ink,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
                actions = {
                    IconGlyph("⌕", onClick = onOpenSearch)
                },
            )
        },
        floatingActionButton = {
            Row(
                Modifier
                    .height(54.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(c.accent)
                    .clickable(onClick = onOpenCapture)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("+", fontFamily = PlexSans, fontSize = 21.sp, color = c.accentInk)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Capture",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, color = c.accentInk,
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
        ) {
            // Sync status strip
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.surface)
                    .border(1.dp, c.line, RoundedCornerShape(11.dp))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("○", fontFamily = PlexMono, fontSize = 13.sp, color = c.ink3)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Sync not set up yet",
                    fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
                )
            }

            // Empty state until the vault lands in M2
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✦", fontFamily = PlexMono, fontSize = 28.sp, color = c.ink3)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "No notebooks yet",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp, color = c.ink2,
                    )
                    Text(
                        "Your .org files will appear here",
                        fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3,
                    )
                }
            }
        }
    }
}

@Composable
internal fun IconGlyph(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            fontFamily = PlexMono,
            fontSize = 18.sp,
            color = MaterialTheme.grove.ink,
        )
    }
}
