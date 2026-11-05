import java.util.Properties
plugins { id("com.android.application"); kotlin("android"); kotlin("plugin.compose") }
val signing=Properties().apply { val file=rootProject.file(".local/signing.properties"); if(file.exists()) file.inputStream().use { load(it) } }
android {
 namespace="pl.kripusek.pelican"
 compileSdk=36
 defaultConfig { applicationId="pl.kripusek.pelican"; minSdk=29; targetSdk=36; versionCode=5; versionName="2.1.0"; testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner" }
 buildFeatures { compose=true }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_21; targetCompatibility=JavaVersion.VERSION_21 }
 signingConfigs { if(signing.isNotEmpty()) create("localRelease") { storeFile=rootProject.file(".local/release.jks"); storePassword=signing.getProperty("password"); keyAlias="pelican"; keyPassword=signing.getProperty("password") } }
 buildTypes { release { isMinifyEnabled=true; isShrinkResources=true; signingConfig=signingConfigs.findByName("localRelease"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro") } }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { jvmToolchain(21) }
dependencies {
 implementation(platform("androidx.compose:compose-bom:2025.10.01"))
 implementation("androidx.activity:activity-compose:1.11.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.json:json:20250517")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.test:runner:1.7.0")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
}
