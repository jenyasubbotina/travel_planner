import org.travelplanner.app.IosComposeRoot
import platform.UIKit.UIViewController

/** @deprecated Prefer `IosComposeRoot.shared.viewController()` from Swift. */
@Suppress("unused")
fun MainViewController(): UIViewController = IosComposeRoot.viewController()
