import com.android.build.gradle.api.ApplicationVariant

/*
 * Copyright (c) 2012-2016 Arne Schwabe
 * Distributed under the GNU GPL v2 with additional terms. For full terms see the file doc/LICENSE.txt
 */

plugins {
    id("com.android.application")
    id("checkstyle")

    id("kotlin-android")
}

android {
    compileSdk = 32

    ndkVersion = "24.0.8215888"

    defaultConfig {
        minSdk = 21
        targetSdk = 32
        versionCode = 192
        versionName = "0.7.37"
        externalNativeBuild {
            cmake {
            }
        }
    }


    testOptions.unitTests.isIncludeAndroidResources = true

    externalNativeBuild {
        cmake {
            path = File("${projectDir}/src/main/cpp/CMakeLists.txt")
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets", "build/ovpnassets")

        }

        create("ui") {
        }

        create("skeleton") {
        }

        getByName("debug") {
        }

        getByName("release") {
        }
    }

    signingConfigs {
        create("release") {
            // ~/.gradle/gradle.properties
            val keystoreFile: String? by project
            storeFile = keystoreFile?.let { file(it) }
            val keystorePassword: String? by project
            storePassword = keystorePassword
            val keystoreAliasPassword: String? by project
            keyPassword = keystoreAliasPassword
            val keystoreAlias: String? by project
            keyAlias = keystoreAlias
            enableV1Signing = true
            enableV2Signing = true
        }

    }

    lint {
        enable += setOf("BackButton", "EasterEgg", "StopShip", "IconExpectedSize", "GradleDynamicVersion", "NewerVersionAvailable")
        checkOnly += setOf("ImpliedQuantity", "MissingQuantity")
        disable += setOf("MissingTranslation", "UnsafeNativeCodeLocation")
    }

    buildTypes {
        getByName("release") {
            if (project.hasProperty("icsopenvpnDebugSign")) {
                logger.warn("property icsopenvpnDebugSign set, using debug signing for release")
                signingConfig = android.signingConfigs.getByName("debug")
            } else {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    flavorDimensions += listOf("implementation")

    productFlavors {
        create("ui") {
            dimension = "implementation"
            buildConfigField("boolean", "openvpn3", "false")
        }
        create("skeleton") {
            dimension = "implementation"
            buildConfigField("boolean", "openvpn3", "false")
        }
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_1_8
        sourceCompatibility = JavaVersion.VERSION_1_8
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }


}

// OpenVPN3 SWIG generation removed - using OpenVPN2 only


dependencies {
    // https://maven.google.com/web/index.html
    // https://developer.android.com/jetpack/androidx/releases/core
    val preferenceVersion = "1.2.0"
    val coreVersion = "1.7.0"
    val materialVersion = "1.5.0"
    val fragment_version = "1.4.1"


    implementation("androidx.annotation:annotation:1.3.0")
    implementation("androidx.core:core:$coreVersion")


    // Is there a nicer way to do this?
    dependencies.add("uiImplementation", "androidx.constraintlayout:constraintlayout:2.1.3")
    dependencies.add("uiImplementation", "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21")
    dependencies.add("uiImplementation", "androidx.cardview:cardview:1.0.0")
    dependencies.add("uiImplementation", "androidx.recyclerview:recyclerview:1.2.1")
    dependencies.add("uiImplementation", "androidx.appcompat:appcompat:1.4.1")
    dependencies.add("uiImplementation", "com.github.PhilJay:MPAndroidChart:v3.1.0")
    dependencies.add("uiImplementation", "com.squareup.okhttp3:okhttp:4.9.3")
    dependencies.add("uiImplementation", "androidx.core:core:$coreVersion")
    dependencies.add("uiImplementation", "androidx.core:core-ktx:$coreVersion")
    dependencies.add("uiImplementation", "androidx.fragment:fragment-ktx:$fragment_version")
    dependencies.add("uiImplementation", "androidx.preference:preference:$preferenceVersion")
    dependencies.add("uiImplementation", "androidx.preference:preference-ktx:$preferenceVersion")
    dependencies.add("uiImplementation", "com.google.android.material:material:$materialVersion")
    dependencies.add("uiImplementation", "androidx.webkit:webkit:1.4.0")
    dependencies.add("uiImplementation", "androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.1")
    dependencies.add("uiImplementation", "androidx.lifecycle:lifecycle-runtime-ktx:2.4.1")
    dependencies.add("uiImplementation","androidx.security:security-crypto:1.0.0")


    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.6.21")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.9.0")
    testImplementation("org.robolectric:robolectric:4.5.1")
    testImplementation("androidx.test:core:1.4.0")
}
