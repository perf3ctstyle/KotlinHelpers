package org.example

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter

fun modifyYamlFiles(directoryPath: String) {
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK  // This is the key setting
        indent = 2
        isPrettyFlow = true
    }
    val yaml = Yaml(options)
    val directory = File(directoryPath)

    // Check if the directory exists
    if (!directory.exists() || !directory.isDirectory) {
        throw IllegalArgumentException("The provided path is not a valid directory")
    }

    // Process each YAML file in the directory
    directory.listFiles { file -> file.extension.equals("yaml", ignoreCase = true) }?.forEach { file ->
        try {
            // Read the YAML content
            val yamlContent = file.inputStream().use { yaml.load<Map<String, Any>>(it) }

            // Check if 'jobs' key exists and has elements
            if (yamlContent.containsKey("jobs")) {
                val jobs = yamlContent["jobs"] as? List<MutableMap<String, Any>> ?: return@forEach

                if (jobs.isNotEmpty()) {
                    // Get the first job and add new keys
                    val firstJob = jobs[0]
                    val firstJobName = firstJob["name"] as String
                    val endOfApplicationName = firstJobName.lastIndexOf('-')
                    val applicationName = firstJobName.substring(0, endOfApplicationName)
                    firstJob["application"] = applicationName
                    firstJob["jobType"] = "migration"

                    // Save the modified content back to the file
                    FileWriter(file).use { writer ->
                        yaml.dump(yamlContent, writer)
                    }
                    println("Modified file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            println("Error processing file ${file.name}: ${e.message}")
        }
    }
}

fun main() {
    val directoryPath = "./values/dodobet-tr--prd-k8s--pl-sb-helm-values"
    modifyYamlFiles(directoryPath)
}