package mobi.sevenwinds.app.author

import com.papsign.ktor.openapigen.annotations.type.string.length.MinLength
import com.papsign.ktor.openapigen.route.info
import com.papsign.ktor.openapigen.route.path.normal.NormalOpenAPIRoute
import com.papsign.ktor.openapigen.route.path.normal.post
import com.papsign.ktor.openapigen.route.response.respond
import com.papsign.ktor.openapigen.route.route

fun NormalOpenAPIRoute.author() {
    route("/author") {
        route("/add").post<Unit, AuthorRecord, AuthorCreateRequest>(info("Добавить автора")) { param, body ->
            respond(AuthorService.addAuthor(body.name))
        }
    }
}

data class AuthorCreateRequest(
    @MinLength(1) val name: String
)