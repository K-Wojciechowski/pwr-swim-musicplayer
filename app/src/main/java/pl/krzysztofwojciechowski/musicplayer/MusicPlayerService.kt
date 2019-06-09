package pl.krzysztofwojciechowski.musicplayer

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.File


class MusicPlayerService : Service() {
    var mediaPlayer = MediaPlayer()
    var files: List<File> = listOf()
    var nowPlaying: File? = null
    val updateHandler = Handler()
    var updateRunnable: Runnable? = null
    var updateMetadata: ((File) -> Unit)? = null
    var stateChangeCallback: ((StateChangeType) -> Unit)? = null
    var notificationManager: NotificationManager? = null
    private val musicBind = MusicBinder()

    override fun onCreate() {
        mediaPlayer.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return musicBind
    }

    override fun onUnbind(intent: Intent?): Boolean {
//        mediaPlayer.reset()
        return false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == INTENT_STOP) {
            stopPlaying()
        } else if (intent?.action == INTENT_PLAYPAUSE) {
            playPause()
        } else if (intent?.action == INTENT_PREVIOUS) {
            prevNext(-1)
        } else if (intent?.action == INTENT_NEXT) {
            prevNext(1)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlaying()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopPlaying()
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

    fun stopPlaying() {
        stopUpdatingUI()
        mediaPlayer.stop()
        nowPlaying = null
        stateChangeCallback?.invoke(StateChangeType.STOP)
        notificationManager?.cancelAll()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel() {
        notificationManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val id = CHANNEL_ID
        // The user-visible name of the channel.
        val name = getString(R.string.channel_name)
        // The user-visible description of the channel.
        val description = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_LOW
        val mChannel = NotificationChannel(id, name, importance)
        // Configure the notification channel.
        mChannel.description = description
        mChannel.setShowBadge(false)
        mChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager!!.createNotificationChannel(mChannel)
    }

    fun createNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel()
        } else {
            notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)

        val meta = Metadata(nowPlaying!!)
        val stopPendingIntent = buildPendingIntent(INTENT_STOP)

        val openIntent = Intent(applicationContext, MainActivity::class.java)
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        notificationBuilder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowCancelButton(true)
                .setCancelButtonIntent(stopPendingIntent)
                .setShowActionsInCompactView(1, 3)
        )
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .setContentTitle(meta.title)
            .setContentText(meta.artist)
            .setShowWhen(false)
            .setDeleteIntent(stopPendingIntent)
            .addAction(R.drawable.ic_previous, "Previous", buildPendingIntent(INTENT_PREVIOUS))
        if (mediaPlayer.isPlaying) {
            notificationBuilder
                .setSmallIcon(R.drawable.ic_play)
                .addAction(R.drawable.ic_pause, "Pause", buildPendingIntent(INTENT_PLAYPAUSE))
        } else {
            notificationBuilder
                .setSmallIcon(R.drawable.ic_pause)
                .addAction(R.drawable.ic_play, "Play", buildPendingIntent(INTENT_PLAYPAUSE))
        }
        notificationBuilder
            .addAction(R.drawable.ic_next, "Next", buildPendingIntent(INTENT_NEXT))
            .addAction(R.drawable.ic_stop, "Stop", buildPendingIntent(INTENT_STOP))

        notificationManager!!.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun buildPendingIntent(intent: String): PendingIntent {
        return PendingIntent.getService(
            this, 0, Intent(intent), PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun playPause() {
        if (nowPlaying == null) return
        if (mediaPlayer.isPlaying) {
            stopUpdatingUI()
            mediaPlayer.pause()
            stateChangeCallback?.invoke(StateChangeType.PLAYPAUSE)
            createNotification()
        } else {
            mediaPlayer.start()
            createNotification()
            if (updateRunnable != null) startUpdatingUI()
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

    fun handleAppClosed() {
        stopUpdatingUI()
        if (!mediaPlayer.isPlaying) stopPlaying()
    }

    fun startUpdatingUI() {
        if (updateRunnable != null) updateHandler.postDelayed(updateRunnable, 0)
    }

    fun stopUpdatingUI() {
        if (updateRunnable != null) updateHandler.removeCallbacks(updateRunnable)
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }
}
