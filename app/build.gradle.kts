import java.io.File
import java.net.URI
import java.security.MessageDigest

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

tasks.named("preBuild").configure { dependsOn(prepareFaceModel) }

android {
    namespace = "com.pontocafe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pontocafe.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 18
        versionName = "0.9.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-adaptive-navigation-suite")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("com.google.ai.edge.litert:litert:1.4.1")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.security:security-crypto:1.1.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.09.01"))
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
