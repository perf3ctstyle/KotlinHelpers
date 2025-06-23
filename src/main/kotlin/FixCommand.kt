package org.example

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.DumperOptions
import java.io.File
import java.io.FileWriter

fun modifyYamlFilesWithEnv(yamlDirPath: String, envFilePath: String) {
    // Configure YAML dumper options to preserve formatting
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        indent = 2
        isPrettyFlow = true
    }
    val yaml = Yaml(options)

    // Read the ENV file
    val envVars = readEnvFile(envFilePath)

    val directory = File(yamlDirPath)
    if (!directory.exists() || !directory.isDirectory) {
        throw IllegalArgumentException("The provided YAML directory path is not valid")
    }

    directory.listFiles { file -> file.extension.equals("yaml", ignoreCase = true) }?.forEach { file ->
        try {
            // Read the YAML content
            val yamlContent = file.inputStream().use {
                yaml.load<MutableMap<String, Any>>(it) ?: return@forEach
            }

            // Process the YAML structure
            processYamlContent(yamlContent, envVars)

            // Save the modified content back to the file
            FileWriter(file).use { writer ->
                yaml.dump(yamlContent, writer)
            }
            println("Modified file: ${file.name}")
        } catch (e: Exception) {
            println("Error processing file ${file.name}: ${e.message}")
        }
    }
}

private fun readEnvFile(envFilePath: String): Map<String, String> {
    val envFile = File(envFilePath)
    if (!envFile.exists() || !envFile.isFile) {
        throw IllegalArgumentException("The provided ENV file path is not valid")
    }

    return envFile.readLines()
        .filter { it.contains('=') && !it.startsWith("#") }
        .associate {
            val parts = it.split('=', limit = 2)
            parts[0].trim() to parts[1].trim().removeSurrounding("\"")
        }
}

private fun processYamlContent(yamlContent: MutableMap<String, Any>, envVars: Map<String, String>) {
    val deployments = yamlContent["deployments"] as? List<MutableMap<String, Any>> ?: return

    if (deployments.isEmpty()) return

    val firstDeployment = deployments[0]

    val deploy = firstDeployment["deploy"] as? MutableMap<String, Any> ?: return
    if (deploy.isEmpty()) return

    val command = deploy["command"] as? MutableList<Any> ?: return
    if (command.isEmpty()) return

    val firstCommand = command[0] as? String ?: return
    val envKey = firstCommand.removePrefix("\${").removeSuffix("}")

    envVars[envKey]?.let { envValue ->
        // Split the command string and replace the original command array
        val newCommand = envValue.split("\\s+".toRegex()).filter { it.isNotBlank() }
        deploy["command"] = newCommand
    }
}

fun main() {
    val directoryPath = "./values/dodobet-tr--prd-k8s--pl-sb-helm-values"
    val envFilePath = "./values/backup/DODOBET_TR__PRD__PL_SB_ENV.env"
    modifyYamlFilesWithEnv(directoryPath, envFilePath)
}