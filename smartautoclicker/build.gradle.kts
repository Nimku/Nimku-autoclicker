/*
 * Copyright (C) 2025 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.buzbuz.androidApplication)
    alias(libs.plugins.buzbuz.androidUnitTest)
    alias(libs.plugins.buzbuz.buildParameters)
    alias(libs.plugins.buzbuz.hilt)
}

android {
    namespace = "com.buzbuz.smartautoclicker"

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        // Nimku AutoClicker has its own application id, so it can be installed next to the original app.
        applicationId = "com.nimku.autoclicker"

        manifestPlaceholders["appName"] = "@string/app_name"
        manifestPlaceholders["smartAutoClickerApplication"] =
            "com.buzbuz.smartautoclicker.application.SmartAutoClickerApplication"
        manifestPlaceholders["scenarioActivity"] = "com.buzbuz.smartautoclicker.scenarios.ScenarioActivity"
        manifestPlaceholders["smartAutoClickerService"] = "com.buzbuz.smartautoclicker.SmartAutoClickerService"

        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            // Debug builds can be installed next to the release build.
            applicationIdSuffix = ".debug"
        }
    }

    // Per-ABI APKs (smaller downloads), plus one universal APK that works on every device.
    splits {
        abi {
            isEnable = true
            reset()
            include(
                *buildParameters.abiFilters.typedValue
                    .ifBlank { "armeabi-v7a,arm64-v8a,x86,x86_64" }
                    .split(',')
                    .toTypedArray()
            )
            isUniversalApk = true
        }
    }

    signingConfigs {
        // Shared key committed in the repository, so every build (local or CI) can be installed
        // and updated over the previous one. Replace it with a private key before publishing to a store.
        create("nimku") {
            storeFile = file("./nimku-shared.jks")
            storePassword = "nimkuautoclicker"
            keyAlias = "nimku"
            keyPassword = "nimkuautoclicker"
        }

        // Private release key, used instead of the shared one when the signing parameters are provided.
        create("release") {
            storeFile = file("./nimku-release.jks")
            storePassword = buildParameters.signingStorePassword.typedValue
            keyAlias = buildParameters.signingKeyAlias.typedValue
            keyPassword = buildParameters.signingKeyPassword.typedValue
        }
    }

    buildTypes {
        val release = signingConfigs.getByName("release")
        val hasPrivateKey = release.storeFile?.exists() == true &&
                !release.storePassword.isNullOrBlank() &&
                !release.keyAlias.isNullOrBlank() &&
                !release.keyPassword.isNullOrBlank()

        getByName("debug").signingConfig = signingConfigs.getByName("nimku")
        getByName("release").signingConfig = if (hasPrivateKey) release else signingConfigs.getByName("nimku")
    }
}

// Assign unique versionCodes per ABI
androidComponents.onVariants { variant ->
    variant.outputs.forEach { output ->
        val abiFilter = output.filters
            .find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }
            ?.identifier
        val abiVersionCode = when (abiFilter) {
            "armeabi-v7a" -> 1
            "arm64-v8a"   -> 2
            "x86"         -> 3
            "x86_64"      -> 4
            else          -> 0  // universal
        }
        val baseVersionCode = output.versionCode.get()
        output.versionCode.set(baseVersionCode * 10_000 + abiVersionCode)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.appCompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.recyclerView)
    implementation(libs.androidx.fragment.ktx)

    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.common.java8)

    implementation(libs.airbnb.lottie)
    implementation(libs.google.material)

    implementation(project(":core:common:accessibility"))
    implementation(project(":core:common:actions"))
    implementation(project(":core:common:base"))
    implementation(project(":core:common:bitmaps"))
    implementation(project(":core:common:revenue"))
    implementation(project(":core:common:review"))
    implementation(project(":core:common:display"))
    implementation(project(":core:common:navigation"))
    implementation(project(":core:common:overlays"))
    implementation(project(":core:common:permissions"))
    implementation(project(":core:common:quality"))
    implementation(project(":core:common:settings"))
    implementation(project(":core:common:tutorial"))
    implementation(project(":core:common:ui"))
    implementation(project(":core:dumb"))
    implementation(project(":core:smart:debugging"))
    implementation(project(":core:smart:detection"))
    implementation(project(":core:smart:domain"))
    implementation(project(":core:smart:processing"))

    implementation(project(":feature:backup"))
    implementation(project(":feature:notifications"))
    implementation(project(":feature:quick-settings-tile"))
    implementation(project(":feature:revenue"))
    implementation(project(":feature:review"))
    implementation(project(":feature:smart-config"))
    implementation(project(":feature:smart-debugging"))
    implementation(project(":feature:dumb-config"))
    implementation(project(":feature:tutorial"))
}
