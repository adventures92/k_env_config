@file:OptIn(ExperimentalMaterial3Api::class)

package adven.kenv.config.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sample app demonstrating the KEnv Config Gradle Plugin v2.
 *
 * ## Features Demonstrated
 *
 * ### Runtime Environment Selection
 * In Kotlin Multiplatform projects where Android build variants aren't available,
 * you can select the active environment at runtime:
 * ```kotlin
 * EnvConfig.setActiveEnvironment("production")
 * val url = EnvConfig.Server.API_BASE_URL // → "https://api.kenvconfig.io"
 * ```
 *
 * ### Direct Per-Environment Access
 * You can also access any environment's values directly without setting an active one:
 * ```kotlin
 * val devUrl = EnvConfig.Dev.Server.API_BASE_URL
 * val prodUrl = EnvConfig.Production.Server.API_BASE_URL
 * ```
 *
 * ### Other Plugin Features
 * - **Unified directory convention** — all config files live in `kenv/`
 * - **Global vs environment-scoped variables** — global values shared across all environments
 * - **Multiple type support** — String, Int, Long, Double, Boolean, Url
 * - **Groups** — logical organization into nested objects (Server, Identity, Feature)
 * - **KDoc generation** — schema `description` fields become IDE-visible documentation
 * - **Required package name** — generated code always has a proper package declaration
 * - **No defaults** — all values must be explicit in env files (caught at compile time)
 *
 * @see EnvConfig The generated configuration object
 */
@Composable
fun App() {
    MaterialTheme {
        var currentEnv by remember { mutableStateOf("dev") }

        // Set initial environment only once via remember
        remember { EnvConfig.setActiveEnvironment("dev") }

        Scaffold(topBar = {
            CenterAlignedTopAppBar(title = {
                AppHeader()
            }, scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior())
        }) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Spacer(modifier = Modifier.height(24.dp))

                EnvironmentSwitcher(
                    currentEnv = currentEnv,
                    onEnvironmentSelected = { env ->
                        EnvConfig.setActiveEnvironment(env)
                        currentEnv = env
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                GlobalConfigSection()

                Spacer(modifier = Modifier.height(16.dp))

                EnvironmentConfigSection(currentEnv)

                Spacer(modifier = Modifier.height(16.dp))

                PluginFeaturesSection()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Contact: ${EnvConfig.Identity.SUPPORT_EMAIL}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// region — Sections

/**
 * App header showing the app name and version from global config.
 */
@Composable
private fun AppHeader() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = EnvConfig.Identity.APP_NAME,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "v${EnvConfig.APP_VERSION}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Dropdown-based environment switcher demonstrating runtime environment selection.
 *
 * This is the key KMP feature: call [EnvConfig.setActiveEnvironment] to switch
 * which environment's values are returned by the flat accessors.
 */
@Composable
private fun EnvironmentSwitcher(
    currentEnv: String,
    onEnvironmentSelected: (String) -> Unit
) {
    val environments = listOf("dev", "production")
    var expanded by remember { mutableStateOf(false) }

    SectionCard(title = "Runtime Environment Selection") {
        Text(
            text = "Select the active environment at runtime. In KMP projects without " +
                    "Android build variants, this is how you choose which config values to use.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text("Environment: $currentEnv ▾")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                environments.forEach { env ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = env,
                                fontWeight = if (env == currentEnv) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onEnvironmentSelected(env)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "EnvConfig.setActiveEnvironment(\"$currentEnv\")",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

/**
 * Displays global-scoped configuration values.
 * These are the same regardless of which environment is active.
 */
@Composable
private fun GlobalConfigSection() {
    SectionCard(title = "Global Scope (same in all environments)") {
        ConfigRow("APP_VERSION", EnvConfig.APP_VERSION, "String")
        ConfigRow("APP_NAME", EnvConfig.Identity.APP_NAME, "String")
        ConfigRow("SUPPORT_EMAIL", EnvConfig.Identity.SUPPORT_EMAIL, "String")
    }
}

/**
 * Displays environment-scoped configuration values.
 * These change when the active environment is switched via the dropdown.
 *
 * Values are read directly from the per-environment objects based on [currentEnv],
 * ensuring Compose recomposes correctly when the environment changes.
 */
@Composable
private fun EnvironmentConfigSection(currentEnv: String) {
    // Read values directly from the per-environment objects to guarantee
    // recomposition picks up changes. This avoids relying on the mutable
    // _activeEnvironment internal state which Compose cannot observe.
    val debugMode = when (currentEnv) {
        "dev" -> EnvConfig.Dev.DEBUG_MODE
        else -> EnvConfig.Production.DEBUG_MODE
    }
    val apiBaseUrl = when (currentEnv) {
        "dev" -> EnvConfig.Dev.Server.API_BASE_URL
        else -> EnvConfig.Production.Server.API_BASE_URL
    }
    val apiPort = when (currentEnv) {
        "dev" -> EnvConfig.Dev.Server.API_PORT
        else -> EnvConfig.Production.Server.API_PORT
    }
    val apiTimeout = when (currentEnv) {
        "dev" -> EnvConfig.Dev.Server.API_TIMEOUT_MS
        else -> EnvConfig.Production.Server.API_TIMEOUT_MS
    }
    val enableAnalytics = when (currentEnv) {
        "dev" -> EnvConfig.Dev.Feature.ENABLE_ANALYTICS
        else -> EnvConfig.Production.Feature.ENABLE_ANALYTICS
    }
    val maxRetries = when (currentEnv) {
        "dev" -> EnvConfig.Dev.Feature.MAX_RETRIES
        else -> EnvConfig.Production.Feature.MAX_RETRIES
    }
    val cacheTtl = when (currentEnv) {
        "dev" -> EnvConfig.Dev.Feature.CACHE_TTL_SECONDS
        else -> EnvConfig.Production.Feature.CACHE_TTL_SECONDS
    }

    SectionCard(title = "Environment Scope ($currentEnv)") {
        ConfigRow("DEBUG_MODE", debugMode.toString(), "Boolean")

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        GroupLabel("Server")
        ConfigRow("API_BASE_URL", apiBaseUrl, "Url")
        ConfigRow("API_PORT", apiPort.toString(), "Int")
        ConfigRow("API_TIMEOUT_MS", apiTimeout.toString(), "Long")

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        GroupLabel("Feature")
        ConfigRow("ENABLE_ANALYTICS", enableAnalytics.toString(), "Boolean")
        ConfigRow("MAX_RETRIES", maxRetries.toString(), "Int")
        ConfigRow("CACHE_TTL_SECONDS", cacheTtl.toString(), "Double")
    }
}

/**
 * Summary of all plugin v2 features demonstrated in this sample app.
 */
@Composable
private fun PluginFeaturesSection() {
    SectionCard(title = "Plugin v2 Features Demonstrated") {
        FeatureItem("Runtime selection", "setActiveEnvironment() for KMP flat access")
        FeatureItem("Direct access", "EnvConfig.Dev.* / EnvConfig.Production.*")
        FeatureItem("Unified directory", "All config in kenv/")
        FeatureItem("Global scope", "APP_VERSION, APP_NAME, SUPPORT_EMAIL")
        FeatureItem("Environment scope", "API_BASE_URL, DEBUG_MODE, etc.")
        FeatureItem("Groups", "server, identity, feature")
        FeatureItem("All types", "String, Int, Long, Double, Boolean, Url")
        FeatureItem("KDoc generation", "Descriptions → IDE docs")
        FeatureItem("Required package", "adven.kenv.config.example")
        FeatureItem("No defaults", "All values explicit in env files")
    }
}

// endregion

// region — Reusable Components

/**
 * A card container for grouping related configuration display.
 */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * A single row displaying a configuration key, its current value, and its type.
 */
@Composable
private fun ConfigRow(key: String, value: String, type: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = key,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(6.dp))
        TypeBadge(type)
    }
}

/**
 * A small badge showing the schema type of a configuration value.
 */
@Composable
private fun TypeBadge(type: String) {
    Text(
        text = type,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 9.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

/**
 * A label for a configuration group within a section.
 */
@Composable
private fun GroupLabel(name: String) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

/**
 * A bullet-point item in the features list.
 */
@Composable
private fun FeatureItem(feature: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(end = 6.dp)
        )
        Text(
            text = "$feature — ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Compose Multiplatform previews for the KEnv Config sample app.
 *
 * These previews work across all targets (Android, iOS, Desktop) and allow
 * developers to visualize the UI directly in the IDE.
 */

@Preview
@Composable
fun AppPreview() {
    EnvConfig.setActiveEnvironment("dev")
    App()
}

@Preview
@Composable
fun AppPreviewProduction() {
    EnvConfig.setActiveEnvironment("production")
    App()
}

// endregion
