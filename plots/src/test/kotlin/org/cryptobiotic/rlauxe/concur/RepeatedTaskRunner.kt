
package org.cryptobiotic.rlauxe.concur

class RepeatedTaskRunner<T> (val nruns: Int, val task: ConcurrentTaskG<T>): ConcurrentTaskG<List<T>> {

    override fun name(): String = "Repeated-${task.name()}"

    override fun run(): List<T> {
        val results = mutableListOf<T>()
        repeat(nruns) {
            task.shuffle()
            results.add(task.run())
        }
        return results // dont have a generic way to reduce this
    }

    override fun shuffle() {
        // nop
    }

}