package ai.adapt.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.adapt.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Runs only against ai.adapt.app.validation; never clears the user's installed learner data. */
@RunWith(AndroidJUnit4::class)
class LearnerJourneyTest {
    @get:Rule val compose=createEmptyComposeRule()
    private lateinit var app: AdaptApplication
    private var scenario: ActivityScenario<MainActivity>?=null
    @Before fun setup() {
        app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as AdaptApplication
        check(app.packageName.endsWith(".validation")) { "Tests must use the isolated validation package" }
        runBlocking { withContext(Dispatchers.IO) { app.database.clearAllTables() } }
        scenario=ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("profile-name").fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() { scenario?.close() }
    private fun click(tag: String) { compose.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun waitFor(tag: String) { compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() } }
    private fun createProfile() {
        compose.onNodeWithTag("profile-name").performTextInput("Offline learner")
        click("goal-ANDROID");click("profile-save")
        waitFor("nav-Home")
    }
    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        android.os.SystemClock.sleep(300)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val folder=File(app.filesDir,"validation-evidence").apply { mkdirs() }
        File(folder,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    @Test fun actualUiJourneyScoresAdaptsAndSurvivesActivityRecreation() {
        createProfile()
        compose.onNodeWithTag("nav-Learn").performClick()
        click("start-diagnostic")
        repeat(8) { index ->
            waitFor("question-prompt")
            val item=runBlocking { requireNotNull(app.repository.resumeAssessment()) }
            val choice=if(index==0) (item.question.correct+1)%item.question.options.size else item.question.correct
            compose.waitUntil(15_000) { compose.onAllNodesWithText(item.question.prompt).fetchSemanticsNodes().isNotEmpty() }
            click("option-$choice");waitFor("answer-feedback");click("assessment-continue")
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("answer-feedback").fetchSemanticsNodes().isEmpty() }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Evidence recorded").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(8,runBlocking { app.repository.dao.answersFlow().firstValue().size })
        click("skill-logic");click("start-focused")
        repeat(4) {
            waitFor("question-prompt")
            val item=runBlocking { requireNotNull(app.repository.resumeAssessment()) }
            compose.waitUntil(15_000) { compose.onAllNodesWithText(item.question.prompt).fetchSemanticsNodes().isNotEmpty() }
            click("option-${item.question.correct}");waitFor("answer-feedback");click("assessment-continue")
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("answer-feedback").fetchSemanticsNodes().isEmpty() }
        }
        val mastery=runBlocking { app.repository.dao.mastery() }
        assertTrue(mastery.first { it.skillId=="logic" }.probability>=0.7)
        val roadmap=runBlocking { app.repository.dao.roadmapFlow().firstValue() }
        assertTrue(roadmap.any { it.skillId=="kotlin" })
        compose.onNodeWithTag("nav-Home").performClick();compose.waitForIdle();screenshot("home-evidence")
        compose.onNodeWithTag("nav-Progress").performClick();compose.waitForIdle();screenshot("progress-evidence")
        scenario!!.recreate();waitFor("nav-Home")
        assertEquals(mastery,runBlocking { app.repository.dao.mastery() })
        assertEquals("Offline learner",runBlocking { app.repository.dao.profile()!!.name })
        compose.onNodeWithTag("nav-Tutor").performClick()
        compose.onNodeWithTag("tutor-input").performScrollTo().performTextInput("Explain null safety")
        compose.onNodeWithTag("tutor-generate").performScrollTo().assertIsNotEnabled()
        screenshot("model-required")
    }
    @Test fun interruptedAssessmentResumesInsteadOfScoringTwice() {
        createProfile();compose.onNodeWithTag("nav-Learn").performClick();click("start-diagnostic")
        waitFor("question-prompt")
        val first=runBlocking { requireNotNull(app.repository.resumeAssessment()) }
        click("option-${first.question.correct}");waitFor("answer-feedback")
        scenario!!.close();scenario=ActivityScenario.launch(MainActivity::class.java)
        waitFor("nav-Learn")
        compose.onNodeWithTag("nav-Learn").performClick();waitFor("question-prompt")
        val next=runBlocking { requireNotNull(app.repository.resumeAssessment()) }
        assertNotEquals(first.question.id,next.question.id)
        assertEquals(1,runBlocking { app.repository.dao.answers(first.session.id).size })
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T = first()
