package dev.musyase.pocketaudit.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "inventory_items")
data class ItemEntity(
    @PrimaryKey val code: String,
    val name: String,
    val location: String,
)

@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val code: String,
    val condition: String,
    val note: String,
    val photoPath: String?,
    val observedAt: Long,
)

@Entity(tableName = "audit_meta")
data class AuditMetaEntity(
    @PrimaryKey val id: Int = 1,
    val completedAt: Long? = null,
    val sourceLabel: String = "Fictional sample",
)

@Dao
interface AuditDao {
    @Query("SELECT * FROM inventory_items ORDER BY location, name, code")
    fun items(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM observations ORDER BY observedAt DESC")
    fun observations(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM audit_meta WHERE id = 1")
    fun meta(): Flow<AuditMetaEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putItems(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putObservation(observation: ObservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: AuditMetaEntity)

    @Query("DELETE FROM inventory_items")
    suspend fun clearItems()

    @Query("DELETE FROM observations")
    suspend fun clearObservations()

    @Query("DELETE FROM observations WHERE code = :code")
    suspend fun removeObservation(code: String)

    @Query("SELECT * FROM observations WHERE code = :code")
    suspend fun observation(code: String): ObservationEntity?
}

@Database(entities = [ItemEntity::class, ObservationEntity::class, AuditMetaEntity::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao

    companion object {
        @Volatile private var instance: AuditDatabase? = null

        fun get(context: Context): AuditDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AuditDatabase::class.java, "pocket-audit.db")
                .build().also { instance = it }
        }
    }
}
