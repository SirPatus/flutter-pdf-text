package dev.aluc.pdf_text

import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import kotlin.concurrent.thread
import java.io.File

class PdfTextPlugin: FlutterPlugin, MethodChannel.MethodCallHandler {

    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        // inizializza PDFBox con il context dell'app
        PDFBoxResourceLoader.init(binding.applicationContext)

        // crea il channel per comunicare con Dart
        channel = MethodChannel(binding.binaryMessenger, "pdf_text")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        thread(start = true) {
            when (call.method) {
                "initDoc" -> {
                    val args = call.arguments as Map<*, *>
                    val path = args["path"] as String
                    val password = args["password"] as String
                    initDoc(result, path, password)
                }
                "getDocPageText" -> {
                    val args = call.arguments as Map<*, *>
                    val path = args["path"] as String
                    val pageNumber = (args["number"] as Number).toInt()
                    val password = args["password"] as String
                    getDocPageText(result, path, pageNumber, password)
                }
                "getDocText" -> {
                    val args = call.arguments as Map<*, *>
                    val path = args["path"] as String
                    @Suppress("UNCHECKED_CAST")
                    val missingPagesNumbers = args["missingPagesNumbers"] as List<Int>
                    val password = args["password"] as String
                    getDocText(result, path, missingPagesNumbers, password)
                }
                else -> {
                    Handler(Looper.getMainLooper()).post {
                        result.notImplemented()
                    }
                }
            }
        }
    }

    // ========== helper methods (riportano il comportamento che avevi prima) ==========

    private fun initDoc(result: MethodChannel.Result, path: String, password: String) {
        getDoc(result, path, password)?.use { doc ->
            val info = doc.documentInformation
            val data = hashMapOf<String, Any?>(
                "length" to doc.numberOfPages,
                "info" to hashMapOf(
                    "author" to info.author,
                    "creationDate" to info.creationDate?.time?.toString(),
                    "modificationDate" to info.modificationDate?.time?.toString(),
                    "creator" to info.creator,
                    "producer" to info.producer,
                    "keywords" to splitKeywords(info.keywords),
                    "title" to info.title,
                    "subject" to info.subject
                )
            )
            doc.close()
            Handler(Looper.getMainLooper()).post {
                result.success(data)
            }
        }
    }

    private fun splitKeywords(keywordsString: String?): List<String>? {
        return keywordsString?.split(",")?.map { it.trim() }
    }

    private fun getDocPageText(result: MethodChannel.Result, path: String, pageNumber: Int, password: String) {
        getDoc(result, path, password)?.use { doc ->
            val stripper = PDFTextStripper()
            stripper.startPage = pageNumber
            stripper.endPage = pageNumber
            val text = stripper.getText(doc)
            doc.close()
            Handler(Looper.getMainLooper()).post {
                result.success(text)
            }
        }
    }

    private fun getDocText(result: MethodChannel.Result, path: String, missingPagesNumbers: List<Int>, password: String) {
        getDoc(result, path, password)?.use { doc ->
            val missingPagesTexts = missingPagesNumbers.map { pageNum ->
                val stripper = PDFTextStripper()
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                stripper.getText(doc)
            }
            doc.close()
            Handler(Looper.getMainLooper()).post {
                result.success(missingPagesTexts)
            }
        }
    }

    private fun getDoc(result: MethodChannel.Result, path: String, password: String = ""): PDDocument? {
        return try {
            PDDocument.load(File(path), password)
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                result.error(
                    "INVALID_PATH",
                    "File path or password (in case of encrypted document) is invalid: ${e.message}",
                    null
                )
            }
            null
        }
    }
}
