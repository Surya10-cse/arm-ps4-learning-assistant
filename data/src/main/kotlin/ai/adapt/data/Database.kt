package ai.adapt.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="profiles")
data class ProfileEntity(@PrimaryKey val id: Int = 1, val name: String, val goal: String, val dailyMinutes: Int, val createdAt: Long, @ColumnInfo(defaultValue="'BEGINNER'") val experience: String = "BEGINNER", @ColumnInfo(defaultValue="''") val goalContext: String = "")
@Entity(tableName="questions")
data class QuestionEntity(@PrimaryKey val id: String, val skillId: String, val prompt: String, val optionsJson: String, val correctIndex: Int, val explanation: String, val misconception: String, val difficulty: Double, val contentVersion: Int)
@Entity(tableName="mastery")
data class MasteryEntity(@PrimaryKey val skillId: String, val probability: Double, val attempts: Int, val correct: Int, val updatedAt: Long)
@Entity(tableName="sessions")
data class SessionEntity(@PrimaryKey val id: String, val startedAt: Long, val completedAt: Long? = null, val focusSkill: String? = null)
@Entity(tableName="answers", primaryKeys=["sessionId","questionId"], foreignKeys=[ForeignKey(entity=SessionEntity::class,parentColumns=["id"],childColumns=["sessionId"]),ForeignKey(entity=QuestionEntity::class,parentColumns=["id"],childColumns=["questionId"])], indices=[Index("questionId")])
data class AnswerEntity(val sessionId: String, val questionId: String, val selectedIndex: Int, val correct: Boolean, val answeredAt: Long)
@Entity(tableName="misconceptions", primaryKeys=["skillId","tag"])
data class MisconceptionEntity(val skillId: String, val tag: String, val count: Int, val lastSeen: Long)
@Entity(tableName="revisions")
data class RevisionEntity(@PrimaryKey val skillId: String, val dueAt: Long, val intervalDays: Int, val ease: Double, val streak: Int)
@Entity(tableName="roadmap", primaryKeys=["revision","position"])
data class RoadmapEntity(val revision: Long, val position: Int, val skillId: String, val title: String, val minutes: Int, val priority: Double, val reason: String, val kind: String)
@Entity(tableName="messages", indices=[Index("createdAt")])
data class MessageEntity(@PrimaryKey val id: String, val role: String, val text: String, val mode: String, val skillId: String, val createdAt: Long, val status: String)
@Entity(tableName="benchmarks")
data class BenchmarkEntity(@PrimaryKey val id: String, val createdAt: Long, val modelHash: String, val elapsedMs: Long, val ttftMs: Long?, val nativeStatsJson: String, val device: String, val memoryPssKb: Int, val thermalStatus: Int, val loadMs: Long, val promptSha256: String, val promptBytes: Int, val purpose: String, val quantization: String)

@Dao
interface LearningDao {
    @Query("SELECT * FROM profiles WHERE id=1") fun profileFlow(): Flow<ProfileEntity?>
    @Query("SELECT * FROM profiles WHERE id=1") suspend fun profile(): ProfileEntity?
    @Upsert suspend fun saveProfile(value: ProfileEntity)
    @Upsert suspend fun seed(value: List<QuestionEntity>)
    @Query("SELECT * FROM mastery") fun masteryFlow(): Flow<List<MasteryEntity>>
    @Query("SELECT * FROM mastery") suspend fun mastery(): List<MasteryEntity>
    @Upsert suspend fun saveMastery(value: MasteryEntity)
    @Query("SELECT * FROM sessions WHERE completedAt IS NULL ORDER BY startedAt DESC LIMIT 1") suspend fun activeSession(): SessionEntity?
    @Upsert suspend fun saveSession(value: SessionEntity)
    @Query("SELECT * FROM answers WHERE sessionId=:session") suspend fun answers(session: String): List<AnswerEntity>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun saveAnswer(value: AnswerEntity): Long
    @Query("SELECT * FROM answers ORDER BY answeredAt DESC") fun answersFlow(): Flow<List<AnswerEntity>>
    @Query("SELECT questionId FROM answers ORDER BY answeredAt DESC LIMIT 16") suspend fun recentQuestionIds(): List<String>
    @Query("SELECT * FROM revisions") suspend fun revisions(): List<RevisionEntity>
    @Query("SELECT * FROM revisions ORDER BY dueAt") fun revisionsFlow(): Flow<List<RevisionEntity>>
    @Upsert suspend fun saveRevision(value: RevisionEntity)
    @Query("SELECT * FROM misconceptions WHERE skillId=:skill AND tag=:tag") suspend fun misconception(skill: String, tag: String): MisconceptionEntity?
    @Query("SELECT * FROM misconceptions ORDER BY lastSeen DESC") fun misconceptionsFlow(): Flow<List<MisconceptionEntity>>
    @Upsert suspend fun saveMisconception(value: MisconceptionEntity)
    @Query("SELECT * FROM roadmap WHERE revision=(SELECT MAX(revision) FROM roadmap) ORDER BY position") fun roadmapFlow(): Flow<List<RoadmapEntity>>
    @Insert suspend fun saveRoadmap(value: List<RoadmapEntity>)
    @Query("DELETE FROM roadmap WHERE revision < :keep") suspend fun pruneRoadmaps(keep: Long)
    @Query("SELECT * FROM messages ORDER BY createdAt DESC LIMIT 100") fun messagesFlow(): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE skillId=:skill AND status='complete' ORDER BY createdAt DESC LIMIT 4") suspend fun recentMessages(skill: String): List<MessageEntity>
    @Upsert suspend fun saveMessage(value: MessageEntity)
    @Query("UPDATE messages SET status='interrupted' WHERE status='streaming'") suspend fun recoverMessages()
    @Insert suspend fun saveBenchmark(value: BenchmarkEntity)
    @Query("SELECT * FROM benchmarks ORDER BY createdAt DESC") suspend fun benchmarks(): List<BenchmarkEntity>
}

@Database(entities=[ProfileEntity::class,QuestionEntity::class,MasteryEntity::class,SessionEntity::class,AnswerEntity::class,MisconceptionEntity::class,RevisionEntity::class,RoadmapEntity::class,MessageEntity::class,BenchmarkEntity::class],version=2,exportSchema=true)
abstract class AdaptDatabase: RoomDatabase() { abstract fun learning(): LearningDao }
