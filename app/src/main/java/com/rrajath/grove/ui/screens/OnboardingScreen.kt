package com.rrajath.grove.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.components.BrandMark
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/**
 * Onboarding per design spec §1. In M1 both CTAs just complete onboarding;
 * the real folder picker arrives with the vault work in M2.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = MaterialTheme.grove
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        BrandMark(tileSize = 74.dp)
        Spacer(Modifier.height(18.dp))
        Text(
            "Grove",
            style = MaterialTheme.typography.displaySmall,
            color = c.ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Your org-mode notes, at home on your phone.",
            fontFamily = PlexSans,
            fontSize = 15.sp,
            color = c.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 240.dp),
        )
        Spacer(Modifier.weight(1f))

        // Syncthing setup card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(c.surface)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("Recommended", fg = c.green, bg = c.greenSoft)
                Spacer(Modifier.size(8.dp))
                Text(
                    "Sync with Syncthing",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp, color = c.ink,
                )
            }
            Spacer(Modifier.height(14.dp))
            OnboardingStep(1, "Install Syncthing on this phone and your laptop")
            OnboardingStep(2, "Share your org folder between the two devices")
            OnboardingStep(3, "Point Grove at that folder below — sync is automatic")
        }

        Spacer(Modifier.height(26.dp))

        // Primary CTA
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.accent)
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Choose a local folder",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = c.accentInk,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "I'll set this up later",
                fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
            )
        }
    }
}

@Composable
private fun OnboardingStep(number: Int, text: String) {
    val c = MaterialTheme.grove
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(c.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$number",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, color = c.accent,
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text,
            fontFamily = PlexSans, fontSize = 13.5.sp, color = c.ink2,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
