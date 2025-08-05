import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import kotlin.math.roundToInt

fun main() {
    val folderPath = "values"
    val folder = File(folderPath)

    if (!folder.exists() || !folder.isDirectory) {
        println("The provided path is not a valid directory.")
        return
    }

    val yamlFiles = folder.listFiles { file -> file.extension.equals("yaml", ignoreCase = true) }

    if (yamlFiles.isNullOrEmpty()) {
        println("No YAML files found in the directory.")
        return
    }

    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        isPrettyFlow = true
        indent = 2
    }
    val yaml = Yaml(options)

    // Initialize counters
    var totalOriginalGi = 0.0
    var totalNewGi = 0.0
    var filesProcessed = 0
    var entriesModified = 0

    yamlFiles.forEach { file ->
        println("Processing file: ${file.name}")
        val result = processYamlFile(file, yaml)

        if (result != null) {
            filesProcessed++
            totalOriginalGi += result.first
            totalNewGi += result.second
            entriesModified += result.third
        }
    }

    println("\nProcessing completed.")
    println("Files processed: $filesProcessed")
    println("Entries modified: $entriesModified")
    println("Total original memory: ${totalOriginalGi.roundToInt()} Gi")
    println("Total limit memory: ${totalNewGi.roundToInt()} Gi")
    println("Memory increase: ${(totalNewGi - totalOriginalGi).roundToInt()} Gi (${((totalNewGi / totalOriginalGi - 1) * 100).roundToInt()}%)")
}

fun processYamlFile(file: File, yaml: Yaml): Triple<Double, Double, Int>? {
    try {
        val yamlContent = file.readText()
        val parsed = yaml.load<Map<String, Any>>(yamlContent)?.toMutableMap() ?: return null

        val deployments = parsed["deployments"] as? List<MutableMap<String, Any>>
        if (deployments != null) {
            var modified = false
            var fileOriginalGi = 0.0
            var fileNewGi = 0.0
            var fileEntriesModified = 0

            for (deployment in deployments) {
                val deploy = deployment["deploy"] as? MutableMap<String, Any>
                if (deploy != null) {
                    val command = deploy["command"] as? List<*>
                    if (command != null && command.size >= 4) {
                        val xmxArg = command[3] as? String
                        if (xmxArg != null && xmxArg.startsWith("-Xmx")) {
                            val memoryValue = parseMemoryValue(xmxArg.substring(4))
                            if (memoryValue != null) {
                                // Convert original value to Gi for counting
                                val originalGi = when (memoryValue.second) {
                                    'g' -> memoryValue.first
                                    'm' -> memoryValue.first / 1024
                                    else -> 0.0
                                }
                                fileOriginalGi += originalGi

                                // Calculate new memory value (1.5x)
                                val newMemoryValue = memoryValue.first * 1.5
                                val unit = memoryValue.second

                                // Convert new value to Gi for counting
                                val newGi = when (unit) {
                                    'g' -> newMemoryValue
                                    'm' -> newMemoryValue / 1024
                                    else -> 0.0
                                }
                                fileNewGi += newGi

                                // Convert to Kubernetes format (Mi/Gi)
                                val k8sUnit = when (unit) {
                                    'g' -> "Gi"
                                    'm' -> "Mi"
                                    else -> unit.toString()
                                }

                                // Create resources structure if not exists
                                if (deploy["resources"] == null) {
                                    deploy["resources"] = mutableMapOf<String, Any>()
                                }

                                val resources = deploy["resources"] as MutableMap<String, Any>

                                // Add requests with original value
                                if (resources["requests"] == null) {
                                    resources["requests"] = mutableMapOf<String, Any>()
                                }
                                val requests = resources["requests"] as MutableMap<String, Any>
                                requests["memory"] = "${memoryValue.first.toInt()}${k8sUnit}"

                                // Add limits with 1.5x value
                                if (resources["limits"] == null) {
                                    resources["limits"] = mutableMapOf<String, Any>()
                                }
                                val limits = resources["limits"] as MutableMap<String, Any>
                                limits["memory"] = "${newMemoryValue.toInt()}${k8sUnit}"

                                modified = true
                                fileEntriesModified++
                            }
                        }
                    }
                }
            }

            if (modified) {
                // Write back to the file with proper formatting
                FileWriter(file).use { writer ->
                    // Use dumpAsMap to maintain proper formatting
                    val output = yaml.dumpAsMap(parsed)
                    writer.write(output)
                }
                println("Updated file: ${file.name} (${fileEntriesModified} entries, ${fileOriginalGi.roundToInt()} Gi → ${fileNewGi.roundToInt()} Gi)")
                return Triple(fileOriginalGi, fileNewGi, fileEntriesModified)
            } else {
                println("No changes made to file: ${file.name}")
                return null
            }
        }
    } catch (e: Exception) {
        println("Error processing file ${file.name}: ${e.message}")
    }
    return null
}

fun parseMemoryValue(memoryStr: String): Pair<Double, Char>? {
    if (memoryStr.isEmpty()) return null

    val unit = memoryStr.last()
    if (unit != 'g' && unit != 'm') return null

    val numberStr = memoryStr.substring(0, memoryStr.length - 1)
    val number = numberStr.toDoubleOrNull() ?: return null

    return Pair(number, unit)
}
