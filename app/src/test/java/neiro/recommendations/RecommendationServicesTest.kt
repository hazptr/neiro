package neiro.recommendations

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test

class RecommendationServicesTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val aniList = json(
        """
        {"data":{"Page":{"media":[
          {"title":{"romaji":"Kaze no Tou","english":"Tower of Wind","native":null},"synonyms":[],
           "recommendations":{"edges":[
             {"node":{"mediaRecommendation":{"countryOfOrigin":"JP","siteUrl":"https://anilist.co/manga/1",
               "title":{"romaji":"Umi no Uta","english":null,"native":"x"},"synonyms":["Sea Song"],
               "coverImage":{"large":"https://img/1.jpg"}}}},
             {"node":{"mediaRecommendation":null}},
             {"node":{"mediaRecommendation":{"countryOfOrigin":"KR","siteUrl":"https://anilist.co/manga/2",
               "title":{"romaji":"Badaui Norae","english":null,"native":"y"},"synonyms":["Song of the Sea"],
               "coverImage":null}}}
           ]}},
          {"title":{"romaji":"Other","english":"Unrelated","native":null},"synonyms":[],
           "recommendations":{"edges":[
             {"node":{"mediaRecommendation":{"countryOfOrigin":"JP","siteUrl":"https://anilist.co/manga/3",
               "title":{"romaji":"Nope","english":null,"native":null},"synonyms":[],"coverImage":null}}}
           ]}}
        ]}}}
        """,
    )

    @Test
    fun `AniList search keeps only media matching the title`() {
        AniListRecommendations.parse(aniList, "tower of wind") shouldBe listOf(
            Recommendation("Umi no Uta", "https://anilist.co/manga/1", "https://img/1.jpg"),
            Recommendation("Song of the Sea", "https://anilist.co/manga/2", null),
        )
    }

    @Test
    fun `AniList by id takes every media`() {
        AniListRecommendations.parse(aniList, null).map { it.url } shouldBe
            listOf("https://anilist.co/manga/1", "https://anilist.co/manga/2", "https://anilist.co/manga/3")
    }

    @Test
    fun `MangaUpdates lists and search id`() {
        val series = json(
            """
            {"recommendations":[{"series_name":"A","series_url":"https://mu/a",
               "series_image":{"url":{"original":"https://mu/a.jpg"}}},{"series_name":"no url"}],
             "category_recommendations":[{"series_name":"B","series_url":"https://mu/b"}]}
            """,
        )
        MangaUpdatesRecommendations.parseList(series, "recommendations") shouldBe
            listOf(Recommendation("A", "https://mu/a", "https://mu/a.jpg"))
        MangaUpdatesRecommendations.parseList(series, "category_recommendations") shouldBe
            listOf(Recommendation("B", "https://mu/b", null))
        val search = json("""{"results":[{"record":{"series_id":123456789012}}]}""")
        MangaUpdatesRecommendations.firstSeriesId(search) shouldBe 123456789012L
        MangaUpdatesRecommendations.firstSeriesId(json("""{"results":[]}""")) shouldBe null
    }

    @Test
    fun `MyAnimeList prefers webp covers`() {
        val data = json(
            """
            {"data":[{"entry":{"mal_id":5,"title":"C","url":"https://mal/5",
               "images":{"jpg":{"image_url":"https://mal/5.jpg"},"webp":{"image_url":"https://mal/5.webp"}}}},
             {"entry":{"mal_id":6,"title":"D","url":"https://mal/6","images":{"jpg":{"image_url":"https://mal/6.jpg"}}}}]}
            """,
        )
        MyAnimeListRecommendations.parse(data) shouldBe listOf(
            Recommendation("C", "https://mal/5", "https://mal/5.webp"),
            Recommendation("D", "https://mal/6", "https://mal/6.jpg"),
        )
        MyAnimeListRecommendations.firstMangaId(json("""{"data":[{"mal_id":42}]}""")) shouldBe 42L
    }
}
