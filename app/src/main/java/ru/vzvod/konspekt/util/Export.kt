package ru.vzvod.konspekt.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

object Export {

    /**
     * Ссылка на страницу, пока система готовит печать.
     * Без неё сборщик мусора уничтожит WebView до того, как документ уйдёт
     * в принтер, и печать оборвётся на середине.
     */
    private var printView: WebView? = null

    /** Кладёт текст в буфер обмена — чтобы вставить в сообщение или заметку. */
    fun copy(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        // На Android 13 и новее система показывает своё уведомление о копировании.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
        }
    }

    /** Открывает системный диалог печати. Оттуда доступно «Сохранить в PDF».
     *  Ориентацию можно поменять и в самом диалоге. */
    fun printPdf(context: Context, jobName: String, html: String, landscape: Boolean = true) {
        val web = WebView(context)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val adapter = view.createPrintDocumentAdapter(jobName)
                manager.print(
                    jobName,
                    adapter,
                    // Поля не задаём: система подставит поля принтера.
                    PrintAttributes.Builder()
                        .setMediaSize(
                            if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape()
                            else PrintAttributes.MediaSize.ISO_A4
                        )
                        .build()
                )
                printView = null
            }
        }
        printView = web
        web.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}
