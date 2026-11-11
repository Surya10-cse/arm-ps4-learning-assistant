package ai.adapt.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {
    val MIGRATION_1_2=object: Migration(1,2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE profiles ADD COLUMN experience TEXT NOT NULL DEFAULT 'BEGINNER'")
            db.execSQL("ALTER TABLE profiles ADD COLUMN goalContext TEXT NOT NULL DEFAULT ''")
        }
    }
}
