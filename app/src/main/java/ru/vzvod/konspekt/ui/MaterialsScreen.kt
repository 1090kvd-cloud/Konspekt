package ru.vzvod.konspekt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.data.Materials
import ru.vzvod.konspekt.model.Material

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MaterialsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf<String?>(null) }
    var pendingDiscipline by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            Materials.add(context, uri, pendingDiscipline)
                .onFailure { error = it.message ?: "Не удалось приложить файл" }
        }
    }

    val shown = if (filter == null) Materials.all.toList()
    else Materials.all.filter { it.disciplineId == filter }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            Section(
                "Куда приложить",
                "Материал появится на занятиях по выбранному предмету"
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = pendingDiscipline == null,
                        onClick = { pendingDiscipline = null },
                        label = { Text("Общие") }
                    )
                    Library.all.forEach { d ->
                        FilterChip(
                            selected = pendingDiscipline == d.id,
                            onClick = { pendingDiscipline = d.id },
                            label = { Text(d.short) }
                        )
                    }
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (Materials.all.isEmpty()) {
                Column(
                    Modifier.weight(1f).padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Материалов пока нет", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Приложите методичку, выписку из устава, таблицу нормативов или фото плаката. " +
                            "Файл скопируется в приложение и будет открываться без интернета — даже если исходный удалить.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp)
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = filter == null,
                            onClick = { filter = null },
                            label = { Text("Все") }
                        )
                        Library.all
                            .filter { d -> Materials.all.any { it.disciplineId == d.id } }
                            .forEach { d ->
                                FilterChip(
                                    selected = filter == d.id,
                                    onClick = { filter = if (filter == d.id) null else d.id },
                                    label = { Text(d.short) }
                                )
                            }
                    }
                }
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.id }) { m ->
                        MaterialRow(
                            m = m,
                            onOpen = { Materials.open(context, m) },
                            onSend = { Materials.send(context, m) },
                            onDelete = { Materials.remove(context, m.id) }
                        )
                        ThinRule(Modifier.padding(start = 62.dp))
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Приложить файл") }
        )
    }
}

@Composable
private fun MaterialRow(
    m: Material,
    onOpen: () -> Unit,
    onSend: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val label = m.disciplineId?.let { Library.byId(it).short } ?: "Общий"

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 18.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            iconFor(m.mime),
            null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$label · ${Materials.kindText(m.mime, m.title)} · ${Materials.sizeText(m.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, "Действия", modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Открыть") },
                    onClick = { menu = false; onOpen() }
                )
                DropdownMenuItem(
                    text = { Text("Отправить") },
                    onClick = { menu = false; onSend() }
                )
                DropdownMenuItem(
                    text = { Text("Удалить") },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

private fun iconFor(mime: String): ImageVector = when {
    mime.startsWith("image/") -> Icons.Filled.Image
    mime.contains("pdf") -> Icons.Filled.PictureAsPdf
    mime.contains("spreadsheet") || mime.contains("excel") -> Icons.Filled.TableChart
    mime.contains("presentation") || mime.contains("powerpoint") -> Icons.Filled.Slideshow
    else -> Icons.Filled.Description
}
