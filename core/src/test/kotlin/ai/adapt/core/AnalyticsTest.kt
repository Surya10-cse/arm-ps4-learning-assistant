package ai.adapt.core
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AnalyticsTest {
    @Test fun emptyAnalyticsNeverInventsAccuracyOrAssessedSkills() {
        val result=LearningAnalytics.summarize(Goal.ML,emptyMap(),emptyList(),0,ZoneId.of("UTC"))
        assertNull(result.accuracy);assertEquals(0,result.assessedSkills);assertEquals(7,result.daily.size)
    }
    @Test fun currentGoalAndLocalDayScopeEvidence() {
        val now=Instant.parse("2026-11-11T04:00:00Z").toEpochMilli()
        val observations=listOf(Observation("logic",true,now),Observation("logic",false,now),Observation("android",true,now),Observation("logic",true,now+1))
        val result=LearningAnalytics.summarize(Goal.ML,mapOf("android" to Knowledge("android",0.99,4,4)),observations,now,ZoneId.of("Asia/Kolkata"))
        assertEquals(2,result.observations);assertEquals(0.5,result.accuracy!!,0.0001)
        assertEquals(0,result.masteredSkills);assertEquals(2,result.daily.last().answers)
    }
}
