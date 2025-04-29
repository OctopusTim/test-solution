package mobi.sevenwinds.app.budget

import io.restassured.RestAssured
import mobi.sevenwinds.app.author.AuthorRecord
import mobi.sevenwinds.app.author.AuthorTable
import mobi.sevenwinds.common.ServerTest
import mobi.sevenwinds.common.jsonBody
import mobi.sevenwinds.common.toResponse
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BudgetApiKtTest : ServerTest() {

    @BeforeEach
    internal fun setUp() {
        transaction { BudgetTable.deleteAll()
            AuthorTable.deleteAll()
        }

    }

    @Test
    fun testBudgetPagination() {
        addRecord(BudgetRecord(2020, 5, 10, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 5, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 20, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 30, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 40, BudgetType.Приход))
        addRecord(BudgetRecord(2030, 1, 1, BudgetType.Расход))

        RestAssured.given()
            .queryParam("limit", 3)
            .queryParam("offset", 1)
            .get("/budget/year/2020/stats")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println("${response.total} / ${response.items} / ${response.totalByType}")

                Assert.assertEquals(5, response.total)
                Assert.assertEquals(3, response.items.size)
                Assert.assertEquals(105, response.totalByType[BudgetType.Приход.name])
            }
    }

    @Test
    fun testStatsSortOrder() {
        addRecord(BudgetRecord(2020, 5, 100, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 1, 5, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 50, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 1, 30, BudgetType.Приход))
        addRecord(BudgetRecord(2020, 5, 400, BudgetType.Приход))

        // expected sort order - month ascending, amount descending

        RestAssured.given()
            .get("/budget/year/2020/stats?limit=100&offset=0")
            .toResponse<BudgetYearStatsResponse>().let { response ->
                println(response.items)

                Assert.assertEquals(30, response.items[0].amount)
                Assert.assertEquals(5, response.items[1].amount)
                Assert.assertEquals(400, response.items[2].amount)
                Assert.assertEquals(100, response.items[3].amount)
                Assert.assertEquals(50, response.items[4].amount)
            }
    }

    @Test
    fun testInvalidMonthValues() {
        RestAssured.given()
            .jsonBody(BudgetRecord(2020, -5, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)

        RestAssured.given()
            .jsonBody(BudgetRecord(2020, 15, 5, BudgetType.Приход))
            .post("/budget/add")
            .then().statusCode(400)
    }

    @Test
    fun testAuthorAndBudgetIntegration() {
        val authorId = RestAssured.given()
            .jsonBody(mapOf("name" to "Иванов Иван Иванович"))
            .post("/author/add")
            .then().statusCode(200)
            .extract().body().path<Int>("id")

        RestAssured.given()
            .jsonBody(BudgetRecord(2020, 5, 100, BudgetType.Приход, authorId = authorId))
            .post("/budget/add")
            .then().statusCode(200)

        val statsResponse = RestAssured.given()
            .get("/budget/year/2020/stats?limit=10&offset=0")
            .then().statusCode(200)

        Assert.assertEquals(1, statsResponse.extract().body().path<Int>("total"))
        Assert.assertNotNull(statsResponse.extract().body().path<String>("items[0].authorFullName"))
    }

    @Test
    fun testAuthorNameFilter() {
        val author1Id = RestAssured.given()
            .jsonBody(mapOf("name" to "Уникальный Автор"))
            .post("/author/add")
            .then().statusCode(200)
            .extract().body().path<Int>("id")

        val author2Id = RestAssured.given()
            .jsonBody(mapOf("name" to "Совершенно Другой"))
            .post("/author/add")
            .then().statusCode(200)
            .extract().body().path<Int>("id")

        RestAssured.given().jsonBody(BudgetRecord(2020, 1, 100, BudgetType.Приход, authorId = author1Id)).post("/budget/add").then().statusCode(200)
        RestAssured.given().jsonBody(BudgetRecord(2020, 2, 200, BudgetType.Приход, authorId = author2Id)).post("/budget/add").then().statusCode(200)

        val response1 = RestAssured.given()
            .queryParam("authorFullNameFilter", "Уникальный")
            .get("/budget/year/2020/stats?limit=10&offset=0")
            .then().statusCode(200)

        Assert.assertEquals(1, response1.extract().body().path<Int>("total"))
        Assert.assertEquals("Уникальный Автор", response1.extract().body().path<String>("items[0].authorFullName"))
    }

    private fun addRecord(record: BudgetRecord) {
        RestAssured.given()
            .jsonBody(record)
            .post("/budget/add")
            .toResponse<BudgetRecord>().let { response ->
                Assert.assertEquals(record, response)
            }
    }
}