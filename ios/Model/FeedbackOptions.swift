import Foundation

/**
 * Configuration for feedback buttons in iOS control center.
 */
public struct FeedbackOptions: Equatable {
  public var isActive: Bool
  public var title: String

  public init(isActive: Bool = false, title: String) {
    self.isActive = isActive
    self.title = title
  }

  public static func fromBridge(_ dict: [String: Any]?) -> FeedbackOptions? {
    guard let dict,
          let title = dict["title"] as? String
    else {
      return nil
    }

    let isActive = dict["isActive"] as? Bool ?? false
    return FeedbackOptions(isActive: isActive, title: title)
  }

  public func toBridge() -> [String: Any] {
    return [
      "isActive": isActive,
      "title": title,
    ]
  }
}
