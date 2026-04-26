import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp") version "2.3.6"
}

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

        val moduleArtifacts = runtimeArtifacts
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
            val body = readNoticeOrLicenseText(artifactFile)
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
}

android {
    namespace = "jp.linkserver.nittcsc"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.linkserver.nittcsc"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.9.4-Beta" // (1.1.4-IntDev相当)

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
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    sourceSets.getByName("main") {
        assets.srcDirs("build/generated/oss-assets")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // Glance (Compose-based App Widgets)
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // WorkManager for background downloads
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    
    // llama.cpp Android wrapper (offline multimodal support)
    implementation("io.github.ljcamargo:llamacpp-kotlin:0.4.0")
    
    // ML Kit for local on-device OCR fallback for text-only LLMs
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")
}
