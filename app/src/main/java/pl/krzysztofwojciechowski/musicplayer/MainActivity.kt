package pl.krzysztofwojciechowski.musicplayer

import android.Manifest
import android.app.AlertDialog
import android.content.ServiceConnection
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import android.widget.LinearLayout
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import pl.krzysztofwojciechowski.musicplayer.MusicPlayerService.MusicBinder
import android.os.IBinder
import android.widget.SeekBar


class MainActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var viewAdapter: FileListAdapter
    private lateinit var viewManager: RecyclerView.LayoutManager
    private var files: List<File> = listOf()
    private lateinit var service: MusicPlayerService
    private var musicBound = false
    private var playIntent: Intent? = null
    private val musicConnection = object : ServiceConnection {

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicBinder
            this@MainActivity.service = binder.getService()
            bindToService()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            unbindService()
        }
    }


    private val updateRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            setPlayPauseIcon()
            if (service.mediaPlayer.isPlaying) {
                service.updateHandler.postDelayed(this, 100)
            } else {
                service.prevNext(1)
            }
        }
    }

    fun updateProgress() {
        mp_seek.max = service.mediaPlayer.duration
        mp_duration.text = formatTime(service.mediaPlayer.duration)
        mp_seek.progress = service.mediaPlayer.currentPosition
        mp_progress.text = formatTime(service.mediaPlayer.currentPosition)
    }

    fun bindToService() {
        service.files = files
        service.updateRunnable = updateRunnable
        service.updateMetadata = this::updateMetadata
        service.stateChangeCallback = this::stateChangeCallback
        musicBound = true

        if (service.nowPlaying != null) {
            updateMetadata(service.nowPlaying!!)
            updateProgress()
            service.startUpdatingUI()
        }
        setPlayPauseIcon()
    }

    fun unbindService() {
        service.updateRunnable = null
        service.updateMetadata = null
        service.stateChangeCallback = null
        musicBound = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        service = MusicPlayerService()
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

        mp_playpause.setOnClickListener {
            service.playPause()
            setPlayPauseIcon()
        }
        mp_previous.setOnClickListener { service.prevNext(-1) }
        mp_next.setOnClickListener { service.prevNext(1) }

        mp_seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {

                    service.mediaPlayer.seekTo(progress)
                    if (!service.mediaPlayer.isPlaying) service.playPause()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { /* No interactivity support */ }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { /* No interactivity support */ }
        })
    }

    override fun onStart() {
        super.onStart()
        if (playIntent == null) {
            playIntent = Intent(this, MusicPlayerService::class.java)
            bindService(playIntent!!, musicConnection, Context.BIND_AUTO_CREATE)
            startService(playIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!musicBound) bindToService()
    }

    override fun onDestroy() {
        super.onDestroy()
        service.handleAppClosed()
        unbindService()
    }

    override fun onStop() {
        super.onStop()
        service.handleAppClosed()
        unbindService()
    }

    private fun askForStoragePermission() {
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
        service.files = files
    }

    private fun startPlaying(file: File) {
        service.startPlaying(file)
        updateMetadata(file)
        setPlayPauseIcon()
    }

    private fun updateMetadata(file: File) {
        val meta = Metadata(file)
        mp_title.text = meta.title
        mp_artist.text = meta.artist
        mp_seek.progress = 0
        mp_seek.max = meta.duration
        mp_duration.text = formatTime(meta.duration)
        mp_progress.text = formatTime(0)
    }

    private fun setPlayPauseIcon() {
        if (service.mediaPlayer.isPlaying) mp_playpause.setImageResource(R.drawable.ic_pause)
        else mp_playpause.setImageResource(R.drawable.ic_play)
    }

    private fun stateChangeCallback(type: StateChangeType) {
        if (type == StateChangeType.STOP) {
            mp_artist.text = getText(R.string.mp_artist_placeholder)
            mp_title.text = getText(R.string.mp_prompt)
            mp_seek.progress = 0
            mp_seek.max = 0
            mp_duration.text = formatTime(0)
            mp_progress.text = formatTime(0)
            setPlayPauseIcon()
        } else if (type == StateChangeType.PLAYPAUSE) {
            setPlayPauseIcon()
        }
    }

    private fun formatTime(timeMs: Int): String {
        val time = timeMs / 1000
        val minutes = time / 60
        val seconds = time % 60
        return getString(R.string.time_format, minutes, seconds)
    }
}
