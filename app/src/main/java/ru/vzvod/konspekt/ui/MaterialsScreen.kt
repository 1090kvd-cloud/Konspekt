package ru.vzvod.konspekt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
    // Выбранные файлы ждут, пока укажут предмет: сначала «что», потом «куда».
    var pending by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Material?>(null) }
    var retargeting by remember { mutableStateOf<Material?>(null) }

    // Несколько файлов за раз: методички обычно лежат пачкой.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) pending = uris
    }

    val shown = if (filter == null) Materials.all.toList()
    else Materials.all.filter { it.disciplineId == filter }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            if (error.isNotBlank()) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            if (Materials.all.isEmpty()) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 40.dp)
                ) {
                    Text("Материалов пока нет", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Нажмите кнопку с плюсом справа, выберите методички — приложение " +
                            "спросит, к какому предмету их отнести. Файлы копируются внутрь " +
                            "и открываются без интернета, даже если исходные удалить.",
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
                            onRename = { renaming = m },
                            onRetarget = { retargeting = m },
                            onDelete = { Materials.remove(context, m.id) }
                        )
                        ThinRule(Modifier.padding(start = 62.dp))
                    }
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        // Кнопка живёт у правого края: свайп влево вытягивает её, вправо — убирает.
        SideActionButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }

    if (pending.isNotEmpty()) {
        AttachDialog(
            count = pending.size,
            onPick = { disciplineId ->
                var failed = 0
                pending.forEach { uri ->
                    Materials.add(context, uri, disciplineId).onFailure { failed++ }
                }
                error = if (failed > 0) "Не удалось приложить файлов: $failed" else ""
                pending = emptyList()
            },
            onDismiss = { pending = emptyList() }
        )
    }

    renaming?.let { m ->
        RenameDialog(
            current = m.title,
            onDismiss = { renaming = null },
            onConfirm = { newTitle ->
                Materials.rename(context, m.id, newTitle)
                renaming = null
            }
        )
    }

    retargeting?.let { m ->
        RetargetDialog(
            current = m.disciplineId,
            onDismiss = { retargeting = null },
            onPick = { id ->
                Materials.retarget(context, m.id, id)
                retargeting = null
            }
        )
    }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Наименование материала") },
        text = {
            Column {
                Text(
                    "Под этим наименованием материал попадёт в материальное обеспечение конспекта.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RetargetDialog(
    current: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("К какому предмету относится") },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = current == null,
                    onClick = { onPick(null) },
                    label = { Text("Общий") }
                )
                Library.all.forEach { d ->
                    FilterChip(
                        selected = current == d.id,
                        onClick = { onPick(d.id) },
                        label = { Text(d.short) }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
private fun MaterialRow(
    m: Material,
    onOpen: () -> Unit,
    onSend: () -> Unit,
    onRename: () -> Unit,
    onRetarget: () -> Unit,
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
                "$label · ${Materials.kindText(m)} · ${Materials.sizeText(m.size)}",
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
                    text = { Text("Переименовать") },
                    onClick = { menu = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Сменить предмет") },
                    onClick = { menu = false; onRetarget() }
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

/**
 * Плавающая кнопка у края экрана. В покое утоплена за правый край и не закрывает текст.
 * Свайп влево — выезжает целиком, свайп вправо или повторное нажатие — прячется.
 */
@Composable
private fun SideActionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    var out by remember { mutableStateOf(false) }
    val shift by animateDpAsState(if (out) 0.dp else 30.dp, label = "shift")
    val alpha by animateFloatAsState(if (out) 1f else 0.55f, label = "alpha")

    FloatingActionButton(
        onClick = {
            if (out) onClick() else out = true
        },
        modifier = modifier
            .padding(vertical = 20.dp)
            .offset(x = shift)
            .alpha(alpha)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    if (delta < -3f) out = true
                    if (delta > 3f) out = false
                }
            ),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape
    ) {
        Icon(Icons.Filled.Add, "Приложить файл", modifier = Modifier.size(26.dp))
    }
}

/** Спрашивает предмет для только что выбранных файлов. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AttachDialog(
    count: Int,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "К какому предмету?" else "К какому предмету? Файлов: $count") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Материал появится на занятиях по выбранному предмету.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                ChoiceTile(
                    icon = Icons.Filled.Apps,
                    label = "Общие — для всех предметов",
                    selected = false,
                    modifier = Modifier.fillMaxWidth()
                ) { onPick(null) }
                Spacer(Modifier.height(10.dp))
                TileGrid(Library.all) { d, m ->
                    ChoiceTile(
                        icon = disciplineIcon(d.id),
                        label = d.short,
                        selected = false,
                        modifier = m
                    ) { onPick(d.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
