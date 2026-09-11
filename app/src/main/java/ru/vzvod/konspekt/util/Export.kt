package ru.vzvod.konspekt.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

object Export {

    /** WebView нужно удерживать до начала печати, иначе задание отменяется. */
    private var printView: WebView? = null

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        // На Android 13+ система сама показывает уведомление о копировании.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, subject: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Отправить конспект"))
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
