package ru.vzvod.konspekt.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vzvod.konspekt.data.LessonsJson
import ru.vzvod.konspekt.data.Learned
import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.data.FileVault
import ru.vzvod.konspekt.data.ShareLesson
import ru.vzvod.konspekt.data.Program
import ru.vzvod.konspekt.ui.theme.Conducted
import ru.vzvod.konspekt.data.Sources
import ru.vzvod.konspekt.logic.LessonImport
import ru.vzvod.konspekt.logic.TextExtract
import ru.vzvod.konspekt.model.LessonInput
import ru.vzvod.konspekt.model.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArchiveScreen(
    items: List<LessonInput>,
    onOpen: (LessonInput) -> Unit,
    onEditConditions: (LessonInput) -> Unit,
    onDuplicate: (LessonInput) -> Unit,
    onToggleConducted: (LessonInput) -> Unit,
    onDelete: (LessonInput) -> Unit,
    onImported: (List<LessonInput>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importNote by remember { mutableStateOf("") }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            importNote = "Разбираю файлы…"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val lessons = ArrayList<LessonInput>()
                    val notes = ArrayList<String>()
                    uris.forEach { uri ->
                        val name = FileVault.displayName(context, uri)
                        val raw = runCatching {
                            context.contentResolver.openInputStream(uri)?.use {
                                TextExtract.readAsText(it.readBytes())
                            }
                        }.getOrNull()

                        // Файл базы конспектов узнаём по содержимому, а не по расширению.
                        if (raw != null && LessonsJson.looksLikeLessons(raw)) {
                            runCatching { LessonsJson.decode(raw) }.fold(
                                onSuccess = {
                                    lessons.addAll(it)
                                    notes.add("$name — база конспектов: ${it.size}")
                                },
                                onFailure = { notes.add("$name — ${it.message}") }
                            )
                            return@forEach
                        }

                        when (val t = TextExtract.fromUri(context, uri, name)) {
                            is TextExtract.Result.Unsupported -> notes.add("$name — ${t.reason}")
                            is TextExtract.Result.Ok -> if (Program.looksLikeProgram(t.text)) {
                                // В одном заходе можно выбрать и конспекты, и программу.
                                runCatching { Program.decode(t.text) }
                                    .onSuccess {
                                        Program.replaceAll(context, it)
                                        notes.add("$name — программа, занятий: ${it.size}")
                                    }
                                    .onFailure { notes.add("$name — ${it.message}") }
                            } else {
                                val out = LessonImport.parse(t.text, name)
                                out.lesson?.let { lesson ->
                                    // Исходник храним рядом: его можно распечатать
                                    // как есть, со всеми схемами и рисунками.
                                    val stored = Sources.save(context, uri, name)
                                    lessons.add(
                                        if (stored != null) lesson.copy(sourceName = stored)
                                        else lesson
                                    )
                                }
                                notes.add(out.note)
                            }
                        }
                    }
                    lessons to notes
                }
                onImported(result.first)
                importNote = if (result.first.isEmpty()) {
                    "Ничего не загружено. " + result.second.joinToString("; ")
                } else {
                    "Загружено конспектов: ${result.first.size}. " +
                        result.second.take(4).joinToString("; ")
                }
            }
        }
    }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            importNote = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(LessonsJson.encode(items).toByteArray())
                }
                "Выгружено конспектов: ${items.size}"
            }.getOrElse { "Не удалось сохранить файл" }
        }
    }

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
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                Text("Загрузить старые конспекты из файлов")
            }
            if (importNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    importNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        return
    }

    val fmt = remember { SimpleDateFormat("d MMMM, HH:mm", Locale("ru")) }
    var query by remember { mutableStateOf("") }

    val shown = run {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items
        else items.filter {
            it.topic.lowercase().contains(q) ||
                it.lessonTitle.lowercase().contains(q) ||
                Library.byId(it.disciplineId).name.lowercase().contains(q)
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp)) {
            TextButton(onClick = { importer.launch(arrayOf("*/*")) }) {
                Text("Загрузить из файлов")
            }
            TextButton(onClick = { exporter.launch("konspekty.json") }) {
                Text("Выгрузить базу")
            }
        }
        if (importNote.isNotBlank()) {
            Text(
                importNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        if (items.size >= 6) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск по теме или предмету") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (shown.isEmpty()) {
            Text(
                "Ничего не найдено",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(20.dp)
            )
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.id }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(item) }
                        .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val done = item.conductedAt > 0
                    if (done) {
                        // Зелёная полоса слева: проведённое видно, не вчитываясь.
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(46.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        // Заголовок — наименование занятия: у серии по одной теме оно разное.
                        Text(
                            item.lessonTitle.ifBlank { item.topic }.ifBlank { "Без темы" },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (done) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.lessonTitle.isNotBlank() && item.topic.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "ТЕМА ${item.themeNo}: ${item.topic}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Занятие ${item.lessonNo} · ${Library.byId(item.disciplineId).short} · " +
                                "${item.minutes} мин · ${fmt.format(Date(item.createdAt))}" +
                                (if (item.sourceName.isNotBlank()) " · есть оригинал" else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (done) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "ПРОВЕДЕНО ${fmt.format(Date(item.conductedAt))}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    ArchiveMenu(
                        onOpen = { onOpen(item) },
                        onEditConditions = { onEditConditions(item) },
                        onDuplicate = { onDuplicate(item) },
                        onShare = { ShareLesson.send(context, listOf(item)) },
                        conducted = done,
                        onToggleConducted = { onToggleConducted(item) },
                        hasSource = item.sourceName.isNotBlank(),
                        onSource = { Sources.open(context, item.sourceName) },
                        onDelete = { onDelete(item) }
                    )
                }
                ThinRule(Modifier.padding(start = 20.dp))
            }
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
                supportingText = { Text("Например: личным составом 1 мсв") },
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
            Spacer(Modifier.height(8.dp))
            Text(
                "Подставляется в новые занятия. В уже сохранённых остаётся то, " +
                    "что было на момент составления.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

        LearnedSection()

        ProgramSection()

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

        AboutSection()

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
            // Блокнот на компьютере дописывает метку BOM, а редакторы сохраняют в cp1251 —
            // читаем байтами и разбираемся с кодировкой сами, иначе JSON не разберётся.
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    TextExtract.readAsText(it.readBytes())
                }
            }.getOrNull()
            status = if (text == null) {
                "Не удалось прочитать файл"
            } else {
                Library.install(context, text, "файл на телефоне").fold(
                    onSuccess = { "Загружено предметов: $it" },
                    onFailure = {
                        (it.message ?: "Файл не подошёл") +
                            ". Нужен файл библиотеки — тот, что даёт кнопка «Сохранить файл»"
                    }
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
private fun ArchiveMenu(
    onOpen: () -> Unit,
    onEditConditions: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    conducted: Boolean,
    onToggleConducted: () -> Unit,
    hasSource: Boolean,
    onSource: () -> Unit,
    onDelete: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, "Действия", modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Открыть") }, onClick = { open = false; onOpen() })
            DropdownMenuItem(
                text = { Text("Изменить условия") },
                onClick = { open = false; onEditConditions() }
            )
            DropdownMenuItem(
                text = { Text(if (conducted) "Снять отметку" else "Отметить проведённым") },
                onClick = { open = false; onToggleConducted() }
            )
            DropdownMenuItem(
                text = { Text("Отправить товарищу") },
                onClick = { open = false; onShare() }
            )
            DropdownMenuItem(
                text = { Text("Сделать копию") },
                onClick = { open = false; onDuplicate() }
            )
            if (hasSource) {
                DropdownMenuItem(
                    text = { Text("Открыть оригинал") },
                    onClick = { open = false; onSource() }
                )
            }
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

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }

    Section("О приложении") {
        KeyValue("Название", "План-конспект")
        KeyValue("Версия", "1.0")
        KeyValue("Разработчик", "В. Д. Кривицкий")
        KeyValue("Лицензия", "PolyForm Noncommercial 1.0.0")
        Spacer(Modifier.height(8.dp))
        Text(
            "Свободная программа: её можно использовать, изучать, изменять и передавать " +
                "другим. Производные работы распространяются на тех же условиях. " +
                "Программа поставляется без каких-либо гарантий.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { showLicense = true }) { Text("Текст лицензии") }
    }

    if (showLicense) {
        val text = remember {
            runCatching {
                context.assets.open("LICENSE").bufferedReader().use { it.readText() }
            }.getOrElse { "Текст лицензии доступен в файле LICENSE в составе проекта." }
        }
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("GNU General Public License v3") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicense = false }) { Text("Закрыть") }
            }
        )
    }
}

/**
 * Программа боевой подготовки. Заводится один раз файлом, дальше приложение
 * само знает, какое занятие следующее и сколько часов осталось.
 */
@Composable
private fun ProgramSection() {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    val loader = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            status = runCatching {
                val text = context.contentResolver.openInputStream(uri)?.use {
                    TextExtract.readAsText(it.readBytes())
                } ?: throw Exception("Файл не читается")
                val list = Program.decode(text)
                Program.replaceAll(context, list)
                "Загружено занятий: ${list.size}"
            }.getOrElse { it.message ?: "Файл не подошёл" }
        }
    }

    val saver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            status = runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(Program.encode(Program.items.toList()).toByteArray())
                }
                "Программа сохранена в файл"
            }.getOrElse { "Не удалось сохранить" }
        }
    }

    val total = Program.total()
    val left = Program.remaining()

    Section(
        "Программа подготовки",
        if (total == 0) "Не заведена"
        else "Занятий: $total · осталось $left · ${Program.remainingMinutes() / 45} ч"
    ) {
        Text(
            "Перечень занятий на период обучения. Когда он заведён, конспект " +
                "собирается одной кнопкой: тема, номер занятия и часы берутся отсюда, " +
                "а отметка «проведено» в архиве закрывает пункт.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Row {
            TextButton(onClick = {
                loader.launch(arrayOf("application/json", "text/plain", "*/*"))
            }) { Text("Загрузить файл") }
            if (total > 0) {
                TextButton(onClick = { saver.launch("programma.json") }) { Text("Сохранить") }
            }
        }
        if (total > 0) {
            TextButton(onClick = {
                Program.clear(context)
                status = "Программа очищена"
            }) { Text("Очистить программу") }
        }
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

/** Что приложение переняло из ваших конспектов. */
@Composable
private fun LearnedSection() {
    val context = LocalContext.current
    val total = Learned.total()
    Section(
        "Выученные формулировки",
        if (total == 0) "Пока ничего — приложение пишет общевойсковым языком"
        else "Запомнено строк: $total"
    ) {
        Text(
            "Приложение перенимает то, как пишете вы: цели, действия во вводной " +
                "и заключительной частях, действия обучаемых, требования безопасности. " +
                "Берутся из загруженных конспектов и из ваших правок, и подставляются " +
                "вместо шаблонных — по каждому предмету отдельно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (total > 0) {
            Spacer(Modifier.height(6.dp))
            Library.all.forEach { d ->
                val n = Learned.count(d.id)
                if (n > 0) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${d.short}: $n",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { Learned.forget(context, d.id) }) {
                            Text("Забыть")
                        }
                    }
                }
            }
        }
    }
}
