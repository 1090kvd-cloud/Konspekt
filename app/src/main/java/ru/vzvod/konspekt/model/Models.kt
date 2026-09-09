package ru.vzvod.konspekt.model

/** Блок раздаточного материала: заголовок + строки. */
data class HandoutBlock(val title: String, val items: List<String>)

/** Учебный предмет со всеми шаблонами. */
data class Discipline(
    val id: String,
    val name: String,
    val short: String,
    val places: List<String>,
    val methods: List<String>,
    val eduGoals: List<String>,
    val upGoals: List<String>,
    val metGoals: List<String>,
    val questionTemplates: List<String>,
    val materials: List<String>,
    val references: List<String>,
    val safety: List<String>,
    val practiceHints: List<String>,
    val handout: List<HandoutBlock>,
    val control: List<String>
)

/** Всё, что вводит руководитель занятия. Именно это сохраняется в архив. */
data class LessonInput(
    val id: String,
    val createdAt: Long,
    val disciplineId: String,
    val topic: String,
    val lessonNo: String = "1",
    val minutes: Int = 90,
    val place: String = "",
    val method: String = "",
    val unitName: String = "",
    val date: String = "",
    val leader: String = "",
    val questionCount: Int = 3,
    val customQuestions: List<String> = emptyList(),
    val includeHandout: Boolean = true,
    val includeControl: Boolean = true,
    val includeSafety: Boolean = true,
    val note: String = ""
)

/** Этап хода занятия. */
data class Stage(
    val title: String,
    val minutes: Int,
    val leader: List<String>,
    val trainee: List<String>
)

/** Учебный вопрос основной части. */
data class QuestionBlock(
    val index: Int,
    val title: String,
    val minutes: Int,
    val leader: List<String>,
    val trainee: List<String>
)

/** Готовый документ. Строится из LessonInput детерминированно. */
data class LessonPlan(
    val input: LessonInput,
    val disciplineName: String,
    val place: String,
    val method: String,
    val eduGoals: List<String>,
    val upGoals: List<String>,
    val metGoals: List<String>,
    val materials: List<String>,
    val references: List<String>,
    val safety: List<String>,
    val intro: Stage,
    val questions: List<QuestionBlock>,
    val outro: Stage,
    val handout: List<HandoutBlock>,
    val control: List<String>
) {
    val mainMinutes: Int get() = questions.sumOf { it.minutes }
}

/** Настройки: подставляются в шапку каждого документа. */
data class Settings(
    val leader: String = "",
    val unitName: String = "",
    val approver: String = "Командир роты",
    val defaultMinutes: Int = 90,
    val darkTheme: Boolean = false
)
