package ai.adapt.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

data class Observation(val skillId: String,val correct: Boolean,val answeredAt: Long)
data class EvidenceDay(val date: LocalDate,val answers: Int,val correct: Int)
data class ProgressSummary(val targetSkills: Int,val assessedSkills: Int,val masteredSkills: Int,val observations: Int,val correct: Int,val daily: List<EvidenceDay>) {
    val accuracy: Double? get()=if(observations==0) null else correct.toDouble()/observations
}
object LearningAnalytics {
    fun summarize(goal: Goal,mastery: Map<String,Knowledge>,observations: List<Observation>,now: Long,zone: ZoneId=ZoneId.systemDefault()): ProgressSummary {
        val relevant=Curriculum.graph.relevant(goal)
        val evidence=observations.filter { it.skillId in relevant && it.answeredAt<=now }
        val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val daily=(6 downTo 0).map { offset ->
            val date=today.minusDays(offset.toLong())
            val values=evidence.filter { Instant.ofEpochMilli(it.answeredAt).atZone(zone).toLocalDate()==date }
            EvidenceDay(date,values.size,values.count { it.correct })
        }
        return ProgressSummary(relevant.size,mastery.values.count { it.skillId in relevant && it.attempts>0 },mastery.values.count { it.skillId in relevant && it.attempts>0 && it.probability>=0.85 },evidence.size,evidence.count { it.correct },daily)
    }
}

/** Stable within a session, with original option indices retained for authoritative scoring. */
fun presentationOptions(question: Question,sessionId: String): List<Pair<Int,String>> =
    question.options.mapIndexed { index,text -> index to text }.shuffled(Random(sessionId.hashCode() xor question.id.hashCode()))
