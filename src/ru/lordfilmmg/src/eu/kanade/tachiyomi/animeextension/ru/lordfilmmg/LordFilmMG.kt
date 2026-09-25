package eu.kanade.tachiyomi.animeextension.ru.lordfilmmg

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
        val page = client.newCall(GET(baseUrl + anime.url, headers)).awaitSuccess().useAsJsoup()
        val embed = page.ortifiedUrl() ?: return listOf(singleEpisode(anime.url))
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
                        // Сезон и серию храним в url — плеер выбирает их query-параметрами.
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

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val path = episode.url.substringBefore('#')
        val fragment = episode.url.substringAfter('#', "")
        val season = fragment.removePrefix("S").substringBefore(":E").toIntOrNull()
        val series = fragment.substringAfter(":E", "").takeIf { it.isNotBlank() }

        val page = client.newCall(GET(baseUrl + path, headers)).awaitSuccess().useAsJsoup()
        val referer = baseUrl + path
        val embed = page.ortifiedUrl()

        if (embed != null) {
            val target = if (season != null && series != null) {
                "$embed?season=$season&episode=$series"
            } else {
                embed
            }
            val html = runCatching {
                client.newCall(GET(target, playerHeaders(referer))).awaitSuccess().bodyString()
            }.getOrNull()

            // Сериал: берём ровно ту серию, которую запросили, а не первую в сетке —
            // раньше плеер отдавал playlist целиком и открывалась не та серия.
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

            val videos = source?.let { streamVideos(it, target) }.orEmpty()
            if (videos.isNotEmpty()) return videos
        }

        // Запасной путь — веб-плееры во встроенном WebView.
        val players = page.embeddedPlayers()
        if (players.isEmpty()) throw Exception("Плеер не найден на странице")

        return players.map { player ->
            val label = if (series == null) webTitle(player) else "Серия $series • ${webTitle(player)}"
            Video(player, label, player, headers = playerHeaders(referer))
        }
    }

    /**
     * Собирает видео из HLS-мастера плеера и подставляет НАСТОЯЩИЕ названия озвучек.
     *
     * В master-плейлисте дорожки называются служебно (rus0, rus1, ukr9…), а
     * человеческие названия лежат отдельно в поле audio.names — при простом
     * разборе дорожка и её подпись расходились, из-за чего звук «жил своей
     * жизнью»: включалась не та озвучка, что выбрал пользователь.
     */
    private suspend fun streamVideos(source: PlayerSource, referer: String): List<Video> {
        val hls = source.hls?.takeIf { it.isNotBlank() } ?: return emptyList()
        val master = runCatching {
            client.newCall(GET(hls, playerHeaders(referer))).awaitSuccess().bodyString()
        }.getOrNull() ?: return emptyList()

        val names = source.audio?.names.orEmpty()
        val subtitles = source.cc.orEmpty().map { Track(it.url, it.name) }

        // Дорожки основной группы; failover-группа — те же озвучки с резервного CDN.
        // В v14.6 я добавил AD_GROUP_REGEX/AD_URI_REGEX (по словам ad/ads/preroll…),
        // и ортифаед раздаёт CDN-сегменты вида /cdn/ads-cdn.ru/seg_N.ts — регекс
        // ловил «ads» внутри этого хоста и промахивался все серии на «пусто»,
        // дальше код шёл в WebView-fallback и ExoPlayer показывал
        // «unrecognized file format». Здесь фильтр снова узкий — только когда
        // URI сам по себе — рекламный плейлист целиком (имя файла полностью или
        // первый сегмент пути состоит из ad/ads/preroll/postroll/vast/ima).
        val audio = AUDIO_MEDIA_REGEX.findAll(master)
            .mapNotNull { match ->
                val attrs = match.groupValues[1]
                if (GROUP_REGEX.find(attrs)?.groupValues?.get(1)?.startsWith("failover") == true) return@mapNotNull null
                if (isAdSlot(GROUP_REGEX.find(attrs)?.groupValues?.get(1).orEmpty())) return@mapNotNull null
                val url = MEDIA_URI_REGEX.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
                if (isAdSlot(url)) return@mapNotNull null
                val raw = MEDIA_NAME_REGEX.find(attrs)?.groupValues?.get(1).orEmpty()
                val index = raw.takeLastWhile { it.isDigit() }.toIntOrNull()
                Track(url, names.getOrNull(index ?: -1) ?: raw)
            }.toList()

        val variants = master.split("#EXT-X-STREAM-INF:").drop(1).mapNotNull { block ->
            val attrs = block.substringBefore('\n')
            // Вариант с резервной аудиогруппой — дубликат, его в список не берём.
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

    /**
     * Слот/URI «точно рекламный» только если первый сегмент пути или
     * само имя файла — ровно одно из ключевых слов ORTIFIED-VAST/IMA:
     *
     *   /preroll/1080p/index.m3u8            → AD slot
     *   /ad/720p/index.m3u8                  → AD slot
     *   /vast/master.m3u8                    → AD slot
     *
     * а обычные CDN-узлы вроде /cdn/ads-cdn.ru/seg_001.ts — НЕ реклама,
     * поэтому `ads` внутри подстроки игнорируется.
     */
    private fun isAdSlot(uri: String): Boolean {
        if (uri.isBlank()) return false
        // Относительный путь: отрезаем хост, берём первый сегмент.
        val pathOnly = if (uri.contains("://")) uri.substringAfter("://").substringAfter('/') else uri.trimStart('/')
        val firstSegment = pathOnly.substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
        return firstSegment in AD_SLOT_SEGMENTS
    }

    /** Плеер-iframe ortified: единственный, отдающий сетку серий и прямой поток. */
    private fun Document.ortifiedUrl(): String? = selectFirst("iframe[src*='ortified']")
        ?.attr("src")
        ?.takeIf { it.isNotBlank() }
        ?.toAbsoluteUrl()

    /** Сетка сезонов из `playlist: { seasons:[…] }` на странице плеера. */
    private fun String.parsePlaylist(): List<PlayerSeason>? {
        val payload = substringAfter("seasons:", "").takeIf { it.trimStart().startsWith("[") } ?: return null

        return runCatching { payload.extractJson('[', ']').parseAs<List<PlayerSeason>>() }
            .getOrNull()
            ?.filter { it.episodes.isNotEmpty() }
            ?.sortedBy { it.season }
            ?.takeIf { it.isNotEmpty() }
    }

    /** Одиночный поток фильма из `source: { … }`. */
    private fun String.parseSource(): PlayerSource? {
        val payload = substringAfter("source:", "").takeIf { it.trimStart().startsWith("{") } ?: return null
        return runCatching { payload.extractJson('{', '}').parseAs<PlayerSource>() }.getOrNull()
    }

    /** Отрезает от строки ровно один сбалансированный JSON-объект или массив. */
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
        // Маркеры player/video/embed ловят и подключаемые скрипты
        // (vid_vpaut_script.js, actualize.js) — их нельзя отдавать как видео.
        .filterNot { url -> url.substringBefore('?').endsWith(".js") }
        .filterNot { url -> url.substringBefore('?').endsWith(".css") }
        .filter { url -> PLAYER_HOSTS.any { url.contains(it) } }
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

        /** Хосты реальных плееров этого семейства (без подключаемых скриптов). */
        private val PLAYER_HOSTS =
            listOf(
                "ortified",
                "lordfilm64",
                "fotpro",
                "/embed/",
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

        // Мастер-плейлист: аудиодорожки и варианты качества.
        private val AUDIO_MEDIA_REGEX = Regex("""#EXT-X-MEDIA:(TYPE=AUDIO[^\n]*)""")
        private val GROUP_REGEX = Regex("""GROUP-ID="([^"]+)"""")
        private val MEDIA_URI_REGEX = Regex("""URI="([^"]+)"""")
        private val MEDIA_NAME_REGEX = Regex("""NAME="([^"]+)"""")
        private val STREAM_AUDIO_REGEX = Regex("""AUDIO="([^"]+)"""")
        private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+x\d+)""")
        private val BANDWIDTH_REGEX = Regex("""BANDWIDTH=(\d+)""")
        private val TITLE_PREFIX_REGEX = Regex("""^(?:Фильм|Сериал|Мультфильм|Мультсериал|Аниме)\s+""")

        // Некоторые списки несут опечатку или диапазон года, например "(20265)" / "(2024-2025)".
        private val TITLE_TAIL_REGEX = Regex("""\s*\(\d{4}\S*\)\s*$""")

        /**
         * Маркеры рекламных слотов в ortified. Это именно имена файлов/папок,
         * которые ortified кладёт в мастер-плейлист когда ему вставляют VAST/IMA
         * рекламу. Сравниваем со всем сегментом пути (lowercase, без домена).
         *
         * Подстроки внутри длинных имён (типа /cdn/ads-cdn.ru/…) НЕ матчатся —
         * мы берём только ПЕРВЫЙ сегмент URL.
         */
        private val AD_SLOT_SEGMENTS =
            setOf(
                "ad",        // /ad/1080p/index.m3u8
                "ads",       // /ads/720p/index.m3u8
                "preroll",   // /preroll/...
                "postroll",  // /postroll/...
                "ima",       // /ima/...
                "vast",      // /vast/master.m3u8
                "sponsor",   // /sponsor/...
                "promo",     // /promo/...
            )
    }
}

/** Сезон из сетки плеера ortified. */
@Serializable
data class PlayerSeason(
    val season: Int = 0,
    val episodes: List<PlayerSource> = emptyList(),
)

/** Один источник: серия сериала или единственный поток фильма. */
@Serializable
data class PlayerSource(
    val episode: String = "",
    val hls: String? = null,
    val dash: String? = null,
    val title: String? = null,
    val audio: PlayerAudio? = null,
    val cc: List<PlayerSubtitle>? = null,
)

/** Человеческие названия озвучек — в мастер-плейлисте дорожки служебные. */
@Serializable
data class PlayerAudio(
    val names: List<String> = emptyList(),
)

@Serializable
data class PlayerSubtitle(
    val url: String = "",
    val name: "",
)
