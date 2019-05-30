package pl.krzysztofwojciechowski.musicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.text.TextUtils
import android.widget.SeekBar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import android.widget.LinearLayout


class MainActivity : AppCompatActivity() {
    var mediaPlayer = MediaPlayer()
    var files: List<File> = listOf()
    var nowPlaying: File? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: FileListAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager

    private val updateHandler = Handler()
    private val updateRunnable = object : Runnable {
        override fun run() {
            mp_seek.progress = mediaPlayer.currentPosition
            mp_progress.text = formatTime(mediaPlayer.currentPosition)
            setPlayPauseIcon()
            if (mediaPlayer.isPlaying) {
                updateHandler.postDelayed(this, 100)
            } else {
                prevNext(1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mp_title.isSelected = true
        mp_artist.isSelected = true

        mp_title.post { mp_title.layoutParams = LinearLayout.LayoutParams(mp_title.width, mp_title.height) }
        mp_artist.post { mp_title.layoutParams = LinearLayout.LayoutParams(mp_artist.width, mp_artist.height) }

        viewManager = LinearLayoutManager(this)
        viewAdapter = FileListAdapter(this::startPlaying)

        recyclerView = findViewById<RecyclerView>(R.id.mp_list).apply {
            setHasFixedSize(false)

            layoutManager = viewManager
            adapter = viewAdapter
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            askForStoragePermission()
        } else {
            showFileList()
        }

        mp_playpause.setOnClickListener { playPause() }
        mp_previous.setOnClickListener { prevNext(-1) }
        mp_next.setOnClickListener { prevNext(1) }

        mp_seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                    if (!mediaPlayer.isPlaying) playPause()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.reset()
    }

    fun askForStoragePermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            // If request is cancelled, the result arrays are empty.
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Thread.sleep(500)
                showFileList()
            } else {
                askForStoragePermission()
            }
            return
        }
    }

    private fun showFileList() {
        val baseDir = File(Environment.getExternalStorageDirectory(), DATA_PATH)
        val fileArray = baseDir.listFiles { _: File, name: String -> name.endsWith(".mp3", true)}
        if (fileArray == null || fileArray.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.mp_no_files_title))
                .setMessage(getString(R.string.mp_no_files_text, DATA_PATH))
                .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
            files = listOf()
        } else {
            files = fileArray.toList()
        }
        viewAdapter.loadItems(files)
    }

    private fun startPlaying(file: File) {
        mediaPlayer.stop()
        mediaPlayer.reset()
        nowPlaying = file
        val path = file.absolutePath
        val meta = Metadata(file)
        mp_title.text = meta.title
        mp_artist.text = meta.artist
        mediaPlayer.setDataSource(path)
        mediaPlayer.prepare()
        mp_seek.progress = 0
        mp_seek.max = mediaPlayer.duration
        mp_duration.text = formatTime(mediaPlayer.duration)
        playPause()
    }



    private fun playPause() {
        if (nowPlaying == null) return
        if (mediaPlayer.isPlaying) {
            mp_artist.ellipsize = TextUtils.TruncateAt.MARQUEE
            mp_title.ellipsize = TextUtils.TruncateAt.MARQUEE
            mediaPlayer.pause()
            updateHandler.removeCallbacks(updateRunnable)
        } else {
            mp_artist.ellipsize = TextUtils.TruncateAt.END
            mp_title.ellipsize = TextUtils.TruncateAt.END
            mp_artist.ellipsize = TextUtils.TruncateAt.MARQUEE
            mp_title.ellipsize = TextUtils.TruncateAt.MARQUEE
            mediaPlayer.start()
            updateHandler.postDelayed(updateRunnable, 0)
        }
        setPlayPauseIcon()
    }

    private fun setPlayPauseIcon() {
        if (mediaPlayer.isPlaying) mp_playpause.setImageResource(R.drawable.ic_pause)
        else mp_playpause.setImageResource(R.drawable.ic_play)
    }

    private fun prevNext(shift: Int) {
        val position = files.indexOf(nowPlaying)
        var newPos = (position + shift) % files.size
        if (position == -1) {
            if (shift == -1) newPos = files.size - 1
            else newPos = 0
        }
        startPlaying(files[newPos])
    }

    private fun formatTime(timeMs: Int): String {
        val time = timeMs / 1000
        val minutes = time / 60
        val seconds = time % 60
        return getString(R.string.time_format, minutes, seconds)
    }
}
