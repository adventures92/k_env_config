package adven.kenv.config.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/**
 * Android entry point for the KEnv Config sample app.
 *
 * Sets the active environment to "dev" on startup and renders the [App] composable.
 * In a production app, you might determine the environment from BuildConfig,
 * a remote config service, or user preferences.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}
