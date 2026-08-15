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

fun gitBlobSha(file: java.io.File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-1")
    digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.UTF_8))
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
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
            java.net.URI(faceModelUrl).toURL().openStream().use { input ->
                faceModelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }

        check(gitBlobSha(faceModelFile) == faceModelBlobSha) {
            "O arquivo facenet.tflite baixado não corresponde ao modelo fixado pelo projeto."
        }
    }
}

tasks.named("preBuild").configure { dependsOn(prepareFaceModel) }

android {
    namespace = "com.pontocafe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pontocafe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.4.0"
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
        }
        getByName("release") {
            buildConfigField("String", "API_BASE_URL", "\"$productionApiBaseUrl\"")
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
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    val cameraX = "1.6.1"
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    implementation("com.google.mlkit:face-detection:16.1.7")

    // LiteRT via Google Play Services. O arquivo facenet.tflite é validado antes do build.
    implementation("com.google.android.gms:play-services-tflite-java:16.5.0")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
