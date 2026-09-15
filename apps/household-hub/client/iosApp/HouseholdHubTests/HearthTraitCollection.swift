import UIKit

/// Builds a `UITraitCollection` fixed to one `userInterfaceStyle`, for resolving the light/dark
/// variant of an asset-catalog entry (`UIColor.resolvedColor(with:)`, `UIImageAsset.image(with:)`).
///
/// `UITraitCollection(userInterfaceStyle:)` is deprecated from iOS 17 in favour of the trait
/// builder closure, but the deployment target is iOS 15 (see `project.yml`) — so both paths have
/// to exist, and they belong in exactly one place rather than repeated at every call site. Shared
/// across the Hearth asset tests: introduced alongside `HearthColorAssetTests` in cycle 1, and
/// reused by `HearthLaunchTileTests` in cycle 2 rather than being copied a second time.
func hearthTraitCollection(for style: UIUserInterfaceStyle) -> UITraitCollection {
    if #available(iOS 17.0, *) {
        return UITraitCollection { mutableTraits in
            mutableTraits.userInterfaceStyle = style
        }
    } else {
        return UITraitCollection(userInterfaceStyle: style)
    }
}
