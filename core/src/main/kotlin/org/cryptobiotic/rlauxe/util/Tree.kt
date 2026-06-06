package org.cryptobiotic.rlauxe.util

open class TreeNode<T>(val name: String, val value: T) {
    private val _children = mutableListOf<TreeNode<T>>()
    val children: List<TreeNode<T>> get() = _children

    fun addChild(child: TreeNode<T>) {
        _children.add(child)
    }

    fun youngest(): List<TreeNode<T>> {
        val result = mutableListOf<TreeNode<T>>()
        for (child in children) {
            if (child.children.isEmpty()) {
                result.add(child)
            } else {
                result.addAll(child.youngest())
            }
        }
        return result
    }
}

class Tree<T>: Iterable<T> {
    private val _children = mutableListOf<TreeNode<T>>()
    val children: List<TreeNode<T>> get() = _children

    fun add(child: TreeNode<T>) {
        _children.add(child)
    }

    // Breadth-First Traversal
    override fun iterator(): Iterator<T> {
        return BftIterator(this)
    }

    // get the nodes at targetDepth; root = 0
    fun nodesAtDepth(targetDepth: Int): List<TreeNode<T>> {
        val result = mutableListOf<TreeNode<T>>()
        var currentLevelQueue = ArrayDeque<TreeNode<T>>()
        children.forEach { currentLevelQueue.add(it) }

        var currentDepth = 0

        while (currentLevelQueue.isNotEmpty()) {
            if (currentDepth == targetDepth) {
                result.addAll(currentLevelQueue.map { it })
                break
            }

            val nextLevelQueue = ArrayDeque<TreeNode<T>>()
            for (node in currentLevelQueue) {
                nextLevelQueue.addAll(node.children)
            }

            currentLevelQueue = nextLevelQueue
            currentDepth++
        }

        return result
    }

}

// Breadth-First Traversal
class BftIterator<T>(root: Tree<T>) : Iterator<T> {
    val queue = ArrayDeque<TreeNode<T>>()

    init {
        for (child in root.children) {
            queue.addLast(child)
        }
    }

    override fun next(): T {
        val currentNode = queue.removeFirst()

        // Add all children of the current node to the queue
        for (child in currentNode.children) {
            queue.addLast(child)
        }

        return currentNode.value
    }

    override fun hasNext(): Boolean {
        return queue.isNotEmpty()
    }
}

fun <T> getNodesAtDepthBfs(root: TreeNode<T>, targetDepth: Int): List<T> {
    val result = mutableListOf<T>()
    var currentLevelQueue = ArrayDeque<TreeNode<T>>()
    currentLevelQueue.add(root)

    var currentDepth = 0

    while (currentLevelQueue.isNotEmpty()) {
        if (currentDepth == targetDepth) {
            result.addAll(currentLevelQueue.map { it.value })
            break
        }

        val nextLevelQueue = ArrayDeque<TreeNode<T>>()
        for (node in currentLevelQueue) {
            nextLevelQueue.addAll(node.children)
        }

        currentLevelQueue = nextLevelQueue
        currentDepth++
    }

    return result
}


// Breadth-First Traversal function for N-ary Trees
fun <T> bfsNaryTree(root: TreeNode<T>?, action: (T) -> Unit) {
    if (root == null) return

    val queue = ArrayDeque<TreeNode<T>>()
    queue.addLast(root)

    while (queue.isNotEmpty()) {
        val currentNode = queue.removeFirst()

        action(currentNode.value)

        // Add all children of the current node to the queue
        for (child in currentNode.children) {
            queue.addLast(child)
        }
    }
}
