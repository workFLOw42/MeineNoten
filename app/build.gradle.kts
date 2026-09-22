import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Version of the build being produced, held in `version.properties`.
 *
 * Kept in a file rather than hard-coded here so it can be bumped automatically after a
 * release build (see the `bumpVersion` task) without rewriting the build script.
 */
val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) {
        versionPropsFile.inputStream().use { load(it) }
    }
}
val currentVersionCode = versionProps.getProperty("versionCode")?.toIntOrNull() ?: 1
val currentVersionName = versionProps.getProperty("versionName") ?: "1.0.0"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

android {
    namespace = "de.workflow42.meinenoten"
    compileSdk = 37

    defaultConfig {
        applicationId = "de.workflow42.meinenoten"
        minSdk = 26
        targetSdk = 36
        versionCode = currentVersionCode
        versionName = currentVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val props = Properties()
        val propFile = rootProject.file("local.properties")
        if (propFile.exists()) {
            props.load(propFile.inputStream())
        }

        create("release") {
            storeFile = file(props.getProperty("signing.storeFile") ?: "C:/Users/flori/Documents/Software/Github/Meine Noten/Meine_Noten.jks")
            storePassword = props.getProperty("signing.storePassword") ?: ""
            keyAlias = props.getProperty("signing.keyAlias") ?: ""
            keyPassword = props.getProperty("signing.keyPassword") ?: ""
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

/**
 * Writes the next version into `version.properties` after a release bundle was built.
 *
 * Bumping *after* the build rather than before it keeps the number that was actually
 * shipped visible in the file until the next release, and means a failed build does not
 * burn a versionCode.
 *
 * Only the resulting strings are captured – no project or task references – so this stays
 * compatible with the configuration cache enabled in gradle.properties.
 */
val bumpVersion = tasks.register("bumpVersion") {
    group = "versioning"
    description = "Raises versionCode and the patch level in version.properties."
    val propsFile = versionPropsFile
    val nextCode = currentVersionCode + 1
    // Patch-level bump: a release that changes more than that deserves a deliberate
    // versionName, so only the last segment moves on its own.
    val nextName = currentVersionName.split(".").let { parts ->
        if (parts.size == 3) {
            val patch = parts[2].toIntOrNull()
            if (patch != null) "${parts[0]}.${parts[1]}.${patch + 1}" else currentVersionName
        } else {
            currentVersionName
        }
    }
    val builtCode = currentVersionCode
    val builtName = currentVersionName

    doLast {
        propsFile.writeText(
            """
            # Version of the *next* build.
            #
            # Read during configuration and bumped automatically after a successful
            # :app:bundleRelease, so no two uploads can ever carry the same versionCode.
            #
            # Belongs in version control: the whole point is that the number cannot repeat
            # across machines or checkouts.
            #
            # Last built and signed: versionCode $builtCode / $builtName
            versionCode=$nextCode
            versionName=$nextName
            """.trimIndent() + "\n"
        )
        logger.lifecycle("Built $builtCode / $builtName – next build will be $nextCode / $nextName")
    }
}

// Only release bundles bump: debug builds and APKs must not consume upload numbers.
tasks.matching { it.name == "bundleRelease" }.configureEach {
    finalizedBy(bumpVersion)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.adaptive)
    implementation(libs.androidx.adaptive.navigation3)
    implementation(libs.androidx.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}