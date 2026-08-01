package de.lb.mp3tag.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
data class RecentFolder(
    val label: String,
    val treeUri: String,
    val documentId: String,
)

class AppSettings(private val context: Context) {

    val recentFolders: Flow<List<RecentFolder>> =
        context.dataStore.data.map { decode(it[RECENT_FOLDERS]) }

    suspend fun addRecentFolder(folder: RecentFolder) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[RECENT_FOLDERS])
            val updated = (
                listOf(folder) +
                    current.filterNot { it.treeUri == folder.treeUri && it.documentId == folder.documentId }
                ).take(MAX_RECENTS)
            prefs[RECENT_FOLDERS] = json.encodeToString(serializer, updated)
        }
    }

    /** Drops recents that belong to a no-longer-granted tree. */
    suspend fun retainRecentsForTrees(grantedTreeUris: Set<String>) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[RECENT_FOLDERS])
            prefs[RECENT_FOLDERS] =
                json.encodeToString(serializer, current.filter { it.treeUri in grantedTreeUris })
        }
    }

    val discogsToken: Flow<String> =
        context.dataStore.data.map { it[DISCOGS_TOKEN] ?: "" }

    suspend fun discogsTokenNow(): String = discogsToken.first()

    suspend fun setDiscogsToken(token: String) {
        context.dataStore.edit { it[DISCOGS_TOKEN] = token.trim() }
    }

    val exportTemplate: Flow<String> =
        context.dataStore.data.map { it[EXPORT_TEMPLATE] ?: "" }

    suspend fun setExportTemplate(template: String) {
        context.dataStore.edit { it[EXPORT_TEMPLATE] = template }
    }

    private fun decode(raw: String?): List<RecentFolder> =
        raw?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    private companion object {
        val RECENT_FOLDERS = stringPreferencesKey("recent_folders")
        val DISCOGS_TOKEN = stringPreferencesKey("discogs_token")
        val EXPORT_TEMPLATE = stringPreferencesKey("export_template")
        const val MAX_RECENTS = 10
        val json = Json { ignoreUnknownKeys = true }
        val serializer = ListSerializer(RecentFolder.serializer())
    }
}
