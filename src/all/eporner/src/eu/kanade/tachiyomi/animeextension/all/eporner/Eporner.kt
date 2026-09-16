package eu.kanade.tachiyomi.animeextension.all.eporner

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.AnimeHttpLegacySource
import keiyoushi.utils.addListPreference
import keiyoushi.utils.bodyString
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class Eporner :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {

    override val name = "Eporner"

    override val baseUrl = "https://www.eporner.com"

    override val lang = "all"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36",
        )

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int) = searchRequest(page, "", EpornerFilters.SearchParams(order = "most-popular"))

    override fun popularAnimeParse(response: Response): AnimesPage = listParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = searchRequest(page, "", EpornerFilters.SearchParams(order = "latest"))

    override fun latestUpdatesParse(response: Response): AnimesPage = listParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList) = searchRequest(page, query, filters.toParams())

    override fun searchAnimeParse(response: Response): AnimesPage = listParse(response)

    override fun getFilterList(): AnimeFilterList = EpornerFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = idRequest(anime.videoId)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val item = response.parseAs<VideoItem>()

        return item.toSAnime()
    }

    // ============================== Episodes ==============================

    // Every entry is a single clip, so it always has exactly one "episode".
    override fun episodeListRequest(anime: SAnime): Request = idRequest(anime.videoId)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val item = response.parseAs<VideoItem>()

        return listOf(
            SEpisode.create().apply {
                url = "/video-${item.id}/"
                episode_number = 1f
                name = listOfNotNull("Видео", item.length_min?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
            },
        )
    }

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val id = episode.url.substringAfter("/video-").trim('/')

        // The page carries a per-session hash the player endpoint requires.
        val page = client.newCall(GET("$baseUrl/video-$id/", headers)).awaitSuccess().bodyString()
        val hash = HASH_REGEX.find(page)?.groupValues?.get(1)
            ?: throw Exception("Не удалось получить ключ плеера — попробуйте ещё раз")

        val url = "$baseUrl/xhr/video/$id".toHttpUrl().newBuilder()
            .addQueryParameter("hash", hash.toPlayerHash())
            .addQueryParameter("domain", "www.eporner.com")
            .addQueryParameter("fallback", "false")
            .addQueryParameter("embed", "false")
            .addQueryParameter("supportedFormats", "dash,mp4")
            .build()

        val player = client.newCall(GET(url, headers)).awaitSuccess().parseAs<PlayerResponse>()

        if (!player.available) {
            throw Exception(player.message.ifBlank { "Видео недоступно" })
        }

        val videos = player.sources.mp4.entries
            .mapNotNull { (label, source) ->
                val src = source.src.takeIf { it.isNotBlank() && !it.endsWith("na.mp4") }
                    ?: return@mapNotNull null
                val name = source.labelShort?.takeIf { it.isNotBlank() } ?: label

                Video(src, name, src, headers = headers)
            }

        if (videos.isEmpty()) throw Exception("Ссылки на видео не найдены")

        return videos.sortedWith(
            compareByDescending<Video> { it.videoTitle.parseQuality() == preferredQuality }
                .thenByDescending { it.videoTitle.parseQuality() },
        )
    }

    /**
     * The player rejects the raw page hash: its own script splits the 32-char hex
     * value into four 8-char groups and re-encodes each of them as base36.
     */
    private fun String.toPlayerHash(): String {
        if (length != 32) return this

        return (0..3).joinToString("") { index ->
            val part = substring(index * 8, index * 8 + 8)
            part.toLong(16).toString(36)
        }
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addListPreference(
            key = PREF_QUALITY_KEY,
            default = PREF_QUALITY_DEFAULT,
            title = "Предпочитаемое качество",
            summary = "%s",
            entries = PREF_QUALITY_ENTRIES,
            entryValues = PREF_QUALITY_ENTRIES,
        )
    }

    private val preferredQuality: Int
        get() = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
            .filter { it.isDigit() }
            .toIntOrNull()
            ?: 720

    // =============================== Utils ================================

    private fun AnimeFilterList.toParams() = EpornerFilters.getSearchParameters(this)

    private fun searchRequest(page: Int, query: String, params: EpornerFilters.SearchParams): Request {
        // The public api searches by text only, so category and quality are folded
        // into the query the same way the site's own category pages do.
        val terms = listOf(
            query.trim(),
            params.category.takeIf { it.isNotBlank() && it != "all" }.orEmpty(),
            params.quality,
        ).filter { it.isNotBlank() }

        val url = "$baseUrl/api/v2/video/search/".toHttpUrl().newBuilder()
            .addQueryParameter("query", terms.joinToString(" ").ifBlank { "all" })
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE.toString())
            .addQueryParameter("order", params.order)
            .addQueryParameter("thumbsize", "big")
            .addQueryParameter("format", "json")
            .build()

        return GET(url, headers)
    }

    private fun idRequest(id: String): Request {
        val url = "$baseUrl/api/v2/video/id/".toHttpUrl().newBuilder()
            .addQueryParameter("id", id)
            .addQueryParameter("thumbsize", "big")
            .addQueryParameter("format", "json")
            .build()

        return GET(url, headers)
    }

    private fun listParse(response: Response): AnimesPage {
        val result = response.parseAs<SearchResponse>()
        val animes = result.videos.map { it.toSAnime() }

        return AnimesPage(animes, result.page < result.total_pages)
    }

    private fun VideoItem.toSAnime(): SAnime = SAnime.create().apply {
        url = "/video-$id/"
        title = this@toSAnime.title
        thumbnail_url = default_thumb?.src
        genre = keywords?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?.take(12)
            ?.joinToString(", ")
        status = SAnime.COMPLETED
        description = buildString {
            length_min?.takeIf { it.isNotBlank() }?.let { appendLine("Длительность: $it") }
            rate?.takeIf { it.isNotBlank() && it != "0.00" }?.let { appendLine("Рейтинг: $it") }
            if (views > 0) appendLine("Просмотров: $views")
            added?.takeIf { it.isNotBlank() }?.let { appendLine("Добавлено: $it") }
        }.trim()
    }

    private val SAnime.videoId: String
        get() = url.substringAfter("/video-").trim('/')

    private fun String.parseQuality(): Int = QUALITY_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    companion object {
        private const val PER_PAGE = 30

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = listOf("2160p", "1440p", "1080p", "720p", "480p", "360p", "240p")

        private val HASH_REGEX = Regex("""player\.hash\s*=\s*'([a-f0-9]{32})'""")
        private val QUALITY_REGEX = Regex("""(\d+)p""")
    }
}
