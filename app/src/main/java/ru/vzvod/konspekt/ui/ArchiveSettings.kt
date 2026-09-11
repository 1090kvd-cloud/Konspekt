package ru.vzvod.konspekt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import ru.vzvod.konspekt.data.Backup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArchiveScreen(
    items: List<LessonInput>,
    onOpen: (LessonInput) -> Unit,
    onDuplicate: (LessonInput) -> Unit,
    onDelete: (LessonInput) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Архив пуст", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Соберите конспект и нажмите «Сохранить в архив» — он останется здесь на следующий раз.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val fmt = remember { SimpleDateFormat("d MMMM, HH:mm", Locale("ru")) }

    LazyColumn(modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(item) }
                    .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.topic.ifBlank { "Без темы" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${Library.byId(item.disciplineId).name} · ${item.minutes} мин · ${fmt.format(Date(item.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ArchiveMenu(
                    onOpen = { onOpen(item) },
                    onDuplicate = { onDuplicate(item) },
                    onDelete = { onDelete(item) }
                )
            }
            ThinRule(Modifier.padding(start = 20.dp))
        }
    }
}

@Composable
fun SettingsScreen(
    settings: Settings,
    onChange: (Settings) -> Unit,
    onClearArchive: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmClear by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {

        Section("Подставляется в каждый документ", "Чтобы не вводить одно и то же каждый вечер") {
            OutlinedTextField(
                value = settings.leader,
                onValueChange = { onChange(settings.copy(leader = it)) },
                label = { Text("Руководитель занятия") },
                placeholder = { Text("лейтенант Иванов И. И.") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = settings.unitName,
                onValueChange = { onChange(settings.copy(unitName = it)) },
                label = { Text("Подразделение") },
                placeholder = { Text("1 мсв 2 мср") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = settings.approver,
                onValueChange = { onChange(settings.copy(approver = it)) },
                label = { Text("Кто утверждает") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Section("Время по умолчанию") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Новое занятие открывается с этим временем",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Stepper(
                    value = settings.defaultMinutes,
                    onChange = { onChange(settings.copy(defaultMinutes = it)) },
                    range = 15..360,
                    step = 5,
                    suffix = " мин"
                )
            }
        }

        Section("Печать и PDF") {
            ToggleRow(
                "Альбомная ориентация",
                if (settings.landscape)
                    "Альбомный лист: та же таблица, шире графа содержания"
                else
                    "Книжный лист, как в образце план-конспекта",
                settings.landscape
            ) { onChange(settings.copy(landscape = it)) }
            Spacer(Modifier.height(6.dp))
            Text(
                "Ориентацию можно поменять и в самом диалоге печати.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Section("Вид") {
            ToggleRow(
                "Тёмная тема",
                "Удобно в казарме вечером",
                settings.darkTheme
            ) { onChange(settings.copy(darkTheme = it)) }
        }

        LibrarySection()

        BackupSection(onReload)

        Section("Данные") {
            Text(
                "Всё хранится только на этом телефоне. Приложение не выходит в сеть и не требует разрешений.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmClear = true }) { Text("Очистить архив") }
        }

        Spacer(Modifier.height(40.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить архив?") },
            text = { Text("Все сохранённые конспекты будут удалены с телефона. Отменить не получится.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearArchive()
                    confirmClear = false
                }) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun LibrarySection() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            status = if (text == null) {
                "Не удалось прочитать файл"
            } else {
                Library.install(context, text, "файл на телефоне").fold(
                    onSuccess = { "Загружено предметов: $it" },
                    onFailure = { it.message ?: "Файл не подошёл" }
                )
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            status = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(Library.export().toByteArray())
                }
                "Библиотека сохранена в файл"
            }.getOrElse { "Не удалось сохранить файл" }
        }
    }

    Section(
        "Библиотека предметов",
        "Версия ${Library.version} · ${Library.origin} · предметов: ${Library.all.size}"
    ) {
        Text(
            "Формулировки целей, вопросов и пособий можно заменить своими. " +
                "Выгрузите текущий набор, поправьте на компьютере и загрузите обратно — " +
                "или примите готовый файл от товарища.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row {
            TextButton(onClick = {
                importer.launch(arrayOf("application/json", "text/plain", "*/*"))
            }) { Text("Загрузить файл") }
            TextButton(onClick = { exporter.launch("library.json") }) { Text("Сохранить файл") }
        }
        TextButton(onClick = {
            Library.reset(context)
            status = "Возвращена встроенная библиотека"
        }) { Text("Вернуть встроенную") }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun ArchiveMenu(onOpen: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, "Действия", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Открыть") }, onClick = { open = false; onOpen() })
            DropdownMenuItem(
                text = { Text("Сделать копию") },
                onClick = { open = false; onDuplicate() }
            )
            DropdownMenuItem(text = { Text("Удалить") }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
private fun BackupSection(onReload: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) {
            status = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    Backup.write(context, out).getOrThrow()
                } ?: 0
            }.fold(
                onSuccess = { "Сохранено файлов: $it" },
                onFailure = { "Не удалось сохранить копию" }
            )
        }
    }

    val loader = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            status = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    Backup.read(context, input).getOrThrow()
                } ?: 0
            }.fold(
                onSuccess = {
                    onReload()
                    "Восстановлено файлов: $it"
                },
                onFailure = { it.message ?: "Файл не подошёл" }
            )
        }
    }

    Section(
        "Резервная копия",
        "Архив, материалы, библиотека и настройки — одним файлом"
    ) {
        Text(
            "При смене телефона или удалении приложения всё пропадёт. Копию можно " +
                "положить на карту памяти или отправить самому себе.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row {
            TextButton(onClick = { saver.launch("konspekt-kopiya.zip") }) { Text("Сохранить копию") }
            TextButton(onClick = {
                loader.launch(arrayOf("application/zip", "*/*"))
            }) { Text("Восстановить") }
        }
        Text(
            "Восстановление заменяет то, что сейчас в приложении.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (status.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
