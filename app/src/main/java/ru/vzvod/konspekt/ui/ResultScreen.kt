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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import ru.vzvod.konspekt.data.ShareLesson
import ru.vzvod.konspekt.data.Sources
import ru.vzvod.konspekt.logic.DocxRewrite
import ru.vzvod.konspekt.logic.DocxImages
import ru.vzvod.konspekt.logic.DocxWriter
import ru.vzvod.konspekt.logic.Renderer
import ru.vzvod.konspekt.logic.Review
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
    autoNotes: List<String> = emptyList(),
    onConditions: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var showMaterials by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }

    val tabs = buildList {
        add("Конспект")
        if (plan.handout.isNotEmpty()) add("Раздатка")
        if (plan.control.isNotEmpty()) add("Вопросы")
    }

    val currentText: () -> String = {
        DocxImages.stripMarkers(rawText(plan, settings, tabs.getOrNull(tab)))
    }



    // Настоящий .docx: открывается и Word, и мобильными офисами, разметка не лезет наружу.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    ) { uri ->
        if (uri != null) {
            runCatching {
                val source = Sources.file(context, plan.input.sourceName)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    // У загруженного конспекта своя вёрстка, таблицы и рисунки:
                    // правим его копию, а не собираем документ заново.
                    val done = source != null &&
                        DocxRewrite.rewrite(source, out, plan, settings).ok
                    if (!done) DocxWriter.write(out, plan, settings, context)
                }
            }
        }
    }

    if (showMaterials) {
        MaterialsDialog(
            disciplineId = plan.input.disciplineId,
            onDismiss = { showMaterials = false }
        )
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
                    val materials = Materials.forDiscipline(plan.input.disciplineId)
                    if (materials.isNotEmpty()) {
                        IconButton(onClick = { showMaterials = true }) {
                            Icon(
                                Icons.Filled.AttachFile,
                                "Материалы к занятию: ${materials.size}"
                            )
                        }
                    }
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
                    val fromFile = plan.input.sourceName.isNotBlank()

                    ActionButton(Icons.Filled.ContentCopy, "Копировать") {
                        Export.copy(context, "Конспект", currentText())
                    }
                    if (fromFile) {
                        // Оригинал — когда нужна точная вёрстка исходника,
                        // Word и PDF — когда нужен документ в форме приложения.
                        ActionButton(Icons.Filled.Attachment, "Оригинал") {
                            Sources.open(context, plan.input.sourceName)
                        }
                    } else {
                        ActionButton(Icons.Filled.Share, "Отправить") {
                            // Занятие целиком: у товарища оно откроется в приложении.
                            ShareLesson.send(context, listOf(plan.input))
                        }
                    }
                    ActionButton(Icons.Filled.Description, "Word") {
                        saveLauncher.launch(Renderer.fileName(plan) + ".docx")
                    }
                    ActionButton(Icons.Filled.Print, "PDF") {
                        Renderer.imageDir = DocxImages.dir(context)
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

            if (plan.input.sourceName.isNotBlank()) {
                Text(
                    "Конспект загружен из файла. Кнопка «Word» отдаёт его со своей вёрсткой, " +
                        "таблицами и рисунками — приложение только подставит дату, " +
                        "подразделение и руководителя. Текст ниже разобран для правки и поиска.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }

            if (autoNotes.isNotEmpty()) {
                Text(
                    "Подставлено автоматически: " + autoNotes.joinToString("; ") +
                        ". Проверьте текст и поправьте, если не подходит.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }

            val notes = remember(plan) { Review.check(plan, settings) }
            if (notes.isNotEmpty()) {
                val errors = Review.errors(notes)
                val bad = errors > 0
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (bad) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.tertiaryContainer
                        )
                        .clickable { showReview = !showReview }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        if (bad) "Проверка: $errors ${plural(errors)} до сдачи"
                        else "Проверка: ${notes.size} ${remarks(notes.size)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (bad) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    if (showReview) {
                        Spacer(Modifier.height(6.dp))
                        notes.forEach { n ->
                            Text(
                                (if (n.level == Review.Level.ERROR) "• " else "— ") + n.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (bad) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            "Нажмите, чтобы посмотреть список",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bad) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
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


private fun rawText(plan: LessonPlan, settings: Settings, tab: String?): String = when (tab) {
    "Раздатка" -> Renderer.handoutText(plan, settings)
    "Вопросы" -> Renderer.controlText(plan)
    else -> Renderer.planText(plan, settings)
}

/** Приложенные методички — отдельным списком, чтобы не занимать место в документе. */
@Composable
private fun MaterialsDialog(disciplineId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val items = Materials.forDiscipline(disciplineId)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Материалы к занятию") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Наименования входят в материальное обеспечение. " +
                        "Нажмите, чтобы открыть файл.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                items.forEach { m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { Materials.open(context, m) }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(m.title, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${Materials.kindText(m)} · ${Materials.sizeText(m.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    ThinRule()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

private fun plural(n: Int): String = when {
    n % 10 == 1 && n % 100 != 11 -> "замечание"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "замечания"
    else -> "замечаний"
}

private fun remarks(n: Int): String = plural(n)
