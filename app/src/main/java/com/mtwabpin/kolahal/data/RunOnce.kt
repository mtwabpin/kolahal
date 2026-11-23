/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

/**
 * Work that two callers in one process may both ask for, which needs to run only once. The work
 * returns true when it is done; false leaves it to the next caller, who tries again.
 * - [once] runs it unless it is done. A caller that comes while it runs waits until it has ended,
 *   and doesn't run it again: when [once] returns, the work is done or was tried.
 * - [again] runs it whatever ran before, after a run in progress has ended.
 */
class RunOnce {
    private val lock = Any()
    private var done = false

    fun once(work: () -> Boolean) {
        synchronized(lock) {
            if (done) return
            done = work()
        }
    }

    fun again(work: () -> Boolean) {
        synchronized(lock) {
            done = work()
        }
    }
}
