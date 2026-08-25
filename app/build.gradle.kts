import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val productionApiBaseUrl = "https://pontocafe.bernard-castillo.workers.dev/"
val debugApiBaseUrl = providers.gradleProperty("PONTOCAFE_API_URL")
    .orElse(productionApiBaseUrl)
    .get()

val faceModelCommit = "289bc10420aad15fed99094eee364eb24f908ecc"
val faceModelBlobSha = "8254aabae5cc73b8d2c15e7c589730eb3c264b87"
val faceModelUrl = "https://raw.githubusercontent.com/shubham0204/FaceRecognition_With_FaceNet_Android/$faceModelCommit/app/src/main/assets/facenet.tflite"
val faceModelFile = layout.projectDirectory.file("src/main/assets/facenet.tflite").asFile

fun gitBlobSha(file: File): String {
    val digest = MessageDigest.getInstance("SHA-1")
    digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.UTF_8))
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

val prepareFaceModel by tasks.registering {
    group = "build setup"
    description = "Baixa e valida o modelo FaceNet gratuito usado pelo APK."
    outputs.file(faceModelFile)

    doLast {
        faceModelFile.parentFile.mkdirs()
        val currentValid = faceModelFile.exists() && gitBlobSha(faceModelFile) == faceModelBlobSha
        if (!currentValid) {
            faceModelFile.delete()
            URI(faceModelUrl).toURL().openStream().use { input ->
                faceModelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        check(gitBlobSha(faceModelFile) == faceModelBlobSha) {
            "O arquivo facenet.tflite baixado não corresponde ao modelo fixado pelo projeto."
        }
    }
}

// A voz neural pt-BR era baixada em tempo de execução em cada aparelho (ver
// histórico de PontoNeuralVoiceRuntime.kt). Isso expôs o Ponto a corrupção de
// download em redes de quiosque reais (ex.: VOICE_MODEL_SIZE_INVALID quando
// um proxy/rede altera o corpo binário). Mesmo padrão do FaceNet acima:
// baixa, valida e empacota uma única vez em tempo de build, dentro do APK
// assinado — nenhum aparelho depende mais de rede para ter a voz pronta.
val voiceModelDirName = "vits-piper-pt_BR-faber-medium"
val voiceModelFileName = "pt_BR-faber-medium.onnx"
val voiceModelUrl = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-pt_BR-faber-medium.tar.bz2"
// Re-pinned 2026-08-25: the previous constants (size 63_201_428, a different
// SHA-256) no longer match what k2-fsa/sherpa-onnx's GitHub release actually
// serves at this URL — every install attempt against the real file failed
// VOICE_MODEL_SIZE_INVALID, on every network, deterministically (reproduced
// here independently of any device/kiosk network). Verified by downloading
// directly over HTTPS from the official release URL.
val voiceModelSha256 = "956b4f1733903891c4ba0973d0603b2a3d8c09c8432fb3bb5203a90a7431daca"
val voiceModelSizeBytes = 63_201_457L
val voiceModelAssetsDir = layout.projectDirectory.dir("src/main/assets/voice/$voiceModelDirName").asFile
val voiceModelFile = File(voiceModelAssetsDir, voiceModelFileName)

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

val prepareVoiceModel by tasks.registering {
    group = "build setup"
    description = "Baixa, valida e empacota o modelo de voz neural pt-BR usado pelo APK."
    outputs.dir(voiceModelAssetsDir)

    doLast {
        val currentValid = voiceModelFile.isFile &&
            voiceModelFile.length() == voiceModelSizeBytes &&
            sha256Hex(voiceModelFile).equals(voiceModelSha256, ignoreCase = true)

        if (!currentValid) {
            voiceModelAssetsDir.deleteRecursively()
            val workDir = File(temporaryDir, "voice-model-download").apply {
                deleteRecursively()
                mkdirs()
            }
            val archive = File(workDir, "$voiceModelDirName.tar.bz2")
            val extracted = File(workDir, "extracted").apply { mkdirs() }

            URI(voiceModelUrl).toURL().openStream().use { input ->
                archive.outputStream().use { output -> input.copyTo(output) }
            }

            project.exec {
                commandLine("tar", "-xjf", archive.absolutePath, "-C", extracted.absolutePath)
            }

            val extractedModel = extracted.walkTopDown()
                .firstOrNull { it.isFile && it.name == voiceModelFileName }
                ?: error("Modelo de voz não encontrado dentro do arquivo baixado.")
            val extractedModelDir = extractedModel.parentFile
                ?: error("Diretório do modelo de voz ausente após a extração.")

            voiceModelAssetsDir.parentFile.mkdirs()
            voiceModelAssetsDir.deleteRecursively()
            if (!extractedModelDir.renameTo(voiceModelAssetsDir)) {
                check(extractedModelDir.copyRecursively(voiceModelAssetsDir, overwrite = true)) {
                    "Não foi possível copiar o modelo de voz para os assets do app."
                }
            }
            workDir.deleteRecursively()
        }

        check(voiceModelFile.isFile && voiceModelFile.length() == voiceModelSizeBytes) {
            "O arquivo de voz baixado não corresponde ao tamanho fixado pelo projeto."
        }
        check(sha256Hex(voiceModelFile).equals(voiceModelSha256, ignoreCase = true)) {
            "O arquivo de voz baixado não corresponde ao SHA-256 fixado pelo projeto."
        }
        check(File(voiceModelAssetsDir, "tokens.txt").isFile) {
            "tokens.txt ausente no modelo de voz empacotado."
        }
        check(File(voiceModelAssetsDir, "espeak-ng-data").isDirectory) {
            "espeak-ng-data ausente no modelo de voz empacotado."
        }
    }
}

tasks.named("preBuild").configure { dependsOn(prepareFaceModel, prepareVoiceModel) }

android {
    namespace = "com.pontocafe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pontocafe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 100
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Credentials are never stored in this repo (app/keystore.properties
            // and every *.jks/*.keystore file are gitignored). Two supported
            // sources, checked in order:
            //   1. app/keystore.properties (storeFile/storePassword/keyAlias/keyPassword)
            //   2. RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD
            //      env vars, against release.jks at the repo root.
            val propsFile = project.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply {
                    propsFile.inputStream().use { load(it) }
                }
                storeFile = file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            } else {
                val releaseKeystoreFile = rootProject.file("release.jks")
                if (releaseKeystoreFile.exists()) {
                    storeFile = releaseKeystoreFile
                    storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                    keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", "\"$productionApiBaseUrl\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.2.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("com.airbnb.android:lottie-compose:6.7.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    val cameraX = "1.6.1"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("com.google.mlkit:face-detection:16.1.7")

    // CPU/XNNPACK intencional: preserva o mesmo espaço de embeddings dos
    // templates faciais já cadastrados. Delegate GPU só volta após calibração
    // explícita contra o catálogo existente.
    implementation("com.google.android.gms:play-services-tflite-java:16.5.0")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    // Voz neural do Ponto: mantém o AAR Android estável publicado no JitPack.
    // v1.13.6 possui release upstream, mas o artefato JitPack Android não está
    // disponível como coordenada estável neste projeto; v1.13.4 é o artefato
    // previamente resolvido/validado e preserva a API VITS/Piper usada aqui.
    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.4")

    testImplementation("junit:junit:4.13.2")
    // Unit tests run on the JVM, where Android's org.json is only a stub.
    // Use the real implementation so authorization error bodies are parsed
    // exactly as they are on-device, without changing production APK behavior.
    testImplementation("org.json:json:20240303")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
