import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import com.android.build.api.variant.VariantOutputConfiguration.OutputType.UNIVERSAL

private val abiTargets = arrayOf("arm64-v8a", "armeabi-v7a", "x86_64")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

configure<ApplicationExtension> {
    namespace = "icu.guestliang.nfcworkflow"
    compileSdk = 36
    ndkVersion = "30.0.14518594-beta1"

    defaultConfig {
        applicationId = "icu.guestliang.nfcworkflow"
        minSdk = 31
        targetSdk = 36
        versionCode = gitCommitCount()
        versionName = libs.versions.app.version.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            storeFile = System.getenv("ANDROID_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    splits {
        abi {
            isEnable = true
            reset()
            include(*abiTargets)
            isUniversalApk = true
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}
kotlin {
    jvmToolchain(25)
}
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abiFilter = output.filters
                .find { it.filterType == ABI }
                ?.identifier
            val isUniversalOutput = output.outputType == UNIVERSAL
            val abiName = abiFilter ?: "universal"

            if (variant.name == "debug" && !isUniversalOutput) {
                output.enabled.set(false)
                return@forEach
            }

            val appName = "NFCWorkFlow"
            val vName = libs.versions.app.version.get()
            val vCode = gitCommitCount()
            val variantName = variant.name.lowercase()
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName
                ?.set("${appName}_v${vName}_${vCode}-${variantName}-${abiName}.apk")
        }
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)


    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Ktor for networking
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.ktor.serialization.kotlinx.json)
}

fun gitCommitCount(): Int {
    println("rootProject.projectDir=" + rootProject.projectDir.absolutePath)
    fun run(vararg args: String): Int {
        val p = ProcessBuilder(listOf("git", *args))
            .directory(rootProject.projectDir)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText().trim()
        val code = p.waitFor()
        if (code != 0) return 0
        return out.toIntOrNull() ?: 0
    }

    val scoped = run("rev-list", "--count", "HEAD", "--", ".")
    if (scoped > 0) return scoped

    val all = run("rev-list", "--count", "HEAD")
    return if (all > 0) all else 1
}

configurations.matching { it.name == "composeMappingProducerClasspath" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name == "compose-group-mapping") {
            useVersion(libs.versions.kotlin.get())
        }
    }
}
