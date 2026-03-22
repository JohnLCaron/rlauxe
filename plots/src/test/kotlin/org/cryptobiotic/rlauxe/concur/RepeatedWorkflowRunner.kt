
package org.cryptobiotic.rlauxe.concur

import org.cryptobiotic.rlauxe.util.ConcurrentTask
import org.cryptobiotic.rlauxe.workflow.WorkflowResult
import org.cryptobiotic.rlauxe.workflow.ContestAuditTaskGenerator

// TODO specific to ContestAuditTaskGenerator ??
class RepeatedWorkflowRunner (val nruns: Int, val taskGenerator: ContestAuditTaskGenerator):
    ConcurrentTask<List<WorkflowResult>> {

    override fun name(): String = "Repeated-${taskGenerator.name()}"

    override fun run(): List<WorkflowResult> {
        val results = mutableListOf<WorkflowResult>()
        repeat(nruns) {
            val task = taskGenerator.generateNewTask()
            results.add(task.run())
        }
        return results // dont have a generic way to reduce this
    }
}
