package org.cryptobiotic.rlauxe.util

// TODO depth first ?
// shouldnt a treeNode know its depth in the tree ??

// could just use a TreeNode as the root
open class TreeNode<T>(val value: T, val parent: TreeNode<T>? = null, ) {
    private val _children = mutableListOf<TreeNode<T>>()
    val children: List<TreeNode<T>> get() = _children
    var order = mutableListOf<Int>()

    fun name() = buildString {
        if (order.isEmpty()) append("root")
        order.forEach{ append(char[it]) }
    }

    fun addChild(child: TreeNode<T>) {
        child.order.addAll(this.order)
        child.order.add(this._children.size)
        _children.add(child)
    }

    fun add(child: T): TreeNode<T> {
        val ct = TreeNode(child, this)
        addChild(ct)
        return ct
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

    companion object {
        val char = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    }
}

// Depth-First Traversal
class DepthNodeIterator<T>(root: TreeNode<T>) : Iterator<TreeNode<T>> {

    // ArrayDeque is used as a LIFO stack in Kotlin
    private val stack = ArrayDeque<TreeNode<T>>()

    init {
        stack.addLast(root)
        for (i in root.children.indices.reversed()) {
            stack.addLast(root.children[i])
        }
    }

    override fun hasNext(): Boolean {
        return stack.size > 1
    }

    override fun next(): TreeNode<T> {
        if (!hasNext()) throw NoSuchElementException("No more elements in the tree.")

        // Pop the top node off the stack
        val current = stack.removeLast()

        // Push children onto the stack in reverse order
        // This ensures the leftmost child is processed first (standard DFS behavior)
        for (i in current.children.indices.reversed()) {
            stack.addLast(current.children[i])
        }

        return current
    }
}

/////////////////////////////////////////////////////////

class Tree<T>: Iterable<T> {
    private val _children = mutableListOf<TreeNode<T>>()
    val children: List<TreeNode<T>> get() = _children

    fun add(child: TreeNode<T>) {
        _children.add(child)
    }

    fun add(name: String, child: T): TreeNode<T> {
        val childNode = TreeNode(child, null)
        _children.add(childNode)
        return childNode
    }

    // Breadth-First Traversal
    override fun iterator(): Iterator<T> {
        return BreadthIterator(this)
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

    fun count(): Int {
        return BreadthIterator(this).asSequence().count()
    }

}

// Breadth-First Traversal
class BreadthIterator<T>(root: Tree<T>) : Iterator<T> {
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
