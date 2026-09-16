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

    override fun popularAnimeRequest(page: Int): Request = listRequest("/top50", page)

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
            title = info["Название"] ?: document.selectFirst("h1")?.text()?.cleanTitle().orEmpty()
            thumbnail_url = document.selectFirst(".fposter img, .fleft img")?.absUrl("src")
            author = info["Режиссер"]
            artist = info["Актеры"]?.split(',')?.take(4)?.joinToString(", ") { it.trim() }
            genre = info["Категории"]?.split('/')?.joinToString(", ") { it.trim() }
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
        val embed = document.embedUrl()
            ?: return listOf(singleEpisode(anime.url))

        val player = client.newCall(GET(embed, playerHeaders())).awaitSuccess().bodyString()
        val seasons = player.seasons()

        // Movies carry no season list — the embed itself is the whole video.
        if (seasons.isEmpty()) return listOf(singleEpisode(anime.url))

        return seasons
            .sortedByDescending { it.season }
            .flatMap { season ->
                season.episodes.map { episode ->
                    val number = episode.episode.toFloatOrNull() ?: 0f

                    SEpisode.create().apply {
                        url = "$embed#${season.season}:${episode.episode}"
                        episode_number = season.season * 1000f + number
                        name = "${season.season} сезон, ${number.toInt()} серия"
                        scanlator = episode.duration.takeIf { it > 0 }?.toDuration()
                    }
                }.sortedByDescending { it.episode_number }
            }
    }

    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException("Not used.")

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val embed = episode.url.substringBefore('#')
        val target = episode.url.substringAfter('#', "")

        val master = if (embed.startsWith("http")) {
            val player = client.newCall(GET(embed, playerHeaders())).awaitSuccess().bodyString()

            if (target.isBlank()) {
                player.movieHls()
            } else {
                val season = target.substringBefore(':').toIntOrNull()
                val number = target.substringAfter(':')

                player.seasons()
                    .firstOrNull { it.season == season }
                    ?.episodes
                    ?.firstOrNull { it.episode == number }
                    ?.hls
            }
        } else {
            val document = client.newCall(GET(baseUrl + embed, headers)).awaitSuccess().useAsJsoup()
            val url = document.embedUrl() ?: throw Exception("Плеер не найден на странице")

            client.newCall(GET(url, playerHeaders())).awaitSuccess().bodyString().movieHls()
        }

        if (master.isNullOrBlank()) throw Exception("Ссылка на видео не найдена")

        val videos = playlistUtils.extractFromHls(master, referer = "$PLAYER_HOST/")
            .ifEmpty { listOf(Video(master, "Авто", master, headers = playerHeaders())) }

        return videos.sortedWith(
            compareByDescending<Video> { it.videoTitle.parseQuality() == preferredQuality }
                .thenByDescending { it.videoTitle.parseQuality() },
        )
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

    /** The main player is the only iframe served straight in the markup. */
    private fun Document.embedUrl(): String? = select("iframe")
        .map { it.attr("src") }
        .firstOrNull { it.contains("/embed/", ignoreCase = true) }
        ?.toAbsoluteUrl()

    /** Series players inline a `seasons:[...]` array holding every episode's hls link. */
    private fun String.seasons(): List<Season> {
        val marker = indexOf(SEASONS_MARKER).takeIf { it >= 0 } ?: return emptyList()
        val start = indexOf('[', marker)
        var depth = 0

        for (index in start until length) {
            when (this[index]) {
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) {
                        return runCatching { substring(start, index + 1).parseAs<List<Season>>() }
                            .getOrDefault(emptyList())
                    }
                }
            }
        }

        return emptyList()
    }

    private fun String.movieHls(): String? = HLS_REGEX.find(this)?.groupValues?.get(1)

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
        .replace(TITLE_TAIL_REGEX, "")
        .trim()

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
        private const val PREF_DOMAIN_DEFAULT = "https://mg.lordfilm.md"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
        private val PREF_QUALITY_ENTRIES = listOf("2160p", "1080p", "720p", "480p", "360p")

        private const val PLAYER_HOST = "https://api.ortified.ws"
        private const val SEASONS_MARKER = "seasons:"

        private val DETAIL_KEYS = listOf(
            "Оригинальное название",
            "Год выхода",
            "Страна",
            "Качество",
            "Режиссер",
        )

        private val HLS_REGEX = Regex("""hls:\s*"([^"]+)"""")
        private val QUALITY_REGEX = Regex("""(\d+)p""")
        private val TITLE_TAIL_REGEX = Regex("""\s*\(\d{4}\)\s*$""")
    }
}
