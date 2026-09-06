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

fun getShortGitHash(): String {
    return gitOutput("rev-parse", "--short", "HEAD")
}

fun hasGitRevision(revision: String): Boolean {
    return runCatching {
        gitOutput("rev-parse", "--verify", revision)
    }.isSuccess
}

fun getGitCommitCountSince(revision: String): Int? {
    return runCatching {
        gitOutput("rev-list", "--count", "$revision..HEAD").toInt()
    }.getOrNull()
}

fun getKernelBaseVersionCode(): Int? {
    val makefile = rootProject.projectDir.parentFile.resolve("kernel/Makefile")
    if (!makefile.isFile) return null

    val versionRegex = Regex("""KSU_VERSION_BASE\s*:=\s*(\d+)""")
    return makefile.useLines { lines ->
        lines.firstNotNullOfOrNull { line ->
            versionRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        }
    }
}

fun getKernelVersionName(versionCode: Int): String {
    return when (versionCode) {
        in 32600..32699 -> {
            val patch = versionCode - 32600
            if (patch == 0) "v3.3.0" else "v3.3.0-$patch"
        }
        else -> versionCode.toString()
    }
}

fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()
    return 30000 + commitCount - managerVersionCodeFallbackOffset
}

fun getVersionName(): String {
    val kernelVersionCode = getKernelBaseVersionCode()
    if (kernelVersionCode != null) {
        val baseName = getKernelVersionName(kernelVersionCode)
        val baseRevision = kernelVersionCode.toString()
        val distance = if (hasGitRevision("$baseRevision^{}")) {
            getGitCommitCountSince(baseRevision)
        } else {
            null
        }

        return if (distance != null && distance > 0) {
            "$baseName-$distance-g${getShortGitHash()}"
        } else if (distance == 0) {
            baseName
        } else {
            "$baseName-g${getShortGitHash()}"
        }
    }

    return gitOutput("describe", "--tags", "--match", "v[0-9]*", "--always")
}
