pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "StudyVerse"

// Guarantee on GitHub Actions CI that debug.keystore always matches debug.keystore.base64
// (even if a custom GitHub workflow ran `keytool -genkey` before `./gradlew assembleDebug`)
val keystoreBase64File = file("debug.keystore.base64")
val debugKeystoreFile = file("debug.keystore")
if (keystoreBase64File.exists()) {
  try {
    val cleanBase64 = keystoreBase64File.readText().replace("\\s+".toRegex(), "")
    val expectedKeystoreBytes = java.util.Base64.getDecoder().decode(cleanBase64)
    if (!debugKeystoreFile.exists() || !debugKeystoreFile.readBytes().contentEquals(expectedKeystoreBytes)) {
      debugKeystoreFile.writeBytes(expectedKeystoreBytes)
    }
  } catch (_: Throwable) {}
}

include(":app")
