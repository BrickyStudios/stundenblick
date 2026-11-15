package io.github.brickystudios.stundenblick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every timetable the school lists. Whether Untis actually hands one out depends on the school's rights for
 * this account; a refusal shows up as a clear message in the timetable, not as a hidden entry here.
 * ownMode: choosing sets the own plan (F3), only classes make sense there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(repo: Repo, ownMode: Boolean, onBack: () -> Unit, onPick: (Element) -> Unit) {
    val master = repo.master.value
    val kinds = if (ownMode) listOf(Kind.CLASS) else listOf(Kind.CLASS, Kind.TEACHER, Kind.ROOM, Kind.SUBJECT)
    val favs = repo.favoriteElements()
    var tab by rememberSaveable { mutableStateOf(if (!ownMode && favs.isNotEmpty()) "FAV" else Kind.CLASS.name) }
    var query by remember { mutableStateOf("") }
    val source: List<Element> = when {
        query.isNotBlank() -> kinds.flatMap { master?.elements?.get(it).orEmpty() }
        tab == "FAV" -> favs
        else -> master?.elements?.get(Kind.valueOf(tab)).orEmpty()
    }
    val q = query.trim()
    val list = if (q.isEmpty()) source else source.filter { it.name.contains(q, true) || it.longName.contains(q, true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (ownMode) "Deine Klasse wählen" else "Plan wählen", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Suchen: 9b, Müller, A101, Mathe …") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            if (query.isBlank()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!ownMode) FilterChip(tab == "FAV", { tab = "FAV" }, { Text("Favoriten") })
                kinds.forEach { k ->
                    val label = when (k) { Kind.CLASS -> "Klassen"; Kind.TEACHER -> "Lehrkräfte"; Kind.ROOM -> "Räume"; Kind.SUBJECT -> "Fächer"; Kind.STUDENT -> "" }
                    FilterChip(tab == k.name, { tab = k.name }, { Text("$label ${master?.elements?.get(k)?.size ?: 0}") })
                }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                val own = repo.own.value
                if (!ownMode && own != null && query.isBlank()) item {
                    Row(Modifier.fillMaxWidth().clickable { onPick(own) }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, null, tint = MaterialTheme.colorScheme.primary)
                        Text("  Mein Plan", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    HorizontalDivider(color = LocalSignals.current.line)
                }
                if (list.isEmpty()) item {
                    Text(
                        when {
                            master == null -> "Noch keine Schuldaten geladen."
                            tab == "FAV" && q.isEmpty() -> "Noch keine Favoriten. Tipp auf den Stern neben einem Plan."
                            else -> "Nichts gefunden."
                        },
                        Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(list, key = { it.key }) { e ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(e) }.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            if (e.longName.isNotBlank() && e.longName != e.name) Text(e.longName, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (query.isNotBlank()) Text(e.kind.label, fontSize = 11.sp, color = LocalSignals.current.muted)
                        }
                        if (!ownMode) {
                            val fav = repo.isFavorite(e)
                            IconButton(onClick = { repo.toggleFavorite(e) }) {
                                Icon(if (fav) Icons.Filled.Star else Icons.Outlined.Star, if (fav) "Aus Favoriten entfernen" else "Zu Favoriten",
                                    tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}
