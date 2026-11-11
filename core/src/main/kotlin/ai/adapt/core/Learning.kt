package ai.adapt.core

import kotlin.math.abs
import kotlin.math.roundToInt

const val CONTRACT_VERSION = 1
const val DAY_MS = 86_400_000L
enum class Goal(val title: String) { ANDROID("Android developer"), EDGE_AI("Edge AI engineer"), ML("ML engineer") }
enum class TutorMode { TEACH, SOCRATIC, DEBUG, PRACTICE, REVIEW }
enum class Experience(val title: String) { BEGINNER("Starting out"), INTERMEDIATE("Some experience"), EXPERIENCED("Experienced") }
data class Skill(val id: String, val title: String, val prerequisites: Set<String>, val minutes: Int, val difficulty: Double, val relevance: Map<Goal, Double>, val lesson: String)
data class Question(val id: String, val skillId: String, val prompt: String, val options: List<String>, val correct: Int, val explanation: String, val misconception: String, val difficulty: Double = 0.5) {
    init { require(options.size in 2..6 && correct in options.indices && difficulty in 0.0..1.0) }
}
data class Knowledge(val skillId: String, val probability: Double = 0.2, val attempts: Int = 0, val correct: Int = 0) {
    init { require(probability.isFinite() && probability in 0.0..1.0); require(attempts >= 0 && correct in 0..attempts) }
}
data class Revision(val skillId: String, val dueAt: Long, val intervalDays: Int = 0, val ease: Double = 2.3, val streak: Int = 0)
data class Activity(val skillId: String, val title: String, val minutes: Int, val priority: Double, val reason: String, val kind: String)

class CompetencyGraph(val skills: List<Skill>) {
    val byId = skills.associateBy { it.id }
    init {
        require(byId.size == skills.size && skills.isNotEmpty())
        val visiting = mutableSetOf<String>(); val visited = mutableSetOf<String>()
        fun visit(id: String) {
            require(id in byId) { "Unknown prerequisite $id" }
            require(id !in visiting) { "Prerequisite cycle at $id" }
            if (id in visited) return
            visiting += id; byId.getValue(id).prerequisites.forEach(::visit); visiting -= id; visited += id
        }
        skills.forEach { require(it.minutes > 0 && it.difficulty in 0.0..1.0); visit(it.id) }
    }
    fun eligible(id: String, mastery: Map<String, Knowledge>) = byId.getValue(id).prerequisites.all { (mastery[it]?.probability ?: 0.2) >= 0.7 }
    fun blockers(id: String, mastery: Map<String, Knowledge>) = byId.getValue(id).prerequisites.filter { (mastery[it]?.probability ?: 0.2) < 0.7 }
    fun relevant(goal: Goal): Set<String> {
        val result = mutableSetOf<String>()
        fun add(id: String) { if(result.add(id)) byId.getValue(id).prerequisites.forEach(::add) }
        skills.filter { (it.relevance[goal] ?: 0.0) > 0 }.forEach { add(it.id) }
        return result
    }
}

/** BKT-inspired evidence update; no LLM path can create an observation. */
object MasteryEstimator {
    fun update(old: Knowledge, correct: Boolean, optionCount: Int = 4): Knowledge {
        require(optionCount in 2..6)
        val p = old.probability; val guess = 1.0/optionCount; val slip = 0.10; val learn = 0.08
        val posterior = if (correct) p * (1-slip) / (p*(1-slip)+(1-p)*guess)
            else p * slip / (p*slip+(1-p)*(1-guess))
        val next = if(correct) (posterior+(1-posterior)*learn).coerceIn(0.0,0.99) else minOf(p,posterior).coerceIn(0.0,0.99)
        return old.copy(probability = next, attempts = old.attempts+1, correct = old.correct+if(correct) 1 else 0)
    }
}

object RevisionScheduler {
    fun update(old: Revision?, skillId: String, correct: Boolean, now: Long): Revision {
        // Multiple correct items in one sitting are not multiple spaced recalls.
        if(old!=null && correct && now<old.dueAt) return old
        val streak = if(correct) (old?.streak ?: 0)+1 else 0
        val ease = ((old?.ease ?: 2.3)+if(correct) 0.05 else -0.2).coerceIn(1.3,2.8)
        val interval = if(!correct) 1 else when(streak) { 1 -> 1; 2 -> 3; else -> ((old?.intervalDays ?: 1)*ease).roundToInt().coerceIn(1,90) }
        return Revision(skillId, now+interval*DAY_MS,interval,ease,streak)
    }
}

class LearningEngine(val graph: CompetencyGraph, val questions: List<Question>) {
    init { require(questions.map { it.id }.distinct().size == questions.size); require(questions.all { it.skillId in graph.byId }) }
    fun nextQuestion(goal: Goal, mastery: Map<String, Knowledge>, seen: Set<String>, focus: String? = null, recent: Set<String> = emptySet()): Question? =
        questions.filter { it.id !in seen && (if(focus!=null) it.skillId==focus else it.skillId in graph.relevant(goal)) }
            .sortedWith(compareBy<Question> { it.id in recent }.thenByDescending {
                val k = mastery[it.skillId] ?: Knowledge(it.skillId)
                val uncertainty = 1-abs(k.probability-0.5)*2
                val coverage = 1.0/(k.attempts+1)
                uncertainty*0.25+coverage*0.4+(graph.byId.getValue(it.skillId).relevance[goal] ?: 0.3)*0.2-abs(it.difficulty-k.probability)*0.15
            }.thenBy { it.id }).firstOrNull()
    fun roadmap(goal: Goal, mastery: Map<String, Knowledge>, revisions: List<Revision>, minutes: Int, now: Long): List<Activity> {
        require(minutes in 5..240)
        val due = revisions.filter { it.dueAt <= now }.map { it.skillId }.toSet()
        val candidates = graph.skills.filter { it.id in graph.relevant(goal) && (it.id in due || graph.eligible(it.id,mastery)) }
            .filter { (mastery[it.id]?.probability ?: 0.2) < 0.85 || it.id in due }
            .map { s ->
                val p = mastery[s.id]?.probability ?: 0.2
                val review = s.id in due
                val time = minOf(if(review) 5 else s.minutes,minutes)
                Activity(s.id,s.title,time, (1-p)*0.5+(s.relevance[goal] ?: 0.3)*0.3+(if(review) 0.3 else 0.0)+(1-abs(s.difficulty-p))*0.2,
                    if(review) "Recall due; strengthen retention" else if(time<s.minutes) "$time-minute study segment; prerequisites ready" else "${(p*100).roundToInt()}% estimate; prerequisites ready", if(review) "REVIEW" else "LEARN")
            }.sortedWith(compareByDescending<Activity> { it.priority }.thenBy { it.skillId })
        var remaining = minutes
        return candidates.filter { if(it.minutes <= remaining) { remaining -= it.minutes; true } else false }
    }
    fun gaps(goal: Goal, mastery: Map<String, Knowledge>) = graph.skills.filter { it.id in graph.relevant(goal) && (mastery[it.id]?.probability ?: 0.2) < 0.85 }
}
