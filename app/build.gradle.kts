import java.io.FileInputStream
import java.util.*

plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
//    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("dagger.hilt.android.plugin")
    id("jacoco")
    id("kotlin-android")
}

repositories {
}

android {
    compileSdk = 34

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    defaultConfig {
        applicationId = "com.hobengineering.ssdogapp"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("CIRCLE_BUILD_NUM")?.toIntOrNull() ?: 13
        versionName = "0.9.12${System.getenv("CIRCLE_BUILD_NUM") ?: ""}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

//    signingConfigs {
//
//        val ksName = "keystore.properties"
//        val ksProp = loadKeyStore(ksName)
//        ksProp?.let {
//            create("release") {
//                keyAlias = ksProp.getProperty("release.keyAlias")
//                keyPassword = ksProp.getProperty("release.keyPassword")
//                storeFile = file(ksProp.getProperty("release.file"))
//                storePassword = ksProp.getProperty("release.storePassword")
//            }
//        }
//    }

    buildTypes {
        named("release") {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        named("debug") {
            isTestCoverageEnabled = true
        }
    }

    testOptions {

        animationsDisabled = true

        managedDevices {
            devices {
                maybeCreate<com.android.build.api.dsl.ManagedVirtualDevice>("pixel2api30").apply {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "google_atd"
                }
            }
        }
    }

    namespace = "com.hobengineering.ssdogapp"

    kotlinOptions {
        jvmTarget = "1.8"
    }

}

jacoco {
    val jacoco_version: String by project
    toolVersion = jacoco_version
    reportsDirectory.set(layout.buildDirectory.dir("mergedReportDir"))
}

tasks.register<JacocoReport>("jacocoTestReport") {

    dependsOn("testDebugUnitTest")
    dependsOn("pixel2api30DebugAndroidTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf("**/R.class", "**/R$*.class", "**/BuildConfig.*", "**/Manifest*.*", "**/*Test*.*", "android/**/*.*")
    val debugTree = fileTree("${buildDir}/tmp/kotlin-classes/debug") { exclude(fileFilter) }
    val mainSrc = "${project.projectDir}/src/main/kotlin"

    sourceDirectories.from(files(setOf(mainSrc)))
    classDirectories.from(files(setOf(debugTree)))
    executionData.from(fileTree(buildDir) { include(setOf(
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            "outputs/managed_device_code_coverage/pixel2api30/coverage.ec"
    ))})
}

//sonarqube {
//    properties {
//        property("sonar.projectKey", System.getenv("SONAR_PROJECT_KEY"))
//        property("sonar.organization", System.getenv("SONAR_ORGANIZATION"))
//        property("sonar.host.url", System.getenv("SONAR_HOST_URL"))
//    }
//}

//tasks.register("generateGoogleServicesJson") {
//    doLast {
//        val jsonFileName = "google-services.json"
//        val fileContent = System.getenv("GOOGLE_SERVICES_JSON")
//        File(projectDir, jsonFileName).apply {
//            createNewFile(); writeText(fileContent)
//            println("generated $jsonFileName")
//        }
//    }
//}

//tasks.register("generateKsPropFile") {
//    doLast {
//        val configFileName = "keystore.properties"
//        File(projectDir, configFileName).apply {
//            createNewFile()
//            writeText("""
//                # Gradle signing properties for app module
//                release.file=${System.getenv("KS_PATH")}
//                release.storePassword=${System.getenv("KS_PASSWORD")}
//                release.keyAlias=${System.getenv("KS_KEY_ALIAS")}
//                release.keyPassword=${System.getenv("KS_KEY_PASSWORD")}
//                """.trimIndent())
//            println("generated ${this.path}")
//        }
//    }
//}

//tasks.register("generateAppDistKey") {
//    doLast {
//        val jsonFileName = "app-dist-key.json"
//        val fileContent = System.getenv("GOOGLE_APP_DIST_FASTLANE_SERVICE_ACCOUNT")
//        File(rootDir, jsonFileName).apply {
//                createNewFile()
//                writeText(fileContent)
//                println("generated ${this.path}")
//        }
//    }
//}

//fun loadKeyStore(name: String): Properties? {
//    val ksProp = Properties()
//    val ksPropFile = file(name)
//    return if (ksPropFile.exists()) {
//        ksProp.load(FileInputStream(ksPropFile))
//        ksProp
//    } else {
//        println("ERROR: local keystore file not found")
//        null
//    }
//}

val firebase_bom_version: String by project
val hilt_version: String by project
dependencies {

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Firebase
//    implementation(platform("com.google.firebase:firebase-bom:$firebase_bom_version"))
//    implementation("com.google.firebase:firebase-analytics-ktx")
//    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-analytics")

    // Dependency Injection
    implementation("com.google.dagger:hilt-android:$hilt_version")
    kapt("com.google.dagger:hilt-compiler:$hilt_version")

    // Coroutines
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-livedata-core-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")


    // Stripe integration
    implementation("com.stripe:stripeterminal:3.2.1")
    implementation("com.facebook.stetho:stetho-okhttp3:1.5.1")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    kapt("androidx.lifecycle:lifecycle-compiler:2.6.2")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.0")

    // hilt testing
    // more info:
    // https://developer.android.com/training/dependency-injection/hilt-testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:$hilt_version")
    kaptAndroidTest("com.google.dagger:hilt-android-compiler:$hilt_version")

    // unit test libs
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.1.3")

    // instrumented test libs
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
    // Espresso
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    // Hamcrest for view matching
    androidTestImplementation("org.hamcrest:hamcrest-library:2.2")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")

    // logging
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")


    /**
     * This library helps to automate some parts of the USB serial connection.
     * For more information, visit: https://github.com/felHR85/UsbSerial
     */
    implementation("com.github.felHR85:UsbSerial:6.1.0")
}