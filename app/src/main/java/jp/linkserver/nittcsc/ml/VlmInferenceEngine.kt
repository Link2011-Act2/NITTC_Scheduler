package jp.linkserver.nittcsc.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File

data class VlmFullResult(
    val jsonOutput: String,
    val debugText: String,
    val usedDirectVision: Boolean
)

class VlmInferenceEngine(private val context: Context) {

    private var inferenceScope: CoroutineScope? = null
    private var isCancelRequested = false
    private var currentHelper: LlamaHelper? = null

    fun cancelInference() {
        isCancelRequested = true
        android.util.Log.d("VlmInference", "Cancel requested - releasing helper and cancelling scope")
        // LlamaHelper を直接 release して推論プロセスを強制終了
        currentHelper?.release()
        currentHelper = null
        // Coroutine Scope もキャンセル
        inferenceScope?.cancel()
    }

    private fun resetCancelFlag() {
        isCancelRequested = false
    }

    suspend fun extractDataFromJsonVlm(
        modelFile: File,
        mmprojFile: File?,
        imageUri: Uri,
        isAbTable: Boolean = false,
        disableJsonCorrection: Boolean = false,
        jsonCorrectionModelFile: File? = null,
        jsonCorrectionMmprojFile: File? = null,
        onStatusUpdate: (String) -> Unit = {}
    ): VlmFullResult = withContext(Dispatchers.IO) {
        resetCancelFlag()
        val directPrompt = buildDirectVisionPrompt(isAbTable)
        
        // JSON校正用モデルが指定されていない場合は、メインモデルを使用
        val correctionModelFile = jsonCorrectionModelFile ?: modelFile
        val correctionMmprojFile = jsonCorrectionMmprojFile ?: mmprojFile

        try {
            val phasePrefix = context.getString(jp.linkserver.nittcsc.R.string.phase_image_processing)
            onStatusUpdate("$phasePrefix${context.getString(jp.linkserver.nittcsc.R.string.msg_model_initializing)}")
            val directJson = runLocalLlamaPrompt(
                modelFile = modelFile,
                mmprojFile = mmprojFile,
                prompt = directPrompt,
                imageUri = imageUri,
                onStatusUpdate = { status -> onStatusUpdate("$phasePrefix$status") }
            )

            // JSON形式を校正
            val correctedJson = if (disableJsonCorrection) {
                directJson
            } else {
                val jsonCorrectionPhase = context.getString(jp.linkserver.nittcsc.R.string.phase_json_correction)
                val formatValidatingMsg = context.getString(jp.linkserver.nittcsc.R.string.msg_format_validating)
                onStatusUpdate("$jsonCorrectionPhase$formatValidatingMsg")
                correctJsonFormat(
                    modelFile = correctionModelFile,
                    mmprojFile = correctionMmprojFile,
                    jsonString = directJson,
                    isAbTable = isAbTable,
                    onStatusUpdate = { status -> onStatusUpdate("$jsonCorrectionPhase$status") }
                )
            }

            return@withContext VlmFullResult(
                jsonOutput = correctedJson,
                debugText = "offline vision: model=${modelFile.name}, mmproj=${mmprojFile?.name ?: "none"}",
                usedDirectVision = true
            )
        } catch (_: Exception) {
            val failedMsg = context.getString(jp.linkserver.nittcsc.R.string.msg_direct_vision_failed)
            onStatusUpdate(failedMsg)
        }

        val scannedText = runLocalOcr(imageUri)
        val fallbackPrompt = buildOcrPrompt(scannedText, isAbTable)

        val ocrFallbackPhase = context.getString(jp.linkserver.nittcsc.R.string.phase_ocr_fallback)
        onStatusUpdate("${ocrFallbackPhase}${context.getString(jp.linkserver.nittcsc.R.string.msg_text_analyzing)}")
        val fallbackJson = runLocalLlamaPrompt(
            modelFile = modelFile,
            mmprojFile = mmprojFile,
            prompt = fallbackPrompt,
            imageUri = null,
            onStatusUpdate = { status -> onStatusUpdate("$ocrFallbackPhase$status") }
        )

        // JSON形式を校正
        val correctedJson = if (disableJsonCorrection) {
            fallbackJson
        } else {
            val jsonCorrectionPhase = context.getString(jp.linkserver.nittcsc.R.string.phase_json_correction)
            val formatValidatingMsg = context.getString(jp.linkserver.nittcsc.R.string.msg_format_validating)
            onStatusUpdate("$jsonCorrectionPhase$formatValidatingMsg")
            correctJsonFormat(
                modelFile = correctionModelFile,
                mmprojFile = correctionMmprojFile,
                jsonString = fallbackJson,
                isAbTable = isAbTable,
                onStatusUpdate = { status -> onStatusUpdate("$jsonCorrectionPhase$status") }
            )
        }

        return@withContext VlmFullResult(
            jsonOutput = correctedJson,
            debugText = scannedText,
            usedDirectVision = false
        )
    }

    private suspend fun runLocalLlamaPrompt(
        modelFile: File,
        mmprojFile: File?,
        prompt: String,
        imageUri: Uri?,
        onStatusUpdate: (String) -> Unit
    ): String {
        val events = MutableSharedFlow<LlamaHelper.LLMEvent>(extraBufferCapacity = 256)
        val scope = CoroutineScope(Dispatchers.IO)
        val helper = LlamaHelper(
            contentResolver = context.contentResolver,
            scope = scope,
            sharedFlow = events
        )
        
        // 現在実行中の helper を記録（キャンセル時に使用）
        currentHelper = helper

        val modelUri = fileToContentUri(modelFile).toString()
        val mmprojUri = mmprojFile?.takeIf { it.exists() }?.let { fileToContentUri(it).toString() }

        val loadDone = CompletableDeferred<Unit>()
        val predictDone = CompletableDeferred<Unit>()
        var eventError: String? = null
        var fullText = ""
        var receivedDoneEvent = false
        val predictStartTime = System.currentTimeMillis()
        
        android.util.Log.d("VlmInference", "Model: ${modelFile.name}")

        val collector = scope.launch {
            events.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Loaded -> {
                        android.util.Log.d("VlmInference", "Model loaded successfully")
                        if (!loadDone.isCompleted) loadDone.complete(Unit)
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        eventError = event.message
                        android.util.Log.e("VlmInference", "Error event: ${event.message}")
                        if (!loadDone.isCompleted) loadDone.complete(Unit)
                        if (!predictDone.isCompleted) predictDone.complete(Unit)
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        fullText = event.fullText
                        receivedDoneEvent = true
                        val elapsedMs = System.currentTimeMillis() - predictStartTime
                        android.util.Log.d("VlmInference", "Done event received after ${elapsedMs}ms, output length: ${fullText.length}")
                        if (!predictDone.isCompleted) predictDone.complete(Unit)
                    }
                    else -> {
                        android.util.Log.d("VlmInference", "Event: ${event::class.simpleName}")
                    }
                }
            }
        }

        try {
            helper.load(
                path = modelUri,
                contextLength = 4096,
                mmprojPath = mmprojUri
            ) { }

            onStatusUpdate(context.getString(jp.linkserver.nittcsc.R.string.msg_model_loading))
            withTimeout(120_000) { loadDone.await() }
            eventError?.let { throw Exception(it) }
            onStatusUpdate(context.getString(jp.linkserver.nittcsc.R.string.msg_model_load_complete))

            if (isCancelRequested) throw Exception(context.getString(jp.linkserver.nittcsc.R.string.msg_inference_cancelled))

            onStatusUpdate(context.getString(jp.linkserver.nittcsc.R.string.msg_inference_running))
            helper.predict(
                prompt = prompt,
                imagePath = imageUri?.toString()
            )

            try {
                withTimeout(1_200_000) { predictDone.await() }  // 20 分
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.e("VlmInference", "Prediction timeout")
                throw Exception(context.getString(jp.linkserver.nittcsc.R.string.msg_inference_timeout))
            }
            eventError?.let { throw Exception(it) }

            if (isCancelRequested) throw Exception(context.getString(jp.linkserver.nittcsc.R.string.msg_inference_cancelled))

            // 応答がない場合の確認と警告
            if (fullText.isBlank()) {
                android.util.Log.w("VlmInference", "fullText is blank. receivedDoneEvent: $receivedDoneEvent")
                throw Exception(context.getString(jp.linkserver.nittcsc.R.string.msg_no_model_response))
            }

            onStatusUpdate(context.getString(jp.linkserver.nittcsc.R.string.msg_processing_result))
            return cleanJsonResponse(fullText)
        } finally {
            helper.release()
            currentHelper = null  // キャンセル用の参照をクリア
            collector.cancel()
            scope.cancel()
        }
    }

    private suspend fun correctJsonFormat(
        modelFile: File,
        mmprojFile: File?,
        jsonString: String,
        isAbTable: Boolean,
        onStatusUpdate: (String) -> Unit
    ): String {
        val correctionPrompt = buildJsonCorrectionPrompt(jsonString, isAbTable)
        
        return try {
            android.util.Log.d("VlmInference", "JSON correction starting. Input length: ${jsonString.length}")
            onStatusUpdate(context.getString(jp.linkserver.nittcsc.R.string.msg_starting_correction_model))
            
            val correctedJson = runLocalLlamaPrompt(
                modelFile = modelFile,
                mmprojFile = mmprojFile,
                prompt = correctionPrompt,
                imageUri = null,  // テキスト処理なので画像不要
                onStatusUpdate = onStatusUpdate
            )
            
            android.util.Log.d("VlmInference", "JSON correction completed. Output length: ${correctedJson.length}")
            val completeMsg = context.getString(jp.linkserver.nittcsc.R.string.msg_json_correction_complete)
            onStatusUpdate(completeMsg)
            correctedJson
        } catch (e: Exception) {
            android.util.Log.w("VlmInference", "JSON correction failed, returning original JSON: ${e.message}")
            onStatusUpdate(context.getString(jp.linkserver.nittcsc.R.string.msg_correction_failed))
            // 校正に失敗した場合は、元のJSONを返す
            jsonString
        }
    }

    private suspend fun runLocalOcr(imageUri: Uri): String {
        val bitmap = loadBitmapFromUri(imageUri) ?: throw Exception(context.getString(jp.linkserver.nittcsc.R.string.err_image_load_failed))

        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

        val ocrResult = runCatching { recognizer.process(image).await() }
            .getOrElse { throw Exception(context.getString(jp.linkserver.nittcsc.R.string.err_ocr_scan_failed, it.message)) }

        val sortedTextBuilder = StringBuilder()
        val blocks = ocrResult.textBlocks.sortedWith(compareBy({ it.boundingBox?.top ?: 0 }, { it.boundingBox?.left ?: 0 }))

        for (block in blocks) {
            for (line in block.lines) {
                sortedTextBuilder.append(line.text).append(" ")
            }
            sortedTextBuilder.append("\n")
        }

        return sortedTextBuilder.toString().takeIf { it.isNotBlank() }
            ?: throw Exception(context.getString(jp.linkserver.nittcsc.R.string.err_ocr_no_text))
    }

    private fun buildDirectVisionPrompt(isAbTable: Boolean): String {
        return if (isAbTable) {
            """
            この画像は学校のA/B週カレンダーです。画像内の情報を正確に読み取り、構造化してください。
            
            【最重要】
            - 出力はJSONのみ。説明文・Markdown・コメントは一切不要。
            - JSON形式が不正にならないよう厳密に出力すること。
            
            【処理対象】
            - 表に存在する「すべての行」を最後まで処理すること
            - 途中で処理を止めてはいけない
            - 1行だけ処理して終了することは禁止
            
            【最重要ルール（絶対）】
            - 「4月6日〜4月10日」のような期間は必ず1日ずつ分解する
              例:
                4/6〜4/10 → 4/6, 4/7, 4/8, 4/9, 4/10
            
            【全行処理ルール】
            - 1行ずつ順番に処理する
            - 各行ごとに:
              1. 期間を取得
              2. 日付をすべて展開
              3. 月〜金の各列と対応させる
            - これを「表の最終行まで」繰り返すこと
            
            【読み取りルール】
            - 表は「期間（開始日〜終了日）」と「月〜金」の対応表として解釈する
            - A / B / HOLIDAY のいずれかが判定できる日のみ出力する
            - 空欄は出力しない
            - 判読不能はスキップ
            
            【曜日と日付】
            - 月〜金の列は実際の曜日として扱う
            - 日付と曜日の整合性を保つ
            
            【日付処理】
            - YYYY-MM-DD 形式
            - 年がなければ ${java.time.LocalDate.now().year}年
            - 0埋めする
            
            【dayType】
            - "A" / "B" / "HOLIDAY"
            
            【HOLIDAY判定】
            - 祝日 / 振替休日 / 休校
            - テスト（中間・期末・試験）
            - 学校行事
            - → A/Bより優先
            
            【禁止事項】
            - 期間の終了日だけ出力すること
            - 最初の1週間だけ出力すること
            - 行をスキップすること
            
            【出力】
            {
              "dayTypes": [
                { "date": "2026-04-06", "dayType": "A" }
              ]
            }
            
            【追加制約】
            - 全日付を出力すること（途中で止めない）
            - 日付昇順でソート
            - 重複禁止
            - null禁止
            """.trimIndent()
        } else {
            """
            この画像は学校の時間割です。画像内の情報を正確に読み取り、構造化してください。
            
            【最重要】
            - 出力はJSONのみ。説明文・Markdown・コメントは一切不要。
            - JSON形式が不正にならないよう厳密に出力すること。
            
            【読み取りルール】
            - 表形式として解釈し、「曜日 × 時限」のマスごとに1レコード生成する
            - 空欄のマスは出力しない
            - 判読できない文字は空文字 "" にする
            - 科目名と教員名が両方ある場合のみそれぞれに格納する
            - 教員名が括弧書きや改行で書かれている場合も正しく分離する
            - ()などで授業の場所と考えられるテキストが書かれている場合はLocationに格納する
            
            【フィールド定義】
            - dayOfWeek: 曜日（月=1, 火=2, 水=3, 木=4, 金=5）
            - slotIndex: 時限（1限=0, 2限=1, 3限=2, 4限=3）
            
            【モード判定】
            - 通常の単一記載 → mode = "WEEKLY"
            - A/Bや隔週（例: "A/B", "隔週", "前後期で異なる" 等）→ mode = "ALTERNATING"
            
            【modeごとの格納ルール】
            - WEEKLY:
                            - weeklySubject, weeklyTeacher, weeklyLocation のみ使用
                            - aSubject, aTeacher, aLocation, bSubject, bTeacher, bLocation はすべて空文字
            
            - ALTERNATING:
                            - aSubject, aTeacher, aLocation, bSubject, bTeacher, bLocation を使用
                            - weeklySubject, weeklyTeacher, weeklyLocation は空文字
            
            【出力フォーマット】
            {
              "lessons": [
                {
                  "dayOfWeek": 1,
                  "slotIndex": 0,
                  "mode": "WEEKLY",
                  "weeklySubject": "",
                  "weeklyTeacher": "",
                  "weeklyLocation": "",
                  "aSubject": "",
                  "aTeacher": "",
                  "aLocation": "",
                  "bSubject": "",
                  "bTeacher": "",
                  "bLocation": ""
                }
              ]
            }
            
            【追加制約】
            - lessons配列は「dayOfWeek昇順 → slotIndex昇順」でソートする
            - 同一マスから複数レコードを作らない
            - 不要なフィールドは追加しない
            - nullは使わず、空は必ず "" を使用
            """.trimIndent()
        }
    }

    private fun buildOcrPrompt(scannedText: String, isAbTable: Boolean): String {
        return if (isAbTable) {
            """
                あなたはOCR結果からA/B週カレンダーを構造化するAIです。
                出力はJSONのみで返してください。

                【DayType の定義】
                - "A": A週
                - "B": B週
                - "HOLIDAY": 祝日・休日

                【年の扱い】
                - 年が不明な場合は ${java.time.LocalDate.now().year}年 として扱う

                【読み取りテキスト】
                $scannedText

                【出力フォーマット】
                {
                  "dayTypes": [
                    { "date": "2026-04-15", "dayType": "A" },
                    { "date": "2026-04-29", "dayType": "HOLIDAY" }
                  ]
                }
            """.trimIndent()
        } else {
            """
                あなたはOCR結果から時間割を構造化するAIです。
                出力はJSONのみで返してください。

                【ルール】
                - 1=月曜, 2=火曜, 3=水曜, 4=木曜, 5=金曜
                - 0=1限, 1=2限, 2=3限, 3=4限

                【読み取りテキスト】
                $scannedText

                【出力フォーマット】
                {
                  "lessons": [
                    {
                      "dayOfWeek": 1,
                      "slotIndex": 0,
                      "mode": "WEEKLY",
                      "weeklySubject": "",
                      "weeklyTeacher": "",
                      "aSubject": "",
                      "aTeacher": "",
                      "bSubject": "",
                      "bTeacher": ""
                    }
                  ]
                }
            """.trimIndent()
        }
    }

    private fun buildJsonCorrectionPrompt(jsonString: String, isAbTable: Boolean): String {
        return if (isAbTable) {
            """
            以下のJSONはA/B週カレンダーの解析結果です。
            このJSONの形式を確認して、不正な部分があれば修正してください。
            
            【要件】
            - 出力はJSONのみ。説明文は不要。
            - JSON形式が厳密に正しいこと
            - 不正なエスケープなし
            - 全ての文字列が正しくクォートされている
            - 配列と括弧が正しく対応している
            
            【DayType の定義】
            - "A": A週
            - "B": B週
            - "HOLIDAY": 祝日・休日
            
            【修正ルール】
            - dateは YYYY-MM-DD 形式
            - dayType は "A", "B", "HOLIDAY" のいずれか
            - 不正なデータは削除
            - 重複を削除
            - 日付でソート
            
            【入力JSON】
            $jsonString
            
            【出力フォーマット】
            {
              "dayTypes": [
                { "date": "2026-04-06", "dayType": "A" }
              ]
            }
            """.trimIndent()
        } else {
            """
            以下のJSONは学校の時間割の解析結果です。
            このJSONの形式を確認して、不正な部分があれば修正してください。
            
            【要件】
            - 出力はJSONのみ。説明文は不要。
            - JSON形式が厳密に正しいこと
            - 不正なエスケープなし
            - 全ての文字列が正しくクォートされている
            - 配列と括弧が正しく対応している
            
            【フィールド定義】
            - dayOfWeek: 曜日（月=1, 火=2, 水=3, 木=4, 金=5）
            - slotIndex: 時限（1限=0, 2限=1, 3限=2, 4限=3）
            - mode: "WEEKLY" または "ALTERNATING"
            - weeklySubject, weeklyTeacher, weeklyLocation: WEEKLY時に使用
            - aSubject, aTeacher, aLocation, bSubject, bTeacher, bLocation: ALTERNATING時に使用
            
            【修正ルール】
            - dayOfWeek は 1-5の整数
            - slotIndex は 0-3の整数
            - modeは "WEEKLY" または "ALTERNATING"
            - 文字列フィールドは空でも "" を使用（nullなし）
            - 不正なレコードは削除
            - dayOfWeek昇順 → slotIndex昇順でソート
            
            【入力JSON】
            $jsonString
            
            【出力フォーマット】
            {
              "lessons": [
                {
                  "dayOfWeek": 1,
                  "slotIndex": 0,
                  "mode": "WEEKLY",
                  "weeklySubject": "",
                  "weeklyTeacher": "",
                                    "weeklyLocation": "",
                  "aSubject": "",
                  "aTeacher": "",
                                    "aLocation": "",
                  "bSubject": "",
                                    "bTeacher": "",
                                    "bLocation": ""
                }
              ]
            }
            """.trimIndent()
        }
    }

    private fun cleanJsonResponse(rawText: String): String {
        val trimmed = rawText.trim()
        return when {
            trimmed.contains("```json") -> trimmed.substringAfter("```json").substringBeforeLast("```").trim()
            trimmed.startsWith("```") && trimmed.endsWith("```") -> trimmed.removePrefix("```").removeSuffix("```").trim()
            else -> trimmed
        }
    }

    private fun fileToContentUri(file: File): Uri {
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }

    fun parseLessonsFromJson(jsonString: String): List<LessonDraftWithSlot> {
        return ScheduleParser.parseLessonsFromJson(jsonString)
    }
}

object ScheduleParser {
    fun parseLessonsFromJson(jsonString: String): List<LessonDraftWithSlot> {
        val result = mutableListOf<LessonDraftWithSlot>()
        try {
            val root = JSONObject(jsonString)
            val lessonsArray = root.optJSONArray("lessons") ?: return emptyList()

            for (i in 0 until lessonsArray.length()) {
                val obj = lessonsArray.getJSONObject(i)
                val dayOfWeek = obj.getInt("dayOfWeek")
                val slotIndex = obj.getInt("slotIndex")
                val modeStr = obj.optString("mode", "WEEKLY")
                val mode = try {
                    LessonMode.valueOf(modeStr)
                } catch (_: Exception) {
                    LessonMode.WEEKLY
                }

                val draft = LessonDraft(
                    mode = mode,
                    weeklySubject = obj.optString("weeklySubject", ""),
                    weeklyTeacher = obj.optString("weeklyTeacher", ""),
                    weeklyLocation = obj.optString("weeklyLocation", ""),
                    aSubject = obj.optString("aSubject", ""),
                    aTeacher = obj.optString("aTeacher", ""),
                    aLocation = obj.optString("aLocation", ""),
                    bSubject = obj.optString("bSubject", ""),
                    bTeacher = obj.optString("bTeacher", ""),
                    bLocation = obj.optString("bLocation", "")
                )

                result.add(LessonDraftWithSlot(dayOfWeek, slotIndex, draft))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }
}

data class LessonDraftWithSlot(
    val dayOfWeek: Int,
    val slotIndex: Int,
    val draft: LessonDraft
)
