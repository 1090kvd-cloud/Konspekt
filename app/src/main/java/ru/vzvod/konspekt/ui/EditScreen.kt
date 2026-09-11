package ru.vzvod.konspekt.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.logic.Generator
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.LessonPlan

private data class EditField(
    val key: String,
    val label: String,
    val hint: String,
    val default: String,
    val singleLine: Boolean = false
)

/**
 * Правка готового документа. Каждый раздел — обычное текстовое поле,
 * строка соответствует пункту. Сохраняется только то, что отличается от шаблона,
 * поэтому «Вернуть шаблонный текст» всегда работает.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    input: LessonInput,
    onCancel: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val raw = remember(input.id) { Generator.build(input, applyEdits = false) }
    val fields = remember(input.id) { fieldsOf(raw) }
    val texts = remember(input.id) {
        mutableStateListOf<String>().apply {
            fields.forEach { add(input.edits[it.key] ?: it.default) }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Правка конспекта", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Строка — один пункт документа",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Отменить") }
                },
                actions = {
                    IconButton(onClick = {
                        val result = HashMap<String, String>()
                        fields.forEachIndexed { i, f ->
                            val value = texts[i]
                            if (value.trim() != f.default.trim() && value.isNotBlank()) {
                                result[f.key] = value
                            }
                        }
                        onSave(result)
                    }) { Icon(Icons.Filled.Done, "Сохранить правки") }
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
            fields.forEachIndexed { i, f ->
                Section(f.label, f.hint) {
                    OutlinedTextField(
                        value = texts[i],
                        onValueChange = { texts[i] = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = f.singleLine,
                        minLines = if (f.singleLine) 1 else 3,
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    if (texts[i].trim() != f.default.trim()) {
                        TextButton(onClick = { texts[i] = f.default }) {
                            Text("Вернуть шаблонный текст")
                        }
                    }
                }
            }

            TextButton(
                onClick = { fields.forEachIndexed { i, f -> texts[i] = f.default } },
                modifier = Modifier.padding(start = 20.dp)
            ) { Text("Сбросить все правки") }

            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun fieldsOf(plan: LessonPlan): List<EditField> {
    val out = ArrayList<EditField>()
    val perLine = "Каждая строка — отдельный пункт"

    out += EditField(Generator.Keys.GOALS, "Цели занятия", "Нумерация проставится сама", plan.goals.join())
    out += EditField(Generator.Keys.PROVISION, "Материальное обеспечение", perLine, plan.provision.join())
    if (plan.safety.isNotEmpty()) {
        out += EditField(Generator.Keys.SAFETY, "Требования безопасности", perLine, plan.safety.join())
    }

    out += EditField(
        Generator.Keys.INTRO_CONTENT, "Вводная часть — содержание",
        "От первого лица: «Проверяю…», «Довожу…»", plan.intro.content.join()
    )
    out += EditField(
        Generator.Keys.INTRO_TRAINEE, "Вводная часть — действия обучаемых",
        perLine, plan.intro.trainee.join()
    )

    plan.questions.forEach { q ->
        out += EditField(
            Generator.Keys.qTitle(q.index), "Учебный вопрос № ${q.index}",
            "Наименование вопроса", q.title, singleLine = true
        )
        out += EditField(
            Generator.Keys.qContent(q.index), "Вопрос № ${q.index} — содержание",
            "Сюда вставляется материал из методички", q.content.join()
        )
        out += EditField(
            Generator.Keys.qTrainee(q.index), "Вопрос № ${q.index} — действия обучаемых",
            perLine, q.trainee.join()
        )
    }

    out += EditField(
        Generator.Keys.OUTRO_CONTENT, "Заключительная часть — содержание",
        perLine, plan.outro.content.join()
    )
    out += EditField(
        Generator.Keys.OUTRO_TRAINEE, "Заключительная часть — действия обучаемых",
        perLine, plan.outro.trainee.join()
    )

    if (plan.control.isNotEmpty()) {
        out += EditField(Generator.Keys.CONTROL, "Контрольные вопросы", perLine, plan.control.join())
    }
    return out
}

private fun List<String>.join() = joinToString("\n")
