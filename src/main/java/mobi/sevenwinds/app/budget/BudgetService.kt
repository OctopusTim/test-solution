package mobi.sevenwinds.app.budget
import org.jetbrains.exposed.dao.EntityID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mobi.sevenwinds.app.author.AuthorTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction


object BudgetService {
    suspend fun addRecord(body: BudgetRecord): BudgetRecord = withContext(Dispatchers.IO) {
        transaction {
            val entity = BudgetEntity.new {
                this.year = body.year
                this.month = body.month
                this.amount = body.amount
                this.type = body.type
                this.authorId = body.authorId?.let { EntityID(it, AuthorTable) }
            }

            return@transaction entity.toResponse()
        }
    }

    suspend fun getYearStats(param: BudgetYearParam): BudgetYearStatsResponse = withContext(Dispatchers.IO) {
        transaction {
            val allRecordsQuery = BudgetTable.select { BudgetTable.year eq param.year }
            val total = allRecordsQuery.count()

            val query = BudgetTable
                .join(AuthorTable, JoinType.LEFT, BudgetTable.authorId, AuthorTable.id)
                .select {
                    val conditions = BudgetTable.year eq param.year

                    if (param.authorFullNameFilter != null) {
                        conditions and (AuthorTable.fullName.upperCase() like "%${param.authorFullNameFilter.toUpperCase()}%")
                    } else {
                        conditions
                    }
                }
                .orderBy(BudgetTable.month to SortOrder.ASC, BudgetTable.amount to SortOrder.DESC)
                .limit(param.limit, param.offset)

            val data = query.map { row ->
                val budget = BudgetEntity.wrapRow(row)
                BudgetRecord(
                    year = budget.year,
                    month = budget.month,
                    amount = budget.amount,
                    type = budget.type,
                    authorId = budget.authorId?.value,
                    authorFullName = row.getOrNull(AuthorTable.fullName),
                    authorCreatedAt = row.getOrNull(AuthorTable.createdAt)
                )
            }

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