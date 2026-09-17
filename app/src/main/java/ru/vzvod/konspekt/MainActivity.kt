/*
 * План-конспект — конструктор занятий для командира взвода.
 * Copyright (C) 2026 В. Д. Кривицкий
 *
 * Это свободная программа: вы можете распространять и/или изменять её
 * на условиях GNU General Public License версии 3, опубликованной
 * Free Software Foundation.
 *
 * Программа распространяется в надежде, что она будет полезной, но БЕЗ
 * КАКИХ-ЛИБО ГАРАНТИЙ. Подробности в файле LICENSE.
 */
package ru.vzvod.konspekt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.vzvod.konspekt.data.Learned
import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.data.Materials
import ru.vzvod.konspekt.data.Program
import ru.vzvod.konspekt.data.Store
import ru.vzvod.konspekt.logic.Generator
import ru.vzvod.konspekt.model.LessonInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vzvod.konspekt.logic.AutoFill
import ru.vzvod.konspekt.ui.AppHeader
import ru.vzvod.konspekt.ui.ArchiveScreen
import ru.vzvod.konspekt.ui.CreateScreen
import ru.vzvod.konspekt.ui.EditScreen
import ru.vzvod.konspekt.ui.FormState
import ru.vzvod.konspekt.ui.MaterialsScreen
import ru.vzvod.konspekt.ui.ResultScreen
import ru.vzvod.konspekt.ui.SeriesScreen
import ru.vzvod.konspekt.ui.SettingsScreen
import ru.vzvod.konspekt.ui.theme.KonspektTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

private enum class Tab(val title: String, val label: String) {
    Create("Конструктор занятий", "Занятие"),
    Archive("Архив", "Архив"),
    Materials("Справочные материалы", "Материалы"),
    Settings("Настройки", "Настройки")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val store = remember { Store(context) }
    // Библиотека предметов: сначала файл на телефоне, иначе встроенный набор.
    remember {
        Library.load(context); Materials.load(context)
        Program.load(context); Learned.load(context)
        true
    }
    val scope = rememberCoroutineScope()
    val form = remember {
        FormState(store.settings).also { f -> store.draft?.let { f.loadFrom(it) } }
    }

    var tab by remember { mutableStateOf(Tab.Create) }
    var current by remember { mutableStateOf<LessonInput?>(null) }
    var editing by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf(false) }
    // Что приложение подставило само — показывается на экране конспекта.
    var autoNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    var building by remember { mutableStateOf(false) }

    // Черновик пишется при каждом переходе между вкладками: набранное не пропадёт.
    LaunchedEffect(tab) {
        if (form.topic.isNotBlank()) store.saveDraft(form.toInput(store.settings))
    }

    KonspektTheme(dark = store.settings.darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {

            if (series) {
                BackHandler { series = false }
                SeriesScreen(
                    form = form,
                    settings = store.settings,
                    onBack = { series = false },
                    onCreate = { list ->
                        // Сохраняем с конца, чтобы первое занятие темы оказалось сверху.
                        list.reversed().forEach { store.save(it) }
                        series = false
                        tab = Tab.Archive
                    }
                )
                return@Surface
            }

            val openPlan = current
            if (openPlan != null && editing) {
                BackHandler { editing = false }
                EditScreen(
                    input = openPlan,
                    onCancel = { editing = false },
                    onSave = { updated ->
                        current = updated
                        store.saveDraft(updated)
                        // Если занятие уже в архиве, правки сохраняются вместе с ним.
                        if (store.archive.any { it.id == updated.id }) store.save(updated)
                        Learned.learn(context, updated)
                        editing = false
                    }
                )
                return@Surface
            }

            if (openPlan != null) {
                BackHandler { current = null }
                ResultScreen(
                    plan = Generator.build(openPlan),
                    settings = store.settings,
                    savedAlready = store.archive.any { it.id == openPlan.id },
                    onSave = {
                        // Учимся только на правках и загруженных конспектах:
                        // иначе приложение запомнит то, что подставило само,
                        // и своя же ошибка начнёт усиливаться.
                        store.save(openPlan)
                        tab = Tab.Archive
                    },
                    onEdit = { editing = true },
                    autoNotes = autoNotes,
                    onConditions = {
                        // Занятие возвращается в форму: можно поменять время, вопросы, предмет.
                        form.loadFrom(openPlan)
                        current = null
                        tab = Tab.Create
                    },
                    onBack = { current = null }
                )
                return@Surface
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    AppHeader(
                        title = tab.label,
                        subtitle = when (tab) {
                            // Полезнее счётчика: что за занятие сейчас собирается.
                            Tab.Create -> Library.byId(form.disciplineId).name.lowercase() +
                                ", ${form.minutes} мин"
                            Tab.Archive -> "конспектов: ${store.archive.size}"
                            Tab.Materials -> "материалов: ${Materials.all.size}"
                            Tab.Settings -> "версия 1.0"
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = {
                                    Icon(
                                        when (t) {
                                            Tab.Create -> Icons.Filled.EditNote
                                            Tab.Archive -> Icons.Filled.Inventory2
                                            Tab.Materials -> Icons.Filled.FolderOpen
                                            Tab.Settings -> Icons.Filled.Tune
                                        },
                                        t.label
                                    )
                                },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            ) { inner ->
                val m = Modifier.padding(inner).fillMaxSize()
                when (tab) {
                    Tab.Create -> CreateScreen(
                        form = form,
                        settings = store.settings,
                        archive = store.archive,
                        onBuild = {
                            val base = form.toInput(store.settings)
                            building = true
                            scope.launch {
                                // Поиск по методичкам идёт с диска, поэтому в фоне.
                                val report = withContext(Dispatchers.IO) {
                                    AutoFill.enrich(context, base, store.archive.toList())
                                }
                                autoNotes = report.notes
                                store.saveDraft(report.input)
                                current = report.input
                                building = false
                            }
                        },
                        busy = building,
                        onSeries = { series = true },
                        onNext = { item ->
                            // Занятие из программы собирается целиком: тема, время,
                            // предмет — оттуда, содержание — из методичек и архива.
                            form.reset(store.settings)
                            form.disciplineId = item.disciplineId
                            form.themeNo = item.themeNo
                            form.topic = item.topic
                            form.lessonNo = item.lessonNo
                            form.lessonTitle = item.lessonTitle
                            form.minutes = item.minutes
                            if (form.date.isBlank()) form.date = today()
                            val base = form.toInput(store.settings)
                            building = true
                            scope.launch {
                                val report = withContext(Dispatchers.IO) {
                                    AutoFill.enrich(context, base, store.archive.toList())
                                }
                                autoNotes = report.notes
                                // Конспект сразу в архив, но пункт программы
                                // закрывается позже — когда занятие проведено.
                                store.save(report.input)
                                store.saveDraft(null)
                                current = report.input
                                building = false
                            }
                        },
                        onNew = {
                            // Сбрасываем и форму, и сохранённый черновик, иначе
                            // старое занятие вернётся при следующем запуске.
                            form.reset(store.settings)
                            store.saveDraft(null)
                        },
                        modifier = m
                    )

                    Tab.Archive -> ArchiveScreen(
                        items = store.archive,
                        onOpen = { current = it },
                        onEditConditions = {
                            form.loadFrom(it)
                            tab = Tab.Create
                        },
                        onDuplicate = { current = store.duplicate(it) },
                        onToggleConducted = {
                            store.toggleConducted(it.id)
                            // Отметка в архиве закрывает пункт программы сама.
                            Program.syncWithArchive(context, store.archive.toList())
                        },
                        onDelete = {
                            store.delete(it.id)
                            Program.syncWithArchive(context, store.archive.toList())
                        },
                        onImported = { list ->
                            list.reversed().forEach { store.save(it) }
                            // Загруженные конспекты — лучший источник ваших формулировок.
                            Learned.learnAll(context, list)
                        },
                        modifier = m
                    )

                    Tab.Materials -> MaterialsScreen(modifier = m)

                    Tab.Settings -> SettingsScreen(
                        settings = store.settings,
                        onChange = { store.updateSettings(it) },
                        onClearArchive = {
                            store.clearArchive()
                            Program.syncWithArchive(context, store.archive.toList())
                        },
                        onReload = {
                            store.reload()
                            Library.load(context)
                            Materials.load(context)
                            Program.load(context)
                            Learned.load(context)
                            // Форма должна взять восстановленные данные, иначе она
                            // перезапишет их своим прежним черновиком.
                            val restored = store.draft
                            if (restored != null) form.loadFrom(restored)
                            else form.reset(store.settings)
                        },
                        modifier = m
                    )
                }
            }
        }
    }
}

/** Сегодняшняя дата в том виде, в каком она стоит в документе. */
private fun today(): String = java.text.SimpleDateFormat(
    "d MMMM yyyy 'г.'",
    java.util.Locale("ru")
).format(java.util.Date())
