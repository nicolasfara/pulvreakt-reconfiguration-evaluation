import org.gradle.configurationcache.extensions.capitalized
import java.io.ByteArrayOutputStream

plugins {
    application
    alias(libs.plugins.gitSemVer)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.qa)
    alias(libs.plugins.multiJvmTesting)
    alias(libs.plugins.taskTree)
}

repositories {
    mavenCentral()
}
/*
 * Only required if you plan to use Protelis, remove otherwise
 */
sourceSets {
    main {
        resources {
            srcDir("src/main/protelis")
        }
    }
}

val usesJvm: Int = File(File(projectDir, "docker/sim"), "Dockerfile")
    .readLines()
    .first { it.isNotBlank() }
    .let {
        Regex("FROM\\s+eclipse-temurin:(\\d+)\\s*$").find(it)?.groups?.get(1)?.value
            ?: error("Cannot read information on the JVM to use.")
    }
    .toInt()

multiJvm {
    jvmVersionForCompilation.set(usesJvm)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.bundles.alchemist.protelis)
    implementation(libs.bundles.pulverization)
    implementation(libs.kotlinx.coroutine)
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    implementation("it.unibo.alchemist:alchemist-swingui:${libs.versions.alchemist.get()}")
    implementation("it.unibo.alchemist:alchemist-maps:${libs.versions.alchemist.get()}")
}

// Heap size estimation for batches
val maxHeap: Long? = 220000
val heap: Long = maxHeap ?: if (System.getProperty("os.name").lowercase().contains("linux")) {
    ByteArrayOutputStream().use { output ->
        exec {
            executable = "bash"
            args = listOf("-c", "cat /proc/meminfo | grep MemAvailable | grep -o '[0-9]*'")
            standardOutput = output
        }
        output.toString().trim().toLong() / 1024
    }.also { println("Detected ${it}MB RAM available.") } * 9 / 10
} else {
    // Guess 16GB RAM of which 2 used by the OS
    14 * 1024L
}
val taskSizeFromProject: Int? by project
val taskSize = taskSizeFromProject ?: (512 + 256)
val threadCount = maxOf(1, minOf(Runtime.getRuntime().availableProcessors(), heap.toInt() / taskSize))

val alchemistGroup = "Run Alchemist"

val maxTime = project.properties["maxTime"] as? Int ?: 50
val batch = project.properties["batch"] as? Boolean ?: false

/*
 * This task is used to run all experiments in sequence
 */
val runAll by tasks.register<DefaultTask>("runAll") {
    group = alchemistGroup
    description = "Launches all simulations"
}

val ciAlchemistConfiguration = """
    terminate:
      - type: AfterTime
        parameters: $maxTime
""".trimIndent()

val batchAlchemistConfiguration = """
launcher:
  type: HeadlessSimulationLauncher
  parameters:
    variables: [seed, cloud_epi, balance, epi_ratio, device_count]
""".trimIndent()

fun graphicsAlchemistConfiguration(effectName: String) = """
    launcher:
      type: SingleRunSwingUI
      parameters:
        graphics: effects/$effectName.json
""".trimIndent()

/*
 * Scan the folder with the simulation files, and create a task for each one of them.
 */
File(rootProject.rootDir.path + "/src/main/yaml").listFiles()
    ?.filter { it.extension == "yml" }
    ?.sortedBy { it.nameWithoutExtension }
    ?.forEach {
        val task by tasks.register<JavaExec>("run${it.nameWithoutExtension.capitalized()}") {
            group = alchemistGroup
            description = "Launch simulation ${it.nameWithoutExtension}"
            mainClass.set("it.unibo.alchemist.Alchemist")
            classpath = sourceSets["main"].runtimeClasspath

            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(multiJvm.latestJava))
                },
            )
            jvmArgs("-Dsun.java2d.opengl=true")
            args("run", it.absolutePath, "--override")

            when {
                System.getenv("CI") == "true" -> args(ciAlchemistConfiguration)
                batch -> {
                    maxHeapSize = "${minOf(heap.toInt(), Runtime.getRuntime().availableProcessors() * taskSize)}m"
                    args(batchAlchemistConfiguration)
                }
                else -> args(graphicsAlchemistConfiguration(it.nameWithoutExtension))
            }
        }

        runAll.dependsOn(task)
//        fun basetask(name: String, additionalConfiguration: JavaExec.() -> Unit = {}) = tasks.register<JavaExec>(name) {
//            group = alchemistGroup
//            description = "Launches graphic simulation ${it.nameWithoutExtension}"
//            mainClass.set("it.unibo.alchemist.Alchemist")
//            classpath = sourceSets["main"].runtimeClasspath
//            args("-y", it.absolutePath)
//            if (System.getenv("CI") == "true") {
//                args("-hl", "-t", "2")
//            } else {
//                args("-g", "effects/${it.nameWithoutExtension}.json")
//            }
//            javaLauncher.set(
//                javaToolchains.launcherFor {
//                    languageVersion.set(JavaLanguageVersion.of(usesJvm))
//                },
//            )
//            this.additionalConfiguration()
//        }
//        val capitalizedName = it.nameWithoutExtension.capitalized()
//        val graphic by basetask("run${capitalizedName}Graphic")
//        runAllGraphic.dependsOn(graphic)
//        val batch by basetask("run${capitalizedName}Batch") {
//            description = "Launches batch experiments for $capitalizedName"
//            maxHeapSize = "220g"// "${minOf(heap.toInt(), Runtime.getRuntime().availableProcessors() * taskSize)}m"
//            File("data").mkdirs()
//            val variablesUnderTest = arrayOf(
//                "seed",
//                // uncomment to test for different kinds of applications
//                // "battery_discharge_time",
//                "cloud_epi",
//                "balance",
//                "epi_ratio",
//                "device_count",
//            )
//            args(
//                "-e",
//                "data/${it.nameWithoutExtension}",
//                "-b",
//                "-var",
//                *variablesUnderTest,
//                "-p", threadCount,
//                "-i", 1,
//            )
//        }
//        runAllBatch.dependsOn(batch)
    }
