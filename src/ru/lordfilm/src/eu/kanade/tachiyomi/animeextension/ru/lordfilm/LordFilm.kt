package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import androidx.preference.PreferenceScreen
import aniyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.AnimeHttpLegacySource
import keiyoushi.utils.addEditTextPreference
import keiyoushi.utils.addListPreference
import keiyoushi.utils.bodyString
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parallelCatchingFlatMap
import keiyoushi.utils.parseAs
import keiyoushi.utils.useAsJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class LordFilm :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {

    override val name = "LordFilm"

    override val baseUrl by lazy { domain() }

    override val lang = "ru"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36",
        )

    // ============================== Popular ===============================

    // The site dropped its rating charts, so films (its largest, hand-curated
    // section) stand in for "popular" while the front page stays as "latest".
    override fun popularAnimeRequest(page: Int): Request = listRequest("/filmy", page)

    override fun popularAnimeParse(response: Response): AnimesPage = listParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = listRequest("", page)

    override fun latestUpdatesParse(response: Response): AnimesPage = listParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) return searchRequest(query, page)

        return listRequest(LordFilmFilters.getSearchParameters(filters).path, page)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = listParse(response)

    override fun getFilterList(): AnimeFilterList = LordFilmFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.useAsJsoup()
        val info = document.infoMap()

        return SAnime.create().apply {
            url = response.request.url.encodedPath
            // The info table's "Название" is the original-language title, so the
            // heading is what actually matches the card the user tapped.
            title = document.selectFirst("h1")?.text()?.cleanTitle()?.takeIf { it.isNotBlank() }
                ?: info["Название"].orEmpty()
            thumbnail_url = document.selectFirst(".fposter img, .fleft img")?.absUrl("src")
            author = info["Режиссер"]
            artist = info["Актеры"]?.split(',')?.take(4)?.joinToString(", ") { it.trim() }
            genre = (info["Жанр"] ?: info["Категории"])
                ?.split('/', ',')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.joinToString(", ")
            status = SAnime.COMPLETED
            description = buildString {
                document.selectFirst(".fdesc")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        appendLine(it)
                        appendLine()
                    }
                DETAIL_KEYS.forEach { key ->
                    info[key]?.takeIf { it.isNotBlank() }?.let { appendLine("$key: $it") }
                }
            }.trim()
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val document = client.newCall(episodeListRequest(anime)).awaitSuccess().useAsJsoup()
        val embed = document.embedUrl() ?: return listOf(singleEpisode(anime.url))

        val session = playerSession(embed)
        val episodes = session.episodes()

        // A single unnamed entry means the content is a movie, not a series.
        if (episodes.size <= 1) return listOf(singleEpisode(anime.url))

        return episodes
            .sortedWith(compareByDescending<EpisodeDto> { it.season?.order ?: 1 }.thenByDescending { it.order })
            .map { episode ->
                val season = episode.season?.order ?: 1

                SEpisode.create().apply {
                    url = "${anime.url}#${episode.id}"
                    episode_number = season * 1000f + episode.order
                    name = "$season сезон, ${episode.order} серия"
                    scanlator = episode.episodeVariants
                        .firstOrNull { it.duration > 0 }
                        ?.duration
                        ?.toDuration()
                }
            }
    }

    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException("Not used.")

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val path = episode.url.substringBefore('#')
        val episodeId = episode.url.substringAfter('#', "").toLongOrNull()

        val document = client.newCall(GET(baseUrl + path, headers)).awaitSuccess().useAsJsoup()
        val embed = document.embedUrl() ?: throw Exception("Плеер не найден на странице")

        val session = playerSession(embed)
        val episodes = session.episodes()
        val wanted = episodeId?.let { id -> episodes.firstOrNull { it.id == id } }
            ?: episodes.firstOrNull()
            ?: throw Exception("Серия не найдена в плейлисте")

        // Every dub is a separate variant with its own playlist, so all of them are
        // offered; some also expose subtitle tracks inside their master playlist.
        val variants = wanted.episodeVariants.filter { !it.filepath.isNullOrBlank() }
        if (variants.isEmpty()) throw Exception("Ссылка на видео не найдена")

        val videos = variants.parallelCatchingFlatMap { variant ->
            val master = variant.filepath!!.trimEnd('/') + "/master.m3u8"
            val label = variant.title?.takeIf { it.isNotBlank() && it != "Default" } ?: "Оригинал"

            playlistUtils.extractFromHls(
                playlistUrl = master,
                referer = "$PLAYER_ORIGIN/",
                masterHeaders = session.headers,
                videoHeaders = session.headers,
                videoNameGen = { quality -> "$label - $quality" },
            ).ifEmpty { listOf(Video(master, label, master, headers = session.headers)) }
        }

        return videos.sortedWith(
            compareByDescending<Video> { it.videoTitle.parseQuality() == preferredQuality }
                .thenByDescending { it.videoTitle.parseQuality() },
        )
    }

    /**
     * The balancer iframe embeds a one-shot API token plus a request id; both are
     * required on every catalogue call and expire with the page, so they are read
     * fresh each time instead of being cached.
     */
    private suspend fun playerSession(embed: String): PlayerSession {
        val page = client.newCall(GET(embed, playerHeaders())).awaitSuccess().bodyString()

        val token = TOKEN_REGEX.find(page)?.groupValues?.get(1)
            ?: throw Exception("Плеер не выдал токен")
        val requestId = REQUEST_ID_REGEX.find(page)?.groupValues?.get(1).orEmpty()
        val contentId = CONTENT_ID_REGEX.find(embed)?.groupValues?.get(1)
            ?: throw Exception("Не удалось определить id контента")

        val sessionHeaders = headers.newBuilder()
            .set("Referer", "$baseUrl/")
            .set("DLE-API-TOKEN", token)
            .set("X-Has-Token", "true")
            .apply { if (requestId.isNotBlank()) set("Iframe-Request-Id", requestId) }
            .build()

        return PlayerSession(contentId, sessionHeaders)
    }

    private suspend fun PlayerSession.episodes(): List<EpisodeDto> {
        val url = "$CATALOG_API/episodes".toHttpUrl().newBuilder()
            .addQueryParameter("content-id", contentId)
            .build()

        return client.newCall(GET(url, headers)).awaitSuccess().parseAs<List<EpisodeDto>>()
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addEditTextPreference(
            key = PREF_DOMAIN_KEY,
            default = PREF_DOMAIN_DEFAULT,
            title = "Домен сайта",
            summary = "%s\nЗеркало на случай блокировки.",
            dialogMessage = "По умолчанию: $PREF_DOMAIN_DEFAULT",
            restartRequired = true,
        )

        screen.addListPreference(
            key = PREF_QUALITY_KEY,
            default = PREF_QUALITY_DEFAULT,
            title = "Предпочитаемое качество",
            summary = "%s",
            entries = PREF_QUALITY_ENTRIES,
            entryValues = PREF_QUALITY_ENTRIES,
        )
    }

    private fun domain(): String {
        val raw = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!!.trim().trimEnd('/')

        return when {
            raw.isBlank() -> PREF_DOMAIN_DEFAULT
            raw.startsWith("http") -> raw
            else -> "https://$raw"
        }
    }

    private val preferredQuality: Int
        get() = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
            .filter { it.isDigit() }
            .toIntOrNull()
            ?: 720

    // =============================== Utils ================================

    /** Listing pages hang the number on the path tail: `/filmy/boevik/page/2/`. */
    private fun listRequest(path: String, page: Int): Request {
        val clean = path.removeSuffix("/")
        val url = if (page > 1) "$baseUrl$clean/page/$page/" else "$baseUrl$clean/"

        return GET(url, headers)
    }

    /** Search is the stock DLE post; results are paged by `search_start`. */
    private fun searchRequest(query: String, page: Int): Request {
        val body = FormBody.Builder()
            .add("do", "search")
            .add("subaction", "search")
            .add("full_search", "0")
            .add("search_start", page.toString())
            .add("result_from", ((page - 1) * PER_PAGE + 1).toString())
            .add("story", query.trim())
            .build()

        return POST("$baseUrl/index.php?do=search", headers, body)
    }

    private fun listParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select(".th-item")
            .mapNotNull { it.toSAnime() }
            .distinctBy { it.url }

        return AnimesPage(animes, animes.size >= PER_PAGE)
    }

    private fun Element.toSAnime(): SAnime? {
        val link = selectFirst("a[href]") ?: return null
        val href = link.absUrl("href").takeIf { it.isNotBlank() } ?: return null
        val name = selectFirst(".th-title")?.text()?.cleanTitle().orEmpty()
        if (name.isBlank()) return null

        return SAnime.create().apply {
            url = runCatching { href.toHttpUrl().encodedPath }.getOrDefault(href)
            title = name
            thumbnail_url = selectFirst(".th-img img")?.absUrl("src")
            genre = selectFirst(".th-series")?.text()?.trim()
        }
    }

    private fun singleEpisode(path: String): SEpisode = SEpisode.create().apply {
        url = path
        episode_number = 1f
        name = "Фильм"
    }

    /** The balancer iframe is the only player served straight in the markup. */
    private fun Document.embedUrl(): String? = select("iframe")
        .map { it.attr("src").ifBlank { it.attr("data-src") } }
        .firstOrNull { it.contains(BALANCER_MARKER, ignoreCase = true) }
        ?.toAbsoluteUrl()

    private fun playerHeaders() = headers.newBuilder()
        .set("Referer", "$baseUrl/")
        .build()

    private fun Document.infoMap(): Map<String, String> = select(".flist li").mapNotNull { item ->
        val text = item.text().trim()
        val key = text.substringBefore(':').trim().takeIf { it.isNotBlank() && it != text } ?: return@mapNotNull null

        key to text.substringAfter(':').trim()
    }.toMap()

    private fun String.cleanTitle(): String = trim()
        .substringBefore(" смотреть онлайн")
        .replace(TITLE_PREFIX_REGEX, "")
        .replace(TITLE_TAIL_REGEX, "")
        .trim()
        .trim('«', '»', ' ')

    private fun String.toAbsoluteUrl(): String = when {
        startsWith("//") -> "https:$this"
        startsWith("http") -> this
        startsWith("/") -> baseUrl + this
        else -> "https://$this"
    }

    private fun Int.toDuration(): String = "${this / 60} мин"

    private fun String.parseQuality(): Int = QUALITY_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    companion object {
        private const val PER_PAGE = 30

        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://lordfilm.uno"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
        private val PREF_QUALITY_ENTRIES = listOf("2160p", "1080p", "720p", "480p", "360p")

        private const val PLAYER_ORIGIN = "https://player.temptcdn.com"
        private const val BALANCER_MARKER = "/balancer-api/iframe"
        private const val CATALOG_API = "$PLAYER_ORIGIN/balancer-api/proxy/playlists/catalog-api"

        private val DETAIL_KEYS = listOf(
            "Название",
            "Год выхода",
            "Страна",
            "Качество",
            "Озвучка",
            "Режиссер",
        )

        private val TOKEN_REGEX = Regex("""'DLE-API-TOKEN'\s*:\s*'([^']+)'""")
        private val REQUEST_ID_REGEX = Regex("""'Iframe-Request-Id'\s*:\s*'([^']+)'""")
        private val CONTENT_ID_REGEX = Regex("""movie_id=(\d+)""")
        private val QUALITY_REGEX = Regex("""(\d+)p""")
        private val TITLE_PREFIX_REGEX = Regex("""^(?:Фильм|Сериал|Мультфильм|Мультсериал|Аниме)\s+""")

        // Some listings carry a typo'd or ranged year, e.g. "(20265)" / "(2024-2025)".
        private val TITLE_TAIL_REGEX = Regex("""\s*\(\d{4}\S*\)\s*$""")
    }
}
