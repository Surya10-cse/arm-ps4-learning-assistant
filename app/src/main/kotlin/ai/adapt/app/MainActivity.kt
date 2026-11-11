package ai.adapt.app

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.adapt.core.*
import ai.adapt.data.*
import ai.adapt.inference.ModelState
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val Mint=Color(0xFF9CF5CF)
private val Ink=Color(0xFF0B1117)
private val Panel=Color(0xFF151F29)
private val Muted=Color(0xFF9AAEBD)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=Mint,background=Ink,surface=Panel,onPrimary=Ink,onSurface=Color(0xFFE7F0F5),secondary=Color(0xFFACB8FF))) {
                val vm: LearnerViewModel=viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                val owner=LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val observer=LifecycleEventObserver { _,event -> if(event==Lifecycle.Event.ON_STOP) vm.onBackground(); if(event==Lifecycle.Event.ON_RESUME) vm.refresh() }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }
                AdaptScreen(state,vm)
            }
        }
    }
}

@Composable
private fun AdaptScreen(s: LearnerState,vm: LearnerViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs=listOf("Home","Learn","Tutor","Progress","Device")
    val listState=rememberLazyListState()
    LaunchedEffect(tab) { listState.scrollToItem(0) }
    Scaffold(containerColor=Ink,bottomBar={ if(s.profile!=null) NavigationBar(containerColor=Panel) {
        tabs.forEachIndexed { index,label -> NavigationBarItem(modifier=Modifier.testTag("nav-$label"),selected=tab==index,onClick={tab=index},icon={ NavigationGlyph(index,tab==index) },label={Text(label)}) }
    } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal=22.dp).testTag("screen-list"),state=listState,verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(vertical=22.dp)) {
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("ADAPT—AI",fontWeight=FontWeight.ExtraBold,color=Mint,style=MaterialTheme.typography.titleLarge); Text("ON DEVICE",color=Muted,style=MaterialTheme.typography.labelSmall) } }
            if(s.error!=null) item { Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF3A2027))) { Column(Modifier.padding(16.dp)) { Text(s.error); TextButton(onClick=vm::clearError) { Text("Dismiss") } } } }
            if(s.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(!s.ready) item { CircularProgressIndicator() }
            else if(s.profile==null) item { ProfileForm(null,vm,!s.busy) }
            else when(tab) {
                0 -> {
                    item { Heading("Your next chapter.","Hello ${s.profile.name}. Build towards ${Goal.valueOf(s.profile.goal).title.lowercase()} with ${s.profile.dailyMinutes} focused minutes a day.") }
                    item { EvidenceOverview(s.summary()) }
                    item { Hero("A path that grows with you","${s.summary().masteredSkills} / ${s.summary().targetSkills} target competencies mastered") {
                        Button(onClick={vm.assess();tab=1},enabled=!s.busy) { Text(if(s.answers.isEmpty()) "Discover my starting point" else "Reassess my skills") }
                    } }
                    item { Heading("Today's roadmap","Scored evidence decides what comes next.") }
                    if(s.roadmap.isEmpty()) item { Text("Your target skills are ready for maintenance. Open Learn for focused practice or return when a recall is due.",color=Muted) }
                    items(s.roadmap,key={it.skillId}) { a -> ActivityCard(a,onLearn={vm.selectSkill(a.skillId);tab=1},onTutor={vm.selectSkill(a.skillId);tab=2}) }
                    item { Text("PRIVATE BY DESIGN · Core learning works without a model. AI tutoring requires an imported Llama bundle.",style=MaterialTheme.typography.labelSmall,color=Muted) }
                }
                1 -> {
                    item { Heading("Learning studio","Read, practise, then demonstrate what you know.") }
                    if(s.assessment!=null) {
                        val item=s.assessment
                        item { Hero("${if(item.session.focusSkill==null) "Diagnostic" else "Focused practice"} · ${item.answered+1}",Curriculum.graph.byId.getValue(item.question.skillId).title) {
                            LinearProgressIndicator(progress={item.answered.toFloat()/(if(item.session.focusSkill==null) 8 else 4)},modifier=Modifier.fillMaxWidth())
                            Text(item.question.prompt,style=MaterialTheme.typography.titleMedium,modifier=Modifier.testTag("question-prompt"))
                            presentationOptions(item.question,item.session.id).forEach { (index,option) -> OutlinedButton(onClick={vm.answer(index)},enabled=!s.busy && s.feedback==null,modifier=Modifier.fillMaxWidth().testTag("option-$index")) { Text(option) } }
                            s.feedback?.let { f ->
                                Text(if(f.correct) "Correct" else "Let's work on this",color=Mint,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("answer-feedback"))
                                Text(f.explanation)
                                Text("Updated estimate: ${(f.mastery*100).roundToInt()}%",color=Muted)
                                Button(onClick=vm::nextQuestion,enabled=!s.busy,modifier=Modifier.testTag("assessment-continue")) { Text("Continue") }
                            }
                        } }
                    } else {
                        if(s.assessmentDone) item { Hero("Evidence recorded","Your mastery, skill gaps, revision dates and roadmap have been updated.") { Button(onClick={tab=0}) { Text("See my adapted roadmap") } } }
                        item { Button(onClick={vm.assess()},enabled=!s.busy,modifier=Modifier.testTag("start-diagnostic")) { Text("Start adaptive diagnostic · up to 8 questions") } }
                    }
                    item { SkillPicker(s.selectedSkill,vm::selectSkill) }
                    val skill=Curriculum.graph.byId.getValue(s.selectedSkill)
                    item { Hero(skill.title,"Offline lesson · ${skill.minutes} minutes") {
                        Text(skill.lesson,style=MaterialTheme.typography.bodyLarge)
                        val blockers=Curriculum.graph.blockers(skill.id,s.mastery.associate { it.skillId to it.domain() })
                        if(blockers.isNotEmpty()) Text("Suggested prerequisites: ${blockers.joinToString { Curriculum.graph.byId.getValue(it).title }}",color=Muted)
                        Button(onClick={vm.assess(skill.id)},enabled=!s.busy,modifier=Modifier.testTag("start-focused")) { Text("Check understanding · 4 questions") }
                        OutlinedButton(onClick={tab=2}) { Text("Discuss with local tutor") }
                    } }
                    val due=s.revisions.filter { it.dueAt<=System.currentTimeMillis() }
                    item { Heading("Recall queue","${due.size} competencies due now") }
                    items(s.revisions,key={it.skillId}) { r -> Card { Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) { Text(Curriculum.graph.byId.getValue(r.skillId).title); Text("Due ${DateFormat.getDateInstance().format(Date(r.dueAt))}",color=Muted) }
                        TextButton(onClick={vm.selectSkill(r.skillId);vm.assess(r.skillId)},enabled=!s.busy) { Text("Recall") }
                    } } }
                }
                2 -> {
                    item { Heading("Think it through.","Llama 3.2 1B · ${s.modelState.name.lowercase()}") }
                    item { SkillPicker(s.selectedSkill,vm::selectSkill) }
                    item { TutorComposer(s,vm) }
                    items(s.messages.filter { it.skillId==s.selectedSkill && it.status!="streaming" },key={it.id}) { message ->
                        Card(colors=CardDefaults.cardColors(containerColor=if(message.role=="user") Color(0xFF1C3034) else Panel)) { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("${if(message.role=="user") "You" else "Local tutor"} · ${message.mode.lowercase().replaceFirstChar { it.uppercase() }}${if(message.status=="complete") "" else " · ${message.status.replace('_',' ')}"}",color=Muted,style=MaterialTheme.typography.labelSmall)
                            Text(message.text.ifBlank { if(message.status=="streaming") "Generating locally…" else "No text returned" })
                        } }
                    }
                    if(s.generating) item { Hero("Local generation","${s.modelState.name.lowercase()}") { Text(s.stream.ifBlank { "Loading model / evaluating prompt…" }); OutlinedButton(onClick=vm::stop) { Text("Stop") } } }
                    item { Text(s.lastTiming,color=Muted,style=MaterialTheme.typography.labelSmall) }
                }
                3 -> {
                    val relevant=Curriculum.graph.relevant(Goal.valueOf(s.profile.goal))
                    item { Heading("Evidence of progress","${s.summary().observations} goal-relevant answers · ${s.summary().correct} correct") }
                    item { EvidenceOverview(s.summary()) }
                    item { EvidenceChart(s.summary()) }
                    item { CompetencyMap(s) { id -> vm.selectSkill(id);tab=1 } }
                    item { Text("Mastery is a BKT-inspired estimate. Unassessed skills start at a 20% prior; this is not a validated proficiency certificate.",color=Muted) }
                    items(Curriculum.skills.filter { it.id in relevant },key={it.id}) { skill ->
                        val k=s.mastery.firstOrNull { it.skillId==skill.id }
                        Card { Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(skill.title,modifier=Modifier.weight(1f)); Text(if(k==null) "Unassessed" else "${(k.probability*100).roundToInt()}%",color=Mint) }
                            LinearProgressIndicator(progress={ (k?.probability ?: 0.2).toFloat() },modifier=Modifier.fillMaxWidth())
                            Text("${k?.attempts ?: 0} observations · ${if(Curriculum.graph.eligible(skill.id,s.mastery.associate { it.skillId to it.domain() })) "Prerequisites ready" else "Prerequisites need work"}",style=MaterialTheme.typography.labelSmall,color=Muted)
                        } }
                    }
                    item { Heading("Patterns to revisit","Tags come from scored errors, never tutor guesses.") }
                    items(s.misconceptions,key={it.skillId+it.tag}) { Text("${Curriculum.graph.byId.getValue(it.skillId).title}: ${it.tag.replace('-',' ')} · ${it.count} observations",color=Muted) }
                    item { ProfileForm(s.profile,vm,!s.busy) }
                }
                4 -> {
                    item { DevicePanel(s,vm) }
                }
            }
        }
    }
}

@Composable private fun Heading(title: String,subtitle: String) { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold); Text(subtitle,color=Muted,style=MaterialTheme.typography.bodyMedium) } }
@Composable private fun Hero(title: String,subtitle: String,content: @Composable ColumnScope.()->Unit) {
    Card(shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=Panel)) { Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold); Text(subtitle,color=Muted); content() } }
}
@Composable private fun ActivityCard(a: RoadmapEntity,onLearn: ()->Unit,onTutor: ()->Unit) {
    Hero(a.title,"${a.kind} · ${a.minutes} min · ${a.reason}") { Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Button(onClick=onLearn) { Text("Open lesson") }; TextButton(onClick=onTutor) { Text("Ask tutor") } } }
}
@Composable private fun SkillPicker(selected: String,onSelect: (String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { Curriculum.skills.forEach { skill -> FilterChip(modifier=Modifier.testTag("skill-${skill.id}"),selected=selected==skill.id,onClick={onSelect(skill.id)},label={Text(skill.title)}) } }
}
@Composable private fun ProfileForm(profile: ProfileEntity?,vm: LearnerViewModel,enabled: Boolean=true) {
    var name by rememberSaveable(profile?.name) { mutableStateOf(profile?.name ?: "") }
    var goal by rememberSaveable(profile?.goal) { mutableStateOf(profile?.goal ?: Goal.EDGE_AI.name) }
    var minutes by rememberSaveable(profile?.dailyMinutes) { mutableFloatStateOf((profile?.dailyMinutes ?: 30).toFloat()) }
    var experience by rememberSaveable(profile?.experience) { mutableStateOf(profile?.experience ?: Experience.BEGINNER.name) }
    var context by rememberSaveable(profile?.goalContext) { mutableStateOf(profile?.goalContext ?: "") }
    Hero(if(profile==null) "Make ambition actionable." else "Your learning preferences","Choose a direction. Your plan will adapt to evidence and your available time.") {
        OutlinedTextField(name,{name=it.take(60)},label={Text("Your name")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("profile-name"))
        Goal.entries.forEach { g -> FilterChip(modifier=Modifier.testTag("goal-${g.name}"),selected=goal==g.name,onClick={goal=g.name},label={Text(g.title)}) }
        Text("Experience guides explanations. Assessment evidence determines mastery.",style=MaterialTheme.typography.bodySmall,color=Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { Experience.entries.forEach { e -> FilterChip(selected=experience==e.name,onClick={experience=e.name},label={Text(e.title)}) } }
        OutlinedTextField(context,{context=it.take(160)},label={Text("What would you like to build? (optional)")},modifier=Modifier.fillMaxWidth(),minLines=2)
        Text("Daily focus · ${minutes.roundToInt()} minutes")
        Slider(value=minutes,onValueChange={minutes=it},valueRange=5f..240f,steps=46)
        Button(onClick={vm.saveProfile(name,Goal.valueOf(goal),minutes.roundToInt(),Experience.valueOf(experience),context)},enabled=name.isNotBlank() && enabled,modifier=Modifier.testTag("profile-save")) { Text(if(profile==null) "Build my learning space" else "Update preferences") }
    }
}
@Composable private fun TutorComposer(s: LearnerState,vm: LearnerViewModel) {
    var message by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(TutorMode.TEACH.name) }
    var structured by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { TutorMode.entries.forEach { m -> FilterChip(selected=mode==m.name,onClick={mode=m.name},label={Text(m.name.lowercase().replaceFirstChar { it.uppercase() })}) } }
        if(s.modelState==ModelState.MISSING) Text("Import the Llama bundle in Device to enable tutoring. Lessons and assessments are available now.",color=Mint)
        OutlinedTextField(message,{message=it.take(300)},label={Text("Ask about this competency")},modifier=Modifier.fillMaxWidth().testTag("tutor-input"),minLines=3)
        Row { Checkbox(checked=structured,onCheckedChange={structured=it}); Text("Request structured teaching JSON",modifier=Modifier.padding(top=12.dp)) }
        Button(onClick={vm.ask(message,TutorMode.valueOf(mode),structured)},enabled=message.isNotBlank() && !s.generating && !s.busy && s.modelState in setOf(ModelState.INSTALLED,ModelState.READY),modifier=Modifier.testTag("tutor-generate")) { Text("Generate on this device") }
        if(s.modelState==ModelState.ERROR) OutlinedButton(onClick=vm::unload,enabled=!s.busy && !s.generating) { Text("Reset model state and retry") }
        Text("AI explanations may contain errors. Only scored assessments change mastery.",style=MaterialTheme.typography.labelSmall,color=Muted)
    }
}
@Composable private fun DevicePanel(s: LearnerState,vm: LearnerViewModel) {
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importModel) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBenchmarks) }
    Hero("Local intelligence","Model lifecycle · ${s.modelState.name.lowercase()}") {
        Text("${Build.MANUFACTURER} ${Build.MODEL}\nAndroid ${Build.VERSION.RELEASE} · ${Build.SUPPORTED_ABIS.joinToString()}")
        Text("ExecuTorch 1.0.0 · ARM64 CPU / XNNPACK target. Backend execution and speed must be measured on this device.",color=Muted)
        Button(onClick={picker.launch(arrayOf("application/zip","application/octet-stream"))},enabled=!s.busy && !s.generating) { Text("Import verified model bundle") }
        OutlinedButton(onClick=vm::unload,enabled=!s.busy && !s.generating) { Text("Release model memory") }
        OutlinedButton(onClick=vm::runBenchmark,enabled=!s.busy && !s.generating && s.modelState in setOf(ModelState.INSTALLED,ModelState.READY)) { Text("Run fixed ARM benchmark suite · 8 runs") }
        if(s.generating) { Text(s.stream); OutlinedButton(onClick=vm::stop) { Text("Stop generation") } }
        OutlinedButton(onClick={export.launch("adapt-benchmarks.json")},enabled=!s.busy) { Text("Export measured inference records") }
        Text("Bundle: manifest.json + model.pte + tokenizer.model. The importer checks version, sizes and SHA-256 before activation. No model is bundled in the APK.",color=Muted)
        Text(s.lastTiming)
        Text("No network permission. Private Room storage. Android backup disabled. Uninstalling clears learner data and imported models.",style=MaterialTheme.typography.labelSmall,color=Muted)
    }
}

@Composable private fun NavigationGlyph(index: Int,selected: Boolean) {
    val color=if(selected) Mint else Muted
    Canvas(Modifier.size(24.dp)) {
        val w=size.width;val h=size.height
        fun line(x1: Float,y1: Float,x2: Float,y2: Float) { drawLine(color,Offset(w*x1,h*y1),Offset(w*x2,h*y2),strokeWidth=2.dp.toPx(),cap=StrokeCap.Round) }
        when(index) {
            0 -> { line(.15f,.45f,.5f,.15f);line(.5f,.15f,.85f,.45f);line(.25f,.4f,.25f,.85f);line(.75f,.4f,.75f,.85f);line(.25f,.85f,.75f,.85f) }
            1 -> { line(.15f,.2f,.5f,.3f);line(.5f,.3f,.85f,.2f);line(.15f,.2f,.15f,.75f);line(.85f,.2f,.85f,.75f);line(.15f,.75f,.5f,.85f);line(.5f,.85f,.85f,.75f);line(.5f,.3f,.5f,.85f) }
            2 -> { drawCircle(color,radius=w*.32f,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()));line(.2f,.75f,.15f,.9f);line(.15f,.9f,.4f,.8f);line(.35f,.5f,.65f,.5f) }
            3 -> { line(.2f,.85f,.2f,.55f);line(.5f,.85f,.5f,.35f);line(.8f,.85f,.8f,.15f) }
            else -> { drawCircle(color,radius=w*.32f,style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()));drawCircle(color,radius=w*.1f) }
        }
    }
}
