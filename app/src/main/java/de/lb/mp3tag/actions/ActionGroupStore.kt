package de.lb.mp3tag.actions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Persists action groups as one JSON file each under filesDir/actions. */
class ActionGroupStore(private val dir: File) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    suspend fun list(): List<ActionGroup> = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<ActionGroup>(file.readText()) }.getOrNull()
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    suspend fun save(group: ActionGroup): Unit = withContext(Dispatchers.IO) {
        dir.mkdirs()
        fileFor(group.name).writeText(json.encodeToString(ActionGroup.serializer(), group))
    }

    suspend fun delete(name: String): Unit = withContext(Dispatchers.IO) {
        fileFor(name).delete()
    }

    private fun fileFor(name: String): File {
        val safe = name.map { if (it.isLetterOrDigit() || it in "-_ ") it else '_' }
            .joinToString("")
            .trim()
            .ifEmpty { "group" }
        return File(dir, "$safe.json")
    }
}
