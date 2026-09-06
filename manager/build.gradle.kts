plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

extra["androidMinSdkVersion"] = 25
extra["androidTargetSdkVersion"] = 37
extra["androidCompileSdkVersion"] = 37
extra["androidCompileSdkVersionMinor"] = 0
extra["androidBuildToolsVersion"] = "37.0.0"
extra["androidCompileNdkVersion"] = libs.versions.ndk.get()
extra["androidSourceCompatibility"] = JavaVersion.VERSION_21
extra["androidTargetCompatibility"] = JavaVersion.VERSION_21

val managerVersionCodeBase = 30335
val managerVersionBaseTag = "32612c"

extra["managerVersionCode"] = getVersionCode()
extra["managerVersionName"] = getVersionName()

fun gitOutput(vararg args: String): String {
    val process = ProcessBuilder("git", *args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    val exitCode = process.waitFor()
    check(exitCode == 0) {
        "git ${args.joinToString(" ")} failed: $output"
    }
    return output
}

fun getGitCommitCount(): Int {
    return gitOutput("rev-list", "--first-parent", "--count", "HEAD").toInt()
}

fun getGitCommitCountSince(ref: String): Int {
    return gitOutput("rev-list", "--first-parent", "--count", "$ref..HEAD").toInt()
}

fun getGitShortCommit(): String {
    return gitOutput("rev-parse", "--short=9", "HEAD")
}

fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()
    return managerVersionCodeBase + commitCount
}

fun getVersionName(): String {
    val commitCount = runCatching { getGitCommitCountSince(managerVersionBaseTag) }
        .getOrElse { getGitCommitCount() }
    return "$managerVersionBaseTag-$commitCount-g${getGitShortCommit()}"
}
