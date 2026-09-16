package adven.kenv.config.example

import androidx.compose.ui.window.ComposeUIViewController

// PascalCase is deliberate and load-bearing: this is the entry point Swift calls as
// `MainViewControllerKt.MainViewController()`, so the name is part of the iOS app's source. It is a
// factory returning a UIViewController rather than a `@Composable`, so the Composable exemption
// configured for ktlint in the root build script does not cover it.
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { App() }
