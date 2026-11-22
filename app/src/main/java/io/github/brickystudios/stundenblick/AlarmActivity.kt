package io.github.brickystudios.stundenblick

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The alarm's own screen, like an alarm clock: the full-screen intent opens it over the lock screen and turns the
 * screen on. The sound stays with the notification (it also rings when the phone is in use and Android shows only
 * the banner); "Aus" cancels the notification and with it the sound.
 */
class AlarmActivity : ComponentActivity() {
    private val lines = mutableStateOf(emptyList<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        // Android 8.0 lacks the manifest attributes showWhenLocked/turnScreenOn
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < 27) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        lines.value = intent.lines()
        setContent {
            StundenblickTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Stundenplan-Alarm", color = LocalSignals.current.accent, fontSize = 16.sp)
                        lines.value.forEach { Text(it, style = MaterialTheme.typography.headlineSmall) }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = ::stop, Modifier.fillMaxWidth().height(64.dp), shape = ControlShape) { Text("Aus", fontSize = 20.sp) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        lines.value = intent.lines()
    }

    private fun stop() {
        getSystemService(NotificationManager::class.java).cancel(ChangeJob.ALARM_NOTE)
        finish()
    }

    private fun Intent.lines() = getStringArrayExtra(LINES)?.toList().orEmpty()

    companion object {
        private const val LINES = "lines"

        fun intent(ctx: Context, messages: List<String>) =
            Intent(ctx, AlarmActivity::class.java).putExtra(LINES, messages.toTypedArray()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
