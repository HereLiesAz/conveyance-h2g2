package com.hereliesaz.conveyance.h2g2.guide


import com.hereliesaz.conveyance.h2g2.menu.MenuNode

/**
 * What the host's live command tree says about one Guide entry: whether the command is on offer
 * ([available]), its capability caption ([cap], a [MenuNode.cap]) and up to eight sub-options
 * ([hints]) the entry page offers as "LIVE OPTIONS" chips.
 */
data class GuideRuntimeInfo(
    val available: Boolean,
    val cap: String? = null,
    val hints: List<String> = emptyList()
)

/**
 * Read-only index over the same materialized command tree shown by the terminal composer. Build one
 * with [fromTree] from the [MenuNode] tree the host feeds its pill menu and pass it to
 * [H2g2GuideReaderScreen]; [H2g2CommandGuideScreen] does this itself. [Empty] knows no commands,
 * so every entry reads as "Not in this build".
 */
class GuideRuntimeIndex private constructor(private val nodes: List<MenuNode>) {
    /** Looks [command] (an entry's `cmd`, e.g. "pkg install") up in the tree. */
    fun info(command: String): GuideRuntimeInfo {
        val normalized = command.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return GuideRuntimeInfo(false)
        val first = normalized.substringBefore(' ')

        val exact = nodes.firstOrNull { node ->
            node.value?.trim()?.replace(Regex("\\s+"), " ") == normalized || node.label == normalized
        }
        val root = exact ?: nodes
            .filter { node -> node.label == first || node.value?.trim()?.substringBefore(' ') == first }
            .maxByOrNull { it.children.size }
            ?: return GuideRuntimeInfo(false)

        val hintSource = when {
            exact?.children?.isNotEmpty() == true -> exact.children
            else -> nodes.filter { node ->
                val value = node.value?.trim().orEmpty()
                value.startsWith("$normalized ") ||
                    (normalized == first && value.startsWith("$first "))
            }
        }
        val hints = hintSource.asSequence()
            .filter { it.emitsToken }
            .map { it.value?.trim().orEmpty().ifBlank { it.label } }
            .map { value ->
                if (value.startsWith("$first ")) value.removePrefix("$first ") else value
            }
            .filter(String::isNotBlank)
            .distinct()
            .take(8)
            .toList()
        return GuideRuntimeInfo(true, exact?.cap ?: root.cap, hints)
    }

    companion object {
        /** An index over no tree at all: nothing is available, nothing has hints. */
        val Empty = GuideRuntimeIndex(emptyList())

        /** Indexes [tree] and everything under it ([MenuNode.children]; lazy children are not resolved). */
        fun fromTree(tree: List<MenuNode>): GuideRuntimeIndex = GuideRuntimeIndex(flatten(tree))

        private fun flatten(tree: List<MenuNode>): List<MenuNode> = buildList {
            fun addNode(node: MenuNode) {
                add(node)
                node.children.forEach(::addNode)
            }
            tree.forEach(::addNode)
        }
    }
}
