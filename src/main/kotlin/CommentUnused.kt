import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.StringWriter

fun main() {
    val folderPath = "values/dodobet-tr--prd-k8s--pl-sb-helm-values"
    val valuesFilePath = "values/keys_to_comment.txt"
    processYamlFiles(folderPath, valuesFilePath)
}

fun processYamlFiles(folderPath: String, valuesFilePath: String) {
    val valuesFile = File(valuesFilePath)
    val values = valuesFile.readLines().filter { it.isNotBlank() }.toSet()

    val folder = File(folderPath)
    if (!folder.exists() || !folder.isDirectory) {
        println("Invalid folder path: $folderPath")
        return
    }

    folder.listFiles { file -> file.extension == "yaml" || file.extension == "yml" }?.forEach { yamlFile ->
        processYamlFile(yamlFile, values)
    }
}

fun processYamlFile(yamlFile: File, values: Set<String>) {
    val options = DumperOptions().apply {
        defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
        indent = 2
        isPrettyFlow = true
    }

    val yaml = Yaml(options)
    val yamlContent = yamlFile.readText()
    val yamlData = yaml.load<MutableMap<String, Any>>(yamlContent)

    processDeploymentSection(yamlData, values)
    processJobsSection(yamlData, values)

    val writer = StringWriter()
    yaml.dump(yamlData, writer)
    yamlFile.writeText(writer.toString())
}

fun processDeploymentSection(yamlData: Map<String, Any>, values: Set<String>) {
    val deployment = (yamlData["deployments"] as? List<*>)?.firstOrNull() as? MutableMap<*, *> ?: return
    val deploy = deployment["deploy"] as? MutableMap<*, *> ?: return

    (deploy["env_business"] as? MutableMap<*, *>)?.let { envBusiness ->
        commentUnused(envBusiness as MutableMap<String, Any>, values)
    }

    (deploy["env_system"] as? MutableMap<*, *>)?.let { envSystem ->
        commentUnused(envSystem as MutableMap<String, Any>, values)
    }
}

fun processJobsSection(yamlData: Map<String, Any>, values: Set<String>) {
    val jobs = (yamlData["jobs"] as? List<*>)?.firstOrNull() as? MutableMap<*, *> ?: return

    (jobs["env_business"] as? MutableMap<*, *>)?.let { envBusiness ->
        commentUnused(envBusiness as MutableMap<String, Any>, values)
    }

    (jobs["env_system"] as? MutableMap<*, *>)?.let { envSystem ->
        commentUnused(envSystem as MutableMap<String, Any>, values)
    }
}

fun commentUnused(envMap: MutableMap<String, Any>, values: Set<String>) {
    val keysToComment = envMap.keys.filter { it in values }

    keysToComment.forEach { key ->
        val value = envMap.remove(key)
        // Create a new key with # prefix
        val commentedKey = "#$key"
        envMap[commentedKey] = value!!
    }
}