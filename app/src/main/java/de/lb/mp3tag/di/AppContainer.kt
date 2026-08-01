package de.lb.mp3tag.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.lb.mp3tag.actions.ActionGroupStore
import de.lb.mp3tag.online.DiscogsClient
import de.lb.mp3tag.online.MusicBrainzClient
import de.lb.mp3tag.online.TagSourceClient
import de.lb.mp3tag.online.TagSourceKind
import de.lb.mp3tag.settings.AppSettings
import de.lb.mp3tag.storage.FileScanner
import de.lb.mp3tag.storage.PlaylistGenerator
import de.lb.mp3tag.storage.SafStorage
import okhttp3.OkHttpClient
import de.lb.mp3tag.tag.SavePipeline
import de.lb.mp3tag.tag.TagIo
import de.lb.mp3tag.tag.TagRepository
import de.lb.mp3tag.tag.UndoStore
import de.lb.mp3tag.ui.session.SessionViewModel
import java.io.File

class AppContainer(val application: Application) {

    val settings = AppSettings(application)
    val storage = SafStorage(application.contentResolver)
    val tagIo = TagIo(application.contentResolver)
    val tagRepository = TagRepository(tagIo, storage)
    val fileScanner = FileScanner(storage, tagRepository)
    val savePipeline = SavePipeline(tagIo, storage, tagRepository)
    val undoStore = UndoStore(File(application.cacheDir, "undo"), tagIo, storage)
    val actionGroupStore = ActionGroupStore(File(application.filesDir, "actions"))
    val playlistGenerator = PlaylistGenerator(storage)

    private val httpClient = OkHttpClient()
    val tagSources: Map<TagSourceKind, TagSourceClient> = mapOf(
        TagSourceKind.MUSICBRAINZ to MusicBrainzClient(httpClient),
        TagSourceKind.DISCOGS to DiscogsClient(httpClient) { settings.discogsTokenNow() },
    )

    val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
            modelClass.isAssignableFrom(SessionViewModel::class.java) ->
                SessionViewModel(
                    fileScanner, savePipeline, storage, tagIo,
                    tagRepository, undoStore, actionGroupStore,
                    tagSources, playlistGenerator,
                ) as T
            else -> throw IllegalArgumentException("Unknown ViewModel: $modelClass")
        }
    }
}
