package com.dtb.editor.fdt.diff

import com.dtb.editor.fdt.model.FdtNode

enum class DiffType {
    ADDED,
    REMOVED,
    MODIFIED,
    UNCHANGED
}

data class NodeDiffItem(
    val path: String,
    val type: DiffType,
    val description: String,
    val details: String = ""
)

data class DiffSummary(
    val addedCount: Int,
    val removedCount: Int,
    val modifiedCount: Int,
    val items: List<NodeDiffItem>,
    val lineDiff: List<LineDiffItem>
)

data class LineDiffItem(
    val lineNumber: Int,
    val type: DiffType,
    val text: String
)

class DtsDiffEngine {

    fun compareNodes(originalRoot: FdtNode, modifiedRoot: FdtNode): DiffSummary {
        val items = mutableListOf<NodeDiffItem>()

        fun compareRecursive(orig: FdtNode?, mod: FdtNode?, currentPath: String) {
            if (orig == null && mod != null) {
                items.add(NodeDiffItem(currentPath, DiffType.ADDED, "Added Node: ${mod.name}", "${mod.properties.size} properties, ${mod.children.size} subnodes"))
                for (child in mod.children) {
                    val p = if (currentPath == "/") "/${child.name}" else "$currentPath/${child.name}"
                    compareRecursive(null, child, p)
                }
                return
            }
            if (orig != null && mod == null) {
                items.add(NodeDiffItem(currentPath, DiffType.REMOVED, "Removed Node: ${orig.name}", ""))
                for (child in orig.children) {
                    val p = if (currentPath == "/") "/${child.name}" else "$currentPath/${child.name}"
                    compareRecursive(child, null, p)
                }
                return
            }
            if (orig == null || mod == null) return

            // Compare properties
            val origPropMap = orig.properties.associateBy { it.name }
            val modPropMap = mod.properties.associateBy { it.name }

            for ((name, pMod) in modPropMap) {
                val pOrig = origPropMap[name]
                if (pOrig == null) {
                    items.add(NodeDiffItem("$currentPath/$name", DiffType.ADDED, "Added property: $name", pMod.formattedValue()))
                } else if (!pOrig.data.contentEquals(pMod.data)) {
                    items.add(
                        NodeDiffItem(
                            "$currentPath/$name",
                            DiffType.MODIFIED,
                            "Modified property: $name",
                            "Original: ${pOrig.formattedValue()} \nModified: ${pMod.formattedValue()}"
                        )
                    )
                }
            }

            for ((name, _) in origPropMap) {
                if (!modPropMap.containsKey(name)) {
                    items.add(NodeDiffItem("$currentPath/$name", DiffType.REMOVED, "Removed property: $name", ""))
                }
            }

            // Compare children
            val origChildMap = orig.children.associateBy { it.name }
            val modChildMap = mod.children.associateBy { it.name }

            val allChildNames = (origChildMap.keys + modChildMap.keys).toSet()
            for (childName in allChildNames) {
                val p = if (currentPath == "/") "/$childName" else "$currentPath/$childName"
                compareRecursive(origChildMap[childName], modChildMap[childName], p)
            }
        }

        compareRecursive(originalRoot, modifiedRoot, "/")

        return DiffSummary(
            addedCount = items.count { it.type == DiffType.ADDED },
            removedCount = items.count { it.type == DiffType.REMOVED },
            modifiedCount = items.count { it.type == DiffType.MODIFIED },
            items = items,
            lineDiff = emptyList()
        )
    }

    fun compareDtsLines(origText: String, modText: String): List<LineDiffItem> {
        val origLines = origText.lines()
        val modLines = modText.lines()

        val result = mutableListOf<LineDiffItem>()
        val maxLen = maxOf(origLines.size, modLines.size)

        for (i in 0 until maxLen) {
            val o = origLines.getOrNull(i)
            val m = modLines.getOrNull(i)

            if (o == null && m != null) {
                result.add(LineDiffItem(i + 1, DiffType.ADDED, "+ $m"))
            } else if (o != null && m == null) {
                result.add(LineDiffItem(i + 1, DiffType.REMOVED, "- $o"))
            } else if (o != null && m != null) {
                if (o == m) {
                    result.add(LineDiffItem(i + 1, DiffType.UNCHANGED, "  $m"))
                } else {
                    result.add(LineDiffItem(i + 1, DiffType.MODIFIED, "~ $m (was: ${o.trim()})"))
                }
            }
        }

        return result
    }
}
