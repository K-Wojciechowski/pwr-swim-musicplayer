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
    var shuffle = false
    var nowPlaying: File? = null
    val updateHandler = Handler()
    var guiUpdateRunnable: Runnable? = null
    var updateMetadata: ((File) -> Unit)? = null
    var stateChangeCallback: ((StateChangeType) -> Unit)? = null
    private var notificationManager: NotificationManager? = null
    private val musicBind = MusicBinder()

    // GUI updates and previous/pause handling
    private val updateRunnable = object : Runnable {
        override fun run() {
            guiUpdateRunnable?.run()
            if (mediaPlayer.isPlaying) {
                updateHandler.postDelayed(this, 100)
            } else {
                prevNext(1)
            }
        }
    }

    // Service management

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
        when {
            intent?.action == INTENT_STOP -> stopPlaying()
            intent?.action == INTENT_PLAYPAUSE -> playPause()
            intent?.action == INTENT_PREVIOUS -> prevNext(-1)
            intent?.action == INTENT_NEXT -> prevNext(1)
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

    // Interactions and music handling

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
        stopUpdating()
        mediaPlayer.stop()
        nowPlaying = null
        stateChangeCallback?.invoke(StateChangeType.STOP)
        notificationManager?.cancelAll()
    }

    fun playPause() {
        if (nowPlaying == null && files.isNotEmpty()) {
            startPlaying(if (shuffle) getRandomFile() else files[0])
            return
        }
        if (mediaPlayer.isPlaying) {
            stopUpdating()
            mediaPlayer.pause()
            stateChangeCallback?.invoke(StateChangeType.PLAYPAUSE)
            createNotification()
        } else {
            mediaPlayer.start()
            createNotification()
            startUpdating()
        }
    }

    fun prevNext(shift: Int) {
        if (shuffle) startPlaying(getRandomFile())
        val position = files.indexOf(nowPlaying)
        var newPos = (position + shift) % files.size
        if (position == -1 || newPos == -1) {
            newPos = if (shift == -1) files.size - 1 else 0
        }
        startPlaying(files[newPos])
    }

    fun getRandomFile(): File = files.random()

    // Notifications

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

    private fun createNotification() {
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

    // Timer helpers
    fun startUpdating() {
        updateHandler.postDelayed(updateRunnable, 0)
    }

    private fun stopUpdating() {
        updateHandler.removeCallbacks(updateRunnable)
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }
}
