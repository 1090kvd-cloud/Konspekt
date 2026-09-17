package ru.vzvod.konspekt.model

/** Блок раздаточного материала: заголовок + строки. */
data class HandoutBlock(val title: String, val items: List<String>)

/** Учебный предмет со всеми шаблонами. */
data class Discipline(
    val id: String,
    val name: String,
    val short: String,
    /** Типовые темы занятий по предмету — подсказки при заполнении формы. */
    val topics: List<String> = emptyList(),
    val places: List<String>,
    val methods: List<String>,
    val eduGoals: List<String>,
    val upGoals: List<String>,
    val questionTemplates: List<String>,
    val materials: List<String>,
    val references: List<String>,
    val safety: List<String>,
    val practiceHints: List<String>,
    val control: List<String>,
    /** Название в дательном падеже для шапки: «занятия по огневой подготовке».
     *  Пусто — приложение образует само. */
    val dative: String = ""
)

/** Всё, что вводит руководитель занятия. Именно это сохраняется в архив. */
data class LessonInput(
    val id: String,
    val createdAt: Long,
    val disciplineId: String,
    val topic: String,
    val themeNo: String = "1",
    val lessonNo: String = "1",
    /** Полное наименование занятия — идёт строкой «ЗАНЯТИЕ N:». */
    val lessonTitle: String = "",
    /** Как записать время в шапке: «2 часа», «90 мин.». Пусто — считается само. */
    val timeLabel: String = "",
    val minutes: Int = 90,
    val place: String = "",
    val method: String = "",
    val unitName: String = "",
    val date: String = "",
    val leader: String = "",
    val questionCount: Int = 3,
    val customQuestions: List<String> = emptyList(),
    val includeHandout: Boolean = false,
    val includeControl: Boolean = true,
    val includeSafety: Boolean = true,
    val note: String = "",
    /** Имя исходного файла, если конспект загружен готовым. Печатается без изменений. */
    val sourceName: String = "",
    /** Когда занятие проведено. 0 — ещё предстоит. */
    val conductedAt: Long = 0L,
    /**
     * Ручные правки готового документа: ключ раздела -> текст, строка на пункт.
     * Пустая карта — документ целиком собран из шаблонов.
     */
    val edits: Map<String, String> = emptyMap()
)

/** Строка таблицы хода занятия. */
data class Stage(
    val title: String,
    val minutes: Int,
    /** Графа «Содержание учебных вопросов» — от первого лица. */
    val content: List<String>,
    /** Графа «Действия обучаемых». */
    val trainee: List<String>
)

/** Учебный вопрос основной части. */
data class QuestionBlock(
    val index: Int,
    val title: String,
    val minutes: Int,
    val content: List<String>,
    val trainee: List<String>
)

/** Готовый документ. Строится из LessonInput детерминированно. */
data class LessonPlan(
    val input: LessonInput,
    val disciplineName: String,
    /** Название предмета для шапки: «занятия по огневой подготовке». */
    val dative: String,
    val place: String,
    val method: String,
    /** Цели сплошным нумерованным списком, как в образце. */
    val goals: List<String>,
    /** Материальное обеспечение вместе с руководствами — одной строкой. */
    val provision: List<String>,
    val safety: List<String>,
    val intro: Stage,
    val questions: List<QuestionBlock>,
    val outro: Stage,
    /** Раздатка: собирается из вписанного руководителем содержания, может быть пустой. */
    val handout: List<HandoutBlock>,
    val control: List<String>
) {
    val mainMinutes: Int get() = questions.sumOf { it.minutes }
}

/**
 * Справочный материал: файл любого формата, приложенный к предмету обучения.
 * disciplineId = null — материал общий, показывается на любом занятии.
 */
data class Material(
    val id: String,
    val title: String,
    val storedName: String,
    val mime: String,
    val size: Long,
    val disciplineId: String?,
    val addedAt: Long
)

/**
 * Пункт программы боевой подготовки: одно занятие, которое положено провести.
 * Из него собирается конспект без единого ввода — всё уже известно заранее.
 */
data class ProgramItem(
    val id: String,
    val disciplineId: String,
    val themeNo: String = "1",
    val topic: String,
    val lessonNo: String = "1",
    val lessonTitle: String = "",
    val minutes: Int = 90,
    /** id проведённого занятия из архива. Пусто — ещё предстоит. */
    val doneBy: String = ""
)

/** Настройки: подставляются в шапку каждого документа. */
data class Settings(
    val leader: String = "",
    val unitName: String = "",
    val approver: String = "Командир роты",
    val defaultMinutes: Int = 90,
    val darkTheme: Boolean = false,
    /** Альбомная ориентация листа при печати. По образцу — книжная. */
    val landscape: Boolean = false
)
