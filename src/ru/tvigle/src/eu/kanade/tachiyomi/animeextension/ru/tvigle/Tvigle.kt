package eu.kanade.tachiyomi.animeextension.ru.tvigle

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.AnimeHttpLegacySource
import keiyoushi.utils.useAsJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class Tvigle : AnimeHttpLegacySource() {

    override val name = "Tvigle"

    override val baseUrl = "https://www.tvigle.ru"

    override val lang = "ru"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36",
        )

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = listRequest("/", page)

    override fun popularAnimeParse(response: Response): AnimesPage = listParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = listRequest("/", page)

    override fun latestUpdatesParse(response: Response): AnimesPage = listParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request = GET("$baseUrl/?s=" + query, headers)

    override fun searchAnimeParse(response: Response): AnimesPage = listParse(response)

    // =========================== Anime Details ============================

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.useAsJsoup()
        return SAnime.create().apply {
            title = document.selectFirst("h1")?.text().orEmpty()
            thumbnail_url = document.selectFirst("img.styles_posterImage__HQvDn")?.absUrl("src")
            description = document.selectFirst("div.description, div.full-text, p")?.text()
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.useAsJsoup()
        return document.select("a[href*=series], a[href*=episode], a[href*=seriya]").mapIndexed { index, element ->
            SEpisode.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.text().ifBlank { "Серия ${index + 1}" }
                episode_number = (index + 1).toFloat()
            }
        }.reversed()
    }

    // ============================ Video Links =============================

    override fun videoListParse(response: Response): List<Video> {
        val document = response.useAsJsoup()
        return document.select("iframe[src], video source[src]").mapNotNull { element ->
            val url = element.absUrl("src").ifBlank { return@mapNotNull null }
            Video(url, "Основной", url, headers)
        }
    }

    // ============================== Helpers ===============================

    private fun listRequest(path: String, page: Int): Request {
        val url = if (page > 1) baseUrl + path + PAGE_SUFFIX.format(page) else baseUrl + path
        return GET(url, headers)
    }

    private fun listParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val entries = document.select("div.styles_productCard__6GXQ7").map(::animeFromElement)
        val hasNext = document.selectFirst("a.next") != null
        return AnimesPage(entries, hasNext)
    }

    private fun animeFromElement(element: Element): SAnime = SAnime.create().apply {
        val link = element.selectFirst("a.styles_root__XM0Up")
        setUrlWithoutDomain(link?.absUrl("href").orEmpty())
        title = element.selectFirst("a.styles_root__XM0Up")?.text()
            ?: link?.attr("title").orEmpty()
        thumbnail_url = element.selectFirst("img.styles_posterImage__HQvDn")?.absUrl("src")
    }

    companion object {
        /** Хвост URL для страниц каталога начиная со второй. */
        private const val PAGE_SUFFIX = "?page=%d"
    }
}
