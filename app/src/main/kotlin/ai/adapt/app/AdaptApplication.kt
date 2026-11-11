package ai.adapt.app

import android.app.Application
import androidx.room.Room
import ai.adapt.data.*
import ai.adapt.inference.LocalTutor
import kotlinx.coroutines.*

class AdaptApplication: Application() {
    val database by lazy { Room.databaseBuilder(this,AdaptDatabase::class.java,"adapt-v1.db").addMigrations(DatabaseMigrations.MIGRATION_1_2).build() }
    val applicationScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val repository by lazy { LearningRepository(database) }
    val tutor by lazy { LocalTutor(this) }
}
