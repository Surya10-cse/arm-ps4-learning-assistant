package ai.adapt.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ai.adapt.core.Goal
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun answerIsAtomicIdempotentAndDurableAcrossReopen() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="repository-test.db"
        context.deleteDatabase(name)
        fun open()=Room.databaseBuilder(context,AdaptDatabase::class.java,name).build()
        var db=open()
        try {
            val repo=LearningRepository(db) { 1_000_000L }
            repo.initialize(); repo.saveProfile("Learner",Goal.EDGE_AI,30)
            val question=requireNotNull(repo.startAssessment("logic"))
            repo.submit(question,question.question.correct)
            val updated=db.learning().mastery().single()
            try { repo.submit(question,question.question.correct); fail("Duplicate answer should be rejected") } catch(_: IllegalArgumentException) { }
            assertEquals(1,db.learning().answers(question.session.id).size)
            assertEquals(1,db.learning().mastery().single().attempts)
            assertEquals(1,db.learning().revisions().size)
            assertTrue(db.learning().roadmapFlow().first().isNotEmpty())
            db.close(); db=open()
            assertEquals(updated,db.learning().mastery().single())
            assertEquals("Learner",db.learning().profile()?.name)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun invalidChoiceRollsBackAllLearningWrites() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AdaptDatabase::class.java).build()
        try {
            val repo=LearningRepository(db)
            repo.initialize(); repo.saveProfile("Learner",Goal.ANDROID,30)
            val item=requireNotNull(repo.startAssessment())
            try { repo.submit(item,999); fail("Invalid option accepted") } catch(_: IllegalArgumentException) { }
            assertTrue(db.learning().answers(item.session.id).isEmpty())
            assertTrue(db.learning().mastery().isEmpty())
            assertTrue(db.learning().revisions().isEmpty())
        } finally { db.close() }
    }
}
