import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val apiBaseUrl: String = (localProps["api.baseUrl"] as String?) ?: "http://localhost:8080/v1"
val useFake: Boolean = ((localProps["app.useFake"] as String?) ?: "false").toBoolean()

val generatedBuildConfigDir = layout.buildDirectory.dir("generated/buildconfig/commonMain/kotlin")

val generateBuildConfig = tasks.register("generateBuildConfig") {
    val outDir = generatedBuildConfigDir
    val base = apiBaseUrl
    val fake = useFake
    outputs.dir(outDir)
    doLast {
        val pkgDir = outDir.get().asFile.resolve("br/tec/wrcoder/meucondominio/core")
        pkgDir.mkdirs()
        pkgDir.resolve("BuildConfig.kt").writeText(
            """
            package br.tec.wrcoder.meucondominio.core

            object BuildConfig {
                const val API_BASE_URL: String = "$base"
                const val USE_FAKE: Boolean = $fake
            }
            """.trimIndent()
        )
    }
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            linkerOpts.add("-lsqlite3")
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generatedBuildConfigDir)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.koin.android)
            implementation(libs.androidx.security.crypto)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.iconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.json)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutinesExt)
            implementation(libs.sqldelight.primitiveAdapters)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeViewmodel)

            implementation(libs.peekaboo.imagePicker)
            implementation(libs.filekit.core)
            implementation(libs.filekit.compose)
            implementation(libs.coil.compose)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.nativeDriver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

android {
    namespace = "br.tec.wrcoder.meucondominio"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "br.tec.wrcoder.meucondominio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

sqldelight {
    databases {
        create("MeuCondominioDb") {
            packageName.set("br.tec.wrcoder.meucondominio.data.local.db")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateBuildConfig)
}
tasks.matching { it.name.endsWith("SourcesJar") }.configureEach {
    dependsOn(generateBuildConfig)
}

// Workaround: o plugin Compose Multiplatform às vezes não encadeia
// corretamente generateActualResourceCollectorsFor<Target>Main ->
// compileKotlin<Target>, especialmente com config cache reutilizado.
// Forçar a dependência elimina o "No such file or directory" em
// ActualResourceCollectors.kt.
tasks.matching { it.name.startsWith("compileKotlinIos") }.configureEach {
    val target = name.removePrefix("compileKotlin")
    dependsOn("generateActualResourceCollectorsFor${target}Main")
}

dependencies {
    debugImplementation(libs.compose.uiTooling)
}
