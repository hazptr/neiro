package neiro.recommendations

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.i18n.MR

// Adapted from TachiyomiSY's exh.recs, as carried by Komikku (Apache-2.0)

/** One suggested series, as the service lists it. [url] is its page on that service. */
data class Recommendation(val title: String, val url: String, val thumbnailUrl: String?)

enum class RecommendationKind(val label: StringResource) {
    Community(MR.strings.recommendations_community),
    Similar(MR.strings.recommendations_similar),
}

/**
 * A tracking service's recommendations for a series: by the series' id there when it's tracked on
 * that service, otherwise by its title. One lookup fills every [kinds] row the service has.
 */
abstract class RecommendationService(
    val name: String,
    val trackerId: Long,
    val kinds: List<RecommendationKind>,
) {
    abstract suspend fun byId(id: Long): Map<RecommendationKind, List<Recommendation>>

    /** Empty when the service has no series by that title. */
    abstract suspend fun byTitle(title: String): Map<RecommendationKind, List<Recommendation>>
}

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private val JsonElement?.string: String?
    get() = (this as? JsonPrimitive)?.contentOrNull

private val JsonElement?.obj: JsonObject?
    get() = this as? JsonObject

private val JsonElement?.arr: JsonArray?
    get() = this as? JsonArray

class AniListRecommendations(
    private val client: OkHttpClient,
    private val json: Json,
    trackerId: Long,
) : RecommendationService("AniList", trackerId, listOf(RecommendationKind.Community)) {

    override suspend fun byId(id: Long) = mapOf(RecommendationKind.Community to fetch(BY_ID, "id", id, null))

    override suspend fun byTitle(title: String) =
        mapOf(RecommendationKind.Community to fetch(BY_SEARCH, "search", title, title))

    private suspend fun fetch(query: String, name: String, value: Any, title: String?): List<Recommendation> {
        val body = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    when (value) {
                        is Long -> put(name, value)
                        else -> put(name, value.toString())
                    }
                },
            )
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val data = with(json) { client.newCall(POST(ENDPOINT, body = body)).awaitSuccess().parseAs<JsonObject>() }
        return parse(data, title)
    }

    companion object {
        private const val ENDPOINT = "https://graphql.anilist.co/"

        private const val RECOMMENDATIONS =
            "recommendations { edges { node { mediaRecommendation { " +
                "countryOfOrigin siteUrl title { romaji english native } synonyms coverImage { large } } } } }"
        private const val BY_ID =
            "query Recommendations(\$id: Int!) { Page { media(id: \$id, type: MANGA) { $RECOMMENDATIONS } } }"
        private const val BY_SEARCH =
            "query Recommendations(\$search: String!) { Page { media(search: \$search, type: MANGA) { " +
                "title { romaji english native } synonyms $RECOMMENDATIONS } } }"

        /** With a [title], only the media whose titles or synonyms contain it count. */
        fun parse(data: JsonObject, title: String?): List<Recommendation> {
            val media = data["data"].obj?.get("Page").obj?.get("media").arr.orEmpty()
                .mapNotNull { it.obj }
                .filter { title == null || it.matches(title) }
            return media.flatMap { it["recommendations"].obj?.get("edges").arr.orEmpty() }
                .mapNotNull { edge ->
                    val rec = edge.obj?.get("node").obj?.get("mediaRecommendation").obj ?: return@mapNotNull null
                    val url = rec["siteUrl"].string ?: return@mapNotNull null
                    Recommendation(
                        title = displayTitle(rec) ?: return@mapNotNull null,
                        url = url,
                        thumbnailUrl = rec["coverImage"].obj?.get("large").string,
                    )
                }
        }

        private fun JsonObject.matches(search: String): Boolean {
            val titles = this["title"].obj
            return listOf("romaji", "english", "native").any {
                titles?.get(it).string?.contains(search, true) == true
            } ||
                this["synonyms"].arr.orEmpty().any { it.string?.contains(search, true) == true }
        }

        private fun displayTitle(media: JsonObject): String? {
            val titles = media["title"].obj
            val english = titles?.get("english").string
            val romaji = titles?.get("romaji").string
            val native = titles?.get("native").string
            val synonym = media["synonyms"].arr?.firstOrNull().string
            val isJapanese = media["countryOfOrigin"].string == "JP"
            return when {
                !english.isNullOrBlank() -> english
                isJapanese && !romaji.isNullOrBlank() -> romaji
                !synonym.isNullOrBlank() -> synonym
                !romaji.isNullOrBlank() -> romaji
                else -> native
            }
        }
    }
}

class MangaUpdatesRecommendations(
    private val client: OkHttpClient,
    private val json: Json,
    trackerId: Long,
) : RecommendationService(
    "MangaUpdates",
    trackerId,
    listOf(RecommendationKind.Community, RecommendationKind.Similar),
) {

    override suspend fun byId(id: Long): Map<RecommendationKind, List<Recommendation>> {
        val series = get(GET("${ENDPOINT}series/$id"))
        return mapOf(
            RecommendationKind.Community to parseList(series, "recommendations"),
            RecommendationKind.Similar to parseList(series, "category_recommendations"),
        )
    }

    override suspend fun byTitle(title: String): Map<RecommendationKind, List<Recommendation>> {
        val body = buildJsonObject {
            put("search", title)
            put("stype", "title")
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val id = firstSeriesId(get(POST("${ENDPOINT}series/search", body = body))) ?: return emptyMap()
        return byId(id)
    }

    private suspend fun get(request: Request): JsonObject =
        with(json) { client.newCall(request).awaitSuccess().parseAs<JsonObject>() }

    companion object {
        private const val ENDPOINT = "https://api.mangaupdates.com/v1/"

        fun firstSeriesId(search: JsonObject): Long? =
            search["results"].arr?.firstOrNull().obj?.get("record").obj?.get("series_id").string?.toLongOrNull()

        fun parseList(series: JsonObject, field: String): List<Recommendation> =
            series[field].arr.orEmpty().mapNotNull { element ->
                val rec = element.obj ?: return@mapNotNull null
                Recommendation(
                    title = rec["series_name"].string ?: return@mapNotNull null,
                    url = rec["series_url"].string ?: return@mapNotNull null,
                    thumbnailUrl = rec["series_image"].obj?.get("url").obj?.get("original").string,
                )
            }
    }
}

/** MyAnimeList through Jikan, a public read-only MyAnimeList API run by a third party. */
class MyAnimeListRecommendations(
    private val client: OkHttpClient,
    private val json: Json,
    trackerId: Long,
) : RecommendationService("MyAnimeList", trackerId, listOf(RecommendationKind.Community)) {

    override suspend fun byId(id: Long) =
        mapOf(RecommendationKind.Community to parse(get("${ENDPOINT}manga/$id/recommendations")))

    override suspend fun byTitle(title: String): Map<RecommendationKind, List<Recommendation>> {
        val url = "${ENDPOINT}manga".toHttpUrl().newBuilder().addQueryParameter("q", title).build().toString()
        val id = firstMangaId(get(url)) ?: return emptyMap()
        return byId(id)
    }

    private suspend fun get(url: String): JsonObject =
        with(json) { client.newCall(GET(url)).awaitSuccess().parseAs<JsonObject>() }

    companion object {
        private const val ENDPOINT = "https://api.jikan.moe/v4/"

        fun firstMangaId(search: JsonObject): Long? =
            search["data"].arr?.firstOrNull().obj?.get("mal_id").string?.toLongOrNull()

        fun parse(data: JsonObject): List<Recommendation> =
            data["data"].arr.orEmpty().mapNotNull { element ->
                val entry = element.obj?.get("entry").obj ?: return@mapNotNull null
                val images = entry["images"].obj
                Recommendation(
                    title = entry["title"].string ?: return@mapNotNull null,
                    url = entry["url"].string ?: return@mapNotNull null,
                    thumbnailUrl = images?.get("webp").obj?.get("image_url").string
                        ?: images?.get("jpg").obj?.get("image_url").string,
                )
            }
    }
}
