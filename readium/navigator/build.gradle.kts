/*
 * Copyright 2018 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

plugins {
    id("com.android.library")
    // id("com.vanniktech.maven.publish")
    // id("org.jetbrains.kotlin.android")
    kotlin("android")
    kotlin("plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
}

group = "org.readium.kotlin-toolkit" // property("pom.groupId") as String


android {
    namespace = "org.readium.r2.navigator"

    resourcePrefix = "readium_"

    compileSdk = 36 // (property("android.compileSdk") as String).toInt()

    defaultConfig {
        minSdk = 23 // (property("android.minSdk") as String).toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
        allWarningsAsErrors = true
        // See https://github.com/readium/kotlin-toolkit/pull/525#issuecomment-2300084041
        freeCompilerArgs = freeCompilerArgs + ("-Xconsistent-data-class-copy-visibility")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        // FIXME: Look into whether we can remove this.
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            // proguardFiles(getDefaultProguardFile("proguard-android.txt"))
        }
    }
    // 👇 ensure both release + debug variants are published
    publishing {
        singleVariant("release")
        singleVariant("debug")
    }
}

/*
kotlin {
    explicitApi()
}
*/

dependencies {
    // api(project(":readium:readium-shared"))
    api("org.readium.kotlin-toolkit:readium-shared:3.1.1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    // api(files("libs/PhotoView-2.3.0.jar"))
    api("com.github.Baseflow:PhotoView:2.3.0")

    api("androidx.appcompat:appcompat:1.7.0")
    api("androidx.browser:browser:1.8.0")
    api("androidx.constraintlayout:constraintlayout:2.2.1")
    api("androidx.core:core-ktx:1.16.0")
    api("androidx.fragment:fragment-ktx:1.8.7")
    api("androidx.legacy:legacy-support-core-ui:1.0.0")
    api("androidx.lifecycle:lifecycle-common-java8:2.9.0")
    api("androidx.recyclerview:recyclerview:1.4.0")
    api("androidx.media3:media3-session:1.7.1")
    api("androidx.media3:media3-common-ktx:1.7.1")
    api("androidx.media3:media3-exoplayer:1.7.1")
    api("androidx.webkit:webkit:1.13.0")

    api("com.jakewharton.timber:timber:5.0.1")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    api("org.jsoup:jsoup:1.18.1")

    // Tests
    testImplementation("junit:junit:4.13.2")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.1.21")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
