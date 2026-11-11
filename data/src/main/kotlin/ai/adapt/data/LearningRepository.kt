package ai.adapt.data

import ai.adapt.core.*
import androidx.room.withTransaction
import org.json.JSONArray
import java.util.UUID

fun MasteryEntity.domain() = Knowledge(skillId,probability,attempts,correct)
fun RevisionEntity.domain() = Revision(skillId,dueAt,intervalDays,ease,streak)
data class AssessmentItem(val session: SessionEntity, val question: Question, val answered: Int)
data class AnswerFeedback(val correct: Boolean, val explanation: String, val mastery: Double)

class LearningRepository(val db: AdaptDatabase, private val now: () -> Long = System::currentTimeMillis) {
    val dao = db.learning()
    private val engine = Curriculum.engine
    suspend fun initialize() = db.withTransaction {
        dao.seed(Curriculum.questions.map { QuestionEntity(it.id,it.skillId,it.prompt,JSONArray(it.options).toString(),it.correct,it.explanation,it.misconception,it.difficulty,Curriculum.VERSION) })
        dao.recoverMessages()
        if(dao.profile()!=null) refreshRoadmap()
    }
    suspend fun saveProfile(name: String, goal: Goal, minutes: Int, experience: Experience=Experience.BEGINNER, goalContext: String="") = db.withTransaction {
        require(name.trim().length in 1..60 && minutes in 5..240 && goalContext.length<=160)
        dao.activeSession()?.let { dao.saveSession(it.copy(completedAt=now())) }
        dao.saveProfile(ProfileEntity(name=name.trim(),goal=goal.name,dailyMinutes=minutes,createdAt=dao.profile()?.createdAt ?: now(),experience=experience.name,goalContext=goalContext.trim()))
        refreshRoadmap()
    }
    suspend fun startAssessment(focus: String? = null): AssessmentItem? = db.withTransaction {
        require(focus == null || focus in Curriculum.graph.byId)
        val active = dao.activeSession()
        if(active != null && active.focusSkill != focus) dao.saveSession(active.copy(completedAt=now()))
        val session = active?.takeIf { it.focusSkill == focus } ?: SessionEntity(UUID.randomUUID().toString(),now(),focusSkill=focus).also { dao.saveSession(it) }
        next(session)
    }
    private suspend fun next(session: SessionEntity): AssessmentItem? {
        val profile = requireNotNull(dao.profile()) { "Create a learner profile first" }
        val answers = dao.answers(session.id)
        val limit = if(session.focusSkill==null) 8 else 4
        val question = if(answers.size < limit) engine.nextQuestion(Goal.valueOf(profile.goal),dao.mastery().associate { it.skillId to it.domain() },answers.map { it.questionId }.toSet(),session.focusSkill,dao.recentQuestionIds().toSet()) else null
        if(question==null) { dao.saveSession(session.copy(completedAt=now())); return null }
        return AssessmentItem(session,question,answers.size)
    }
    suspend fun submit(item: AssessmentItem, selected: Int): AnswerFeedback = db.withTransaction {
        val active = requireNotNull(dao.activeSession()) { "Assessment already completed" }
        require(active.id == item.session.id)
        val expected = requireNotNull(next(active)) { "Assessment complete" }
        require(expected.question.id == item.question.id) { "Stale assessment question" }
        val q = expected.question
        require(selected in q.options.indices)
        val correct = selected == q.correct
        check(dao.saveAnswer(AnswerEntity(active.id,q.id,selected,correct,now())) != -1L) { "Answer already recorded" }
        val old = dao.mastery().firstOrNull { it.skillId==q.skillId }?.domain() ?: Knowledge(q.skillId)
        val updated = MasteryEstimator.update(old,correct,q.options.size)
        dao.saveMastery(MasteryEntity(updated.skillId,updated.probability,updated.attempts,updated.correct,now()))
        if(!correct) {
            val oldTag = dao.misconception(q.skillId,q.misconception)
            dao.saveMisconception(MisconceptionEntity(q.skillId,q.misconception,(oldTag?.count ?: 0)+1,now()))
        }
        val review = RevisionScheduler.update(dao.revisions().firstOrNull { it.skillId==q.skillId }?.domain(),q.skillId,correct,now())
        dao.saveRevision(RevisionEntity(review.skillId,review.dueAt,review.intervalDays,review.ease,review.streak))
        refreshRoadmap()
        AnswerFeedback(correct,q.explanation,updated.probability)
    }
    suspend fun continueAssessment(session: SessionEntity) = db.withTransaction {
        val active=requireNotNull(dao.activeSession()) { "Assessment already completed" }
        require(active.id==session.id) { "This assessment is no longer active" }
        next(active)
    }
    suspend fun resumeAssessment() = db.withTransaction { dao.activeSession()?.let { next(it) } }
    private suspend fun refreshRoadmap() {
        val p = dao.profile() ?: return
        val stamp = now()
        val plan = engine.roadmap(Goal.valueOf(p.goal),dao.mastery().associate { it.skillId to it.domain() },dao.revisions().map { it.domain() },p.dailyMinutes,stamp)
        // Delete current snapshot as part of caller transaction, including when plan becomes empty.
        dao.pruneRoadmaps(Long.MAX_VALUE)
        dao.saveRoadmap(plan.mapIndexed { i,a -> RoadmapEntity(stamp,i,a.skillId,a.title,a.minutes,a.priority,a.reason,a.kind) })
    }
    suspend fun refresh() = db.withTransaction { refreshRoadmap() }
}
