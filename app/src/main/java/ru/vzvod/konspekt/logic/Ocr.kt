package ru.vzvod.konspekt.logic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * Распознавание русского текста на изображениях и в PDF-сканах.
 *
 * Работает офлайн: модель языка лежит внутри приложения. Страницы PDF переводятся
 * в картинку системным средством Android, дальше идёт обычное распознавание.
 *
 * Качество зависит от снимка. Ровный скан читается хорошо, фотография страницы
 * под углом и с тенями — заметно хуже. Результат всегда нужно проверять глазами.
 */
object Ocr {

    private const val LANG = "rus"
    private const val MAX_PAGES = 40

    /** Разрешение рендера страницы: 2× от размера в точках — примерно 144 dpi. */
    private const val SCALE = 2f

    /** Tesseract читает модель с диска, поэтому один раз копируем её из приложения. */
    private fun dataPath(context: Context): String? {
        val dir = File(context.filesDir, "tess")
        val tessdata = File(dir, "tessdata").apply { mkdirs() }
        val model = File(tessdata, "$LANG.traineddata")
        if (!model.exists() || model.length() == 0L) {
            val ok = runCatching {
                context.assets.open("tessdata/$LANG.traineddata").use { input ->
                    model.outputStream().use { out -> input.copyTo(out) }
                }
            }.isSuccess
            if (!ok) return null
        }
        return dir.absolutePath
    }

    private fun <T> withApi(context: Context, block: (TessBaseAPI) -> T): T? {
        val path = dataPath(context) ?: return null
        val api = TessBaseAPI()
        return try {
            if (!api.init(path, LANG)) null else block(api)
        } catch (e: Throwable) {
            null
        } finally {
            runCatching { api.recycle() }
        }
    }

    /** Распознаёт одно изображение. */
    fun fromImage(context: Context, file: File): String? = withApi(context) { api ->
        val bmp = decode(file) ?: return@withApi null
        api.setImage(bmp)
        val text = api.getUTF8Text()
        bmp.recycle()
        text
    }

    /** Постранично переводит PDF в изображения и распознаёт каждое. */
    fun fromPdf(context: Context, file: File): String? = withApi(context) { api ->
        val out = StringBuilder()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            val pages = minOf(renderer.pageCount, MAX_PAGES)
            for (n in 0 until pages) {
                val bmp = renderPage(renderer, n) ?: continue
                api.setImage(bmp)
                val text = api.getUTF8Text()
                bmp.recycle()
                if (!text.isNullOrBlank()) {
                    if (out.isNotEmpty()) out.append("\n\n")
                    out.append(text.trim())
                }
            }
        } finally {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
        out.toString().ifBlank { null }
    }

    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap? = runCatching {
        val page = renderer.openPage(index)
        try {
            val w = (page.width * SCALE).toInt().coerceIn(100, 4000)
            val h = (page.height * SCALE).toInt().coerceIn(100, 4000)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            // Прозрачный фон распознаётся плохо, поэтому заливаем белым.
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } finally {
            page.close()
        }
    }.getOrNull()

    private fun decode(file: File): Bitmap? = runCatching {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        // Огромные снимки уменьшаем: на распознавание это почти не влияет, а память бережёт.
        var sample = 1
        while (opts.outWidth / sample > 2600) sample *= 2
        android.graphics.BitmapFactory.decodeFile(
            file.absolutePath,
            android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        )
    }.getOrNull()
}
