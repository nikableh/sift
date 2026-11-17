package moe.nikableh.sift

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Move or undo in flight. It belongs to the process, not to a view model, so the files keep
 * moving and the queue still gets settled after the activity is gone. Main thread only, apart
 * from the count in [done]
 */
object MoveRunner {
    class Run(val title: String, val total: Int, val cancellable: Boolean)

    sealed interface Outcome {
        class Moved(val result: Mover.Result) : Outcome
        class Undone(val result: Mover.UndoResult) : Outcome
    }

    var run by mutableStateOf<Run?>(null)
        private set
    var done by mutableIntStateOf(0)
        private set

    /** Waits for a view model to show it, which can be a later one than the one that started the run. */
    var outcome by mutableStateOf<Outcome?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var cancel: AtomicBoolean? = null

    fun move(ctx: Context, entries: List<Pair<String, String>>) {
        val journal = Store.newJournal()
        val stop = AtomicBoolean(false)
        cancel = stop
        start(ctx, Run("Moving ${entries.size} files", entries.size, cancellable = true), journal, undo = false) { app ->
            val result = withContext(Dispatchers.IO) { Mover.move(app, entries, journal, stop, ::tick) }
            for (path in result.settled) Store.pending.remove(path)
            Outcome.Moved(result)
        }
    }

    fun undo(ctx: Context, batch: Batch) {
        cancel = null
        start(ctx, Run("Moving ${batch.moved} files back", batch.moved, cancellable = false), batch.journal, undo = true) { app ->
            val result = withContext(Dispatchers.IO) { Mover.undo(app, batch.journal, ::tick) }
            for ((path, dest) in result.restored) Store.pending[path] = dest
            Store.lastPath = result.restored.lastOrNull()?.first
            Outcome.Undone(result)
        }
    }

    fun stop() {
        cancel?.set(true)
    }

    private fun start(ctx: Context, run: Run, journal: File, undo: Boolean, work: suspend (Context) -> Outcome) {
        val app = ctx.applicationContext
        done = 0
        outcome = null
        this.run = run
        Store.markRunning(journal, undo)
        MoveService.start(app)
        scope.launch {
            val result = work(app)
            Store.compactPending()
            Store.clearRunning(journal, undo)
            ImageLoader.clear()
            Store.scanVersion++
            // Cleared first: a view model does nothing while a run is on, and the outcome sends it to a screen
            this@MoveRunner.run = null
            outcome = result
            MoveService.stop(app)
        }
    }

    private fun tick(n: Int) {
        if (n % 8 == 0) done = n
    }
}
