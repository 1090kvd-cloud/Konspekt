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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ru.vzvod.konspekt.data.Library
import ru.vzvod.konspekt.data.Materials
import ru.vzvod.konspekt.data.Store
import ru.vzvod.konspekt.logic.Generator
import ru.vzvod.konspekt.model.LessonInput
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
    remember { Library.load(context); Materials.load(context); true }
    val form = remember {
        FormState(store.settings).also { f -> store.draft?.let { f.loadFrom(it) } }
    }

    var tab by remember { mutableStateOf(Tab.Create) }
    var current by remember { mutableStateOf<LessonInput?>(null) }
    var editing by remember { mutableStateOf(false) }
    var series by remember { mutableStateOf(false) }

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
                    onSave = { edits ->
                        val updated = openPlan.copy(edits = edits)
                        current = updated
                        store.saveDraft(updated)
                        // Если занятие уже в архиве, правки сохраняются вместе с ним.
                        if (store.archive.any { it.id == updated.id }) store.save(updated)
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
                        store.save(openPlan)
                        tab = Tab.Archive
                    },
                    onEdit = { editing = true },
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
                    TopAppBar(
                        title = {
                            Column {
                                Text(tab.title, style = MaterialTheme.typography.headlineSmall)
                                if (tab == Tab.Create) {
                                    Text(
                                        "Тема и время — остальное подставится",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
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
                        onBuild = {
                            val input = form.toInput(store.settings)
                            store.saveDraft(input)
                            current = input
                        },
                        onSeries = { series = true },
                        onNew = { form.reset(store.settings) },
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
                        onDelete = { store.delete(it.id) },
                        onImported = { list -> list.reversed().forEach { store.save(it) } },
                        modifier = m
                    )

                    Tab.Materials -> MaterialsScreen(modifier = m)

                    Tab.Settings -> SettingsScreen(
                        settings = store.settings,
                        onChange = { store.updateSettings(it) },
                        onClearArchive = { store.clearArchive() },
                        onReload = {
                            store.reload()
                            Library.load(context)
                            Materials.load(context)
                        },
                        modifier = m
                    )
                }
            }
        }
    }
}
