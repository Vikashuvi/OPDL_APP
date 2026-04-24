/*
 * SPDX-FileCopyrightText: 2018 Erik Duisters <e.duisters1@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-KDE-Accepted-GPL
 */
package org.opdl.transfer.async

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.BlockingQueue
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Scheduler for [BackgroundJob] objects.
 *
 * We use an internal [ThreadPoolExecutor] to catch Exceptions and pass them along to [.handleUncaughtException].
 *
 * Might be able to be replaced with coroutines later
 */
class BackgroundJobHandler {
    private constructor(corePoolSize: Int, maxPoolSize: Int, keepAliveTime: Long, unit: TimeUnit, workQueue: BlockingQueue<Runnable>) {
        this.threadPoolExecutor = MyThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue)
        this.handler = Handler(Looper.getMainLooper())
    }

    private val jobMap: MutableMap<BackgroundJob<*, *>, Future<*>> = HashMap()
    private val jobMapLock: Any = Any()

    private fun registerJobGlobally(job: BackgroundJob<*, *>) {
        _activeJobs.update { it + job }
    }

    private fun unregisterJobGlobally(job: BackgroundJob<*, *>) {
        _activeJobs.update { it - job }
    }

    private inner class MyThreadPoolExecutor(corePoolSize: Int, maxPoolSize: Int, keepAliveTime: Long, unit: TimeUnit, workQueue: BlockingQueue<Runnable>) : ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, unit, workQueue) {
        override fun afterExecute(runnable: Runnable, throwable: Throwable?) {
            super.afterExecute(runnable, throwable)

            if (runnable !is Future<*>) {
                return
            }

            val future = runnable as Future<*>

            var t: Throwable? = throwable
            if (t == null) {
                try {
                    future.get()
                }
                catch (ce: CancellationException) {
                    Log.d(LOG_TAG, "afterExecute got a CancellationException")
                }
                catch (ee: ExecutionException) {
                    t = ee
                }
                catch (ie: InterruptedException) {
                    Log.d(LOG_TAG, "afterExecute got an InterruptedException")
                    Thread.currentThread().interrupt() // ignore / reset
                }
            }

            if (t != null) {
                this@BackgroundJobHandler.handleUncaughtException(future, t)
            }
        }
    }

    private val threadPoolExecutor: ThreadPoolExecutor
    private val handler: Handler

    fun runJob(bgJob: BackgroundJob<*, *>) {
        bgJob.setBackgroundJobHandler(this)
        try {
            synchronized(jobMapLock) {
                val future: Future<*> = threadPoolExecutor.submit(bgJob)
                jobMap.put(bgJob, future)
                registerJobGlobally(bgJob)
            }
        }
        catch (e: RejectedExecutionException) {
            Log.d(LOG_TAG, "threadPoolExecutor.submit rejected a background job: ${e.message}")
            bgJob.reportError(e)
        }
    }

    fun isRunning(jobId: Long): Boolean {
        synchronized(jobMapLock) {
            return jobMap.keys.any { it.id == jobId }
        }
    }

    fun getJob(jobId: Long): BackgroundJob<*, *>? {
        synchronized(jobMapLock) {
            return jobMap.keys.find { it.id == jobId }
        }
    }

    fun cancelJob(job: BackgroundJob<*, *>) {
        synchronized(jobMapLock) {
            if (jobMap.containsKey(job)) {
                if (jobMap[job]!!.cancel(true)) {
                    threadPoolExecutor.purge()
                }
                jobMap.remove(job)
                unregisterJobGlobally(job)
            }
        }
    }

    private fun handleUncaughtException(ft: Future<*>, t: Throwable) {
        synchronized(jobMapLock) {
            jobMap.entries.find { it.value == ft }?.key?.reportError(t)
        }
    }

    fun onFinished(job: BackgroundJob<*, *>) {
        synchronized(jobMapLock) {
            jobMap.remove(job)
            unregisterJobGlobally(job)
        }
    }

    fun runOnUiThread(runnable: Runnable) {
        handler.post(runnable)
    }

    companion object {
        private val LOG_TAG: String = BackgroundJobHandler::class.java.simpleName

        @JvmStatic
        fun newFixedThreadPoolBackgroundJobHandler(numThreads: Int): BackgroundJobHandler {
            return BackgroundJobHandler(numThreads, numThreads, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
        }

        private val _activeJobs = MutableStateFlow<List<BackgroundJob<*, *>>>(emptyList())
        val activeJobs: StateFlow<List<BackgroundJob<*, *>>> = _activeJobs.asStateFlow()

        @JvmStatic
        fun onProgressChanged(job: BackgroundJob<*, *>) {
            // Trigger an update to the flow to notify observers
            _activeJobs.update { it.toList() }
        }
    }
}
