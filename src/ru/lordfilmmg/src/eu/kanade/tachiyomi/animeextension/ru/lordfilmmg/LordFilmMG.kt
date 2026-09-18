package eu.kanade.tachiyomi.animeextension.ru.lordfilmmg

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
import keiyoushi.utils.bodyString
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.useAsJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * mg.lordfilm.md — зеркало семейства Лордфильма на движке DLE.
 *
 * Структура та же, что у LordFilm (lordfilm.uno/xin), но БД своя: URL тайтлов
 * вида /filmy/<id>-<slug>.html и /serialy/<id>-<slug>.html. Видео отдаётся
 * несколькими защищёнными веб-плеерами (ortified / cdn.lordfilm64 /
 * fotpro135alto), поэтому поток ищется в embed-страницах вручную, а если не
 * находится — плеер открывается во встроенном WebView, где серия/качество
 * выбирается на самом сайте.
 */
class LordFilmMG :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {
    override val name = "LordFilmMG"

    override val baseUrl by lazy { domain() }

    override val lang = "ru"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder() = super
        .headersBuilder()
        .set("Referer", "$baseUrl/")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36",
        )

    // ============================== Popular ===============================

    // Фильмы — крупнейший ручной раздел, играет роль "популярного".
    override fun popularAnimeRequest(page: Int): Request = listRequest(SECTION_FILMS, page)

    override fun popularAnimeParse(response: Response): AnimesPage = listParse(response)

    // =============================== Latest ===============================

    // Лента новинок DLE — единственный список с пагинацией cstart.
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/index.php?do=lastnews&cstart=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = listParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        if (query.isNotBlank()) return searchRequest(query, page)

        return listRequest(LordFilmMGFilters.getSearchParameters(filters).path, page)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = listParse(response)

    override fun getFilterList(): AnimeFilterList = LordFilmMGFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.useAsJsoup()
        val info = document.infoMap()

        return SAnime.create().apply {
            url = response.request.url.encodedPath
            title = document
                .selectFirst("h1")
                ?.text()
                ?.cleanTitle()
                ?.takeIf { it.isNotBlank() }
                ?: info["Название"].orEmpty()
            thumbnail_url = document.selectFirst(".fposter img, .fleft-img img")?.absUrl("src")
            author = info["Режиссер"]
            artist = info["Актеры"]?.split(',')?.take(4)?.joinToString(", ") { it.trim() }
            genre =
                (info["Жанр"] ?: info["Категории"])
                    ?.split('/', ',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.joinToString(", ")
            status = SAnime.COMPLETED
            description =
                buildString {
                    document
                        .selectFirst(".fdesc")
                        ?.text()
                        ?.trim()
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
        val document = client.newCall(GET(baseUrl + anime.url, headers)).awaitSuccess().useAsJsoup()

        // Сериалы: lordfilm64-плеер отдаёт JSON со всеми сезонами/сериями. Фильмы
        // такой сетки не имеют — для них остаётся одна запись «Смотреть».
        val lfUrl = document.lordFilm64Url() ?: return listOf(singleEpisode(anime.url))
        val lfPage =
            runCatching {
                client.newCall(GET(lfUrl, playerHeaders(baseUrl + anime.url))).awaitSuccess().bodyString()
            }.getOrNull()
        val seasons = lfPage?.let { parseSeasons(it) } ?: return listOf(singleEpisode(anime.url))

        return buildList {
            seasons.forEach { (season, episodes) ->
                episodes.forEach { (series, translation) ->
                    add(
                        SEpisode.create().apply {
                            url = "${anime.url}#S$season:E$series"
                            name =
                                buildString {
                                    append("Сезон $season • Серия $series")
                                    if (translation.isNotBlank()) append(" ($translation)")
                                }
                            episode_number = (season * 1000 + series).toFloat()
                        },
                    )
                }
            }
        }.also { if (it.size > 1) it.sortedBy { e -> e.episode_number } }
    }

    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException("Not used.")

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val path = episode.url.substringBefore('#')
        val fragment = episode.url.substringAfter('#', "")

        val document = client.newCall(GET(baseUrl + path, headers)).awaitSuccess().useAsJsoup()
        val embedded = document.embeddedPlayers()
        if (embedded.isEmpty()) throw Exception("Плеер не найден на странице")

        // Прямой поток ищем только для фильмов: серии без JS-плеера не сопоставить
        // с конкретным m3u8, поэтому для них открываем веб-плеер сразу на серии.
        val direct = if (fragment.isEmpty()) embedded.mapNotNull { directStream(it) } else emptyList()
        if (direct.isNotEmpty()) return direct

        val series = fragment.takeIf { it.isNotBlank() }?.substringAfter(":E", "")

        return embedded.map { player ->
            val url =
                if (!fragment.isBlank() && player.contains("lordfilm64")) {
                    player.toSeriesUrl(fragment)
                } else {
                    player
                }
            val label = if (fragment.isBlank()) webTitle(player) else "Серия $series • ${webTitle(player)}"
            Video(url, label, url)
        }
    }

    /**
     * Пробует вытащить прямой HLS/DASH из embed-страницы. Некоторые плееры
     * (ortified) обфусцированы и отдают поток только после JS-логики — для
     * них вернётся null, и видео пойдёт через WebView.
     */
    private suspend fun directStream(playerUrl: String): Video? = try {
        val page = client.newCall(GET(playerUrl, playerHeaders(playerUrl))).awaitSuccess().bodyString()

        HLS_REGEX.find(page)?.let { match ->
            playlistUtils
                .extractFromHls(
                    playlistUrl = match.value,
                    masterHeaders = headers,
                    referer = "$baseUrl/",
                    videoHeaders = headers,
                    videoNameGen = { quality: String -> "HLS - $quality" },
                ).firstOrNull()
        } ?: MPD_REGEX.find(page)?.let { match ->
            playlistUtils
                .extractFromDash(
                    mpdUrl = match.value,
                    videoNameGen = { quality: String -> "DASH - $quality" },
                    mpdHeaders = headers,
                    videoHeaders = headers,
                    referer = "$baseUrl/",
                ).firstOrNull()
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Вытаскивает URL lordfilm64-плеера из вкладки проигрывателя: сайт кладёт его
     * в onclick как обычную ссылку (src=...&amp;token=...).
     */
    private fun Document.lordFilm64Url(): String? = selectFirst("span[onclick*='lordfilm64']")
        ?.attr("onclick")
        ?.let { onclick -> SRC_REGEX.find(onclick)?.groupValues?.get(1) }
        ?.replace("&amp;", "&")
        ?.toAbsoluteUrl()

    /**
     * Парсит JSON lordfilm64-плеера вида {"all":{"1":{"1":{"t66":{...,"translation":"..."}}}}}
     * в карту сезон → серия → название перевода. Первый доступный перевод считается
     * основным (сайт сам упорядочивает их по качеству).
     */
    private fun parseSeasons(page: String): Map<Int, Map<Int, String>>? {
        val body = page.substringAfter("\"all\":", "").takeIf { it.startsWith("{") } ?: return null
        val seasons = linkedMapOf<Int, LinkedHashMap<Int, String>>()
        var i = 0

        fun skipWhitespace() {
            while (i < body.length && body[i].isWhitespace()) i++
        }

        fun readString(): String? {
            if (body.getOrNull(i) != '"') return null
            i++
            val sb = StringBuilder()
            while (i < body.length) {
                val c = body[i]
                if (c == '\\') {
                    i++
                    when (val n = body.getOrNull(i)) {
                        'u' -> {
                            runCatching { body.substring(i + 1, i + 5).toInt(16).toChar() }
                                .getOrNull()
                                ?.let { sb.append(it) }
                            i += 4
                        }
                        else -> sb.append(n ?: ' ')
                    }
                } else if (c == '"') {
                    i++
                    return sb.toString()
                } else {
                    sb.append(c)
                }
                i++
            }
            return null
        }

        fun skipValue() {
            skipWhitespace()
            val c = body.getOrNull(i) ?: return
            when {
                c == '{' || c == '[' -> {
                    var depth = 0
                    var inString = false
                    while (i < body.length) {
                        val ch = body[i]
                        if (inString) {
                            if (ch == '\\') {
                                i++
                            } else if (ch == '"') {
                                inString = false
                            }
                        } else if (ch == '"') {
                            inString = true
                        } else if (ch == '{' || ch == '[') {
                            depth++
                        } else if (ch == '}' || ch == ']') {
                            depth--
                            if (depth == 0) {
                                i++
                                return
                            }
                        }
                        i++
                    }
                }
                c == '"' -> readString()
                else -> while (i < body.length && body[i] !in ",}") i++
            }
        }

        skipWhitespace()
        if (body.getOrNull(i) == '{') i++

        while (i < body.length) {
            skipWhitespace()
            if (body.getOrNull(i) == '}') break
            val seasonKey = readString() ?: break
            skipWhitespace()
            if (body.getOrNull(i) != ':') break
            i++
            skipWhitespace()
            if (body.getOrNull(i) != '{') {
                skipValue()
                continue
            }
            i++
            val season = seasonKey.toIntOrNull() ?: continue
            val episodes = linkedMapOf<Int, String>()

            while (i < body.length) {
                skipWhitespace()
                if (body.getOrNull(i) == '}') break
                val epKey = readString() ?: break
                skipWhitespace()
                if (body.getOrNull(i) != ':') break
                i++
                skipWhitespace()
                if (body.getOrNull(i) != '{') {
                    skipValue()
                    continue
                }
                i++
                var translation: String? = null
                while (i < body.length) {
                    skipWhitespace()
                    if (body.getOrNull(i) == '}') break
                    val trKey = readString() ?: break
                    skipWhitespace()
                    if (body.getOrNull(i) != ':') break
                    i++
                    skipWhitespace()
                    if (body.getOrNull(i) == '{') {
                        if (translation == null && trKey.startsWith("t")) {
                            translation = TRANSLATION_REGEX.find(body.substring(i))?.groupValues?.get(1)
                        }
                        skipValue()
                    }
                }
                episodes[epKey.toIntOrNull() ?: continue] = translation.orEmpty()
            }
            seasons[season] = episodes
        }

        return seasons.takeIf { it.isNotEmpty() }
    }

    private fun String.toSeriesUrl(fragment: String): String {
        val season = fragment.removePrefix("S").substringBefore(":E")
        val series = fragment.substringAfter(":E", "")
        return runCatching {
            toHttpUrl()
                .newBuilder()
                .addQueryParameter("season", season)
                .addQueryParameter("episode", series)
                .build()
                .toString()
        }.getOrDefault(this)
    }

    private fun webTitle(playerUrl: String): String = when {
        playerUrl.contains("ortified") -> "Веб-плеер (Ortified)"
        playerUrl.contains("lordfilm64") -> "Веб-плеер (LordFilm64)"
        playerUrl.contains("fotpro135alto") -> "Веб-плеер (FotPro)"
        else -> "Веб-плеер"
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addEditTextPreference(
            key = PREF_DOMAIN_KEY,
            default = PREF_DOMAIN_DEFAULT,
            title = "Домен сайта",
            summary = "%s\nЗеркало на случай блокировки.",
            dialogMessage =
            "По умолчанию: $PREF_DOMAIN_DEFAULT\n" +
                "Рабочие зеркала: mg.lordfilm.md, m.lordfilm.md",
            restartRequired = true,
        )
    }

    private fun domain(): String {
        val raw = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!!.trim().trimEnd('/')
        val url =
            when {
                raw.isBlank() -> PREF_DOMAIN_DEFAULT
                raw.startsWith("http") -> raw
                else -> "https://$raw"
            }
        val host = runCatching { url.toHttpUrl().host }.getOrNull().orEmpty()

        if (DEAD_MIRRORS.any { it == host }) {
            preferences.edit().putString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT).apply()

            return PREF_DOMAIN_DEFAULT
        }

        return url
    }

    // =============================== Utils ================================

    private fun listRequest(
        path: String,
        page: Int,
    ): Request {
        val clean = path.removeSuffix("/")
        val url = if (page > 1) "$baseUrl$clean/page/$page/" else "$baseUrl$clean/"

        return GET(url, headers)
    }

    /** Поиск — стандартный DLE POST, как у всего семейства. */
    private fun searchRequest(
        query: String,
        page: Int,
    ): Request {
        val body =
            FormBody
                .Builder()
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
        val animes =
            document
                .select(".th-item")
                .mapNotNull { it.toSAnime() }
                .distinctBy { it.url }
                .distinctBy { it.title.lowercase() }

        return AnimesPage(animes, document.hasNextPage())
    }

    private fun Document.hasNextPage(): Boolean = selectFirst("#pagi-load a[href], .pnext a[href]") != null

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
        name = "Смотреть"
    }

    /**
     * Собирает плееры со страницы: обычные <iframe> плюс еслиrame-ы, которые
     * сайт вставляет из JavaScript-строк (они присутствуют в исходнике целиком).
     */
    private fun Document.embeddedPlayers(): List<String> = buildList {
        select("iframe[src]").forEach { add(it.absUrl("src")) }
        val html = html()
        EMBED_REGEX.findAll(html).forEach { add(it.groupValues[1]) }
    }.map { it.toAbsoluteUrl() }
        .filter { url -> PLAYER_MARKERS.any { url.contains(it) } }
        .distinct()

    private fun playerHeaders(referer: String) = headers
        .newBuilder()
        .set("Referer", referer)
        .build()

    private fun Document.infoMap(): Map<String, String> = select(".flist li")
        .mapNotNull { item ->
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

    companion object {
        private const val PER_PAGE = 24

        private const val SECTION_FILMS = "/filmy"

        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://mg.lordfilm.md"

        /** Зеркала, которые больше не резолвятся (проверено 18.09.2026). */
        private val DEAD_MIRRORS =
            listOf(
                "lordfilm.md",
                "ww1.lordfilm.md",
                "tv.lordfilm.md",
                "serial.lordfilm.md",
            )

        private val PLAYER_MARKERS =
            listOf(
                "ortified",
                "lordfilm64",
                "fotpro135alto",
                "embed",
                "player",
                "stream",
                "video",
            )

        private val DETAIL_KEYS =
            listOf(
                "Название",
                "Год выхода",
                "Страна",
                "Качество",
                "Озвучка",
                "Режиссер",
            )

        private val EMBED_REGEX = Regex("""src\s*=\s*["'](https?://[^"']+)["']""")
        private val SRC_REGEX = Regex("""src=([^\s>]+)""")
        private val TRANSLATION_REGEX = Regex(""""translation":"((?:[^"\\]|\\.)*)"""")
        private val HLS_REGEX = Regex("""https?://[^"'\s<>]+\.m3u8[^"'\s<>]*""")
        private val MPD_REGEX = Regex("""https?://[^"'\s<>]+\.mpd[^"'\s<>]*""")
        private val TITLE_PREFIX_REGEX = Regex("""^(?:Фильм|Сериал|Мультфильм|Мультсериал|Аниме)\s+""")

        // Некоторые списки несут опечатку или диапазон года, например "(20265)" / "(2024-2025)".
        private val TITLE_TAIL_REGEX = Regex("""\s*\(\d{4}\S*\)\s*$""")
    }
}
