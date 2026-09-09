package ru.vzvod.konspekt.ui

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.data.Disciplines
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArchiveScreen(
    items: List<LessonInput>,
    onOpen: (LessonInput) -> Unit,
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
                        "${Disciplines.byId(item.disciplineId).name} · ${item.minutes} мин · ${fmt.format(Date(item.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Filled.DeleteOutline, "Удалить", modifier = Modifier.size(20.dp))
                }
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

        Section("Вид") {
            ToggleRow(
                "Тёмная тема",
                "Удобно в казарме вечером",
                settings.darkTheme
            ) { onChange(settings.copy(darkTheme = it)) }
        }

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
