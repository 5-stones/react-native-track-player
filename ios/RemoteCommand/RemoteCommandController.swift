//
//  RemoteCommandController.swift
//  SwiftAudio
//
//  Created by Jørgen Henrichsen on 20/03/2018.
//

import Foundation
import MediaPlayer

public protocol RemoteCommandable {
  func getCommands() -> [RemoteCommand]
}

public class RemoteCommandController {
  private let center: MPRemoteCommandCenter

  weak var audioPlayer: AudioPlayer?

  var commandTargetPointers: [String: Any] = [:]
  private var enabledCommands: [RemoteCommand] = []

  /**
   Create a new RemoteCommandController.

   - parameter remoteCommandCenter: The MPRemoteCommandCenter used. Default is `MPRemoteCommandCenter.shared()`
   */
  public init(remoteCommandCenter: MPRemoteCommandCenter = MPRemoteCommandCenter.shared()) {
    center = remoteCommandCenter
  }

  func enable(commands: [RemoteCommand]) {
    let commandsToDisable = enabledCommands.filter { command in
      !commands.contains(where: { $0.description == command.description })
    }

    enabledCommands = commands
    commands.forEach { self.enable(command: $0) }
    disable(commands: commandsToDisable)
  }

  func disable(commands: [RemoteCommand]) {
    commands.forEach { self.disable(command: $0) }
  }

  private func enableCommand(_ command: some RemoteCommandProtocol) {
    center[keyPath: command.commandKeyPath].isEnabled = true
    center[keyPath: command.commandKeyPath].removeTarget(commandTargetPointers[command.id])
    commandTargetPointers[command.id] = center[keyPath: command.commandKeyPath]
      .addTarget(handler: self[keyPath: command.handlerKeyPath])
  }

  private func disableCommand(_ command: some RemoteCommandProtocol) {
    center[keyPath: command.commandKeyPath].isEnabled = false
    center[keyPath: command.commandKeyPath].removeTarget(commandTargetPointers[command.id])
    commandTargetPointers.removeValue(forKey: command.id)
  }

  private func enable(command: RemoteCommand) {
    switch command {
    case .play: enableCommand(PlayBackCommand.play)
    case .pause: enableCommand(PlayBackCommand.pause)
    case .stop: enableCommand(PlayBackCommand.stop)
    case .togglePlayPause: enableCommand(PlayBackCommand.togglePlayPause)
    case .next: enableCommand(PlayBackCommand.nextTrack)
    case .previous: enableCommand(PlayBackCommand.previousTrack)
    case .changePlaybackPosition: enableCommand(ChangePlaybackPositionCommand
        .changePlaybackPosition
      )
    case let .skipForward(preferredIntervals): enableCommand(SkipIntervalCommand.skipForward
        .set(preferredIntervals: preferredIntervals)
      )
    case let .skipBackward(preferredIntervals): enableCommand(SkipIntervalCommand.skipBackward
        .set(preferredIntervals: preferredIntervals)
      )
    case let .like(isActive, localizedTitle, localizedShortTitle):
      enableCommand(FeedbackCommand.like.set(
        isActive: isActive,
        localizedTitle: localizedTitle,
        localizedShortTitle: localizedShortTitle
      ))
    case let .dislike(isActive, localizedTitle, localizedShortTitle):
      enableCommand(FeedbackCommand.dislike.set(
        isActive: isActive,
        localizedTitle: localizedTitle,
        localizedShortTitle: localizedShortTitle
      ))
    case let .bookmark(isActive, localizedTitle, localizedShortTitle):
      enableCommand(FeedbackCommand.bookmark.set(
        isActive: isActive,
        localizedTitle: localizedTitle,
        localizedShortTitle: localizedShortTitle
      ))
    }
  }

  private func disable(command: RemoteCommand) {
    switch command {
    case .play: disableCommand(PlayBackCommand.play)
    case .pause: disableCommand(PlayBackCommand.pause)
    case .stop: disableCommand(PlayBackCommand.stop)
    case .togglePlayPause: disableCommand(PlayBackCommand.togglePlayPause)
    case .next: disableCommand(PlayBackCommand.nextTrack)
    case .previous: disableCommand(PlayBackCommand.previousTrack)
    case .changePlaybackPosition: disableCommand(ChangePlaybackPositionCommand
        .changePlaybackPosition
      )
    case .skipForward: disableCommand(SkipIntervalCommand.skipForward)
    case .skipBackward: disableCommand(SkipIntervalCommand.skipBackward)
    case .like: disableCommand(FeedbackCommand.like)
    case .dislike: disableCommand(FeedbackCommand.dislike)
    case .bookmark: disableCommand(FeedbackCommand.bookmark)
    }
  }

  // MARK: - Handlers

  public lazy var handlePlayCommand: RemoteCommandHandler = handlePlayCommandDefault
  public lazy var handlePauseCommand: RemoteCommandHandler = handlePauseCommandDefault
  public lazy var handleStopCommand: RemoteCommandHandler = handleStopCommandDefault
  public lazy var handleTogglePlayPauseCommand: RemoteCommandHandler =
    handleTogglePlayPauseCommandDefault
  public lazy var handleSkipForwardCommand: RemoteCommandHandler = handleSkipForwardCommandDefault
  public lazy var handleSkipBackwardCommand: RemoteCommandHandler = handleSkipBackwardDefault
  public lazy var handleChangePlaybackPositionCommand: RemoteCommandHandler =
    handleChangePlaybackPositionCommandDefault
  public lazy var handleNextTrackCommand: RemoteCommandHandler = handleNextTrackCommandDefault
  public lazy var handlePreviousTrackCommand: RemoteCommandHandler =
    handlePreviousTrackCommandDefault
  public lazy var handleLikeCommand: RemoteCommandHandler = handleLikeCommandDefault
  public lazy var handleDislikeCommand: RemoteCommandHandler = handleDislikeCommandDefault
  public lazy var handleBookmarkCommand: RemoteCommandHandler = handleBookmarkCommandDefault

  private func handlePlayCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let audioPlayer {
      audioPlayer.play()
      return MPRemoteCommandHandlerStatus.success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handlePauseCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let audioPlayer {
      audioPlayer.pause()
      return MPRemoteCommandHandlerStatus.success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handleStopCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let audioPlayer {
      audioPlayer.stop()
      return .success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handleTogglePlayPauseCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let audioPlayer {
      audioPlayer.togglePlaying()
      return MPRemoteCommandHandlerStatus.success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handleSkipForwardCommandDefault(event: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let command = event.command as? MPSkipIntervalCommand,
       let interval = command.preferredIntervals.first,
       let audioPlayer
    {
      audioPlayer.seek(to: audioPlayer.currentTime + Double(truncating: interval))
      return MPRemoteCommandHandlerStatus.success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handleSkipBackwardDefault(event: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let command = event.command as? MPSkipIntervalCommand,
       let interval = command.preferredIntervals.first,
       let audioPlayer
    {
      audioPlayer.seek(to: audioPlayer.currentTime - Double(truncating: interval))
      return MPRemoteCommandHandlerStatus.success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handleChangePlaybackPositionCommandDefault(event: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let event = event as? MPChangePlaybackPositionCommandEvent,
       let audioPlayer
    {
      audioPlayer.seek(to: event.positionTime)
      return MPRemoteCommandHandlerStatus.success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handleNextTrackCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let player = audioPlayer as? QueuedAudioPlayer {
      player.next()
      return MPRemoteCommandHandlerStatus.success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handlePreviousTrackCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    if let player = audioPlayer as? QueuedAudioPlayer {
      player.previous()
      return MPRemoteCommandHandlerStatus.success
    }
    return MPRemoteCommandHandlerStatus.commandFailed
  }

  private func handleLikeCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    MPRemoteCommandHandlerStatus.success
  }

  private func handleDislikeCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    MPRemoteCommandHandlerStatus.success
  }

  private func handleBookmarkCommandDefault(event _: MPRemoteCommandEvent)
    -> MPRemoteCommandHandlerStatus
  {
    MPRemoteCommandHandlerStatus.success
  }

  private func getRemoteCommandHandlerStatus(forError error: Error)
    -> MPRemoteCommandHandlerStatus
  {
    return error is AudioPlayerError.QueueError
      ? MPRemoteCommandHandlerStatus.noSuchContent
      : MPRemoteCommandHandlerStatus.commandFailed
  }
}
