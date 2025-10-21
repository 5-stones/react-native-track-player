package com.doublesymmetry.trackplayer

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import com.doublesymmetry.trackplayer.model.AppKilledPlaybackBehavior
import com.doublesymmetry.trackplayer.model.TrackPlayerOptions
import com.doublesymmetry.trackplayer.option.PlayerCapability
import com.facebook.react.bridge.Arguments
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.exitProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(UnstableApi::class)
@MainThread
class TrackPlayerService : MediaLibraryService() {
  lateinit var player: TrackPlayer
  private val binder = LocalBinder()
  private val scope = MainScope()
  private var module = CompletableDeferred<TrackPlayerModule>()
  private lateinit var mediaSession: MediaLibrarySession
  private var sessionCommands: SessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
  private var playerCommands: Player.Commands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
  private var customLayout: List<CommandButton> = listOf()

  // Headless service binding
  private val headlessConnection: ServiceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(className: ComponentName, service: IBinder) {}

      override fun onServiceDisconnected(className: ComponentName) {}
    }

  private val pendingGetItemRequests = ConcurrentHashMap<String, SettableFuture<MediaItem?>>()
  private val pendingGetChildrenRequests =
    ConcurrentHashMap<String, SettableFuture<List<MediaItem>>>()
  private val pendingSearchRequests = ConcurrentHashMap<String, SettableFuture<List<MediaItem>>>()
  private var mediaItemById: MutableMap<String, MediaItem> = mutableMapOf()

  @SuppressLint("WakelockTimeout")
  fun acquireWakeLock() {
    if (wakeLock?.isHeld == true) return
    wakeLock =
      (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TrackPlayerService::class.java.canonicalName)
        .apply {
          setReferenceCounted(false)
          acquire()
        }
  }

  fun abandonWakeLock() {
    wakeLock?.release()
  }

  override fun onCreate() {
    super.onCreate()

    if (BuildConfig.DEBUG) {
      Timber.Forest.plant(
        object : Timber.DebugTree() {
          override fun createStackElementTag(element: StackTraceElement): String? {
            return "${element.className.substringAfterLast('.')}:${element.methodName}"
          }
        }
      )
    } else {
      Timber.Forest.plant(
        object : Timber.Tree() {
          override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= Log.WARN) {
              Log.println(priority, tag ?: "TrackPlayer", message)
              t?.let { throwable ->
                Log.println(priority, tag ?: "TrackPlayer", throwable.toString())
              }
            }
          }
        }
      )
    }

    // Create initial player with default options for MediaSession
    player = TrackPlayer(this)

    val openAppIntent =
      packageManager.getLaunchIntentForPackage(packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        // Add the Uri data so apps can identify that it was a notification click
        data = "trackplayer://notification.click".toUri()
        action = Intent.ACTION_VIEW
      }
    mediaSession =
      MediaLibrarySession.Builder(this, player.player, InnerMediaSessionCallback())
        // https://github.com/androidx/media/issues/1218
        .setSessionActivity(
          PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
          )
        )
        .build()

    // Now set up player properly with default options
    setupPlayer(TrackPlayerOptions())

    // Bind headless service once at startup for JS task execution
    val headlessIntent = Intent(applicationContext, TrackPlayerHeadlessTaskService::class.java)
    bindService(headlessIntent, headlessConnection, BIND_AUTO_CREATE)
  }

  private var appKilledPlaybackBehavior =
    AppKilledPlaybackBehavior.STOP_PLAYBACK_AND_REMOVE_NOTIFICATION

  fun setupPlayer(playerOptionsData: TrackPlayerOptions, callbacks: TrackPlayerCallbacks? = null) {
    Timber.d("Setting up player")

    val options = playerOptionsData.toPlayerOptions()

    // Always create a new player instance
    val oldPlayer = if (::player.isInitialized) player else null
    player = TrackPlayer(this@TrackPlayerService, options, callbacks)
    oldPlayer?.destroy()
    mediaSession.player = player.forwardingPlayer
  }

  /**
   * Registers a TrackPlayerModule instance with this service. Called when the module connects to
   * the service.
   */
  fun registerModule(moduleInstance: TrackPlayerModule) {
    Timber.d("TrackPlayerModule registered with service")

    player.setCallbacks(moduleInstance.callbacks)

    if (!module.isCompleted) {
      module.complete(moduleInstance)
      Timber.d("Completed module registration")
    }
  }

  /**
   * Resets the module registration for new registrations. Called when the app is closed but service
   * continues running.
   */
  private fun resetModule() {
    module = CompletableDeferred()
    Timber.d("Reset module for future registrations")
  }

  fun updateOptions(options: TrackPlayerOptions) {
    options.audioOffload?.let { audioOffload -> player.setAudioOffload(audioOffload) }

    options.skipSilence?.let { skipSilence -> player.skipSilence = skipSilence }

    options.ratingType?.let { ratingType -> player.ratingType = ratingType.compat }

    appKilledPlaybackBehavior =
      AppKilledPlaybackBehavior.values().find { it.string == options.appKilledPlaybackBehavior }
        ?: AppKilledPlaybackBehavior.STOP_PLAYBACK_AND_REMOVE_NOTIFICATION

    player.shuffleMode = options.shuffle ?: false

    // Progress update events now handled by MusicModule
    val capabilities = options.capabilities ?: emptyList()
    val notificationCapabilities =
      options.notificationCapabilities?.takeIf { it.isNotEmpty() } ?: capabilities

    // Start with default player commands and filter based on capabilities
    val playerCommandsBuilder = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()

    // Commands to remove - start with always-disabled commands
    val disabledCommands =
      mutableSetOf<@Player.Command Int>(
        // Always filter out direct media item commands to avoid dual-command confusion
        // This forces MediaSession to only use the "smart" commands we can control via capabilities
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        // Jump commands need custom buttons to be visible in notifications
        Player.COMMAND_SEEK_FORWARD,
        Player.COMMAND_SEEK_BACK,
      )

    // Check each capability and add commands to remove if not enabled
    val hasPlayPause =
      notificationCapabilities.any { it == PlayerCapability.PLAY || it == PlayerCapability.PAUSE }
    if (!hasPlayPause) {
      disabledCommands.add(Player.COMMAND_PLAY_PAUSE)
    }

    if (!notificationCapabilities.contains(PlayerCapability.STOP)) {
      disabledCommands.add(Player.COMMAND_STOP)
    }

    if (!notificationCapabilities.contains(PlayerCapability.SEEK_TO)) {
      disabledCommands.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    }

    if (!notificationCapabilities.contains(PlayerCapability.SKIP_TO_NEXT)) {
      disabledCommands.add(Player.COMMAND_SEEK_TO_NEXT)
    }

    if (!notificationCapabilities.contains(PlayerCapability.SKIP_TO_PREVIOUS)) {
      disabledCommands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
    }

    // Remove disabled commands from the builder
    disabledCommands.forEach { command ->
      playerCommandsBuilder.remove(command)
      Timber.d("Removed command: $command")
    }

    // Create custom command buttons for jump commands (required for notification visibility)
    val customLayoutButtons = mutableListOf<CommandButton>()
    val sessionCommandsBuilder =
      MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()

    if (notificationCapabilities.contains(PlayerCapability.JUMP_BACKWARD)) {
      val jumpBackCommand = SessionCommand("JUMP_BACKWARD", Bundle())
      customLayoutButtons.add(
        CommandButton.Builder()
          .setDisplayName("Jump Backward")
          .setSessionCommand(jumpBackCommand)
          .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_back)
          .build()
      )
      sessionCommandsBuilder.add(jumpBackCommand)
    }

    if (notificationCapabilities.contains(PlayerCapability.JUMP_FORWARD)) {
      val jumpForwardCommand = SessionCommand("JUMP_FORWARD", Bundle())
      customLayoutButtons.add(
        CommandButton.Builder()
          .setDisplayName("Jump Forward")
          .setSessionCommand(jumpForwardCommand)
          .setIconResId(androidx.media3.session.R.drawable.media3_icon_skip_forward)
          .build()
      )
      sessionCommandsBuilder.add(jumpForwardCommand)
    }

    customLayout = customLayoutButtons

    sessionCommands = sessionCommandsBuilder.build()
    playerCommands = playerCommandsBuilder.build()

    mediaSession.mediaNotificationControllerInfo?.let { controllerInfo ->
      // https://github.com/androidx/media/blob/c35a9d62baec57118ea898e271ac66819399649b/demos/session_service/src/main/java/androidx/media3/demo/session/DemoMediaLibrarySessionCallback.kt#L107
      mediaSession.setCustomLayout(controllerInfo, customLayout)
      mediaSession.setAvailableCommands(
        controllerInfo,
        sessionCommands,
        playerCommands,
      )
    }
  }

  override fun onBind(intent: Intent?): IBinder? {
    Timber.d("action: ${intent?.action}, package: ${intent?.`package`}")
    return if (intent?.action != null) {
      Timber.d("Returning MediaLibraryService binder for ${intent.action}")
      super.onBind(intent)
    } else {
      Timber.d("Service being bound by module - returning LocalBinder")
      binder
    }
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    onUnbind(rootIntent)
    Timber.d("player = $player, appKilledPlaybackBehavior = $appKilledPlaybackBehavior")

    // Check if there are still external controllers connected (like Android Auto)
    val hasExternalControllers =
      mediaSession.connectedControllers.any { controller ->
        controller.packageName != packageName && // Not our own app
          controller.packageName != "com.android.systemui" // Not system UI
      }

    Timber.d("hasExternalControllers = $hasExternalControllers")

    // Reset module for future registrations when app is closed
    resetModule()

    when (appKilledPlaybackBehavior) {
      AppKilledPlaybackBehavior.PAUSE_PLAYBACK -> {
        Timber.d("Pausing playback - appKilledPlaybackBehavior = $appKilledPlaybackBehavior")
        player.pause()
        // Service continues running for Android Auto
      }

      AppKilledPlaybackBehavior.STOP_PLAYBACK_AND_REMOVE_NOTIFICATION -> {
        if (hasExternalControllers) {
          Timber.d("External controllers still connected - deferring aggressive cleanup")
          // Just pause and remove notification, but keep service alive for external controllers
          player.pause()
          stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
          Timber.d("No external controllers - proceeding with service shutdown")
          try {
            if (::mediaSession.isInitialized) {
              mediaSession.release()
            }
            player.clear()
            player.stop()
            player.destroy()
            scope.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            exitProcess(0)
          } catch (e: Exception) {
            Timber.e(e, "Error during aggressive cleanup in onTaskRemoved")
            // Still try to stop the service
            stopSelf()
          }
        }
      }

      AppKilledPlaybackBehavior.CONTINUE_PLAYBACK -> {
        Timber.d("Continuing playback - service remains available for Android Auto")
        // Service continues running for Android Auto with existing callbacks
      }
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession {
    Timber.d("onGetSession requested by: ${controllerInfo.packageName}")
    if (!::player.isInitialized) {
      Timber.w("Player not initialized - recreating with default options")
      player = TrackPlayer(this)
      setupPlayer(TrackPlayerOptions())
    }
    return mediaSession
  }

  override fun onUnbind(intent: Intent?): Boolean {
    Timber.d("onUnbind called - action: ${intent?.action}, package: ${intent?.`package`}")
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    Timber.d("onDestroy called")

    // Release wake lock if held
    wakeLock?.let {
      if (it.isHeld) {
        it.release()
      }
    }

    if (::player.isInitialized) {
      Timber.d("Releasing media session and destroying player")
      if (::mediaSession.isInitialized) {
        mediaSession.release()
      }
      player.destroy()
    }

    super.onDestroy()
  }

  // Android Auto request resolution methods
  fun resolveGetItemRequest(requestId: String, mediaItem: MediaItem) {
    // Store MediaItem in lookup map for later use in onAddMediaItems/onSetMediaItems
    mediaItem.mediaId.let { mediaId ->
      mediaItemById[mediaId] = mediaItem
      Timber.d("Stored single MediaItem: mediaId=$mediaId, title=${mediaItem.mediaMetadata.title}")
    }

    pendingGetItemRequests.remove(requestId)?.set(mediaItem)
  }

  fun resolveGetChildrenRequest(
    requestId: String,
    items: List<MediaItem>,
    totalChildrenCount: Int,
  ) {
    Timber.d(
      "resolveGetChildrenRequest service method called: requestId=$requestId, itemCount=${items.size}"
    )

    // Store MediaItems in lookup map for later use in onAddMediaItems/onSetMediaItems
    items.forEach { mediaItem ->
      mediaItem.mediaId?.let { mediaId ->
        mediaItemById[mediaId] = mediaItem
        Timber.d("Stored MediaItem: mediaId=$mediaId, title=${mediaItem.mediaMetadata.title}")
      }
    }

    val future = pendingGetChildrenRequests.remove(requestId)
    if (future != null) {
      future.set(items)
      Timber.d("Resolved future for requestId=$requestId with ${items.size} items")
    } else {
      Timber.w("No pending future found for requestId=$requestId")
    }
  }

  fun resolveSearchRequest(requestId: String, items: List<MediaItem>, totalMatchesCount: Int) {
    pendingSearchRequests.remove(requestId)?.set(items)
  }

  inner class LocalBinder : Binder() {
    val service = this@TrackPlayerService
  }

  private val rootItem =
    MediaItem.Builder()
      .setMediaId("/")
      .setMediaMetadata(MediaMetadata.Builder().setIsBrowsable(true).setIsPlayable(false).build())
      .build()

  private inner class InnerMediaSessionCallback : MediaLibrarySession.Callback {
    override fun onConnect(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
      Timber.d(controller.packageName)

      Timber.d("Providing standard player commands to controller: ${controller.packageName}")

      return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        .setCustomLayout(customLayout)
        .setAvailableSessionCommands(sessionCommands)
        .setAvailablePlayerCommands(playerCommands)
        .build()
    }

    override fun onCustomCommand(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      command: SessionCommand,
      args: Bundle,
    ): ListenableFuture<SessionResult> {
      Timber.d("onCustomCommand: action=${command.customAction}")

      when (command.customAction) {
        "JUMP_BACKWARD" -> {
          Timber.d("Executing jump backward command")
          player.player.seekBack()
        }
        "JUMP_FORWARD" -> {
          Timber.d("Executing jump forward command")
          player.player.seekForward()
        }
        else -> {
          Timber.w("Received unexpected custom command: ${command.customAction}")
        }
      }
      return super.onCustomCommand(session, controller, command, args)
    }

    override fun onSetRating(
      session: MediaSession,
      controller: MediaSession.ControllerInfo,
      rating: Rating,
    ): ListenableFuture<SessionResult> {
      player.onRatingChanged(rating)
      return super.onSetRating(session, controller, rating)
    }

    override fun onGetLibraryRoot(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
      Timber.d("onGetLibraryRoot: { package: ${browser.packageName} }")
      val rootExtras =
        Bundle().apply {
          putBoolean("android.media.browse.CONTENT_STYLE_SUPPORTED", true)
          //        putInt(
          //          "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT",
          //          MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
          //        )
          //        putInt(
          //          "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT",
          //          MediaConstants.DESCRIPTION_EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
          //        )
        }
      val libraryParams = LibraryParams.Builder().setExtras(rootExtras).build()
      // https://github.com/androidx/media/issues/1731#issuecomment-2411109462
      val mRootItem =
        when (browser.packageName) {
          "com.google.android.googlequicksearchbox" -> {
            // TODO: make "For You" work
            // if (mediaTree[AA_FOR_YOU_KEY] == null) rootItem else forYouItem
            rootItem
          }

          else -> rootItem
        }
      return Futures.immediateFuture(LibraryResult.ofItem(rootItem, libraryParams))
    }

    override fun onGetChildren(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      parentId: String,
      page: Int,
      pageSize: Int,
      params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
      Timber.d("onGetChildren: {parentId: $parentId, page: $page, pageSize: $pageSize }")

      val requestId = UUID.randomUUID().toString()
      val future = SettableFuture.create<List<MediaItem>>()

      // Store the future for later resolution
      pendingGetChildrenRequests[requestId] = future

      // Emit event to JavaScript via module
      CoroutineScope(Dispatchers.Main).launch {
        try {
          Timber.d("Getting module: requestId=$requestId, parentId=$parentId")
          // Wait for module to be registered
          val moduleInstance = module.await()

          Timber.d("Emitting onGetChildrenRequest to JS: requestId=$requestId, parentId=$parentId")
          moduleInstance.emitGetChildrenRequest(requestId, parentId, page, pageSize)
          Timber.d("Emitted onGetChildrenRequest to JS: requestId=$requestId, parentId=$parentId")
        } catch (e: Exception) {
          Timber.e(e, "Failed to emit onGetChildrenRequest to JS")
          // Fallback: resolve with empty list
          pendingGetChildrenRequests.remove(requestId)?.set(emptyList())
        }
      }

      return Futures.transform(
        future,
        { items -> LibraryResult.ofItemList(ImmutableList.copyOf(items), null) },
        MoreExecutors.directExecutor(),
      )
    }

    override fun onGetItem(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> {
      Timber.d("onGetItem: ${browser.packageName}, mediaId = $mediaId")

      val requestId = UUID.randomUUID().toString()
      val future = SettableFuture.create<MediaItem>()

      // Store the future for later resolution
      pendingGetItemRequests[requestId] = future

      // Emit event to JavaScript via module
      CoroutineScope(Dispatchers.Main).launch {
        try {
          Timber.d("Getting module for onGetItem: requestId=$requestId, mediaId=$mediaId")
          // Wait for module to be registered
          val moduleInstance = module.await()

          Timber.d("Emitting onGetItemRequest to JS: requestId=$requestId, mediaId=$mediaId")
          moduleInstance.emitGetItemRequest(requestId, mediaId)
          Timber.d("Emitted onGetItemRequest to JS: requestId=$requestId, mediaId=$mediaId")
        } catch (e: Exception) {
          Timber.e(e, "Failed to emit onGetItemRequest to JS")
          // Fallback: resolve with default item
          pendingGetItemRequests
            .remove(requestId)
            ?.set(
              MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                  MediaMetadata.Builder()
                    .setTitle("Error")
                    .setIsBrowsable(false)
                    .setIsPlayable(false)
                    .build()
                )
                .build()
            )
        }
      }

      return Futures.transform(
        future,
        { item -> LibraryResult.ofItem(item, null) },
        MoreExecutors.directExecutor(),
      )
    }

    override fun onSearch(
      session: MediaLibrarySession,
      browser: MediaSession.ControllerInfo,
      query: String,
      params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
      Timber.d("onSearch: ${browser.packageName}, query = $query")

      // Emit event to JavaScript via module for search initiation
      try {
        val requestId = UUID.randomUUID().toString()
        if (module.isCompleted) {
          val moduleInstance = module.getCompleted()
          val extrasMap =
            params?.extras?.let { bundle ->
              Arguments.createMap().apply {
                for (key in bundle.keySet()) {
                  when (val value = bundle.get(key)) {
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Double -> putDouble(key, value)
                    is Boolean -> putBoolean(key, value)
                  // Add other types as needed
                  }
                }
              }
            }
          moduleInstance.emitSearchResultRequest(
            requestId,
            query,
            extrasMap,
            0,
            50,
          ) // Default page parameters
          Timber.d("Emitted onGetSearchResultRequest to JS: requestId=$requestId, query=$query")
        } else {
          Timber.w("No module registered - cannot emit onGetSearchResultRequest")
        }
      } catch (e: Exception) {
        Timber.e(e, "Failed to emit onGetSearchResultRequest to JS")
      }

      // Return standard void result - search completion is handled separately
      return super.onSearch(session, browser, query, params)
    }

    override fun onSetMediaItems(
      mediaSession: MediaSession,
      controller: MediaSession.ControllerInfo,
      mediaItems: MutableList<MediaItem>,
      startIndex: Int,
      startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
      Timber.d(
        "onSetMediaItems: ${controller.packageName}, mediaId=${mediaItems[0].mediaId}, uri=${mediaItems[0].localConfiguration?.uri}, title=${mediaItems[0].mediaMetadata.title}"
      )

      return CoroutineScope(Dispatchers.Main).future {
        val resolvedItems =
          mediaItems.map { mediaItem ->
            val mediaId = mediaItem.mediaId
            val fullMediaItem = mediaItemById[mediaId]
            if (fullMediaItem != null) {
              Timber.d(
                "Resolved stub MediaItem in onSetMediaItems: mediaId=$mediaId -> title=${fullMediaItem.mediaMetadata.title}"
              )
              fullMediaItem
            } else {
              Timber.w("No stored MediaItem found for mediaId=$mediaId")
              mediaItem // Return original if no lookup found
            }
          }

        Timber.d(
          "Returning ${resolvedItems.size} resolved MediaItems to MediaSession for onSetMediaItems"
        )

        // Return resolved items with original start position - MediaSession will handle queue
        // management
        MediaSession.MediaItemsWithStartPosition(
          resolvedItems.toMutableList(),
          startIndex,
          startPositionMs,
        )
      }
    }
  }

  companion object {
    // Wake lock management
    @Volatile private var wakeLock: PowerManager.WakeLock? = null
  }
}
