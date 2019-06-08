package pl.krzysztofwojciechowski.musicplayer

import android.media.MediaMetadataRetriever

import java.io.File

class Metadata(file: File) {
    var title: String = ""
    var artist: String = ""
    var duration: Int = 0
    var hasTitle: Boolean = false

    init {
        val meta = MediaMetadataRetriever()
        meta.setDataSource(file.absolutePath)
        val titleN: String? = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        if (titleN == null || titleN.isBlank()) {
            hasTitle = false
            title = file.name
        } else {
            hasTitle = true
            title = titleN
        }
        val artistN: String? = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        artist = if (artistN == null || artistN.isBlank()) "" else artistN
        duration = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toIntOrNull() ?: 0
    }
}
