package za.co.clarity

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class MoneyTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: String,
    val category: String,
    val date: Long = System.currentTimeMillis(),
    val note: String = ""
)

@Entity(tableName = "categories")
data class MoneyCategory(
    @PrimaryKey val name: String,
    val type: String = "Expense"
)

@Entity(tableName = "goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Double,
    val saved: Double = 0.0
)

@Entity(tableName = "plans")
data class MonthlyPlan(
    @PrimaryKey val monthKey: String,
    val incomeEstimate: Double = 0.0,
    val expenseEstimate: Double = 0.0,
    val savingsEstimate: Double = 0.0
)

@Dao
interface ClarityDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    fun transactions(): Flow<List<MoneyTransaction>>
    @Insert suspend fun addTransaction(item: MoneyTransaction)
    @Query("DELETE FROM transactions WHERE id=:id") suspend fun deleteTransaction(id: Long)
    @Query("SELECT * FROM categories ORDER BY name")
    fun categories(): Flow<List<MoneyCategory>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addCategory(item: MoneyCategory)
    @Query("SELECT * FROM goals ORDER BY id DESC")
    fun goals(): Flow<List<SavingsGoal>>
    @Insert suspend fun addGoal(item: SavingsGoal)
    @Query("UPDATE goals SET saved=:saved WHERE id=:id") suspend fun updateGoal(id: Long, saved: Double)
    @Query("DELETE FROM goals WHERE id=:id") suspend fun deleteGoal(id: Long)
    @Query("SELECT * FROM plans WHERE monthKey=:key") suspend fun plan(key: String): MonthlyPlan?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePlan(plan: MonthlyPlan)
}

@Database(entities = [MoneyTransaction::class, MoneyCategory::class, SavingsGoal::class, MonthlyPlan::class], version = 1, exportSchema = false)
abstract class ClarityDatabase : RoomDatabase() {
    abstract fun dao(): ClarityDao
    companion object {
        @Volatile private var INSTANCE: ClarityDatabase? = null
        fun get(context: Context): ClarityDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, ClarityDatabase::class.java, "clarity_local.db").build().also { INSTANCE = it }
        }
    }
}
