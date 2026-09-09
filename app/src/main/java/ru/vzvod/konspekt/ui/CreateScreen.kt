package ru.vzvod.konspekt.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.data.Disciplines
import ru.vzvod.konspekt.logic.Generator

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    form: FormState,
    onBuild: () -> Unit,
    modifier: Modifier = Modifier
) {
    val discipline = Disciplines.byId(form.disciplineId)
    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Section("Предмет обучения") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Disciplines.all.forEach { d ->
                    FilterChip(
                        selected = d.id == form.disciplineId,
                        onClick = { form.disciplineId = d.id },
                        label = { Text(d.short) }
                    )
                }
            }
        }

        Section("Тема занятия", "Пишите так, как тема стоит в расписании") {
            OutlinedTextField(
                value = form.topic,
                onValueChange = { form.topic = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Например: Действия солдата в обороне") },
                minLines = 2,
                textStyle = MaterialTheme.typography.bodyLarge
            )
        }

        Section("Время", timeBreakdown(form.minutes, form.effectiveQuestionCount())) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(45, 50, 90, 120, 180).forEach { m ->
                    FilterChip(
                        selected = form.minutes == m,
                        onClick = { form.minutes = m },
                        label = { Text("$m мин") }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Точное время",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Stepper(
                    value = form.minutes,
                    onChange = { form.minutes = it },
                    range = 15..360,
                    step = 5,
                    suffix = " мин"
                )
            }
        }

        Section(
            "Учебные вопросы",
            if (form.customQuestions.isEmpty()) "Сформулируются по предмету и теме" else "Заданы вручную"
        ) {
            if (form.customQuestions.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Сколько вопросов",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Stepper(
                        value = form.questionCount,
                        onChange = { form.questionCount = it },
                        range = 1..6
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    discipline.questionTemplates.first()
                        .replace("{t}", form.topic.ifBlank { "тема занятия" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            form.customQuestions.forEachIndexed { idx, q ->
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = q,
                        onValueChange = { form.customQuestions[idx] = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Вопрос ${idx + 1}") },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = { form.customQuestions.removeAt(idx) }) {
                        Icon(Icons.Filled.Close, "Убрать вопрос", modifier = Modifier.size(20.dp))
                    }
                }
            }

            TextButton(
                onClick = { form.customQuestions.add("") },
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Написать вопрос вручную")
            }
        }

        Section("Что вложить в комплект") {
            ToggleRow(
                "Раздаточный материал",
                "Опорный конспект для обучаемых",
                form.includeHandout
            ) { form.includeHandout = it }
            ThinRule(Modifier.padding(vertical = 4.dp))
            ToggleRow(
                "Контрольные вопросы",
                "Для опроса в заключительной части",
                form.includeControl
            ) { form.includeControl = it }
            ThinRule(Modifier.padding(vertical = 4.dp))
            ToggleRow(
                "Требования безопасности",
                "Отдельный раздел и пункт инструктажа",
                form.includeSafety
            ) { form.includeSafety = it }
        }

        TextButton(
            onClick = { showDetails = !showDetails },
            modifier = Modifier.padding(start = 20.dp)
        ) {
            Icon(
                if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.size(6.dp))
            Text(if (showDetails) "Свернуть шапку документа" else "Заполнить шапку документа")
        }

        AnimatedVisibility(visible = showDetails) {
            Column {
                Section("Шапка документа", "Пустые поля подставятся из настроек") {
                    OutlinedTextField(
                        value = form.unitName,
                        onValueChange = { form.unitName = it },
                        label = { Text("Подразделение") },
                        placeholder = { Text("1 мсв 2 мср") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        OutlinedTextField(
                            value = form.date,
                            onValueChange = { form.date = it },
                            label = { Text("Дата") },
                            modifier = Modifier.weight(1.4f)
                        )
                        Spacer(Modifier.size(10.dp))
                        OutlinedTextField(
                            value = form.lessonNo,
                            onValueChange = { form.lessonNo = it },
                            label = { Text("Занятие №") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = form.leader,
                        onValueChange = { form.leader = it },
                        label = { Text("Руководитель занятия") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Section("Место проведения") {
                    FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                        discipline.places.forEach { p ->
                            FilterChip(
                                selected = form.place == p,
                                onClick = { form.place = if (form.place == p) "" else p },
                                label = { Text(p) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = form.place,
                        onValueChange = { form.place = it },
                        label = { Text("Или впишите своё") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Section("Метод проведения") {
                    FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                        discipline.methods.forEach { m ->
                            FilterChip(
                                selected = form.method == m,
                                onClick = { form.method = if (form.method == m) "" else m },
                                label = { Text(m) }
                            )
                        }
                    }
                }

                Section("Примечания", "Попадут отдельным разделом в конспект") {
                    OutlinedTextField(
                        value = form.note,
                        onValueChange = { form.note = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("Что учесть именно на этом занятии") }
                    )
                }
            }
        }

        Button(
            onClick = onBuild,
            enabled = form.topic.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(54.dp)
        ) {
            Text("Собрать конспект", style = MaterialTheme.typography.titleMedium)
        }

        Text(
            if (form.topic.isBlank()) "Введите тему — кнопка станет активной"
            else "Готовый комплект можно отправить, скопировать или сохранить в PDF",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.height(28.dp))
    }
}

/** Живой расчёт: показывает, как время разложится по частям занятия. */
private fun timeBreakdown(minutes: Int, questions: Int): String {
    val total = maxOf(15, minutes)
    val intro = (((total * 10 / 100) + 2) / 5 * 5).coerceIn(5, 15)
    val outro = (((total * 8 / 100) + 2) / 5 * 5).coerceIn(5, 10)
    val main = maxOf(5, total - intro - outro)
    val slices = Generator.split(main, questions)
    return "Вводная $intro · основная $main (${slices.joinToString("+")}) · заключительная $outro"
}
