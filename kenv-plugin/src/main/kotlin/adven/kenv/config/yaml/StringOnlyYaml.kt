package adven.kenv.config.yaml

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer
import org.yaml.snakeyaml.resolver.Resolver
import java.util.regex.Pattern

/**
 * Builds the [Yaml] instance every parser in this project must use for *loading*.
 *
 * KEnv consumes every scalar as a [String] — variable names, environment names, type and scope
 * names, descriptions. YAML 1.1 implicit typing is therefore never wanted, and is actively
 * harmful: it rewrites the text before it is converted back to a String.
 *
 * ```
 * environments: [dev, no]   ->  ["dev", "false"]
 * ON:                       ->  the key becomes the boolean true
 * description: 1.10         ->  "1.1"
 * API_PORT: 0755            ->  "493"   (read as octal)
 * ```
 *
 * The variable-name case is the sharpest: KEnv variables are conventionally
 * SCREAMING_SNAKE_CASE, and YAML 1.1 resolves `ON`, `OFF`, `YES`, `NO`, `Y` and `N` — in any
 * casing — as booleans. A variable legitimately named `ON` would silently become `true`.
 *
 * This lives here, rather than privately inside one parser, because it previously did: the
 * hardening was added to the env parser and the schema parser kept SnakeYAML's defaults, so the
 * same bug survived in a sibling. One implementation, used by both.
 */
internal object StringOnlyYaml {

    /**
     * A loader that reads every scalar as a String.
     *
     * [SafeConstructor] additionally prevents arbitrary type instantiation from explicit tags.
     * SnakeYAML 2.x already refuses global tags by default, so this is defence in depth rather
     * than the only thing standing between a schema file and arbitrary instantiation.
     */
    fun createLoader(): Yaml {
        val loaderOptions = LoaderOptions()
        return Yaml(
            SafeConstructor(loaderOptions),
            Representer(DumperOptions()),
            DumperOptions(),
            loaderOptions,
            StringOnlyResolver(),
        )
    }

    /**
     * A [Resolver] that registers no implicit resolvers, so SnakeYAML falls back to
     * `tag:yaml.org,2002:str` for every unquoted scalar.
     */
    private class StringOnlyResolver : Resolver() {
        override fun addImplicitResolver(tag: Tag?, regexp: Pattern?, first: String?) = Unit

        override fun addImplicitResolver(tag: Tag?, regexp: Pattern?, first: String?, limit: Int) = Unit
    }
}
