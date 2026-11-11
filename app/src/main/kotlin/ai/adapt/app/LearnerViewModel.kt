package ai.adapt.app

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import ai.adapt.core.*
import ai.adapt.data.*
import ai.adapt.inference.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.security.MessageDigest

data class LearnerState(
    val ready: Boolean=false, val profile: ProfileEntity?=null, val mastery: List<MasteryEntity> = emptyList(),
    val roadmap: List<RoadmapEntity> = emptyList(), val revisions: List<RevisionEntity> = emptyList(),
    val misconceptions: List<MisconceptionEntity> = emptyList(), val answers: List<AnswerEntity> = emptyList(), val messages: List<MessageEntity> = emptyList(),
    val assessment: AssessmentItem?=null, val feedback: AnswerFeedback?=null, val assessmentDone: Boolean=false,
    val busy: Boolean=false, val generating: Boolean=false, val stream: String="", val error: String?=null,
    val modelState: ModelState=ModelState.MISSING, val selectedSkill: String="logic", val lastTiming: String="No inference measured"
)

class LearnerViewModel(application: Application,private val saved: SavedStateHandle): AndroidViewModel(application) {
    private val app=application as AdaptApplication
    private val repo=app.repository
    private val tutor=app.tutor
    private val mutable=MutableStateFlow(LearnerState(selectedSkill=saved.get<String>("selectedSkill")?.takeIf { it in Curriculum.graph.byId } ?: "logic"))
    val state=mutable.asStateFlow()
    private var generation: Job?=null
    init {
        viewModelScope.launch {
            try {
                repo.initialize()
                tutor.initialize()
                val profile=repo.dao.profile()
                val assessment=if(profile!=null) repo.resumeAssessment() else null
                mutable.update { it.copy(ready=true,profile=profile,assessment=assessment) }
                launch { repo.dao.profileFlow().guardRead().collect { value -> mutable.update { it.copy(profile=value) } } }
                launch { repo.dao.masteryFlow().guardRead().collect { value -> mutable.update { it.copy(mastery=value) } } }
                launch { repo.dao.roadmapFlow().guardRead().collect { value -> mutable.update { it.copy(roadmap=value) } } }
                launch { repo.dao.revisionsFlow().guardRead().collect { value -> mutable.update { it.copy(revisions=value) } } }
                launch { repo.dao.misconceptionsFlow().guardRead().collect { value -> mutable.update { it.copy(misconceptions=value) } } }
                launch { repo.dao.answersFlow().guardRead().collect { value -> mutable.update { it.copy(answers=value) } } }
                launch { repo.dao.messagesFlow().guardRead().collect { value -> mutable.update { it.copy(messages=value.reversed()) } } }
                launch { tutor.state.collect { value -> mutable.update { it.copy(modelState=value) } } }
            } catch(t: Exception) { mutable.update { it.copy(ready=true,error=t.message ?: "Database initialization failed") } }
        }
    }
    private fun <T> Flow<T>.guardRead(): Flow<T> = catch { error ->
        if(error is CancellationException) throw error
        mutable.update { it.copy(error="Local data could not be read. Restart the app; existing learner data has not been deleted.") }
    }
    private fun action(block: suspend () -> Unit) {
        if(mutable.value.busy) return
        mutable.update { it.copy(busy=true,error=null) }
        viewModelScope.launch {
            try { block() } catch(t: Exception) { if(t is CancellationException) throw t; mutable.update { it.copy(error=t.message ?: "Operation failed") } }
            finally { mutable.update { it.copy(busy=false) } }
        }
    }
    fun clearError() { mutable.update { it.copy(error=null) } }
    fun saveProfile(name: String,goal: Goal,minutes: Int,experience: Experience=Experience.BEGINNER,goalContext: String="") = action { repo.saveProfile(name,goal,minutes,experience,goalContext); mutable.update { it.copy(assessment=null,feedback=null,assessmentDone=false) } }
    fun selectSkill(id: String) { require(id in Curriculum.graph.byId); saved["selectedSkill"]=id; mutable.update { it.copy(selectedSkill=id) } }
    fun assess(focus: String?=null) = action {
        val item=repo.startAssessment(focus)
        mutable.update { it.copy(assessment=item,feedback=null,assessmentDone=item==null) }
    }
    fun answer(index: Int) = action {
        val item=mutable.value.assessment ?: return@action
        if(mutable.value.feedback!=null) return@action
        val result=repo.submit(item,index)
        mutable.update { it.copy(feedback=result) }
    }
    fun nextQuestion() = action {
        val session=mutable.value.assessment?.session ?: return@action
        val next=repo.continueAssessment(session)
        mutable.update { it.copy(assessment=next,feedback=null,assessmentDone=next==null) }
    }
    fun refresh() = action { repo.refresh() }
    fun importModel(uri: Uri) = action {
        check(!mutable.value.generating) { "Stop the tutor before importing" }
        tutor.importBundle(uri)
    }
    fun unload() = action { check(!mutable.value.generating); tutor.unload() }
    fun stop() { tutor.stop(); generation?.cancel() }
    fun onBackground() {
        stop()
        val active=generation
        app.applicationScope.launch { runCatching { active?.join(); tutor.unload() } }
    }
    fun ask(message: String, mode: TutorMode, structured: Boolean) {
        if(mutable.value.generating || mutable.value.busy || message.isBlank()) return
        val snapshot=mutable.value
        val profile=snapshot.profile ?: return
        val skill=Curriculum.graph.byId.getValue(snapshot.selectedSkill)
        mutable.update { it.copy(generating=true,stream="",error=null) }
        generation=viewModelScope.launch {
            val id=UUID.randomUUID().toString(); val createdAt=System.currentTimeMillis()
            var output=""
            var status="complete"
            var limited=false
            try {
                val history=repo.dao.recentMessages(skill.id).reversed().map { it.role to it.text }
                val knowledge=snapshot.mastery.firstOrNull { it.skillId==skill.id }
                val prompt=TutorPrompt.build(TutorRequest(mode,Goal.valueOf(profile.goal),skill,knowledge?.probability ?: 0.2,snapshot.misconceptions.filter { it.skillId==skill.id }.map { it.tag },history,message,structured,Experience.valueOf(profile.experience),profile.goalContext,knowledge!=null))
                repo.dao.saveMessage(MessageEntity(UUID.randomUUID().toString(),"user",message.take(2000),mode.name,skill.id,createdAt,"complete"))
                repo.dao.saveMessage(MessageEntity(id,"assistant","",mode.name,skill.id,createdAt+1,"streaming"))
                tutor.generate(prompt).collect { event ->
                    when(event) {
                        is TutorEvent.Token -> { output+=event.text; if(!structured) mutable.update { it.copy(stream=output) } }
                        is TutorEvent.Complete -> {
                            limited=event.textLimited
                            runCatching { recordMeasurement(event,prompt,"tutor") }.onFailure { mutable.update { it.copy(error="Response generated, but its performance record could not be saved") } }
                            mutable.update { it.copy(lastTiming="Load ${event.loadMs} ms · first text ${event.ttftMs ?: "unavailable"} ms · generation ${event.elapsedMs} ms") }
                        }
                    }
                }
                if(structured) {
                    try { val doc=TutorJson.parse(output); output=doc.displayText() }
                    catch(t: Exception) { status="invalid_json"; output="Structured response could not be validated. Retry with a shorter request or use plain text."; mutable.update { it.copy(error="The model returned invalid teaching JSON. It was not displayed or stored.") } }
                }
                if(limited && status=="complete") status="limited"
            } catch(t: CancellationException) { status="interrupted"; if(structured) output="Structured generation was interrupted before validation." }
            catch(t: Throwable) { status="error"; if(structured) output="Structured generation failed before validation."; mutable.update { it.copy(error=t.message ?: "Local inference failed") } }
            finally {
                withContext(NonCancellable+Dispatchers.IO) {
                    runCatching { repo.dao.saveMessage(MessageEntity(id,"assistant",output,mode.name,skill.id,createdAt+1,status)) }
                }
                mutable.update { it.copy(generating=false,stream="") }
            }
        }
    }
    private suspend fun recordMeasurement(event: TutorEvent.Complete,prompt: String,purpose: String) = withContext(Dispatchers.IO) {
        val memory=Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val pm=app.getSystemService(PowerManager::class.java)
        val thermal=if(Build.VERSION.SDK_INT>=29) pm.currentThermalStatus else -1
        val promptBytes=prompt.toByteArray(Charsets.UTF_8)
        val hash=MessageDigest.getInstance("SHA-256").digest(promptBytes).joinToString("") { "%02x".format(it) }
        val quant=withContext(Dispatchers.IO) { tutor.store.installed()?.quantization ?: "unknown" }
        repo.dao.saveBenchmark(BenchmarkEntity(UUID.randomUUID().toString(),System.currentTimeMillis(),event.modelHash,event.elapsedMs,event.ttftMs,event.nativeStats,"${Build.MANUFACTURER} ${Build.MODEL}; ${Build.SUPPORTED_ABIS.joinToString()}",memory.totalPss,thermal,event.loadMs,hash,promptBytes.size,purpose,quant))
    }
    fun runBenchmark() {
        if(mutable.value.generating || mutable.value.busy) return
        mutable.update { it.copy(generating=true,error=null,stream="Benchmark starting") }
        generation=viewModelScope.launch {
            try {
                tutor.unload()
                val prompt=TutorPrompt.build(TutorRequest(TutorMode.TEACH,Goal.EDGE_AI,Curriculum.skills.last(),0.2,emptyList(),emptyList(),"Explain why XNNPACK CPU inference must be benchmarked on the target ARM phone."))
                repeat(8) { index ->
                    ensureActive()
                    val purpose=when(index) { 0 -> "cold"; 1,2 -> "warmup"; else -> "measured" }
                    mutable.update { it.copy(stream="Benchmark ${index+1}/8 · $purpose") }
                    tutor.generate(prompt).collect { event -> if(event is TutorEvent.Complete) recordMeasurement(event,prompt,purpose) }
                }
                mutable.update { it.copy(lastTiming="Benchmark suite completed: 1 cold, 2 warmup, 5 measured runs. Export records for analysis.") }
            } catch(_: CancellationException) { mutable.update { it.copy(lastTiming="Benchmark interrupted; only completed runs were stored") } }
            catch(t: Throwable) { mutable.update { it.copy(error=t.message ?: "Benchmark failed") } }
            finally { mutable.update { it.copy(generating=false,stream="") } }
        }
    }
    fun exportBenchmarks(uri: Uri) = action {
        val rows=repo.dao.benchmarks()
        val json=JSONObject().put("version",1).put("measurements",JSONArray().apply {
            rows.forEach { b -> put(JSONObject().put("id",b.id).put("createdAt",b.createdAt).put("modelSha256",b.modelHash).put("generationMs",b.elapsedMs).put("firstTextMs",b.ttftMs ?: JSONObject.NULL).put("nativeStats",runCatching { JSONObject(b.nativeStatsJson) }.getOrElse { JSONObject().put("raw",b.nativeStatsJson) }).put("device",b.device).put("postGenerationPssKb",b.memoryPssKb).put("thermalStatus",b.thermalStatus).put("loadMs",b.loadMs).put("promptSha256",b.promptSha256).put("promptBytes",b.promptBytes).put("purpose",b.purpose).put("quantization",b.quantization).put("maxSequenceTokens",2048).put("outputCharacterLimit",6000).put("temperature",0.3)) }
        })
        withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri).use { requireNotNull(it).write(json.toString(2).toByteArray()) } }
    }
    override fun onCleared() { stop(); app.applicationScope.launch { runCatching { generation?.join(); tutor.unload() } }; super.onCleared() }
}
