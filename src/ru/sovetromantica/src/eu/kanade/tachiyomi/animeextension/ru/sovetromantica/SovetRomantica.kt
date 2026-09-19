package eu.kanade.tachiyomi.animeextension.ru.sovetromantica

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.AnimeHttpLegacySource
import keiyoushi.utils.bodyString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Locale

class SovetRomantica : AnimeHttpLegacySource() {

    override val name = "SovetRomantica"

    override val baseUrl = "https://sovetromantica.com"

    override val lang = "ru"

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36",
        )

    // ============================== Popular ===============================
    // The site doesn't have an explicit "Popular" section in the provided HTML.
    // We'll use the main anime listing page, which often implies some form of popularity or latest.
    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/anime?page=$page", headers)

    override fun popularAnimeParse(response: Response): AnimesPage = parseAnimeList(response)

    // =============================== Latest ===============================
    // The "Онгоинги" section on the homepage is a good candidate for latest updates.
    // However, the /anime page is more likely to be paginated.
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/anime?page=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = parseAnimeList(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val url = "$baseUrl/anime".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = parseAnimeList(response)

    // No specific filters found in the provided HTML, so returning empty list.
    override fun getFilterList(): AnimeFilterList = AnimeFilterList()

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.asJsoup()
        val anime = SAnime.create()

        // Description (common selectors + meta tags)
        anime.description = document.selectFirst("div.anime-info__description")?.text()
            ?: document.selectFirst("div.description-text")?.text()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")
            ?: document.selectFirst("meta[name=description]")?.attr("content")

        // Genres (common selectors + meta keywords)
        anime.genre = document.select("div.anime-info__genres a").eachText().joinToString(", ")
            .ifEmpty { document.select("div.genres a").eachText().joinToString(", ") }
            .ifEmpty { document.select("meta[name=keywords]").attr("content").split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ") }

        // Status (common selectors)
        anime.status = parseStatus(
            document.selectFirst("div.anime-info__status")?.text()
                ?: document.selectFirst("span.anime-status")?.text()
        )

        return anime
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.asJsoup()
        val episodes = mutableListOf<SEpisode>()

        // Assuming episodes are listed on the anime detail page
        // Common selectors for episode links
        document.select("ul.episodes-list li a, div.episodes-block a, div.episode-item a").forEach { element ->
            val episode = SEpisode.create()
            episode.url = element.attr("href")
            episode.name = element.text()
            // Try to extract episode number from text like "Эпизод 1" or "Серия 1"
            episode.episode_number = EPISODE_NUMBER_REGEX.find(element.text())?.groupValues?.get(1)?.toFloatOrNull() ?: 1F
            episodes.add(episode)
        }

        // If no specific episode list is found, and it's a single-entry anime (like a movie),
        // create a single episode from the anime details.
        if (episodes.isEmpty()) {
            val title = document.selectFirst("h1.anime--title")?.text() ?: response.request.url.pathSegments.last()
            episodes.add(
                SEpisode.create().apply {
                    url = response.request.url.toString().substringAfter(baseUrl)
                    name = title
                    episode_number = 1F
                }
            )
        }

        return episodes.reversed() // Assuming episodes are listed oldest to newest, reverse for newest first
    }

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val episodeUrl = baseUrl + episode.url
        val document = client.newCall(GET(episodeUrl, headers)).awaitSuccess().asJsoup()
        val videos = mutableListOf<Video>()

        // Attempt 1: Direct <video> tag with <source>
        document.select("video source[src]").forEach { source ->
            val videoUrl = source.attr("abs:src")
            if (videoUrl.isNotBlank()) {
                val quality = source.attr("label").ifBlank { "Default" }
                videos.add(Video(videoUrl, quality, videoUrl, headers = headers))
            }
        }

        // Attempt 2: <iframe src="..."> for embedded players
        document.select("iframe[src]").forEach { iframe ->
            val iframeSrc = iframe.attr("abs:src")
            if (iframeSrc.isNotBlank() && !iframeSrc.contains("youtube.com") && !iframeSrc.contains("vk.com")) { // Exclude common social embeds
                // This is a placeholder. Real implementation would need to fetch the iframe content
                // and parse it for video sources, which can be complex and vary by host.
                // For now, we'll just add a generic "Embedded" video.
                // A more robust solution would involve a separate source for the iframe host.
                videos.add(Video(iframeSrc, "Embedded", iframeSrc, headers = headers))
            }
        }

        // Attempt 3: Look for script variables containing video data (e.g., player config)
        // This is highly speculative without a live site.
        val scriptContent = document.select("script:contains(player)").html()
        val videoRegex = Regex("""['"]file['"]:\s*['"](https?://[^'"]+\.(?:mp4|m3u8|webm))['"]""")
        videoRegex.findAll(scriptContent).forEach { match ->
            val videoUrl = match.groupValues[1]
            if (videoUrl.isNotBlank()) {
                // Try to extract quality from URL or surrounding text if possible
                val quality = videoUrl.substringAfterLast("/").substringBefore(".").substringAfterLast("-").ifBlank { "Default" }
                videos.add(Video(videoUrl, quality, videoUrl, headers = headers))
            }
        }

        if (videos.isEmpty()) {
            throw Exception("Видео не найдено на странице эпизода: $episodeUrl")
        }

        // Sort by quality, preferring HD/720p
        return videos.sortedWith(
            compareByDescending<Video> { it.quality.contains("HD", ignoreCase = true) || it.quality.contains("720", ignoreCase = true) }
                .thenByDescending { it.quality.filter { char -> char.isDigit() }.toIntOrNull() ?: 0 }
        )
    }

    // =============================== Utils ================================

    private fun parseAnimeList(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animes = document.select("div.anime--block__desu").map { it.toSAnime() }

        // Check for next page. The provided HTML snippet doesn't show explicit pagination.
        // Assuming a simple "next" link or a page parameter. For now, assume no next page
        // unless a clear selector like a.pagination__next or a[rel=next] is found.
        val hasNextPage = document.selectFirst("a.pagination__next") != null ||
            document.selectFirst("a[rel=next]") != null

        return AnimesPage(animes, hasNextPage)
    }

    private fun Element.toSAnime(): SAnime = SAnime.create().apply {
        val russianTitle = selectFirst("div.anime--block__name span:last-child")?.text()
        val originalTitle = selectFirst("div.anime--block__name span:first-child")?.text()

        title = if (russianTitle.isNullOrBlank()) originalTitle.orEmpty() else russianTitle
        if (!originalTitle.isNullOrBlank() && originalTitle != russianTitle) {
            title += " / $originalTitle"
        }

        // Thumbnail URL: data-src is relative to chitoge.sovetromantica.com
        thumbnail_url = selectFirst("img.anime--poster.lazy")?.attr("data-src")?.let {
            "https://chitoge.sovetromantica.com/" + it
        }

        url = selectFirst("a.anime--block__poster")?.attr("href").orEmpty()
    }

    private fun parseStatus(statusString: String?): Int {
        return when (statusString?.lowercase(Locale.ROOT)) {
            "завершено" -> SAnime.COMPLETED
            "онгоинг" -> SAnime.ONGOING
            "анонс" -> SAnime.ON_HIATUS
            else -> SAnime.UNKNOWN
        }
    }

    private fun Response.asJsoup(): Document = Jsoup.parse(bodyString())

    companion object {
        private val EPISODE_NUMBER_REGEX = Regex("""(?:Эпизод|Серия)\s*(\d+)|\[(\d+)\]""", RegexOption.IGNORE_CASE)
    }
}
