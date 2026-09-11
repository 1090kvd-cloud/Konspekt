package ru.vzvod.konspekt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.vzvod.konspekt.data.Materials
import ru.vzvod.konspekt.logic.Renderer
import ru.vzvod.konspekt.model.LessonPlan
import ru.vzvod.konspekt.model.Settings
import ru.vzvod.konspekt.util.Export

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    plan: LessonPlan,
    settings: Settings,
    savedAlready: Boolean,
    onSave: () -> Unit,
    onEdit: () -> Unit,
    onConditions: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    val tabs = buildList {
        add("Конспект")
        if (plan.handout.isNotEmpty()) add("Раздатка")
        if (plan.control.isNotEmpty()) add("Вопросы")
    }

    val currentText: () -> String = {
        when (tabs.getOrNull(tab)) {
            "Раздатка" -> Renderer.handoutText(plan, settings)
            "Вопросы" -> Renderer.controlText(plan)
            else -> Renderer.planText(plan, settings)
        }
    }

    // Word открывает html с этим типом как обычный документ и позволяет его править.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/msword")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    // Метка BOM: по ней Word и мобильные офисы опознают UTF-8 без вопросов.
                    it.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                    it.write(Renderer.html(plan, settings).toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            plan.input.topic,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            "${plan.disciplineName} · ${Renderer.timeOf(plan)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onConditions) {
                        Icon(Icons.Filled.Tune, "Изменить условия занятия")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.EditNote, "Править текст")
                    }
                    IconButton(onClick = onSave, enabled = !savedAlready) {
                        Icon(
                            Icons.Filled.BookmarkAdd,
                            if (savedAlready) "Уже в архиве" else "Сохранить в архив"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Column {
                ThinRule()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(Icons.Filled.ContentCopy, "Копировать") {
                        Export.copy(context, "Конспект", currentText())
                    }
                    ActionButton(Icons.Filled.Share, "Отправить") {
                        Export.share(context, Renderer.fileName(plan), currentText())
                    }
                    ActionButton(Icons.Filled.Description, "Word") {
                        saveLauncher.launch(Renderer.fileName(plan) + ".doc")
                    }
                    ActionButton(Icons.Filled.Print, "PDF") {
                        Export.printPdf(
                            context,
                            Renderer.fileName(plan),
                            Renderer.html(plan, settings),
                            settings.landscape
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {

            val gaps = missingFields(plan, settings)
            if (gaps.isNotEmpty()) {
                Text(
                    "Не заполнено: ${gaps.joinToString(", ")}. " +
                        "Документ распечатается с пустыми местами.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }

            if (tabs.size > 1) {
                TabRow(
                    selectedTabIndex = tab.coerceAtMost(tabs.lastIndex),
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    tabs.forEachIndexed { i, name ->
                        Tab(
                            selected = i == tab,
                            onClick = { tab = i },
                            text = { Text(name, style = MaterialTheme.typography.labelLarge) }
                        )
                    }
                }
            }
            Box(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
            ) {
                when (tabs.getOrNull(tab)) {
                    "Раздатка" -> HandoutView(plan)
                    "Вопросы" -> ControlView(plan)
                    else -> PlanView(plan, settings)
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .width(84.dp)
    ) {
        IconButton(onClick = onClick) { Icon(icon, label, modifier = Modifier.size(22.dp)) }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
    }
}

@Composable
private fun PlanView(plan: LessonPlan, settings: Settings) {
    val i = plan.input
    val unit = i.unitName.ifBlank { settings.unitName }

    Column {
        Spacer(Modifier.height(12.dp))

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            Text("УТВЕРЖДАЮ", style = MaterialTheme.typography.labelLarge)
            Text(
                settings.approver.ifBlank { "Командир роты" },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "«___» __________ 20___ г.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "ПЛАН-КОНСПЕКТ",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            "проведения занятия по ${plan.dative}" +
                if (unit.isNotBlank()) "\nс $unit" else "",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
        LabelledParagraph("ТЕМА ${i.themeNo}:", i.topic)
        LabelledParagraph("ЗАНЯТИЕ ${i.lessonNo}:", lessonTitleOf(plan))

        DocHeading("ЦЕЛИ")
        plan.goals.forEachIndexed { k, g ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(
                    "${k + 1}.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.width(24.dp)
                )
                Text(g, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))
        KeyValue("Время", Renderer.timeOf(plan) + if (i.date.isNotBlank()) ", ${i.date}" else "")
        KeyValue("Место", plan.place)
        KeyValue("Мат. обеспечение", plan.provision.joinToString(", "))

        if (plan.safety.isNotEmpty()) {
            DocHeading("ТРЕБОВАНИЯ БЕЗОПАСНОСТИ")
            plan.safety.forEach { Bullet(it) }
        }

        DocHeading("ХОД ЗАНЯТИЯ", Renderer.timeOf(plan))
        StageView("1. ${plan.intro.title}", plan.intro.minutes, plan.intro.content, plan.intro.trainee)

        Spacer(Modifier.height(14.dp))
        Text(
            "2. Основная часть — ${plan.mainMinutes} мин.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        plan.questions.forEach { q ->
            StageView("${q.index}. ${q.title}", q.minutes, q.content, q.trainee)
        }

        Spacer(Modifier.height(14.dp))
        StageView("3. ${plan.outro.title}", plan.outro.minutes, plan.outro.content, plan.outro.trainee)

        if (i.note.isNotBlank()) {
            DocHeading("ПРИМЕЧАНИЯ РУКОВОДИТЕЛЯ")
            Text(i.note, style = MaterialTheme.typography.bodyMedium)
        }

        val materials = Materials.forDiscipline(i.disciplineId)
        if (materials.isNotEmpty()) {
            val context = LocalContext.current
            DocHeading("МАТЕРИАЛЫ К ЗАНЯТИЮ")
            materials.forEach { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { Materials.open(context, m) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        "→",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.width(22.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(m.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${Materials.kindText(m)} · ${Materials.sizeText(m.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                "Раздел для подготовки: в печатный документ и в отправку он не попадает.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            "Руководитель занятия",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            i.leader.ifBlank { settings.leader }.ifBlank { "________________________" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(30.dp))
    }
}

private fun lessonTitleOf(plan: LessonPlan): String {
    val own = plan.input.lessonTitle.trim()
    if (own.isNotEmpty()) return own
    return plan.questions.joinToString(" ") { it.title.trimEnd('.') + "." }
}

@Composable
private fun LabelledParagraph(label: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StageView(title: String, minutes: Int, content: List<String>, trainee: List<String>) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$minutes мин.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(6.dp))
        SubLabel("Содержание учебных вопросов")
        content.forEach { Bullet(it, "•") }
        SubLabel("Действия обучаемых")
        trainee.forEach { Bullet(it, "•") }
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun HandoutView(plan: LessonPlan) {
    Column {
        Spacer(Modifier.height(12.dp))
        Text(
            "РАЗДАТОЧНЫЙ МАТЕРИАЛ",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Печатается по числу отделений",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        plan.handout.forEach { block ->
            DocHeading(block.title.uppercase())
            block.items.forEach { Bullet(it, "") }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ControlView(plan: LessonPlan) {
    Column {
        Spacer(Modifier.height(12.dp))
        Text(
            "КОНТРОЛЬНЫЕ ВОПРОСЫ",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "Для опроса в заключительной части",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        plan.control.forEachIndexed { idx, q ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Text(
                    "${idx + 1}.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.width(26.dp)
                )
                Text(q, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
            ThinRule()
        }
        Spacer(Modifier.height(14.dp))
        Text("Критерии оценки", style = MaterialTheme.typography.titleSmall)
        Bullet("«5» — полный правильный ответ на все вопросы.")
        Bullet("«4» — ответ с несущественными неточностями.")
        Bullet("«3» — ответ на большинство вопросов после наводящих.")
        Bullet("«2» — материал не усвоен.")
        Spacer(Modifier.height(30.dp))
    }
}

/** Что руководитель забыл заполнить. Проверяем перед печатью, а не после. */
private fun missingFields(plan: LessonPlan, settings: Settings): List<String> {
    val i = plan.input
    val out = ArrayList<String>()
    if (i.date.isBlank()) out.add("дата")
    if (i.unitName.ifBlank { settings.unitName }.isBlank()) out.add("подразделение")
    if (i.leader.ifBlank { settings.leader }.isBlank()) out.add("руководитель")
    if (i.lessonTitle.isBlank()) out.add("наименование занятия")
    if (plan.place.isBlank()) out.add("место")
    return out
}
