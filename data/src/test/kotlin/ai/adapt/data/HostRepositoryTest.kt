package ai.adapt.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ai.adapt.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class HostRepositoryTest {
    private lateinit var context: Context
    private lateinit var db: AdaptDatabase
    private lateinit var repo: LearningRepository
    private val name="host-${UUID.randomUUID()}.db"
    private var clock=1_000_000L
    private fun open() { db=Room.databaseBuilder(context,AdaptDatabase::class.java,name).addMigrations(DatabaseMigrations.MIGRATION_1_2).build(); repo=LearningRepository(db) { clock } }
    @Before fun setup() { context=ApplicationProvider.getApplicationContext(); open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun concurrentDoubleSubmitAppliesEvidenceOnlyOnce() = runBlocking {
        repo.initialize();repo.saveProfile("Learner",Goal.EDGE_AI,30)
        val item=requireNotNull(repo.startAssessment("logic"))
        val results=List(2) { async(Dispatchers.Default) { runCatching { repo.submit(item,item.question.correct) } } }.awaitAll()
        assertEquals(1,results.count { it.isSuccess })
        assertEquals(1,db.learning().answers(item.session.id).size)
        assertEquals(1,db.learning().mastery().single().attempts)
        assertEquals(1,db.learning().revisions().size)
    }

    @Test fun scoredJourneyAdaptsAndPersistsWhileChatDoesNotChangeMastery() = runBlocking {
        repo.initialize();repo.saveProfile("Anu",Goal.ANDROID,30)
        assertTrue(db.learning().roadmapFlow().first().none { it.skillId=="kotlin" })
        var item=repo.startAssessment("logic")
        repeat(4) {
            val current=requireNotNull(item)
            repo.submit(current,current.question.correct)
            item=repo.continueAssessment(current.session)
        }
        assertNull(item)
        val mastery=db.learning().mastery()
        assertTrue(mastery.single().probability>0.85)
        assertTrue(db.learning().roadmapFlow().first().any { it.skillId=="kotlin" })
        db.learning().saveMessage(MessageEntity("fixture-message","assistant","A stored conversation, not an inference result","TEACH","logic",clock,"streaming"))
        assertEquals(mastery,db.learning().mastery())
        db.close();open();repo.initialize()
        assertEquals(mastery,db.learning().mastery())
        assertEquals("Anu",db.learning().profile()?.name)
        assertEquals("interrupted",db.learning().messagesFlow().first().single().status)
        clock+=DAY_MS+1;repo.refresh()
        assertTrue(db.learning().roadmapFlow().first().any { it.skillId=="logic" && it.kind=="REVIEW" })
    }

    @Test fun invalidAnswerLeavesAllEvidenceTablesUntouched() = runBlocking {
        repo.initialize();repo.saveProfile("Learner",Goal.ML,30)
        val item=requireNotNull(repo.startAssessment())
        try { repo.submit(item,-1);fail("Invalid option accepted") } catch(_: IllegalArgumentException) { }
        assertTrue(db.learning().answers(item.session.id).isEmpty())
        assertTrue(db.learning().mastery().isEmpty())
        assertTrue(db.learning().revisions().isEmpty())
        assertTrue(db.learning().misconceptionsFlow().first().isEmpty())
    }
    @Test fun staleSessionCannotBeResurrectedAfterGoalChange() = runBlocking {
        repo.initialize();repo.saveProfile("Learner",Goal.ML,30)
        val old=requireNotNull(repo.startAssessment())
        repo.saveProfile("Learner",Goal.ANDROID,20)
        try { repo.continueAssessment(old.session);fail("Stale session accepted") } catch(_: IllegalArgumentException) { }
        assertNull(db.learning().activeSession())
    }
    @Test fun activeAssessmentResumesWithAuthoritativeNextQuestion() = runBlocking {
        repo.initialize();repo.saveProfile("Learner",Goal.ML,30)
        val first=requireNotNull(repo.startAssessment("android"))
        repo.submit(first,first.question.correct)
        db.close();open();repo.initialize()
        val resumed=requireNotNull(repo.resumeAssessment())
        assertEquals(first.session.id,resumed.session.id)
        assertNotEquals(first.question.id,resumed.question.id)
        assertEquals(1,resumed.answered)
    }
}
