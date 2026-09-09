package ru.vzvod.konspekt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.dp
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

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(Renderer.everything(plan, settings).toByteArray())
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
                            "${plan.disciplineName} · ${plan.input.minutes} мин",
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
                    ActionButton(Icons.Filled.Download, "Файл .txt") {
                        saveLauncher.launch(Renderer.fileName(plan) + ".txt")
                    }
                    ActionButton(Icons.Filled.Print, "PDF") {
                        Export.printPdf(
                            context,
                            Renderer.fileName(plan),
                            Renderer.html(plan, settings)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
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
    Column {
        Spacer(Modifier.height(12.dp))
        Text(
            "ПЛАН-КОНСПЕКТ",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            "проведения занятия по предмету «${plan.disciplineName}»",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))

        val unit = i.unitName.ifBlank { settings.unitName }
        if (unit.isNotBlank()) KeyValue("Подразделение", unit)
        KeyValue("Тема", i.topic)
        if (i.lessonNo.isNotBlank()) KeyValue("Занятие", i.lessonNo)
        if (i.date.isNotBlank()) KeyValue("Дата", i.date)
        KeyValue("Время", "${i.minutes} мин.")
        KeyValue("Место", plan.place)
        KeyValue("Метод", plan.method)

        DocHeading("УЧЕБНЫЕ ВОПРОСЫ")
        plan.questions.forEach { q ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(
                    "${q.index}.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.width(24.dp)
                )
                Text(q.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${q.minutes}′",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DocHeading("ЦЕЛИ ЗАНЯТИЯ")
        SubLabel("Учебные")
        plan.eduGoals.forEach { Bullet(it) }
        SubLabel("Воспитательные")
        plan.upGoals.forEach { Bullet(it) }
        if (plan.metGoals.isNotEmpty()) {
            SubLabel("Методические")
            plan.metGoals.forEach { Bullet(it) }
        }

        DocHeading("РУКОВОДСТВА И ПОСОБИЯ")
        plan.references.forEach { Bullet(it) }

        DocHeading("МАТЕРИАЛЬНОЕ ОБЕСПЕЧЕНИЕ")
        plan.materials.forEach { Bullet(it) }

        if (plan.safety.isNotEmpty()) {
            DocHeading("ТРЕБОВАНИЯ БЕЗОПАСНОСТИ")
            plan.safety.forEach { Bullet(it) }
        }

        DocHeading("ХОД ЗАНЯТИЯ", "${i.minutes} мин.")
        StageView("I. ${plan.intro.title}", plan.intro.minutes, plan.intro.leader, plan.intro.trainee)
        Spacer(Modifier.height(14.dp))
        Text(
            "II. Основная часть — ${plan.mainMinutes} мин.",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        plan.questions.forEach { q ->
            StageView(
                "Учебный вопрос № ${q.index}. ${q.title}",
                q.minutes, q.leader, q.trainee
            )
        }
        Spacer(Modifier.height(14.dp))
        StageView("III. ${plan.outro.title}", plan.outro.minutes, plan.outro.leader, plan.outro.trainee)

        if (i.note.isNotBlank()) {
            DocHeading("ПРИМЕЧАНИЯ РУКОВОДИТЕЛЯ")
            Text(i.note, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(22.dp))
        ThinRule()
        Spacer(Modifier.height(8.dp))
        Text(
            "Руководитель занятия: ${i.leader.ifBlank { settings.leader }.ifBlank { "________________" }}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun StageView(title: String, minutes: Int, leader: List<String>, trainee: List<String>) {
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
        SubLabel("Руководитель")
        leader.forEach { Bullet(it, "•") }
        SubLabel("Обучаемые")
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
