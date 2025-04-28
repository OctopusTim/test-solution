package mobi.sevenwinds.app.budget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val allRecordsQuery = BudgetTable.select { BudgetTable.year eq param.year }
            val total = allRecordsQuery.count()

            val query = BudgetTable
                .select { BudgetTable.year eq param.year }
                .orderBy(BudgetTable.month to SortOrder.ASC, BudgetTable.amount to SortOrder.DESC)
                .limit(param.limit, param.offset)

            val data = BudgetEntity.wrapRows(query).map { it.toResponse() }

            val sumByType = BudgetEntity.wrapRows(allRecordsQuery)
                .groupBy { it.type }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
                .mapKeys { it.key.name }

            return@transaction BudgetYearStatsResponse(
                total = total,
                totalByType = sumByType,
                items = data
            )
        }
    }
}