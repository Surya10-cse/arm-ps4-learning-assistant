package ai.adapt.core

import org.junit.Assert.*
import org.junit.Test

class LearningTest {
    private val engine = Curriculum.engine
    @Test fun correctEvidenceIncreasesMasteryWithoutCertainty() {
        val k = MasteryEstimator.update(Knowledge("logic"),true)
        assertTrue(k.probability > 0.2 && k.probability < 0.7)
        assertEquals(1,k.attempts)
    }
    @Test fun incorrectEvidenceReducesEstablishedMastery() {
        val k = MasteryEstimator.update(Knowledge("logic",0.8,5,4),false)
        assertTrue(k.probability < 0.8); assertEquals(4,k.correct)
    }
    @Test fun repeatedCorrectEvidenceUnlocksPrerequisiteButTutorCannot() {
        var k = Knowledge("logic")
        repeat(4) { k = MasteryEstimator.update(k,true) }
        assertTrue(Curriculum.graph.eligible("kotlin",mapOf("logic" to k)))
        assertFalse(Curriculum.graph.eligible("android",mapOf("logic" to k)))
    }
    @Test fun roadmapRespectsTimeAndPrerequisites() {
        val plan = engine.roadmap(Goal.EDGE_AI,emptyMap(),emptyList(),30,0)
        assertTrue(plan.isNotEmpty()); assertTrue(plan.sumOf { it.minutes } <= 30)
        assertEquals(setOf("logic"),plan.map { it.skillId }.toSet())
    }
    @Test fun goalsIncludeTransitivePrerequisites() {
        assertTrue("logic" in Curriculum.graph.relevant(Goal.EDGE_AI))
        assertFalse("android" in Curriculum.graph.relevant(Goal.ML))
    }
    @Test fun selectorDoesNotRepeatWithinSessionAndTerminates() {
        val seen = mutableSetOf<String>()
        while(true) { val q = engine.nextQuestion(Goal.ANDROID,emptyMap(),seen) ?: break; assertTrue(seen.add(q.id)) }
        assertEquals(Curriculum.questions.count { it.skillId in Curriculum.graph.relevant(Goal.ANDROID) },seen.size)
    }
    @Test fun revisionFailureResetsIntervalAndIsDueNextDay() {
        val r = RevisionScheduler.update(Revision("logic",0,12,2.5,4),"logic",false,100)
        assertEquals(1,r.intervalDays); assertEquals(0,r.streak); assertEquals(100+DAY_MS,r.dueAt)
    }
    @Test fun earlyPracticeDoesNotArtificiallyStretchRecallInterval() {
        val old=Revision("logic",200,3,2.3,2)
        assertEquals(old,RevisionScheduler.update(old,"logic",true,100))
        val due=RevisionScheduler.update(old,"logic",true,200)
        assertEquals(3,due.streak)
        assertTrue(due.intervalDays>old.intervalDays)
    }
    @Test fun dueMasteredSkillGetsReviewActivity() {
        val m = mapOf("logic" to Knowledge("logic",0.99,10,10))
        val plan = engine.roadmap(Goal.ANDROID,m,listOf(Revision("logic",0)),30,1)
        assertTrue(plan.any { it.skillId == "logic" && it.kind == "REVIEW" })
    }
    @Test fun malformedGraphsAreRejected() {
        val s = Curriculum.skills.first()
        assertThrows(IllegalArgumentException::class.java) { CompetencyGraph(listOf(s.copy(prerequisites=setOf(s.id)))) }
        assertThrows(IllegalArgumentException::class.java) { CompetencyGraph(listOf(s.copy(prerequisites=setOf("missing")))) }
    }
    @Test fun learningJourneyAdaptsRoadmap() {
        var k = Knowledge("logic")
        val before = engine.roadmap(Goal.ANDROID,emptyMap(),emptyList(),60,0)
        repeat(4) { k = MasteryEstimator.update(k,true) }
        val after = engine.roadmap(Goal.ANDROID,mapOf("logic" to k),emptyList(),60,0)
        assertTrue(before.none { it.skillId == "kotlin" }); assertTrue(after.any { it.skillId == "kotlin" })
    }
    @Test fun wrongEvidenceNeverIncreasesAnyValidPrior() {
        (0..100).forEach { value ->
            val prior=value/100.0
            val next=MasteryEstimator.update(Knowledge("logic",prior),false)
            assertTrue("Wrong answer increased $prior",next.probability<=prior)
        }
    }
    @Test fun chanceCorrectionUsesActualOptionCount() {
        val prior=Knowledge("logic")
        assertTrue(MasteryEstimator.update(prior,true,6).probability>MasteryEstimator.update(prior,true,2).probability)
        assertThrows(IllegalArgumentException::class.java) { MasteryEstimator.update(prior,true,1) }
    }
    @Test fun minimumBudgetStillHasAnActionableStudyChunk() {
        val plan=engine.roadmap(Goal.EDGE_AI,emptyMap(),emptyList(),5,0)
        assertEquals(1,plan.size);assertEquals(5,plan.single().minutes)
    }
    @Test fun dueReviewSurvivesADecliningPrerequisite() {
        val plan=engine.roadmap(Goal.ANDROID,emptyMap(),listOf(Revision("android",0)),30,1)
        assertTrue(plan.any { it.skillId=="android" && it.kind=="REVIEW" })
    }
    @Test fun focusedPracticeCanExploreSkillsOutsideCurrentGoal() {
        assertNotNull(engine.nextQuestion(Goal.ML,emptyMap(),emptySet(),"android"))
    }
    @Test fun previouslySeenItemsAreDeferredAcrossSessions() {
        val first=requireNotNull(engine.nextQuestion(Goal.ANDROID,emptyMap(),emptySet(),"logic"))
        val next=requireNotNull(engine.nextQuestion(Goal.ANDROID,emptyMap(),emptySet(),"logic",setOf(first.id)))
        assertNotEquals(first.id,next.id)
        val options=presentationOptions(first,"stable-session")
        assertEquals(options,presentationOptions(first,"stable-session"))
        assertEquals(first.options.indices.toSet(),options.map { it.first }.toSet())
    }
    @Test fun focusedQuestionDifficultyTracksObservedMastery() {
        val low=requireNotNull(engine.nextQuestion(Goal.ANDROID,mapOf("logic" to Knowledge("logic",0.1)),emptySet(),"logic"))
        val high=requireNotNull(engine.nextQuestion(Goal.ANDROID,mapOf("logic" to Knowledge("logic",0.9)),emptySet(),"logic"))
        assertTrue(high.difficulty>low.difficulty)
    }
}
