package com.dtb.editor.fdt.diff

import com.dtb.editor.fdt.model.FdtNode
import com.dtb.editor.fdt.model.FdtProperty

data class TreeComparisonResult(
    val origNodeCount: Int,
    val rebuiltNodeCount: Int,
    val origPropertyCount: Int,
    val rebuiltPropertyCount: Int,
    val missingNodes: List<String>,
    val missingProperties: List<String>,
    val changedProperties: List<String>,
    val structuralDifferences: List<String>,
    val isExactMatch: Boolean
) {
    fun summaryReport(): String {
        return buildString {
            appendLine("=== DEVICE TREE STRUCTURAL VERIFICATION REPORT ===")
            appendLine("Original Nodes: $origNodeCount | Rebuilt Nodes: $rebuiltNodeCount")
            appendLine("Original Properties: $origPropertyCount | Rebuilt Properties: $rebuiltPropertyCount")
            appendLine("Exact Structural Match: $isExactMatch")

            if (missingNodes.isNotEmpty()) {
                appendLine("\n[!] MISSING NODES (${missingNodes.size}):")
                missingNodes.forEach { appendLine("  - $it") }
            }

            if (missingProperties.isNotEmpty()) {
                appendLine("\n[!] MISSING PROPERTIES (${missingProperties.size}):")
                missingProperties.forEach { appendLine("  - $it") }
            }

            if (changedProperties.isNotEmpty()) {
                appendLine("\n[*] CHANGED PROPERTIES (${changedProperties.size}):")
                changedProperties.forEach { appendLine("  ~ $it") }
            }

            if (structuralDifferences.isNotEmpty()) {
                appendLine("\n[i] STRUCTURAL DIFFERENCES:")
                structuralDifferences.forEach { appendLine("  • $it") }
            }

            if (isExactMatch) {
                appendLine("\n✓ Roundtrip Verification PASSED: Zero data loss detected.")
            }
        }
    }
}

class TreeComparator {

    fun compareTrees(origRoot: FdtNode, rebuiltRoot: FdtNode): TreeComparisonResult {
        val origNodeCount = origRoot.totalNodesCount
        val rebuiltNodeCount = rebuiltRoot.totalNodesCount
        val origPropCount = origRoot.totalPropertiesCount
        val rebuiltPropCount = rebuiltRoot.totalPropertiesCount

        val missingNodes = mutableListOf<String>()
        val missingProperties = mutableListOf<String>()
        val changedProperties = mutableListOf<String>()
        val structuralDiffs = mutableListOf<String>()

        fun compareRecursive(orig: FdtNode, rebuilt: FdtNode) {
            // Compare properties of current node
            val rebuiltPropsMap = rebuilt.properties.associateBy { it.name }

            for (origProp in orig.properties) {
                val rebuiltProp = rebuiltPropsMap[origProp.name]
                val propPath = "${orig.fullPath}/${origProp.name}"

                if (rebuiltProp == null) {
                    missingProperties.add(propPath)
                } else if (!origProp.data.contentEquals(rebuiltProp.data)) {
                    changedProperties.add("$propPath (orig: ${origProp.data.size} B, rebuilt: ${rebuiltProp.data.size} B)")
                }
            }

            // Compare children
            val rebuiltChildrenMap = rebuilt.children.associateBy { it.name }

            for (origChild in orig.children) {
                val rebuiltChild = rebuiltChildrenMap[origChild.name]
                    ?: rebuilt.children.firstOrNull { it.name.startsWith("${origChild.name}@") }

                if (rebuiltChild == null) {
                    missingNodes.add(origChild.fullPath)
                } else {
                    compareRecursive(origChild, rebuiltChild)
                }
            }
        }

        compareRecursive(origRoot, rebuiltRoot)

        if (origNodeCount != rebuiltNodeCount) {
            structuralDiffs.add("Node count mismatch: original $origNodeCount vs rebuilt $rebuiltNodeCount")
        }

        if (origPropCount != rebuiltPropCount) {
            structuralDiffs.add("Property count mismatch: original $origPropCount vs rebuilt $rebuiltPropCount")
        }

        val isExactMatch = missingNodes.isEmpty() &&
                missingProperties.isEmpty() &&
                changedProperties.isEmpty() &&
                origNodeCount == rebuiltNodeCount &&
                origPropCount == rebuiltPropCount

        return TreeComparisonResult(
            origNodeCount = origNodeCount,
            rebuiltNodeCount = rebuiltNodeCount,
            origPropertyCount = origPropCount,
            rebuiltPropertyCount = rebuiltPropCount,
            missingNodes = missingNodes,
            missingProperties = missingProperties,
            changedProperties = changedProperties,
            structuralDifferences = structuralDiffs,
            isExactMatch = isExactMatch
        )
    }
}
