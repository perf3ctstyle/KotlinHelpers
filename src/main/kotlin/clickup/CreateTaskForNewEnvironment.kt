package clickup

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/* Constants */
val USER_ID = 94689332

/* Variables */
val TASK_LIST_ID = "901518891745"
val ENVIRONMENT_CODE = "BETORSPIN_GLOBAL__PRD_K8S__PL_SB"
val PLAYER_UI_URL = "https://betorspin-global.com/"
val ADMIN_UI_URL = "https://admin-betorspin-global--prd-k8s--pl-sb.api4jsv.com/"

fun createTaskForNewEnvironment() {
    val apiToken = getApiToken()
    val mainTaskRequestBody = constructMainTask(ENVIRONMENT_CODE, PLAYER_UI_URL, ADMIN_UI_URL)
    val mainTaskResponse = sendHttpRequest(apiToken, mainTaskRequestBody)
    val mainTaskId = extractTaskId(mainTaskResponse)
    val subtaskRequestBodies = constructSubtasks(mainTaskId)
    subtaskRequestBodies.forEach { sendHttpRequest(apiToken, it) }
}

fun getApiToken(): String {
    val secretsMap = readSecretFromFile()
    return secretsMap["apiToken"] ?: throw IllegalStateException("apiToken not found")
}

fun constructMainTask(environmentCode: String, puiUrl: String, adminUrl: String): String {
    val name = constructMainTaskName(environmentCode)
    val markdownDescription = constructMainTaskMarkdownDescription(environmentCode, puiUrl, adminUrl)
    val sprintPoints = 3
    val assignees = constructAssignees()
    val tags = listOf("\"drop-in\"", "\"sprint goal\"")

    return """
        {
            "name": "$name",
            "markdown_content": "$markdownDescription",
            "assignees": $assignees,
            "points": $sprintPoints,
            "tags": $tags
        }
    """.trimIndent()
}

fun constructMainTaskName(environmentCode: String): String {
    return "DevOps - New Deploy Environment - $environmentCode - Setup"
}

fun constructMainTaskMarkdownDescription(environmentCode: String, puiUrl: String, adminUrl: String): String {
    val rawDescription = """
        Before:
        * No $environmentCode environment
        
        After:
        * $environmentCode is available for deploy in DM
        * $environmentCode player UI is accessible via $puiUrl
        * $environmentCode admin UI is accessible via $adminUrl
    """.trimIndent()
    val descriptionWithEscapedNewLines = rawDescription.replace("\n", "\\n")
    return descriptionWithEscapedNewLines
}

val constructAssignees = { listOf(USER_ID) }

fun constructSubtasks(parentTaskId: String): List<String> {
    val subtaskBodies = mutableListOf<String>()
    val subtaskNames = getSubtaskNames(ENVIRONMENT_CODE)
    for (subtaskName in subtaskNames) {
        val assignees = constructAssignees()
        val body = """
            {
                "name": "$subtaskName",
                "parent": "$parentTaskId",
                "assignees": $assignees
            }
        """.trimIndent()
        subtaskBodies.add(body)
    }
    return subtaskBodies
}

fun getSubtaskNames(environmentCode: String): List<String> {
    return listOf(
        "Install Ubuntu 24.04 on a dedicated server via Kotlin script",
        "Install PostgreSQL 18 on the dedicated server via Kotlin script",
        "Add newly configured server to Inventory",
        "Run the Kotlin script to sync UFW rules",
        "Set up $environmentCode env code in mono repository (with merge request)",
        "Set up $environmentCode env code in mono-devops repository (with merge request)",
        "Upload GitLab variables for $environmentCode in mono-devops repository",
        "Deploy delivery-manager from master",
        "Deploy $environmentCode from DM",
        "Deploy auth-service from current release",
        "Add system-services in mono-manifests repository (without merge request)",
        "Configure DNS in Cloudflare for Player UI and Admin UI",
        "Enable environment in Kubernetes configuration"
    )
}

fun readSecretFromFile(): Map<String, String> {
    val secretsMap = mutableMapOf<String, String>()
    val inputStream = object {}.javaClass.getResourceAsStream("/secrets")
        ?: throw IllegalStateException("secrets file not found in resources")
    
    inputStream.bufferedReader().useLines { lines ->
        lines.forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                val parts = trimmedLine.split("=", limit = 2)
                if (parts.size == 2) {
                    secretsMap[parts[0].trim()] = parts[1].trim()
                }
            }
        }
    }
    
    return secretsMap
}

fun sendHttpRequest(apiToken: String, requestBody: String): Response {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://api.clickup.com/api/v2/list/$TASK_LIST_ID/task")
        .post(requestBody.toRequestBody())
        .addHeader("accept", "application/json")
        .addHeader("content-type", "application/json")
        .addHeader("Authorization", apiToken)
        .build()
    val response = client.newCall(request).execute()
    if (!response.isSuccessful) {
        throw Exception("Request to create task failed. Unexpected code\n$response")
    }
    return response
}

fun extractTaskId(response: Response): String {
    val responseBody = response.body?.string() 
        ?: throw IllegalStateException("Response body is null")
    val gson = Gson()
    val jsonObject = gson.fromJson(responseBody, Map::class.java)
    return jsonObject["id"] as? String 
        ?: throw IllegalStateException("Task ID not found in response")
}
