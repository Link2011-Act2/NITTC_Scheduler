import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.3.6"
}

// 実機で外部ロードを確認するまでは同梱版を既定にする。
// 軽量APKの検証: -PbundleAiRuntime=false
val bundleAiRuntime = providers.gradleProperty("bundleAiRuntime").map {
    it.toBooleanStrict()
}.orElse(true).get()
val llamaNativeArtifacts = configurations.create("llamaNativeArtifacts") {
    isCanBeConsumed = false
    isTransitive = false
}
val generatedLlamaJniDir = layout.buildDirectory.dir("generated/llama-jni")
val prepareBundledAiRuntime = tasks.register("prepareBundledAiRuntime") {
    inputs.files(llamaNativeArtifacts)
    outputs.dir(generatedLlamaJniDir)
    doLast {
        val output = generatedLlamaJniDir.get().asFile
        ZipFile(llamaNativeArtifacts.singleFile).use { source ->
            source.entries().asSequence().filter {
                !it.isDirectory && it.name.matches(Regex("jni/(arm64-v8a|x86_64)/lib[a-z0-9_]+\\.so"))
            }.forEach { entry ->
                val target = File(output, entry.name.removePrefix("jni/"))
                target.parentFile.mkdirs()
                source.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
            }
        }
    }
}

val aiRuntimeReleaseDir = layout.buildDirectory.dir("ai-runtime-release")
tasks.register("prepareAiRuntimeRelease") {
    inputs.files(llamaNativeArtifacts)
    inputs.file("src/main/java/jp/linkserver/nittcsc/logic/AiRuntimeSpec.kt")
    inputs.file("src/main/assets/oss_licenses/ai_runtime_licenses.txt")
    outputs.dir(aiRuntimeReleaseDir)
    doLast {
        val catalog = file("src/main/java/jp/linkserver/nittcsc/logic/AiRuntimeSpec.kt").readText()
        val expectedAarHash = Regex("AI_RUNTIME_AAR_SHA256 = \"([a-f0-9]+)\"").find(catalog)!!.groupValues[1]
        fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val aar = llamaNativeArtifacts.singleFile
        check(sha256(aar.readBytes()) == expectedAarHash) { "Unexpected llama.cpp AAR; update the pinned catalog first" }
        val version = Regex("AI_RUNTIME_VERSION = \"([^\"]+)\"").find(catalog)!!.groupValues[1]
        val artifacts = Regex("AiRuntimeArtifact\\(\"([^\"]+)\", \"([^\"]+)\", ([0-9]+), \"([a-f0-9]+)\"\\)")
            .findAll(catalog).map { it.groupValues.drop(1) }.toList()
        check(artifacts.size == 6) { "Missing AI runtime artifacts" }
        val output = aiRuntimeReleaseDir.get().asFile.apply { mkdirs() }
        val licenses = file("src/main/assets/oss_licenses/ai_runtime_licenses.txt").readBytes()
        val checksums = mutableListOf<String>()
        ZipFile(aar).use { source ->
            artifacts.forEach { (name, abi, size, hash) ->
                val entryName = "lib$name.so"
                val bytes = source.getInputStream(source.getEntry("jni/$abi/$entryName")).use { it.readBytes() }
                check(bytes.size.toLong() == size.toLong() && sha256(bytes) == hash) { "Invalid runtime binary: $name" }
                val target = File(output, "$name.zip")
                ZipOutputStream(target.outputStream()).use { zip ->
                    listOf(entryName to bytes, "LICENSES.txt" to licenses).forEach { (path, contents) ->
                        zip.putNextEntry(ZipEntry(path).apply { time = 0 })
                        zip.write(contents)
                        zip.closeEntry()
                    }
                }
                checksums += "${sha256(target.readBytes())}  ${target.name}"
            }
        }
        File(output, "SHA256SUMS.txt").writeText(checksums.joinToString("\n", postfix = "\n"))
        File(output, "release-notes.md").writeText(
            "NITTC Scheduler用の追加AIエンジンです。\n\n" +
                "io.github.ljcamargo:llamacpp-kotlin:0.4.0 のCPU別ネイティブライブラリを配布します。" +
                "アプリが対応するファイルを取得し、内蔵SHA-256で検証します。各ZIPにLICENSES.txtを含みます。\n"
        )
        logger.lifecycle("AI runtime release {}: {}", version, output)
    }
}

val buildNumberFiles = (
    fileTree("src") {
        exclude("**/build/**")
    }.files + listOf(
        project.file("build.gradle.kts"),
        rootProject.file("build.gradle.kts"),
        rootProject.file("settings.gradle.kts"),
        rootProject.file("gradle/libs.versions.toml")
    ).filter { it.isFile }
).sortedBy { it.relativeTo(rootProject.projectDir).invariantSeparatorsPath }

val appCodeName = "Sist" // トリッカルから取ります
val appVersionName = "1.1.2-IntDev"
val buildContentHash = MessageDigest.getInstance("SHA-256").run {
    buildNumberFiles.forEach { file ->
        update(file.relativeTo(rootProject.projectDir).invariantSeparatorsPath.toByteArray())
        update(0)
        update(file.readBytes())
        update(0)
    }
    digest().joinToString("") { "%02x".format(it) }
}
val buildTimestamp = DateTimeFormatter.ofPattern("yyMMdd-HHmm")
    .withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(buildNumberFiles.maxOf { it.lastModified() }))
val generatedBuildNumber =
    "$appCodeName-v${appVersionName.substringBefore('-').removePrefix("v")}-$buildTimestamp-${buildContentHash.take(3)}"

// ── OSS ライセンス生成タスク ──────────────────────────────────────────────

data class OssCatalogEntry(
    val title: String,
    val coordinate: String,
    val license: String,
    val url: String,
    val body: String
)

fun jsonEscape(value: String): String {
    return buildString(value.length + 16) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}

data class PomLicense(val name: String, val url: String?)

data class PomMetadata(
    val name: String?,
    val projectUrl: String?,
    val licenses: List<PomLicense>
)

fun parsePomMetadata(pomFile: File): PomMetadata {
    return runCatching {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(pomFile)
            .apply { documentElement.normalize() }
        fun firstTextByTag(tag: String): String? {
            val nodes = doc.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val value = nodes.item(i)?.textContent?.trim()
                if (!value.isNullOrBlank()) return value
            }
            return null
        }
        val projectName = firstTextByTag("name")
        val projectUrl = firstTextByTag("url")
        val licenseNodes = doc.getElementsByTagName("license")
        val licenses = buildList {
            for (i in 0 until licenseNodes.length) {
                val node = licenseNodes.item(i) ?: continue
                val children = node.childNodes
                var licenseName: String? = null
                var licenseUrl: String? = null
                for (j in 0 until children.length) {
                    val child = children.item(j) ?: continue
                    when (child.nodeName) {
                        "name" -> licenseName = child.textContent?.trim()
                        "url" -> licenseUrl = child.textContent?.trim()
                    }
                }
                if (!licenseName.isNullOrBlank()) {
                    add(PomLicense(name = licenseName, url = licenseUrl))
                }
            }
        }
        PomMetadata(name = projectName, projectUrl = projectUrl, licenses = licenses)
    }.getOrElse {
        PomMetadata(name = null, projectUrl = null, licenses = emptyList())
    }
}

fun readNoticeOrLicenseText(artifactFile: File): String? {
    if (!artifactFile.exists() || !artifactFile.isFile) return null
    val candidateNames = listOf(
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt",
        "META-INF/NOTICE.md",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/LICENSE.md"
    )
    return runCatching {
        ZipFile(artifactFile).use { zip ->
            candidateNames.firstNotNullOfOrNull { name ->
                val entry = zip.getEntry(name) ?: return@firstNotNullOfOrNull null
                zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    reader.readText().takeIf { it.isNotBlank() }
                }
            }
        }
    }.getOrNull()
}

fun prettifyArtifactName(name: String): String {
    return name.split('-', '_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            when (token.lowercase(Locale.US)) {
                "ktx" -> "KTX"
                "api" -> "API"
                "sdk" -> "SDK"
                else -> token.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                }
            }
        }
}

fun resolveDisplayTitle(group: String, name: String): String {
    return when {
        group == "androidx.core" && name == "core-ktx" -> "AndroidX Core KTX"
        group == "androidx.appcompat" && name == "appcompat" -> "AndroidX AppCompat"
        group == "androidx.activity" && name == "activity-compose" -> "AndroidX Activity Compose"
        group == "androidx.navigation" && name == "navigation-compose" -> "AndroidX Navigation Compose"
        group == "androidx.work" && name.startsWith("work-runtime") -> "AndroidX WorkManager"
        group == "androidx.room" -> "AndroidX Room ${prettifyArtifactName(name.removePrefix("room-"))}"
        group == "com.squareup.okhttp3" && name == "okhttp" -> "OkHttp"
        group == "io.github.ljcamargo" && name == "llamacpp-kotlin" -> "llama.cpp Kotlin (ljcamargo)"
        group == "com.google.android.gms" && name == "play-services-mlkit-text-recognition-japanese" ->
            "ML Kit Japanese Text Recognition"
        group.startsWith("org.jetbrains.kotlin") && name.startsWith("kotlin-stdlib") ->
            "Kotlin Standard Library"
        group.startsWith("org.jetbrains.kotlinx") && name.startsWith("kotlinx-coroutines") ->
            "Kotlin Coroutines ${prettifyArtifactName(name.removePrefix("kotlinx-coroutines-"))}"
        group.startsWith("androidx.compose") -> "Jetpack Compose ${prettifyArtifactName(name)}"
        group.startsWith("androidx.lifecycle") -> "AndroidX Lifecycle ${prettifyArtifactName(name.removePrefix("lifecycle-"))}"
        group.startsWith("androidx.") -> "AndroidX ${prettifyArtifactName(name)}"
        else -> prettifyArtifactName(name)
    }
}

val generatedOssAssetsDir = layout.buildDirectory.dir("generated/oss-assets")
val generatedOssFile = generatedOssAssetsDir.map { it.file("oss_licenses/oss_licenses_auto.json") }

val generateOssLicensesAutoJson = tasks.register("generateOssLicensesAutoJson") {
    inputs.file("src/main/assets/oss_licenses/ai_runtime_licenses.txt")
    outputs.file(generatedOssFile)
    doLast {
        val runtimeConfigurationName = listOf(
            "debugRuntimeClasspath",
            "releaseRuntimeClasspath",
            "runtimeClasspath"
        ).firstOrNull { project.configurations.findByName(it) != null }
            ?: error("No runtime classpath configuration found for OSS generation.")

        val runtimeArtifacts = project.configurations
            .getByName(runtimeConfigurationName)
            .incoming
            .artifacts
            .artifacts
            .filterIsInstance<ResolvedArtifactResult>()

        val nativeArtifacts = llamaNativeArtifacts.incoming.artifacts.artifacts
            .filterIsInstance<ResolvedArtifactResult>()

        val moduleArtifacts = (runtimeArtifacts + nativeArtifacts)
            .mapNotNull { artifact ->
                val id = artifact.id.componentIdentifier as? ModuleComponentIdentifier
                    ?: return@mapNotNull null
                id to artifact.file
            }
            .distinctBy { (id, _) -> "${id.group}:${id.module}:${id.version}" }

        val componentIds = moduleArtifacts.map { it.first }
        val pomByCoordinate = mutableMapOf<String, PomMetadata>()
        if (componentIds.isNotEmpty()) {
            val queryResult = dependencies.createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
                .execute()
            queryResult.resolvedComponents.forEach { component ->
                val id = component.id as? ModuleComponentIdentifier ?: return@forEach
                val pomArtifact = component.getArtifacts(MavenPomArtifact::class.java)
                    .filterIsInstance<ResolvedArtifactResult>()
                    .firstOrNull()
                    ?: return@forEach
                val coordinate = "${id.group}:${id.module}:${id.version}"
                pomByCoordinate[coordinate] = parsePomMetadata(pomArtifact.file)
            }
        }

        val entries = moduleArtifacts.map { (id, artifactFile) ->
            val coordinate = "${id.group}:${id.module}:${id.version}"
            val pom = pomByCoordinate[coordinate]
            val licenses = pom?.licenses.orEmpty()
            val licenseLabel = if (licenses.isEmpty()) {
                "License not specified"
            } else {
                licenses.joinToString(" / ") { it.name }
            }
            val licenseUrl = licenses.firstOrNull { !it.url.isNullOrBlank() }?.url
            val projectUrl = pom?.projectUrl
            val body = if (id.group == "io.github.ljcamargo" && id.module == "llamacpp-kotlin") {
                file("src/main/assets/oss_licenses/ai_runtime_licenses.txt").readText()
            } else readNoticeOrLicenseText(artifactFile)
            OssCatalogEntry(
                title = pom?.name?.takeIf { it.isNotBlank() }
                    ?: resolveDisplayTitle(id.group, id.module),
                coordinate = coordinate,
                license = licenseLabel,
                url = licenseUrl ?: projectUrl
                    ?: "https://mvnrepository.com/artifact/${id.group}/${id.module}",
                body = body ?: ""
            )
        }
            .distinctBy { it.coordinate }
            .sortedBy { it.coordinate.lowercase(Locale.US) }

        val outFile = generatedOssFile.get().asFile
        outFile.parentFile.mkdirs()
        val generatedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
        val json = buildString {
            append("{\n")
            append("  \"generatedAt\": \"").append(jsonEscape(generatedAt)).append("\",\n")
            append("  \"entries\": [\n")
            entries.forEachIndexed { index, entry ->
                append("    {\n")
                append("      \"title\": \"").append(jsonEscape(entry.title)).append("\",\n")
                append("      \"coordinate\": \"").append(jsonEscape(entry.coordinate)).append("\",\n")
                append("      \"license\": \"").append(jsonEscape(entry.license)).append("\",\n")
                append("      \"url\": \"").append(jsonEscape(entry.url)).append("\",\n")
                append("      \"body\": \"").append(jsonEscape(entry.body)).append("\"\n")
                append("    }")
                if (index != entries.lastIndex) append(",")
                append("\n")
            }
            append("  ]\n")
            append("}\n")
        }
        outFile.writeText(json)
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateOssLicensesAutoJson)
    if (bundleAiRuntime) dependsOn(prepareBundledAiRuntime)
}

android {
    namespace = "jp.linkserver.nittcsc"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.linkserver.nittcsc"
        minSdk = 26
        targetSdk = 36
        versionCode = 21
        versionName = appVersionName
        buildConfigField("String", "BUILD_NUMBER", "\"$generatedBuildNumber\"")
        buildConfigField("boolean", "BUNDLED_AI_RUNTIME", bundleAiRuntime.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets.getByName("main") {
        assets.srcDirs("build/generated/oss-assets")
        if (bundleAiRuntime) jniLibs.srcDir(generatedLlamaJniDir.get().asFile)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Glance (Compose-based App Widgets)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")

    // QR共有はモデルのダウンロードなしでオフライン動作する。
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.squareup:gifencoder:0.10.1")
    implementation("com.github.bumptech.glide:gifdecoder:4.16.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // WorkManager for background downloads
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")
    
    // JNIラッパーはソースで保持する。AARは同梱版・配布ZIP生成・OSS表示だけに使用する。
    add(llamaNativeArtifacts.name, "io.github.ljcamargo:llamacpp-kotlin:0.4.0@aar")
    // 元ラッパーの推移依存を維持する。
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    implementation("com.google.android.material:material:1.13.0")
    
    // ML Kit for local on-device OCR fallback for text-only LLMs
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")

    // Nearby Connections for peer-to-peer sync
    implementation("com.google.android.gms:play-services-nearby:19.3.0")
}
