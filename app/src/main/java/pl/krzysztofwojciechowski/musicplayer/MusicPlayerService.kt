package pl.krzysztofwojciechowski.musicplayer

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import java.io.File

class MusicPlayerService : Service() {
    var mediaPlayer = MediaPlayer()
    var files: List<File> = listOf()
    var paths: List<String> = listOf()
    var nowPlaying: File? = null
    val updateHandler = Handler()
    var updateRunnable: Runnable? = null
    var updateMetadata: ((File) -> Unit)? = null
    private val musicBind = MusicBinder()

    override fun onCreate() {
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return musicBind
    }

    override fun onUnbind(intent: Intent?): Boolean {
        mediaPlayer.reset()
        return false
    }

    fun startPlaying(file: File) {
        mediaPlayer.stop()
        mediaPlayer.reset()
        nowPlaying = file
        mediaPlayer.setDataSource(file.absolutePath)
        mediaPlayer.prepare()
        updateMetadata?.invoke(file)
        playPause()
    }

    fun playPause() {
        if (nowPlaying == null) return
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            if (updateRunnable != null) updateHandler.removeCallbacks(updateRunnable)
        } else {
            mediaPlayer.start()
            if (updateRunnable != null) updateHandler.postDelayed(updateRunnable, 0)
        }
    }

    fun prevNext(shift: Int) {
        val position = files.indexOf(nowPlaying)
        var newPos = (position + shift) % files.size
        if (position == -1 || newPos == -1) {
            newPos = if (shift == -1) files.size - 1 else 0
        }
        startPlaying(files[newPos])
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }
}
