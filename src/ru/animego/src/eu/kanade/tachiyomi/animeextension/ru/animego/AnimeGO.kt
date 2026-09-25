package eu.kanade.tachiyomi.animeextension.ru.animego

import android.net.Uri
import android.util.Base64
import androidx.preference.PreferenceScreen
import aniyomi.lib.playlistutils.PlaylistUtils
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
import keiyoushi.utils.addSwitchPreference
import keiyoushi.utils.bodyString
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parallelCatchingFlatMap
import keiyoushi.utils.parseAs
import keiyoushi.utils.useAsJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AnimeGO :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {

    override val name = "AnimeGO"

    override val lang = "ru"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val domain: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!!

    override val baseUrl: String
        get() = "https://$domain"

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36",
        )

    // ============================== Popular ===============================

    // The site's `popular` sort mirrors `aired`, which made this tab a copy of
    // "latest"; `rating` is the one that actually ranks by popularity.
    override fun popularAnimeRequest(page: Int): Request = catalogRequest(page, AnimeGOFilters.SearchParams(sort = "rating"))

    override fun popularAnimeParse(response: Response): AnimesPage = catalogParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = catalogRequest(page, AnimeGOFilters.SearchParams(sort = "createdAt"))

    override fun latestUpdatesParse(response: Response): AnimesPage = catalogParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search/anime".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("p", page.toString())
                .build()
            return GET(url, headers)
        }

        return catalogRequest(page, AnimeGOFilters.getSearchParameters(filters))
    }

    override fun searchAnimeParse(response: Response): AnimesPage = catalogParse(response)

    override fun getFilterList(): AnimeFilterList = AnimeGOFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.useAsJsoup()

        val fields = document.select(".entity-field > div")
        fun field(label: String): String? {
            val index = fields.indexOfFirst { it.text().trim() == label }
            return fields.getOrNull(index + 1)?.takeIf { index >= 0 }?.text()?.trim()
        }

        return SAnime.create().apply {
            url = response.request.url.encodedPath
            title = document.selectFirst("h1")?.text()?.trim() ?: ""
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            genre = document.select(".entity-field__genres a").joinToString { it.text() }
            author = field("Студия") ?: field("Студии")
            status = when (field("Статус")?.lowercase()) {
                "онгоинг" -> SAnime.ONGOING
                "вышел", "вышло" -> SAnime.COMPLETED
                "анонс" -> SAnime.ON_HIATUS
                else -> SAnime.UNKNOWN
            }
            description = buildString {
                document.selectFirst(".description")?.text()?.trim()
                    ?.let {
                        appendLine(it)
                        appendLine()
                    }
                listOf("Тип", "Эпизоды", "Первоисточник", "Сезон", "Выпуск", "Рейтинг MPAA", "Возрастные ограничения", "Длительность")
                    .forEach { label -> field(label)?.let { appendLine("$label: $it") } }
            }.trim()
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = playerRequest(anime.url)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val animePath = response.request.url.encodedPath.removeSuffix("/player")
        val playerData = response.parsePlayerData() ?: return emptyList()

        val episodeNumbers = playerData.allTranslations()
            .flatMap { it.episodes.keys }
            .mapNotNull { it.toFloatOrNull() }
            .distinct()
            .sortedDescending()

        if (episodeNumbers.isEmpty()) return emptyList()

        val isSingle = episodeNumbers.size == 1

        return episodeNumbers.map { number ->
            val key = number.formatNumber()
            val metaTitle = playerData.episodesMeta[key]?.title?.takeIf { it.isNotBlank() }
            SEpisode.create().apply {
                url = "$animePath|$key"
                episode_number = number
                name = when {
                    isSingle -> "Фильм"
                    metaTitle != null -> metaTitle
                    else -> "Серия $key"
                }
            }
        }
    }

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val animePath = episode.url.substringBefore('|')
        val episodeKey = episode.url.substringAfter('|', "")

        val playerData = client.newCall(playerRequest(animePath))
            .awaitSuccess()
            .parsePlayerData()
            ?: return emptyList()

        val ignoreSubs = preferences.getBoolean(PREF_IGNORE_SUBS_KEY, PREF_IGNORE_SUBS_DEFAULT)

        val targets = playerData.allTranslations()
            .filterNot { ignoreSubs && it.isSubtitles }
            .mapNotNull { translation ->
                val link = translation.episodes[episodeKey]
                    ?: translation.episodes.entries.firstOrNull { it.key.toFloatOrNull() == episodeKey.toFloatOrNull() }?.value
                    ?: translation.link.takeIf { translation.episodes.isEmpty() }
                link?.let { translation to it }
            }

        val videos = targets.parallelCatchingFlatMap { (translation, link) ->
            kodikVideos(link, translation)
        }

        return videos.sortedWith(
            compareByDescending<Video> { it.videoTitle.contains(preferredQuality) }
                .thenByDescending { it.videoTitle.parseQuality() },
        )
    }

    private suspend fun kodikVideos(playerLink: String, translation: TranslationDto): List<Video> {
        val playerUrl = playerLink.toAbsoluteUrl()
        val page = client.newCall(GET(playerUrl, headers)).awaitSuccess().bodyString()

        val rawParams = URL_PARAMS_REGEX.find(page)?.groupValues?.get(1) ?: return emptyList()
        val params = rawParams.parseAs<KodikUrlParams>()
        if (params.dSign.isEmpty() || params.pd.isEmpty()) return emptyList()

        // /serial/<id>/<hash>/<quality> or /seria/<id>/<hash>/<quality>
        val segments = playerUrl.toHttpUrl().pathSegments
        if (segments.size < 3) return emptyList()

        val formBody = FormBody.Builder()
            .add("d", params.d)
            .add("d_sign", Uri.decode(params.dSign))
            .add("pd", params.pd)
            .add("pd_sign", Uri.decode(params.pdSign))
            .add("ref", Uri.decode(params.ref))
            .add("ref_sign", Uri.decode(params.refSign))
            .add("type", segments[0])
            .add("id", segments[1])
            .add("hash", segments[2])
            .build()

        val ftorRequest = Request.Builder()
            .url("https://${params.pd}/ftor")
            .post(formBody)
            .headers(
                Headers.Builder()
                    .set("Referer", "$baseUrl/")
                    .set("Origin", "https://${params.pd}")
                    .set("User-Agent", "Mozilla/5.0 (Android)")
                    .build(),
            )
            .build()

        val ftor = client.newCall(ftorRequest).awaitSuccess().parseAs<KodikFtorResponse>()

        val label = buildString {
            append(translation.title ?: "AnimeGO")
            if (translation.isSubtitles) append(" (субтитры)")
        }

        return ftor.links.entries
            .sortedByDescending { it.key.toIntOrNull() ?: 0 }
            .flatMap { (quality, links) ->
                val encoded = links.firstOrNull()?.src ?: return@flatMap emptyList()
                val playlistUrl = decodeSource(encoded) ?: return@flatMap emptyList()

                playlistUtils.extractFromHls(
                    playlistUrl = playlistUrl,
                    referer = "https://${params.pd}/",
                    videoNameGen = { "$label - ${quality}p" },
                ).ifEmpty {
                    listOf(Video(playlistUrl, "$label - ${quality}p", playlistUrl))
                }
            }
    }

    /**
     * Kodik obfuscates the playlist url with a rotating alphabet shift applied before
     * base64 encoding. The shift changes over time, so every variant is tried and the
     * one that decodes into a valid url wins — no JS engine needed.
     */
    private fun decodeSource(encoded: String): String? {
        for (shift in 1..25) {
            val rotated = encoded.rotate(shift)
            val decoded = runCatching {
                String(Base64.decode(rotated.padBase64(), Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull() ?: continue

            if (decoded.startsWith("http") || decoded.startsWith("//")) {
                return decoded.toAbsoluteUrl()
            }
        }
        return null
    }

    private fun String.rotate(shift: Int): String = map { char ->
        when {
            char in 'a'..'z' -> 'a' + (char - 'a' + shift) % 26
            char in 'A'..'Z' -> 'A' + (char - 'A' + shift) % 26
            else -> char
        }
    }.joinToString("")

    private fun String.padBase64(): String {
        val remainder = length % 4
        return if (remainder == 0) this else this + "=".repeat(4 - remainder)
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addListPreference(
            key = PREF_DOMAIN_KEY,
            default = PREF_DOMAIN_DEFAULT,
            title = "Домен сайта",
            summary = "%s\nСменить, если текущее зеркало перестало открываться. Требуется перезапуск.",
            entries = PREF_DOMAIN_ENTRIES,
            entryValues = PREF_DOMAIN_ENTRIES,
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

        screen.addSwitchPreference(
            key = PREF_IGNORE_SUBS_KEY,
            default = PREF_IGNORE_SUBS_DEFAULT,
            title = "Скрывать озвучки с субтитрами",
            summary = "Показывать только голосовые озвучки",
        )
    }

    private val preferredQuality: String
        get() = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!

    // =============================== Utils ================================

    private fun catalogRequest(page: Int, params: AnimeGOFilters.SearchParams): Request {
        val url = "$baseUrl/anime/filter".toHttpUrl().newBuilder()
            .addQueryParameter("sort", params.sort)
            .addQueryParameter("direction", params.direction)
            .addQueryParameter("view", "list")
            .addQueryParameter("p", page.toString())
            .apply {
                params.genres.forEach { addQueryParameter("genre[]", it) }
                if (params.strictGenres) addQueryParameter("genres_strictly", "1")
                params.kinds.forEach { addQueryParameter("kind[]", it) }
                params.statuses.forEach { addQueryParameter("status[]", it) }
                params.ratings.forEach { addQueryParameter("rating[]", it) }
                params.durations.forEach { addQueryParameter("duration[]", it) }
                params.yearFrom.takeIf { it.isNotEmpty() }?.let { addQueryParameter("year_from", it) }
                params.yearTo.takeIf { it.isNotEmpty() }?.let { addQueryParameter("year_to", it) }
            }
            .build()

        return GET(url, headers)
    }

    private fun catalogParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select(".ani-list__item")
            .mapNotNull { it.toSAnime() }
            .distinctBy { it.url }

        return AnimesPage(animes, animes.size >= ITEMS_PER_PAGE)
    }

    private fun Element.toSAnime(): SAnime? {
        val link = selectFirst(".ani-list__item-title a") ?: return null
        val path = link.attr("href").takeIf { it.isNotBlank() } ?: return null

        return SAnime.create().apply {
            url = path
            title = link.attr("title").ifBlank { link.text() }.trim()
            thumbnail_url = selectFirst("img")?.absUrl("src")?.takeIf { it.isNotBlank() }
        }
    }

    private fun playerRequest(animePath: String): Request {
        val path = animePath.substringBefore('|').removeSuffix("/")
        return GET(
            "$baseUrl$path/player",
            headers.newBuilder().set("X-Requested-With", "XMLHttpRequest").build(),
        )
    }

    private fun Response.parsePlayerData(): KodikPlayerData? {
        val content = parseAs<AjaxResponse>().data?.content ?: return null
        val json = Jsoup.parseBodyFragment(content)
            .selectFirst("script#kodikPlayerData")
            ?.data()
            ?: return null

        return runCatching { json.parseAs<KodikPlayerData>() }.getOrNull()
    }

    private fun String.toAbsoluteUrl(): String = when {
        startsWith("//") -> "https:$this"
        startsWith("http") -> this
        else -> "https://$this"
    }

    private fun Float.formatNumber(): String = if (this % 1f == 0f) toInt().toString() else toString()

    private fun String.parseQuality(): Int = QUALITY_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    companion object {
        private const val ITEMS_PER_PAGE = 24

        private const val PREF_DOMAIN_KEY = "pref_domain"

        // 2026-09: animego.lat начал отдавать HTTP 500 и SSL-сертификат
        // на CN=2026-animego.org (subjectAltNames: [2026-animego.org]),
        // т.е. домен фактически мёртв — расширение отказывалось грузить каталог.
        // Переключаем дефолт на живое зеркало 2026-animego.org и поднимаем его
        // в списке первым, чтобы новые инсталлы сразу открывали рабочий сайт.
        private const val PREF_DOMAIN_DEFAULT = "2026-animego.org"
        private val PREF_DOMAIN_ENTRIES = listOf("2026-animego.org", "animego.org", "animego.lat", "animego.me", "animego.one")

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = listOf("1080p", "720p", "480p", "360p")

        private const val PREF_IGNORE_SUBS_KEY = "pref_ignore_subs"
        private const val PREF_IGNORE_SUBS_DEFAULT = true

        private val URL_PARAMS_REGEX = Regex("""urlParams\s*=\s*'(.*?)'""")
        private val QUALITY_REGEX = Regex("""(\d+)p""")
    }
}
