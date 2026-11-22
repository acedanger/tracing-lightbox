package com.acedanger.traceart.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acedanger.traceart.UpdateState
import com.acedanger.traceart.update.OWNER_URL
import com.acedanger.traceart.update.REPO_OWNER
import com.acedanger.traceart.update.REPO_URL
import java.text.DateFormat
import java.util.Date

val UpdateOrange = Color(0xFFE65100)

@Composable
fun AboutScreen(
    update: UpdateState,
    onBack: () -> Unit,
    onCheckNow: () -> Unit,
    onAutoCheck: (Boolean) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No browser on this tablet to open $url", Toast.LENGTH_LONG).show()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFFFFF3E0)).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 56.dp)) {
            Text("‹  Back", fontSize = 22.sp)
        }
        Text("About Trace Art", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Text("Version ${update.installed}", fontSize = 24.sp)

        if (update.updateAvailable) UpdateCard(update, ::open)

        Section("Made by") {
            Link(REPO_OWNER, OWNER_URL, ::open)
            Text("Source code and releases", fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp))
            Link(REPO_URL, REPO_URL, ::open)
        }

        Section("How it was built") {
            Text(
                "Trace Art was developed with Claude Code, Anthropic's AI coding tool. " +
                    "The pictures were drawn for the app as original outline art.",
                fontSize = 20.sp,
            )
        }

        Section("Updates") {
            val status = when {
                update.checking -> "Checking GitHub…"
                update.updateAvailable -> "Version ${update.latest?.tag} is available."
                update.lastCheckFailed -> "Couldn't reach GitHub. Check the Wi-Fi and try again."
                update.latest != null -> "You have the newest version."
                else -> "Not checked yet."
            }
            Text(status, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            update.lastCheckMs?.let {
                Text(
                    "Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)),
                    fontSize = 18.sp,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Check automatically", fontSize = 20.sp)
                    Text("When the app opens, at most once a day", fontSize = 16.sp, color = Color.DarkGray)
                }
                Switch(checked = update.autoCheck, onCheckedChange = onAutoCheck)
            }
            Button(
                onClick = onCheckNow,
                enabled = !update.checking,
                modifier = Modifier.padding(top = 8.dp).heightIn(min = 56.dp),
            ) { Text("Check now", fontSize = 20.sp) }
        }
        Spacer(Modifier.heightIn(min = 24.dp))
    }
}

@Composable
private fun UpdateCard(update: UpdateState, open: (String) -> Unit) {
    val latest = update.latest ?: return
    val apk = latest.apkName ?: "trace-art-${latest.tag}.apk"
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE0B2)),
        modifier = Modifier.widthIn(max = 720.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Update available: ${latest.tag}",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = UpdateOrange,
            )
            Text("You have ${update.installed}. To update from GitHub:", fontSize = 20.sp)
            listOf(
                "Tap Open release page below. It opens in the browser.",
                "Under Assets, tap $apk to download it. Don't use the -usb file.",
                "When the download finishes, tap it to open it, then tap Update or Install.",
                "If Android asks, allow the browser to install apps, then go back and tap Install again.",
                "Open Trace Art again. Saved positions and extras are kept.",
            ).forEachIndexed { i, step -> Text("${i + 1}.  $step", fontSize = 19.sp) }
            Button(onClick = { open(latest.pageUrl) }, modifier = Modifier.heightIn(min = 56.dp)) {
                Text("Open release page", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 720.dp)) {
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
}

@Composable
private fun Link(label: String, url: String, open: (String) -> Unit) {
    TextButton(onClick = { open(url) }) {
        Text(label, fontSize = 20.sp, color = Color(0xFF1565C0), textDecoration = TextDecoration.Underline)
    }
}
