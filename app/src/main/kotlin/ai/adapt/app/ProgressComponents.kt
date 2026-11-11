package ai.adapt.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ai.adapt.core.*
import ai.adapt.data.*
import kotlin.math.roundToInt

fun LearnerState.summary(): ProgressSummary {
    val goal=profile?.let { Goal.valueOf(it.goal) } ?: Goal.EDGE_AI
    val questionSkills=Curriculum.questions.associate { it.id to it.skillId }
    return LearningAnalytics.summarize(goal,mastery.associate { it.skillId to it.domain() },answers.mapNotNull { a -> questionSkills[a.questionId]?.let { Observation(it,a.correct,a.answeredAt) } },System.currentTimeMillis())
}

@Composable fun EvidenceOverview(summary: ProgressSummary) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf("Assessed" to "${summary.assessedSkills}/${summary.targetSkills}","Answers" to "${summary.observations}","Accuracy" to (summary.accuracy?.let { "${(it*100).roundToInt()}%" } ?: "—")).forEach { (label,value) ->
            Card(Modifier.weight(1f),shape=RoundedCornerShape(18.dp)) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(value,style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)
                Text(label,style=MaterialTheme.typography.labelMedium)
            } }
        }
    }
}

@Composable fun EvidenceChart(summary: ProgressSummary) {
    Card(shape=RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Last 7 days",style=MaterialTheme.typography.titleMedium)
        Text(if(summary.daily.all { it.answers==0 }) "Your first scored answer will appear here." else "Scored answers for this career goal. Mint marks correct responses.",style=MaterialTheme.typography.bodySmall)
        val maximum=(summary.daily.maxOfOrNull { it.answers } ?: 0).coerceAtLeast(1)
        Row(Modifier.fillMaxWidth().height(135.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            summary.daily.forEach { day -> Column(Modifier.weight(1f).semantics { contentDescription="${day.date}: ${day.answers} answers, ${day.correct} correct" },verticalArrangement=Arrangement.Bottom) {
                Text("${day.answers}",style=MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                val wrong=day.answers-day.correct
                if(wrong>0) Box(Modifier.fillMaxWidth().height((85f*wrong/maximum).dp).background(Color(0xFF667687),RoundedCornerShape(topStart=4.dp,topEnd=4.dp)))
                if(day.correct>0) Box(Modifier.fillMaxWidth().height((85f*day.correct/maximum).dp).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.height(8.dp))
                Text("${day.date.dayOfMonth}",style=MaterialTheme.typography.labelSmall)
            } }
        }
    } }
}

@Composable fun CompetencyMap(s: LearnerState,onOpen: (String)->Unit) {
    val goal=s.profile?.let { Goal.valueOf(it.goal) } ?: Goal.EDGE_AI
    val relevant=Curriculum.graph.relevant(goal)
    val mastery=s.mastery.associate { it.skillId to it.domain() }
    Card(shape=RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Your competency map",style=MaterialTheme.typography.titleLarge)
        Text("Prerequisites connect foundations to your goal. Due recall remains available when a prerequisite needs more work.",style=MaterialTheme.typography.bodySmall)
        Curriculum.skills.filter { it.id in relevant }.forEach { skill ->
            val ready=Curriculum.graph.eligible(skill.id,mastery)
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick={onOpen(skill.id)},contentPadding=PaddingValues(0.dp)) { Text(skill.title) }
                Text(if(skill.prerequisites.isEmpty()) "Foundation · ready to begin" else "Requires: ${skill.prerequisites.joinToString { Curriculum.graph.byId.getValue(it).title }}",style=MaterialTheme.typography.bodySmall)
                Text(if(ready) "Ready for learning" else "Build prerequisites first",style=MaterialTheme.typography.labelMedium,color=if(ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
        }
    } }
}
