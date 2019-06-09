package pl.krzysztofwojciechowski.musicplayer

const val MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 42
const val DATA_PATH = "MusicPlayerMP3"
const val CHANNEL_ID = "music_playback"
const val INTENT_PLAYPAUSE = "pl.krzysztofwojciechowski.musicplayer.playpause"
const val INTENT_PREVIOUS = "pl.krzysztofwojciechowski.musicplayer.previous"
const val INTENT_NEXT = "pl.krzysztofwojciechowski.musicplayer.next"
const val INTENT_STOP = "pl.krzysztofwojciechowski.musicplayer.stop"
const val NOTIFICATION_ID = 0

enum class StateChangeType { PLAYPAUSE, STOP }
//const val SC_PLAYPAUSE = 1
//const val SC_STOP = 2