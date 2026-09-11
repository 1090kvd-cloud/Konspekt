package ru.vzvod.konspekt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.Settings
import java.util.UUID

private class SeriesRow(no: String, title: String) {
    var no by androidx.compose.runtime.mutableStateOf(no)
    var title by androidx.compose.runtime.mutableStateOf(title)
}

/**
 * Заготовки на всю тему разом. Предмет, тема, время и шапка берутся из формы,
 * здесь вводится только перечень занятий — так, как он стоит в программе.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    form: FormState,
    settings: Settings,
    onBack: () -> Unit,
    onCreate: (List<LessonInput>) -> Unit
) {
    val rows = remember {
        mutableStateListOf(SeriesRow("1", ""), SeriesRow("2", ""))
    }
    val ready = rows.count { it.title.isNotBlank() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Серия занятий", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Одна тема — несколько заготовок",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Назад") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Section("Общее для всей темы", "Меняется на предыдущем экране") {
                KeyValue("Предмет", Library.byId(form.disciplineId).name)
                KeyValue("ТЕМА ${form.themeNo}", form.topic.ifBlank { "не введена" })
                KeyValue("Время каждого", "${form.minutes} мин.")
                val unit = form.unitName.ifBlank { settings.unitName }
                if (unit.isNotBlank()) KeyValue("Подразделение", unit)
            }

            Section(
                "Занятия по теме",
                "Наименования выпишите из программы — они пойдут строкой «ЗАНЯТИЕ N:»"
            ) {
                rows.forEachIndexed { idx, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = if (idx == 0) 0.dp else 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = row.no,
                            onValueChange = { row.no = it },
                            label = { Text("№") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(88.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        OutlinedTextField(
                            value = row.title,
                            onValueChange = { row.title = it },
                            label = { Text("Наименование занятия") },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { if (rows.size > 1) rows.removeAt(idx) },
                            enabled = rows.size > 1
                        ) {
                            Icon(Icons.Filled.Close, "Убрать", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                TextButton(
                    onClick = { rows.add(SeriesRow("${rows.size + 1}", "")) },
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Добавить занятие")
                }
            }

            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val base = form.toInput(settings)
                    val list = rows
                        .filter { it.title.isNotBlank() }
                        .mapIndexed { i, row ->
                            base.copy(
                                id = UUID.randomUUID().toString(),
                                // Раньше в списке — первое занятие темы.
                                createdAt = now - i,
                                lessonNo = row.no.trim().ifBlank { "${i + 1}" },
                                lessonTitle = row.title.trim(),
                                edits = emptyMap()
                            )
                        }
                    onCreate(list)
                },
                enabled = form.topic.isNotBlank() && ready > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(54.dp)
            ) {
                Text(
                    if (ready > 0) "Создать заготовки: $ready" else "Впишите хотя бы одно занятие",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Text(
                "Заготовки лягут в архив. Каждую можно открыть, дописать содержание и распечатать.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(Modifier.height(30.dp))
        }
    }
}
