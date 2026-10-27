import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.plugins.ExtensionAware

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.koinCompiler)
}

kotlin {
    jvmToolchain(17)

    // Hızlı birim testleri için saf JVM hedefi (Android/iOS derlemesi gerektirmez).
    jvm()

    (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        namespace = "com.cinch.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }

    // NOTE: iosX64 (Intel simülatör) hedefi yok — Compose/lifecycle artifact'leri bu varyantı
    // yayınlamıyor ve Apple Silicon'da gerekmiyor. Gerekirse eklenebilir.
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            // Uygulama modülü @KoinApplication ve modül sınıflarını görsün diye api.
            api(libs.koin.core)
            api(libs.koin.annotations)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

sqldelight {
    databases {
        create("CinchDatabase") {
            packageName.set("com.cinch.shared.db")
        }
    }
}
