plugins {
    `java-library`
}

val androidSdkDir: String = System.getenv("ANDROID_HOME")
    ?: (System.getProperty("user.home") + "/AppData/Local/Android/Sdk")

dependencies {
    compileOnly(files("$androidSdkDir/platforms/android-34/android.jar"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
