package io.github.brickystudios.stundenblick

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    /** a setschool link waiting for the user's yes; never applied without it */
    private val link = mutableStateOf<SetSchool?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) take(intent)
        val repo = Repo.get(this)
        setContent { StundenblickTheme { Surface(color = MaterialTheme.colorScheme.background) { App(repo, link) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        val parsed = Parse.setSchool(data)
        if (parsed == null) Toast.makeText(this, "Das ist kein gültiger Untis-Link.", Toast.LENGTH_LONG).show()
        link.value = parsed
    }
}

private enum class Screen { TABS, PICK, PICK_OWN, SETTINGS, HISTORY, ROOMS, INFO }

@Composable
private fun App(repo: Repo, link: androidx.compose.runtime.MutableState<SetSchool?>) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.TABS) }
    var tab by rememberSaveable { mutableStateOf(if (repo.own.value != null) "heute" else "plan") }
    var shownKey by rememberSaveable { mutableStateOf(repo.last?.key) }
    var infoKind by rememberSaveable { mutableStateOf(Repo.Info.EXAMS) }
    val profile = repo.profile.value
    val own = repo.own.value
    val update by Updater.state.collectAsState()

    LaunchedEffect(profile) {
        if (profile != null) {
            ChangeJob.schedule(ctx, repo.notify || repo.alarm) // heals a switch that is on without its job (e.g. after an update)
            BreakInfo.schedule(ctx)
            repo.refreshMaster()
            if (repo.updates) Updater.check(ctx, manual = false)
        }
    }

    link.value?.let { l ->
        return LinkConfirm(repo, l, onDone = { link.value = null; screen = Screen.TABS; tab = "heute"; shownKey = null })
    }
    if (profile == null) return LoginScreen(repo) { screen = Screen.TABS; tab = "heute"; shownKey = null }

    // shown plan: last choice, else the own plan; resolve names from master data
    val shown: Element? = shownKey?.let { key ->
        if (key == own?.key) own else Element.fromKey(key, "")?.let { e -> repo.master.value?.find(e.kind, e.id) ?: repo.last?.takeIf { it.key == key } }
    } ?: own

    fun show(e: Element) {
        shownKey = e.key
        repo.last = e
        screen = Screen.TABS
        tab = "plan"
    }

    BackHandler(enabled = screen != Screen.TABS || tab != "heute") { if (screen != Screen.TABS) screen = Screen.TABS else tab = "heute" }
    when (screen) {
        Screen.PICK -> PickerScreen(repo, ownMode = false, onBack = { screen = Screen.TABS }) { show(it) }
        Screen.PICK_OWN -> PickerScreen(repo, ownMode = true, onBack = { screen = Screen.TABS }) { repo.setOwn(it); show(repo.own.value!!) }
        Screen.SETTINGS -> SettingsScreen(repo, shown, onBack = { screen = Screen.TABS }, onSetOwn = { screen = Screen.PICK_OWN },
            onLoggedOut = { screen = Screen.TABS; tab = "heute"; shownKey = null })
        Screen.HISTORY -> VerlaufScreen(repo) { screen = Screen.TABS }
        Screen.ROOMS -> RoomFinderScreen(repo) { screen = Screen.TABS }
        Screen.INFO -> when (infoKind) {
            Repo.Info.EXAMS -> ExamsScreen(repo) { screen = Screen.TABS }
            Repo.Info.HOMEWORK -> HomeworkScreen(repo) { screen = Screen.TABS }
            Repo.Info.ABSENCES -> AbsencesScreen(repo) { screen = Screen.TABS }
            Repo.Info.OFFICE -> OfficeHoursScreen(repo) { screen = Screen.TABS }
            Repo.Info.MESSAGES -> { screen = Screen.TABS }
        }
        Screen.TABS -> Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                Column { HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(Triple("heute", "Heute", Icons.Default.Home), Triple("plan", "Plan", Icons.Default.DateRange), Triple("schule", "Schule", Icons.Default.LocationOn), Triple("mehr", "Mehr", Icons.Default.Menu))
                        .forEach { (id, label, icon) ->
                            val count = if (id == "mehr") repo.history.value.count { !it.seen } else 0
                            NavigationBarItem(
                                selected = tab == id, onClick = { tab = id },
                                icon = { if (count > 0) BadgedBox(badge = { Badge { Text("$count") } }) { Icon(icon, null) } else Icon(icon, null) },
                                label = { Text(label, fontWeight = if (tab == id) FontWeight.Bold else FontWeight.Normal) },
                                // the M3 indicator is a pill; the bill marks the current tab by ink and weight alone
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                            )
                        }
                } }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Box(Modifier.padding(pad).consumeWindowInsets(pad)) {
                when {
                    tab == "heute" -> HeuteScreen(repo, onOpenPlan = { own?.let(::show) }, onHistory = { screen = Screen.HISTORY }, onSetOwn = { screen = Screen.PICK_OWN },
                        onInfo = { infoKind = it; screen = Screen.INFO })
                    tab == "schule" -> SchoolScreen(repo, onOpen = ::show, onRooms = { screen = Screen.ROOMS })
                    tab == "mehr" -> MehrScreen(repo, onHistory = { screen = Screen.HISTORY }, onRooms = { screen = Screen.ROOMS }, onSettings = { screen = Screen.SETTINGS },
                        onInfo = { infoKind = it; screen = Screen.INFO })
                    shown == null -> OwnMissing(profile, onSetOwn = { screen = Screen.PICK_OWN }, onPick = { screen = Screen.PICK }, onSettings = { screen = Screen.SETTINGS })
                    else -> TimetableScreen(repo, shown, onPick = { screen = Screen.PICK }, onSettings = { screen = Screen.SETTINGS }, onOpen = ::show)
                }
            }
        }
    }

    update?.let { u ->
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { Updater.later() },
            title = { Text("Stundenblick ${u.release.version} ist da") },
            text = {
                Column {
                    Text("Die neue Version wird von GitHub geladen und von Android installiert.")
                    u.progress?.let { Spacer(Modifier.height(12.dp)); LinearProgressIndicator({ it }, Modifier.fillMaxWidth()) }
                    if (u.failed) Text("Download fehlgeschlagen. Versuch es noch einmal.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { Button(shape = RectangleShape, onClick = { scope.launch { Updater.install(ctx) } }, enabled = u.progress == null) { Text("Installieren") } },
            dismissButton = { TextButton(onClick = { Updater.later() }) { Text("Später") } },
        )
    }
}

/** F3: Untis named no own timetable (anonymous access, some parent accounts). One tap fixes it. */
@Composable
private fun OwnMissing(profile: Profile, onSetOwn: () -> Unit, onPick: () -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        PosterTitle("Kein eigener Plan")
        Text(
            if (profile.anonymous) "Ohne Anmeldung kennt Untis dich nicht. Wähl deine Klasse, dann ist sie dein Plan."
            else "Untis liefert für dein Konto keinen eigenen Stundenplan. Das passiert bei manchen Schulen und Eltern-Konten. Wähl deine Klasse als eigenen Plan.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(shape = RectangleShape, onClick = onSetOwn) { Text("Klasse als eigenen Plan setzen") }
        OutlinedButton(shape = RectangleShape, onClick = onPick) { Text("Anderen Plan ansehen") }
        TextButton(onClick = onSettings) { Text("Einstellungen und Verbindungstest") }
    }
}

@Composable
private fun LinkConfirm(repo: Repo, l: SetSchool, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Mit diesem Untis-Zugang anmelden?", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Card {
            Text("Schule: ${l.school}")
            Text("Server: ${l.server}")
            Text("Benutzer: ${l.user ?: "ohne Anmeldung"}")
        }
        if (repo.profile.value != null) Text("Dein bisheriger Zugang in Stundenblick wird dabei ersetzt.", color = LocalSignals.current.changed)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(shape = RectangleShape, enabled = !busy, onClick = {
            busy = true
            scope.launch {
                runCatching { repo.login(Profile(l.server, l.school, "", l.user, l.key)) }
                    .onSuccess { onDone() }.onFailure { error = Errors.explain(it) }
                busy = false
            }
        }) { Text(if (busy) "Melde an …" else "Anmelden") }
        TextButton(onClick = onDone, enabled = !busy) { Text("Abbrechen") }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RectangleShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun LoginScreen(repo: Repo, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pasted by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<School>?>(null) }
    var school by remember { mutableStateOf<School?>(null) }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var needsCode by remember { mutableStateOf(false) }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { block() }.onFailure {
                if (it is UntisException && it.code == Errors.TWO_FACTOR) needsCode = true
                error = Errors.explain(it)
            }
            busy = false
        }
    }

    BackHandler(enabled = school != null) { school = null; error = null; needsCode = false }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(12.dp))
        PosterTitle("Stundenblick", size = 72)
        Text("Alle Stundenpläne deiner Schule, die Untis für dich freigibt: Klassen, Lehrkräfte, Räume. Mit Ausfällen, Vertretungen und Klausuren auf einen Blick.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) CircularProgressIndicator()

        val s = school
        if (s == null) {
            Card {
                Text("Mit QR-Code aus WebUntis", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("Der sicherste Weg, auch mit Zwei-Faktor-Anmeldung: WebUntis im Browser öffnen → Profil → Freigaben → „Anzeigen“. Scanne den QR-Code mit der Kamera-App, sie öffnet Stundenblick. Oder kopiere den Link darunter und füge ihn hier ein.",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(pasted, { pasted = it }, Modifier.fillMaxWidth(), placeholder = { Text("untis://setschool?…") }, singleLine = true)
                Button(shape = RectangleShape, enabled = pasted.isNotBlank() && !busy, onClick = {
                    val l = Parse.setSchool(pasted)
                    if (l == null) error = "Das ist kein gültiger Untis-Link." else run { repo.login(Profile(l.server, l.school, "", l.user, l.key)); onDone() }
                }) { Text("Link übernehmen") }
            }
            Card {
                Text("Schule suchen", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Name oder Ort der Schule") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (query.length >= 3) run { results = withContext(Dispatchers.IO) { Untis.searchSchools(query.trim()) } } }),
                )
                Button(shape = RectangleShape, enabled = query.trim().length >= 3 && !busy, onClick = { run { results = withContext(Dispatchers.IO) { Untis.searchSchools(query.trim()) } } }) { Text("Suchen") }
                results?.let { list ->
                    if (list.isEmpty()) Text("Keine Schule gefunden.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.forEach { r ->
                        HorizontalDivider(color = LocalSignals.current.line)
                        Column(Modifier.fillMaxWidth().clickable { school = r; error = null }.padding(vertical = 8.dp)) {
                            Text(r.name, fontWeight = FontWeight.Medium)
                            if (r.address.isNotBlank()) Text(r.address, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            TextButton(onClick = { run { repo.login(Demo.profile); onDone() } }) { Text("Erst mal die Demo-Schule ansehen") }
            Text("Nicht mit der Untis GmbH verbunden. Dein Passwort wird nicht gespeichert, nur der Zugangsschlüssel, den Untis dafür ausgibt.",
                fontSize = 12.sp, color = LocalSignals.current.muted)
        } else {
            Card {
                Text(s.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                if (s.address.isNotBlank()) Text(s.address, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(user, { user = it }, Modifier.fillMaxWidth(), label = { Text("Benutzername") }, singleLine = true)
                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Passwort") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (needsCode) OutlinedTextField(code, { code = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("6-stelliger Code") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                Button(shape = RectangleShape, enabled = user.isNotBlank() && password.isNotBlank() && !busy, onClick = {
                    run {
                        val secret = withContext(Dispatchers.IO) { Untis.appSecret(s.server, s.login, user.trim(), password, code.ifBlank { null }) }
                        repo.login(Profile(s.server, s.login, s.name, user.trim(), secret))
                        password = ""
                        onDone()
                    }
                }) { Text("Anmelden") }
                OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = { run { repo.login(Profile(s.server, s.login, s.name, null, null)); onDone() } }) { Text("Ohne Anmeldung: öffentlicher Plan") }
                TextButton(onClick = { school = null; error = null; needsCode = false }) { Text("Andere Schule") }
            }
        }
    }
}
