package ai.adapt.inference

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.pytorch.executorch.extension.llm.*
import java.util.concurrent.atomic.AtomicBoolean

sealed interface TutorEvent {
    data class Token(val text: String): TutorEvent
    data class Complete(val elapsedMs: Long, val ttftMs: Long?, val nativeStats: String, val modelHash: String, val loadMs: Long, val textLimited: Boolean=false): TutorEvent
}
enum class ModelState { MISSING, INSTALLED, IMPORTING, VERIFYING, LOADING, READY, GENERATING, ERROR }

class LocalTutor(context: Context) {
    val store=ModelStore(context)
    private val gate=Mutex()
    private val ownership=Any()
    private var module: LlmModule?=null
    private var owner: Any?=null
    private val mutableState=MutableStateFlow(if(runCatching { store.installed() }.getOrNull()!=null) ModelState.INSTALLED else ModelState.MISSING)
    val state=mutableState.asStateFlow()
    fun stop() { synchronized(ownership) { if(owner!=null) module?.stop() } }
    private fun stopOwned(run: Any) { synchronized(ownership) { if(owner===run) module?.stop() } }
    // Called only while gate is held; the short monitor also excludes concurrent stop/reset.
    private fun releaseNative() { synchronized(ownership) { module?.resetNative(); module=null } }
    suspend fun initialize() = withContext(Dispatchers.IO) {
        gate.withLock {
            try { store.recover(); mutableState.value=if(store.installed()!=null) ModelState.INSTALLED else ModelState.MISSING }
            catch(_: Exception) { mutableState.value=ModelState.ERROR }
        }
    }
    suspend fun unload() = withContext(Dispatchers.IO) {
        stop()
        gate.withLock { releaseNative(); mutableState.value=if(runCatching { store.installed() }.getOrNull()!=null) ModelState.INSTALLED else ModelState.MISSING }
    }
    suspend fun importBundle(uri: Uri) = withContext(Dispatchers.IO) {
        gate.withLock {
            releaseNative()
            mutableState.value=ModelState.IMPORTING
            var candidate: LlmModule?=null
            try {
                store.importBundle(uri,checkActive={ ensureActive() }) { installed ->
                    mutableState.value=ModelState.LOADING
                    candidate=createModule(installed)
                    check(candidate!!.load()==0) { "Model failed native validation; previous model retained" }
                }
                synchronized(ownership) { module=candidate }
                mutableState.value=ModelState.READY
            } catch(t: Throwable) {
                candidate?.resetNative()
                mutableState.value=if(runCatching { store.installed() }.getOrNull()!=null) ModelState.INSTALLED else ModelState.ERROR
                throw t
            }
        }
    }
    private fun createModule(installed: InstalledModel): LlmModule = LlmModule(
        LlmModuleConfig.create().modulePath(java.io.File(installed.folder,"model.pte").absolutePath)
            .tokenizerPath(java.io.File(installed.folder,"tokenizer.model").absolutePath)
            .temperature(0.3f).modelType(LlmModuleConfig.MODEL_TYPE_TEXT).build())
    fun generate(prompt: String): Flow<TutorEvent> = callbackFlow {
        require(prompt.toByteArray(Charsets.UTF_8).size<=1600)
        val run=Any()
        val worker=launch(Dispatchers.IO) {
            try {
                gate.withLock {
                    ensureActive()
                    synchronized(ownership) { owner=run }
                    try {
                    require("arm64-v8a" in Build.SUPPORTED_ABIS) { "An ARM64 Android device is required" }
                    val installed = requireNotNull(store.installed()) { "Import a verified Llama model bundle in Device settings" }
                    var loadMs=0L
                    if(module==null) {
                        mutableState.value=ModelState.VERIFYING
                        requireNotNull(store.verify())
                        ensureActive()
                        mutableState.value=ModelState.LOADING
                        val started=SystemClock.elapsedRealtime()
                        val loaded=createModule(installed)
                        synchronized(ownership) { module=loaded }
                        try {
                            check(loaded.load()==0) { "ExecuTorch could not load model. Re-export using v1.0.0" }
                        } catch(t: Throwable) { releaseNative(); throw t }
                        loadMs=SystemClock.elapsedRealtime()-started
                    }
                    ensureActive()
                    val active=requireNotNull(module)
                    active.resetContext()
                    mutableState.value=ModelState.GENERATING
                    val start=SystemClock.elapsedRealtime(); var ttft: Long?=null; var stats="{}"; var emitted=0
                    var limited=false
                    val overflow=AtomicBoolean(false)
                    val timedOut=AtomicBoolean(false)
                    val watchdog=launch(Dispatchers.Default) { delay(120_000); timedOut.set(true); stopOwned(run) }
                    // v1.0.0's config overload ignores maxNewTokens; use the verified native total-sequence limit.
                    val code=try { active.generate(prompt,2048,object: LlmCallback {
                        override fun onResult(result: String) {
                            if(result.isNotEmpty()) {
                                if(ttft==null) ttft=SystemClock.elapsedRealtime()-start
                                val piece=result.take((6000-emitted).coerceAtLeast(0)).let { if(it.lastOrNull()?.isHighSurrogate()==true) it.dropLast(1) else it }
                                emitted+=piece.length
                                if(piece.isNotEmpty() && !trySend(TutorEvent.Token(piece)).isSuccess) { overflow.set(true); active.stop() }
                                if(emitted>=6000 || piece.length<result.length) { limited=true; active.stop() }
                            }
                        }
                        override fun onStats(result: String) { stats=result }
                    },false) } finally { watchdog.cancel() }
                    ensureActive()
                    check(!timedOut.get()) { "Local generation exceeded two minutes and was stopped" }
                    check(!overflow.get()) { "Response delivery interrupted; retry a shorter request" }
                    check(code==0) { "ExecuTorch generation failed: $code" }
                    check(emitted>0) { "Model returned no text; check tokenizer/export compatibility" }
                    send(TutorEvent.Complete(SystemClock.elapsedRealtime()-start,ttft,stats,installed.hash,loadMs,limited))
                    mutableState.value=ModelState.READY
                    } catch(t: Throwable) {
                        if(t !is CancellationException) { releaseNative(); mutableState.value=ModelState.ERROR }
                        else mutableState.value=if(module!=null) ModelState.READY else ModelState.INSTALLED
                        throw t
                    } finally { synchronized(ownership) { if(owner===run) owner=null } }
                }
                close()
            } catch(t: Throwable) {
                close(t)
            }
        }
        awaitClose { stopOwned(run); worker.cancel() }
    }.buffer(512)
}
