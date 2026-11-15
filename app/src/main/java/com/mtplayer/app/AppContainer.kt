package com.mtplayer.app

import android.content.Context
import com.mtplayer.app.core.CrashLog
import com.mtplayer.app.core.DeviceClass
import com.mtplayer.app.data.db.LegacyImporter
import com.mtplayer.app.data.db.MtDatabase
import com.mtplayer.app.data.media.LibraryRepository
import com.mtplayer.app.data.media.MediaStoreSource
import com.mtplayer.app.data.prefs.SettingsStore
import com.mtplayer.app.data.repo.LyricsRepository
import com.mtplayer.app.data.repo.UserDataRepository
import com.mtplayer.app.playback.AudioEffects
import com.mtplayer.app.playback.PlayerConnection
import com.mtplayer.app.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual dependency container (ADR-002). One instance per process, created by [MTApp]. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val deviceClass: DeviceClass by lazy { DeviceClass.detect(app) }
    val crashLog: CrashLog by lazy { CrashLog(app) }
    val db: MtDatabase by lazy { MtDatabase.build(app) }
    val settings: SettingsStore by lazy { SettingsStore(app) }
    val library: LibraryRepository by lazy {
        LibraryRepository(app, MediaStoreSource(app.contentResolver), settings, db.hiddenFolders(), appScope)
    }
    val userData: UserDataRepository by lazy { UserDataRepository(db) }
    val lyrics: LyricsRepository by lazy { LyricsRepository(app, db.lyricsLinks()) }
    val legacyImporter: LegacyImporter by lazy { LegacyImporter(app, db, settings) }
    val audioEffects: AudioEffects by lazy { AudioEffects() }
    val player: PlayerConnection by lazy { PlayerConnection(app) }
    val updates: UpdateManager by lazy { UpdateManager(app, settings, appScope) }
}
