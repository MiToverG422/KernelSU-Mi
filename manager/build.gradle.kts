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

val managerVersionCodeFallbackOffset = 97

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
    return gitOutput("rev-list", "--count", "HEAD").toInt()
}

fun getGitDescribe(): String {
    return gitOutput("describe", "--tags", "--always")
}

fun getKernelVersionCode(): Int? {
    val makefile = rootProject.projectDir.parentFile.resolve("kernel/Makefile")
    if (!makefile.isFile) return null

    val versionRegex = Regex("""-DKSU_VERSION=(\d+)""")
    return makefile.useLines { lines ->
        lines.firstNotNullOfOrNull { line ->
            versionRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}

fun getVersionCode(): Int {
    return getKernelVersionCode() ?: (30000 + getGitCommitCount() - managerVersionCodeFallbackOffset)
}

fun getVersionName(): String {
    return getGitDescribe()
}
