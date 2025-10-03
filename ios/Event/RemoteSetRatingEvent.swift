import Foundation

/**
 Event data for remote set rating command.
 */
public struct RemoteSetRatingEvent {
  /// The rating type.
  public let rating: RatingType

  public init(rating: RatingType) {
    self.rating = rating
  }

  public func toBridge() -> [String: Any] {
    return ["rating": rating.rawValue]
  }
}
