import Foundation

/**
 Event data for controller connected.
 */
public struct ControllerConnectedEvent {
  /// The package name.
  public let package: String

  /// Whether this is a media notification controller.
  public let isMediaNotificationController: Bool

  /// Whether this is an automotive controller.
  public let isAutomotiveController: Bool

  /// Whether this is an auto companion controller.
  public let isAutoCompanionController: Bool

  public init(
    package: String,
    isMediaNotificationController: Bool,
    isAutomotiveController: Bool,
    isAutoCompanionController: Bool
  ) {
    self.package = package
    self.isMediaNotificationController = isMediaNotificationController
    self.isAutomotiveController = isAutomotiveController
    self.isAutoCompanionController = isAutoCompanionController
  }

  public func toBridge() -> [String: Any] {
    return [
      "package": package,
      "isMediaNotificationController": isMediaNotificationController,
      "isAutomotiveController": isAutomotiveController,
      "isAutoCompanionController": isAutoCompanionController
    ]
  }
}
