/*
 * Background Download Implementation Guide
 * 
 * この実装により、アプリを閉じてもAIモデルのダウンロードがバックグラウンドで続行されます。
 * 通知に常駐して、ダウンロード進捗が表示されます。
 * 
 * ================================================================================
 * 1. ViewModel で使用する例：
 * ================================================================================
 * 
 * class ModelDownloadViewModel(application: Application) : AndroidViewModel(application) {
 *     private val helper = ModelDownloadHelper(application)
 * 
 *     fun startDownload(modelUrl: String, fileName: String) {
 *         val downloadState: LiveData<BackgroundDownloadState> = 
 *             helper.startBackgroundDownload(modelUrl, fileName)
 * 
 *         downloadState.observe(this) { state ->
 *             when (state) {
 *                 is BackgroundDownloadState.Downloading -> {
 *                     // Progress: state.progress (0f to 1f)
 *                 }
 *                 is BackgroundDownloadState.Success -> {
 *                     // Download complete
 *                 }
 *                 is BackgroundDownloadState.Error -> {
 *                     // Handle error
 *                 }
 *                 else -> {}
 *             }
 *         }
 *     }
 * }
 * 
 * ================================================================================
 * 2. UI (Compose) で使用する例：
 * ================================================================================
 * 
 * @Composable
 * fun ModelDownloadScreen() {
 *     val viewModel: ModelDownloadViewModel = viewModel()
 *     val downloadState by viewModel.downloadState.observeAsState()
 * 
 *     when (downloadState) {
 *         is BackgroundDownloadState.Downloading -> {
 *             val progress = (downloadState as BackgroundDownloadState.Downloading).progress
 *             LinearProgressIndicator(
 *                 progress = { progress },
 *                 modifier = Modifier.fillMaxWidth()
 *             )
 *             Text("Downloading: ${(progress * 100).toInt()}%")
 *             Text("You can close the app and download will continue")
 *         }
 *         is BackgroundDownloadState.Success -> {
 *             Text("Download completed!")
 *             // Model is ready to use
 *         }
 *         else -> {}
 *     }
 * }
 * 
 * ================================================================================
 * 3. 重要な設定：
 * ================================================================================
 * 
 * build.gradle.kts に追加済み：
 *   - implementation("androidx.work:work-runtime-ktx:2.9.1")
 * 
 * AndroidManifest.xml に追加済み：
 *   - <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
 * 
 * タイムアウト設定（ModelDownloadManager）：
 *   - callTimeout: 60分（大容量モデルダウンロード対応）
 *   - readTimeout: 5分
 *   - writeTimeout: 5分
 *   - connectTimeout: 30秒
 * 
 * ================================================================================
 * 4. 通知の動作：
 * ================================================================================
 * 
 * - ダウンロード中: 進捗バーと速度を表示
 * - 完了: 成功通知
 * - エラー: エラーメッセージと共に表示
 * - ユーザーがアプリを閉じても通知が常駐
 * 
 * ================================================================================
 * 5. キャンセル方法：
 * ================================================================================
 * 
 * helper.cancelDownload(fileName)  // ファイル名でキャンセル
 * 
 * ================================================================================
 */
