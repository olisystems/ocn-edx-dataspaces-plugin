// Authors: OLI Systems GmbH
rootProject.name = "ocn-node-official-plugin"

val nodeRepo = providers.environmentVariable("OCN_NODE_REPO").orNull ?: "../../ocn-node-v2"
if (file("$nodeRepo/settings.gradle.kts").exists()) {
    includeBuild(nodeRepo)
}
