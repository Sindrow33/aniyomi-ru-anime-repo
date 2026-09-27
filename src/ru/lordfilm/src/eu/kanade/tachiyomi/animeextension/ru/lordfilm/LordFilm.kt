package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.AnimeHttpLegacySource
import keiyoushi.utils.addEditTextPreference
import keiyoushi.utils.bodyString
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.useAsJsoup
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

/**
 * LordFilm — зеркало mg.lordfilm.md на движке DLE.
 *
 * URL тайтлов вида /filmy/<id>-<slug>.html и /serialy/<id>-<slug>.html.
 *
 * Плеер: сайт встраивает iframe семейства insertunit (api.nextembed.ws,
 * api.ortified.ws и т.д.), но сами эти хосты отдают 410/404. На странице
 * подключён actualize.js, который в браузере на лету переписывает хост на
 * актуальный ($PLAYER_ACTUAL_DEFAULT) — расширение делает то же самое в
 * [actualizeEmbed]. Внутри embed-страницы лежит `seasons: [...]` (сериалы)
 * или `source: {...}` (фильмы) с HLS-мастер-плейлистом.
 *
 * Если поток не разобрался, плеер отдаётся как внешний Video-URL — Tadami
 * откроет его во встроенном WebView.
 */
class LordFilm :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {
    override val name = "LordFilm"

    override val baseUrl by lazy { domain() }

    override val lang = "ru"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client by lazy {
        network.client
            .newBuilder()
            .addInterceptor(JsChallengeInterceptor(USER_AGENT, network.client.cookieJar))
            .build()
    }

    override fun headersBuilder() = super
        .headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("User-Agent", USER_AGENT)

    // ============================== Popular ===============================

    /**
     * /top50/ — единственная реальная подборка по популярности на сайте: одна
     * страница на 50+ тайтлов без пагинации. Со второй страницы отдаём
     * раздел «Фильмы», чтобы вкладка продолжала листаться.
     */
    override fun popularAnimeRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrl/top50/", headers)
    } else {
        listRequest(LordFilmFilters.SECTION_FILMS, page - 1)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val page = listParse(response)

        // У /top50/ нет пагинации, но следующая страница есть — это «Фильмы».
        return if (response.request.url.encodedPath.startsWith("/top50")) {
            AnimesPage(page.animes, true)
        } else {
            page
        }
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/index.php?do=lastnews&cstart=$page", headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = listParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        if (query.isNotBlank()) return searchRequest(query, page)

        val params = LordFilmFilters.getSearchParameters(filters)

        return listRequest(params.path, page, smartFilter = params.isSmartFilter)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = listParse(response)

    override fun getFilterList(): AnimeFilterList = LordFilmFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.useAsJsoup()
        val info = document.infoMap()
        val path = response.request.url.encodedPath

        return SAnime.create().apply {
            url = path
            title = document
                .selectFirst("h1")
                ?.text()
                ?.cleanTitle()
                ?.takeIf { it.isNotBlank() }
                ?: info["Название"].orEmpty()
            thumbnail_url = document.selectFirst(".fposter img, .fleft-img img")?.absUrl("src")
            author = info["Режиссер"]
            artist = info["Актеры"]?.splitList()?.take(6)?.joinToString(", ")
            // «Жанр» на части страниц пустой, реальные метки лежат в «Категории»;
            // к ним добавляем страну и год, чтобы теги в Tadami были полными.
            genre = listOfNotNull(info["Жанр"], info["Категории"], info["Страна"])
                .flatMap { it.splitList() }
                .filterNot { it in GENRE_NOISE }
                .distinct()
                .joinToString(", ")
            status = path.animeStatus(info["Год выхода"])
            description = buildString {
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

    /**
     * Сайт не пишет статус текстом. Сериалы и мультсериалы свежих лет считаем
     * онгоингами, остальное — завершённым; фильмы всегда завершены.
     */
    private fun String.animeStatus(year: String?): Int {
        val isSeries = startsWith("/serialy") || startsWith("/mult")
        if (!isSeries) return SAnime.COMPLETED
        val released = YEAR_REGEX.find(year.orEmpty())?.value?.toIntOrNull() ?: return SAnime.UNKNOWN

        return if (released >= CURRENT_YEAR - 1) SAnime.ONGOING else SAnime.COMPLETED
    }

    private fun String.splitList(): List<String> = split('/', ',', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val page = client.newCall(GET(baseUrl + anime.url, headers)).awaitSuccess().useAsJsoup()
        val embed = page.playerEmbedUrl() ?: return listOf(singleEpisode(anime.url))
        val html = runCatching {
            client.newCall(GET(embed, playerHeaders(baseUrl + anime.url))).awaitSuccess().bodyString()
        }.getOrNull() ?: return listOf(singleEpisode(anime.url))

        val seasons = html.parsePlaylist() ?: return listOf(singleEpisode(anime.url))
        val multiSeason = seasons.size > 1

        return seasons
            .flatMap { season ->
                season.episodes.map { episode ->
                    val number = episode.episode.toIntOrNull() ?: 0
                    SEpisode.create().apply {
                        url = "${anime.url}#S${season.season}:E${episode.episode}"
                        name = buildString {
                            if (multiSeason) append("Сезон ${season.season} • ")
                            append("Серия ${episode.episode}")
                        }
                        episode_number = (season.season * 1000 + number).toFloat()
                    }
                }
            }.sortedByDescending { it.episode_number }
    }

    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException("Not used.")

    // ============================ Video Links ===============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val path = episode.url.substringBefore('#')
        val fragment = episode.url.substringAfter('#', "")
        val season = fragment.removePrefix("S").substringBefore(":E").toIntOrNull()
        val series = fragment.substringAfter(":E", "").takeIf { it.isNotBlank() }

        val page = client.newCall(GET(baseUrl + path, headers)).awaitSuccess().useAsJsoup()
        val referer = baseUrl + path
        val embed = page.playerEmbedUrl()

        if (embed != null) {
            val target = if (season != null && series != null) {
                "$embed?season=$season&episode=$series"
            } else {
                embed
            }
            val html = runCatching {
                client.newCall(GET(target, playerHeaders(referer))).awaitSuccess().bodyString()
            }.getOrNull()

            val source = html?.let { body ->
                if (season != null && series != null) {
                    body.parsePlaylist()
                        ?.firstOrNull { it.season == season }
                        ?.episodes
                        ?.firstOrNull { it.episode == series }
                } else {
                    body.parseSource()
                }
            }

            val videos = source?.let { streamVideos(it, embedReferer(target)) }.orEmpty()
            if (videos.isNotEmpty()) return videos
        }

        // Возвращать здесь HTML-страницу плеера нельзя: плеер Tadami считает
        // Video.videoUrl медиапотоком и на странице выдаёт
        // «unrecognized file format». Лучше честная ошибка.
        throw Exception(
            "Не удалось получить видео. Смените «Хост плеера» в настройках расширения " +
                "или откройте тайтл в браузере.",
        )
    }

    /** Для запросов к CDN реферером должен быть сам плеер, а не страница сайта. */
    private fun embedReferer(embedUrl: String): String = embedUrl.toOrigin() + "/"

    private suspend fun streamVideos(source: PlayerSource, referer: String): List<Video> {
        val hls = source.hls?.takeIf { it.isNotBlank() } ?: return emptyList()
        val master = runCatching {
            client.newCall(GET(hls, playerHeaders(referer))).awaitSuccess().bodyString()
        }.getOrNull() ?: return emptyList()

        val names = source.audio?.names.orEmpty()
        val subtitles = source.cc.orEmpty().map { Track(it.url, it.name) }

        val audio = AUDIO_MEDIA_REGEX.findAll(master)
            .mapNotNull { match ->
                val attrs = match.groupValues[1]
                if (GROUP_REGEX.find(attrs)?.groupValues?.get(1)?.startsWith("failover") == true) return@mapNotNull null
                if (isAdSlot(GROUP_REGEX.find(attrs)?.groupValues?.get(1).orEmpty())) return@mapNotNull null
                val url = MEDIA_URI_REGEX.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
                if (isAdSlot(url)) return@mapNotNull null
                // NAME в плейлисте — технический "rus0"/"ukr4"; человеческие
                // названия озвучек лежат в audio.names самого плеера.
                val raw = MEDIA_NAME_REGEX.find(attrs)?.groupValues?.get(1).orEmpty()
                val index = raw.takeLastWhile { it.isDigit() }.toIntOrNull()
                val lang = raw.dropLastWhile { it.isDigit() }.toLangLabel()
                val title = names.getOrNull(index ?: -1)
                Track(url, listOfNotNull(title ?: raw.takeIf { it.isNotBlank() }, lang).joinToString(" • "))
            }.toList()

        val variants = master.split("#EXT-X-STREAM-INF:").drop(1).mapNotNull { block ->
            val attrs = block.substringBefore('\n')
            val audioGroup = STREAM_AUDIO_REGEX.find(attrs)?.groupValues?.get(1).orEmpty()
            if (audioGroup.startsWith("failover")) return@mapNotNull null
            if (isAdSlot(audioGroup)) return@mapNotNull null
            val url = block.substringAfter('\n').lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: return@mapNotNull null
            if (isAdSlot(url)) return@mapNotNull null
            val quality = RESOLUTION_REGEX.find(attrs)?.groupValues?.get(1)?.substringAfter('x')?.plus("p")
                ?: "Видео"
            val bandwidth = BANDWIDTH_REGEX.find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

            bandwidth to Video(
                url,
                quality,
                url,
                headers = playerHeaders(referer),
                subtitleTracks = subtitles,
                audioTracks = audio,
            )
        }

        return variants.sortedByDescending { (bandwidth, _) -> bandwidth }.map { (_, video) -> video }
    }

    private fun String.toLangLabel(): String? = when (lowercase()) {
        "rus", "ru" -> "рус"
        "ukr", "uk" -> "укр"
        "eng", "en" -> "eng"
        "kor", "ko" -> "kor"
        "jpn", "ja" -> "jpn"
        else -> null
    }

    private fun isAdSlot(uri: String): Boolean {
        if (uri.isBlank()) return false
        val pathOnly = if (uri.contains("://")) uri.substringAfter("://").substringAfter('/') else uri.trimStart('/')
        val firstSegment = pathOnly.substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
        return firstSegment in AD_SLOT_SEGMENTS
    }

    /**
     * Ищет embed-плеер семейства insertunit и «актуализирует» его хост так же,
     * как это делает actualize.js на самом сайте.
     */
    private fun Document.playerEmbedUrl(): String? = embeddedPlayers()
        .firstOrNull { it.isInsertunitEmbed() }
        ?.actualizeEmbed()

    private fun String.isInsertunitEmbed(): Boolean {
        val host = runCatching { toHttpUrl().host }.getOrNull().orEmpty()
        if (host.isBlank()) return false
        if (host == PLAYER_ACTUAL_HOST) return true
        return PLAYER_EMBED_DOMAINS.any { host == it || host.endsWith(".$it") }
    }

    /** api.nextembed.ws/embed/movie/1 -> <актуальный хост>/embed/movie/1 */
    private fun String.actualizeEmbed(): String {
        val url = runCatching { toHttpUrl() }.getOrNull() ?: return this
        val actual = actualHost()
        if (url.host == actual.toHttpUrl().host) return this

        return buildString {
            append(actual.trimEnd('/'))
            append(url.encodedPath)
            url.encodedQuery?.let {
                append('?')
                append(it)
            }
        }
    }

    private fun String.parsePlaylist(): List<PlayerSeason>? {
        val payload = substringAfter("seasons:", "").takeIf { it.trimStart().startsWith("[") } ?: return null

        return runCatching { payload.extractJson('[', ']').parseAs<List<PlayerSeason>>() }
            .getOrNull()
            ?.filter { it.episodes.isNotEmpty() }
            ?.sortedBy { it.season }
            ?.takeIf { it.isNotEmpty() }
    }

    private fun String.parseSource(): PlayerSource? {
        val payload = substringAfter("source:", "").takeIf { it.trimStart().startsWith("{") } ?: return null
        return runCatching { payload.extractJson('{', '}').parseAs<PlayerSource>() }.getOrNull()
    }

    private fun String.extractJson(open: Char, close: Char): String {
        var depth = 0
        var inString = false
        var escaped = false

        forEachIndexed { index, c ->
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) return substring(0, index + 1)
                }
            }
        }

        return this
    }

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

        screen.addEditTextPreference(
            key = PREF_PLAYER_KEY,
            default = PLAYER_ACTUAL_DEFAULT,
            title = "Хост плеера",
            summary = "%s\nМеняется, когда сайт переезжает на новый плеер.",
            dialogMessage =
            "По умолчанию: $PLAYER_ACTUAL_DEFAULT\n" +
                "Встроенные в страницу хосты (api.nextembed.ws и подобные) " +
                "не работают напрямую — расширение подменяет их на этот.",
            restartRequired = false,
        )
    }

    private fun actualHost(): String {
        val raw = preferences.getString(PREF_PLAYER_KEY, PLAYER_ACTUAL_DEFAULT)!!.trim().trimEnd('/')

        return when {
            raw.isBlank() -> PLAYER_ACTUAL_DEFAULT
            raw.startsWith("http") -> raw
            else -> "https://$raw"
        }
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

    /**
     * Обычные разделы листаются как `/path/page/2/`, «умный фильтр» — как
     * `/sf/genre:.../page:2/` (через двоеточие). Пути с кириллицей
     * кодируются okhttp автоматически.
     */
    private fun listRequest(
        path: String,
        page: Int,
        smartFilter: Boolean = false,
    ): Request {
        val clean = path.removeSuffix("/")
        val suffix = when {
            page <= 1 -> "/"
            smartFilter -> "/page:$page/"
            else -> "/page/$page/"
        }

        return GET("$baseUrl$clean$suffix".toHttpUrl(), headers)
    }

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

            // .th-series — это год выпуска, а не жанр (раньше он попадал в genre
            // и мусорил тегами). Год и рейтинги показываем в описании.
            val year = selectFirst(".th-series")?.text()?.trim().orEmpty()
            val rates = select(".th-rate").joinToString(" • ") { rate ->
                "${rate.attr("data-text")} ${rate.text().trim()}"
            }
            description = listOf(year, rates).filter { it.isNotBlank() }.joinToString("\n")
        }
    }

    private fun singleEpisode(path: String): SEpisode = SEpisode.create().apply {
        url = path
        episode_number = 1f
        name = "Смотреть"
    }

    private fun Document.embeddedPlayers(): List<String> = buildList {
        select("iframe[src]").forEach { add(it.absUrl("src")) }
        val html = html()
        EMBED_REGEX.findAll(html).forEach { add(it.groupValues[1]) }
    }.map { it.toAbsoluteUrl() }
        .filterNot { url -> url.substringBefore('?').endsWith(".js") }
        .filterNot { url -> url.substringBefore('?').endsWith(".css") }
        .filter { url -> url.isInsertunitEmbed() || PLAYER_HOSTS.any { host -> url.contains(host) } }
        .distinct()

    /**
     * CDN плеера (interkh.com и родственные) подписывает ссылки под тот
     * User-Agent, которым забрали embed-страницу: с любым другим UA сегменты
     * отдают HTTP 410 «доступ закрыт». Поэтому и embed, и мастер-плейлист, и
     * сами видео ходят с одним и тем же набором заголовков.
     */
    private fun playerHeaders(referer: String) = headers
        .newBuilder()
        .set("Referer", referer)
        .set("Origin", referer.toOrigin())
        .build()

    private fun String.toOrigin(): String = runCatching {
        val url = toHttpUrl()
        "${url.scheme}://${url.host}"
    }.getOrDefault(baseUrl)

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

        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://mg.lordfilm.md"

        private val DEAD_MIRRORS =
            listOf(
                "lordfilm.md",
                "ww1.lordfilm.md",
                "tv.lordfilm.md",
                "serial.lordfilm.md",
            )

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"

        private const val PREF_PLAYER_KEY = "pref_player_host"
        private const val PLAYER_ACTUAL_DEFAULT = "https://api.femd.ws"
        private const val PLAYER_ACTUAL_HOST = "api.femd.ws"

        /**
         * Домены плееров семейства insertunit — их встраивает сайт, но отвечает
         * на запросы только актуальный хост (см. [actualizeEmbed]).
         */
        private val PLAYER_EMBED_DOMAINS =
            listOf(
                "femd.ws",
                "nextembed.ws",
                "ortified.ws",
                "insertunit.ws",
                "embess.ws",
                "luxembd.ws",
                "zenithjs.ws",
                "atomics.ws",
                "variyt.ws",
                "domem.ws",
                "namy.ws",
                "marts.ws",
                "ninsel.ws",
                "embr.ws",
                "embprox.ws",
                "framprox.ws",
                "hostemb.ws",
                "loadbox.ws",
                "getcodes.ws",
                "strvid.ws",
                "delivembd.ws",
                "delivembed.cc",
                "buildplayer.com",
                "embedstorage.net",
                "synchroncode.com",
                "placehere.link",
                "multikland.net",
            )

        private val PLAYER_HOSTS =
            listOf(
                "lordfilm64",
                "fotpro",
                "/embed/",
            )

        private val DETAIL_KEYS =
            listOf(
                "Название",
                "Оригинальное название",
                "Год выхода",
                "Страна",
                "Категории",
                "Качество",
                "Озвучка",
                "Режиссер",
                "Актеры",
            )

        /** Навигационные метки из «Категории», которые не являются жанрами. */
        private val GENRE_NOISE =
            setOf(
                "Премьеры",
                "Смотреть онлайн",
                "Новинки",
                "Фильмы",
                "Сериалы",
            )

        private val YEAR_REGEX = Regex("""\d{4}""")

        private val CURRENT_YEAR: Int
            get() = Calendar.getInstance().get(Calendar.YEAR)

        private val EMBED_REGEX = Regex("""src\s*=\s*["'](https?://[^"']+)["']""")

        private val AUDIO_MEDIA_REGEX = Regex("""#EXT-X-MEDIA:(TYPE=AUDIO[^\n]*)""")
        private val GROUP_REGEX = Regex("""GROUP-ID="([^"]+)"""")
        private val MEDIA_URI_REGEX = Regex("""URI="([^"]+)"""")
        private val MEDIA_NAME_REGEX = Regex("""NAME="([^"]+)"""")
        private val STREAM_AUDIO_REGEX = Regex("""AUDIO="([^"]+)"""")
        private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+x\d+)""")
        private val BANDWIDTH_REGEX = Regex("""BANDWIDTH=(\d+)""")
        private val TITLE_PREFIX_REGEX = Regex("""^(?:Фильм|Сериал|Мультфильм|Мультсериал|Аниме)\s+""")

        private val TITLE_TAIL_REGEX = Regex("""\s*\(\d{4}\S*\)\s*$""")

        private val AD_SLOT_SEGMENTS =
            setOf(
                "ad",
                "ads",
                "preroll",
                "postroll",
                "ima",
                "vast",
                "sponsor",
                "promo",
            )
    }
}

@Serializable
data class PlayerSeason(
    val season: Int = 0,
    val episodes: List<PlayerSource> = emptyList(),
)

@Serializable
data class PlayerSource(
    val episode: String = "",
    val hls: String? = null,
    val dash: String? = null,
    val title: String? = null,
    val audio: PlayerAudio? = null,
    val cc: List<PlayerSubtitle>? = null,
)

@Serializable
data class PlayerAudio(
    val names: List<String> = emptyList(),
)

@Serializable
data class PlayerSubtitle(
    val url: String = "",
    val name: String = "",
)
