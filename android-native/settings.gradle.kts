import java.io.File
import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// AGP 只认 gradle.properties 里的 android.aapt2FromMavenOverride（须转义 \ 与盘符后的 :）
fun escapeForGradleProperties(path: String): String =
    File(path).absolutePath
        .replace("\\", "\\\\")
        .replace(":", "\\:")

fun unescapeGradlePropertiesPath(value: String): String =
    value.replace("\\:", ":").replace("\\\\", "\\")

fun ensureAapt2OverrideInGradleProperties(aapt2Path: String) {
    val key = "android.aapt2FromMavenOverride"
    val target = File(aapt2Path).absolutePath
    val entry = "$key=${escapeForGradleProperties(target)}"
    val propsFile = File(settingsDir, "gradle.properties")
    val lines = if (propsFile.isFile) propsFile.readLines().toMutableList() else mutableListOf()
    val idx = lines.indexOfFirst { it.trimStart().startsWith("$key=") }
    if (idx >= 0) {
        val current = lines[idx].substringAfter("=").trim()
        if (unescapeGradlePropertiesPath(current).equals(target, ignoreCase = true)) return
        lines[idx] = entry
    } else {
        if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
        lines.add(entry)
    }
    propsFile.writeText(lines.joinToString("\n") + "\n")
}

val localProps = Properties()
val localPropsFile = File(settingsDir, "local.properties")
if (localPropsFile.isFile) {
    localPropsFile.inputStream().use { localProps.load(it) }
}
localProps.getProperty("sdk.dir")?.let { sdkPath ->
    val sdk = File(sdkPath)
    val installed = sdk.resolve("build-tools").list()?.toSet().orEmpty()
    val tryOrder = listOf("34.0.0") + installed.filter { it != "34.0.0" }.sortedDescending()
    for (ver in tryOrder) {
        val win = sdk.resolve("build-tools/$ver/aapt2.exe")
        val unix = sdk.resolve("build-tools/$ver/aapt2")
        val aapt2 = when {
            win.isFile -> win
            unix.isFile -> unix
            else -> null
        }
        if (aapt2 != null) {
            ensureAapt2OverrideInGradleProperties(aapt2.absolutePath)
            println("Wardrobe: 使用本机 AAPT2 → ${aapt2.absolutePath}")
            break
        }
    }
}

rootProject.name = "Wardrobe"
include(":app")
