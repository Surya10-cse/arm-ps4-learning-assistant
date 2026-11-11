package ai.adapt.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),AdaptDatabase::class.java)
    @Test fun versionOneLearnerAndEvidenceSurviveVersionTwoUpgrade() {
        val name="migration-validation.db"
        helper.createDatabase(name,1).apply {
            execSQL("INSERT INTO profiles (id,name,goal,dailyMinutes,createdAt) VALUES (1,'Migration learner','EDGE_AI',30,1000)")
            execSQL("INSERT INTO mastery (skillId,probability,attempts,correct,updatedAt) VALUES ('logic',0.8,3,2,1000)")
            close()
        }
        helper.runMigrationsAndValidate(name,2,true,DatabaseMigrations.MIGRATION_1_2).apply {
            query("SELECT name,experience,goalContext FROM profiles WHERE id=1").use { cursor ->
                assertTrue(cursor.moveToFirst());assertEquals("Migration learner",cursor.getString(0));assertEquals("BEGINNER",cursor.getString(1));assertEquals("",cursor.getString(2))
            }
            query("SELECT probability,attempts,correct FROM mastery WHERE skillId='logic'").use { cursor ->
                assertTrue(cursor.moveToFirst());assertEquals(0.8,cursor.getDouble(0),0.00001);assertEquals(3,cursor.getInt(1));assertEquals(2,cursor.getInt(2))
            }
            close()
        }
    }
}
