package tk.zwander.sprviewer.util

import kotlinx.coroutines.*
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun <T> Enumeration<T>.forEachRemaining(consumer: (T, shortCircuit: () -> Unit) -> Unit) {
    var running = true

    while (hasMoreElements() && running) {
        consumer(nextElement()) {
            running = false
        }
    }
}

suspend fun <T> Collection<T>.forEachParallel(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.(T) -> Unit
) = coroutineScope {
    val jobs = ArrayList<Deferred<*>>(size)
    forEach {
        jobs.add(
            async(context) {
                block(it)
            }
        )
    }
    jobs.awaitAll()
}

fun <T> Collection<T>.get(index: Int): T {
    forEachIndexed { i, t ->
        if (i == index) {
            return t
        }
    }

    throw IndexOutOfBoundsException()
}