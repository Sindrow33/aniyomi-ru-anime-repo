package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import androidx.preference.PreferenceScreen
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
 * LordFilm — зеркало lordfilmonline.cc (и родственные, напр. lordfilmlive.com).
 *
 * Движок DLE. Разделы: фильмы `/filmy/`, сериалы `/serial/`, категории
 * `/film_<key>/` и `/serial_<key>/`. Тайтлы: `/filmy/<id>-<slug>.html`,
 * `/serial/<id>-<slug>.html`. Пагинация — `/page/N/`, лента — `/index.php?do=lastnews`.
 *
 * Плеер (фильмы): сайт встраивает iframe семейства `vid*.sevstar*.com`.
 * Внутри лежит `let p2aCon = {...}` с полями `file` (обфусцирован),
 * `key` (CSRF) и `href` (хост). Ссылка на HLS получается POST-запросом:
 *   POST https://vid11.<href>/playlist/<file>.txt
 *   X-CSRF-TOKEN: <key>
 * Ответ — подписанный master-плейлист (.m3u8), привязанный к IP клиента.
 *
 * Плеер сериалов (`p.lumex.space`) пока не поддерживается — расширение честно
 * сообщает об этом и предлагает открыть тайтл в браузере.
 */
class LordFilm :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {
    override val name = "LordFilm"

    override val baseUrl by lazy { domain() }

    override val lang = "ru"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override fun headersBuilder() = super
        .headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("User-Agent", USER_AGENT)

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = listRequest(SECTION_FILMS, page)

    override fun popularAnimeParse(response: Response): AnimesPage = listParse(response)

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

        return listRequest(params.path, page)
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
            title = document
                .selectFirst("h1")
                ?.text()
                ?.cleanTitle()
                ?.takeIf { it.isNotBlank() }
                ?: info["Название"].orEmpty()
            thumbnail_url = document.selectFirst(".fposter img")?.absUrl("src")
                ?: document.selectFirst(".fleft-img img")?.absUrl("src")
            author = info["Режиссер"]
            artist = info["Актеры"]?.splitList()?.take(6)?.joinToString(", ")
            genre = listOfNotNull(info["Жанр"], info["Категории"], info["Страна"])
                .flatMap { it.splitList() }
                .filterNot { it in GENRE_NOISE }
                .distinct()
                .joinToString(", ")
            status = if (response.request.url.encodedPath.startsWith("/serial")) {
                SAnime.ONGOING
            } else {
                SAnime.COMPLETED
            }
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

    private fun String.splitList(): List<String> = split('/', ',', '|')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun episodeListParse(response: Response): List<SEpisode> =
        listOf(singleEpisode(response.request.url.encodedPath))

    private fun singleEpisode(path: String): SEpisode = SEpisode.create().apply {
        url = path
        episode_number = 1f
        name = "Смотреть"
    }

    // ============================ Video Links ===============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val path = episode.url
        val page = client.newCall(GET(baseUrl + path, headers)).awaitSuccess().useAsJsoup()
        val iframe = page.playerIframe()
            ?: throw Exception("Не удалось найти плеер на странице тайтла.")

        if (!iframe.contains("sevstar")) {
            throw Exception(
                "Плеер этого тайтла (сериалы) пока не поддерживается расширением. " +
                    "Откройте тайтл в браузере или смените зеркало в настройках.",
            )
        }

        val playerHtml = client.newCall(GET(iframe, headers)).awaitSuccess().bodyString()
        val config = parsePlayerConfig(playerHtml)
            ?: throw Exception("Не удалось разобрать данные плеера.")

        val playlistUrl = resolvePlaylist(iframe, config)
        val master = client.newCall(GET(playlistUrl, headers)).awaitSuccess().bodyString()

        return parseMaster(playlistUrl, master)
    }

    /**
     * Обфусцированный `file` (`~...`) плеер разрешает POST-запросом к своему
     * сервису: `https://vid11.<href>/playlist/<file>.txt` c заголовком
     * `X-CSRF-TOKEN: <key>`. Ответ — ссылка на master-плейлист.
     */
    private fun resolvePlaylist(iframe: String, config: PlayerConfig): String {
        val href = config.href.ifBlank { iframe.toHttpUrl().host }
        val file = config.file.removePrefix("~")
        val url = "https://vid11.$href/playlist/$file.txt"

        val postHeaders = headers.newBuilder()
            .set("X-CSRF-TOKEN", config.key)
            .set("Referer", iframe)
            .build()

        val body = client.newCall(POST(url, postHeaders, FormBody.Builder().build()))
            .execute()
            .use { it.body?.string().orEmpty().trim() }

        if (!body.startsWith("http")) {
            throw Exception("Плеер не вернул ссылку на видео.")
        }
        return body.substringBefore('\n').trim()
    }

    private fun parseMaster(masterUrl: String, body: String): List<Video> {
        val base = masterUrl.toHttpUrl()
        val lines = body.lineSequence().map { it.trim() }.toList()
        val videos = mutableListOf<Video>()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val urlLine = lines.getOrNull(i + 1)
                if (urlLine != null && !urlLine.startsWith("#")) {
                    val absolute = base.resolve(urlLine)?.toString() ?: urlLine
                    val quality = RESOLUTION_REGEX.find(line)
                        ?.groupValues
                        ?.get(1)
                        ?.substringAfter('x')
                        ?.plus("p")
                        ?: "Видео"
                    videos += Video(absolute, quality, absolute, headers = headers)
                }
                i += 2
            } else {
                i++
            }
        }

        // Уже готовая media-разметка (без вариантов) — отдаём как один поток.
        if (videos.isEmpty() && body.contains("#EXTINF")) {
            videos += Video(masterUrl, "Видео", masterUrl, headers = headers)
        }

        return videos
    }

    private fun Document.playerIframe(): String? = select("iframe[src]")
        .mapNotNull { it.absUrl("src").takeIf { url -> url.isNotBlank() } }
        .firstOrNull { !it.contains("youtube") && !it.contains("youtu.be") }

    private fun parsePlayerConfig(html: String): PlayerConfig? {
        val anchor = html.indexOf("p2aCon")
        if (anchor < 0) return null
        val start = html.indexOf('{', anchor)
        if (start < 0) return null

        return runCatching { extractJson(html, start).parseAs<PlayerConfig>() }.getOrNull()
    }

    private fun extractJson(source: String, start: Int): String {
        var depth = 0
        var inString = false
        var escaped = false

        for (i in start until source.length) {
            val c = source[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return source.substring(start, i + 1)
                }
            }
        }

        return source.substring(start)
    }

    // ============================== Helpers ===============================

    private fun listRequest(path: String, page: Int): Request {
        val clean = path.removeSuffix("/")
        val suffix = if (page <= 1) "/" else "/page/$page/"

        return GET("$baseUrl$clean$suffix".toHttpUrl(), headers)
    }

    private fun searchRequest(query: String, page: Int): Request {
        val body = FormBody
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
        val animes = document
            .select(".th-item")
            .mapNotNull { it.toSAnime() }
            .distinctBy { it.url }
            .distinctBy { it.title.lowercase() }

        return AnimesPage(animes, document.hasNextPage())
    }

    private fun Document.hasNextPage(): Boolean = selectFirst("#pagi-load a[href], .pagination a[href]") != null

    private fun Element.toSAnime(): SAnime? {
        val link = selectFirst("a[href]") ?: return null
        val href = link.absUrl("href").takeIf { it.isNotBlank() } ?: return null
        val name = selectFirst(".th-title")?.text()?.cleanTitle().orEmpty()
        if (name.isBlank()) return null

        return SAnime.create().apply {
            url = runCatching { href.toHttpUrl().encodedPath }.getOrDefault(href)
            title = name
            thumbnail_url = selectFirst(".th-img img")?.absUrl("src")
            description = selectFirst(".th-year")?.text()?.trim().orEmpty()
        }
    }

    private fun Document.infoMap(): Map<String, String> = select(".flist li")
        .mapNotNull { item ->
            val text = item.text().trim()
            val key = text.substringBefore(':').trim().takeIf { it.isNotBlank() && it != text } ?: return@mapNotNull null

            key to text.substringAfter(':').trim()
        }.toMap()

    private fun String.cleanTitle(): String = trim()
        .substringBefore(" смотреть онлайн")
        .replace(TITLE_TAIL_REGEX, "")
        .trim()
        .trim('«', '»', ' ')

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addEditTextPreference(
            key = PREF_DOMAIN_KEY,
            default = PREF_DOMAIN_DEFAULT,
            title = "Домен сайта",
            summary = "%s\nЗеркало на случай блокировки.",
            dialogMessage =
            "По умолчанию: $PREF_DOMAIN_DEFAULT\n" +
                "Рабочие зеркала: lordfilmonline.cc, lordfilmlive.com",
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

    companion object {
        private const val PER_PAGE = 24

        private const val PREF_DOMAIN_KEY = "pref_domain_key"
        private const val PREF_DOMAIN_DEFAULT = "https://lordfilmonline.cc"

        private const val SECTION_FILMS = "/filmy"

        private val DEAD_MIRRORS =
            listOf(
                "mg.lordfilm.md",
                "m.lordfilm.md",
                "lordfilm.md",
            )

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"

        private val DETAIL_KEYS =
            listOf(
                "Название",
                "Название (RU)",
                "Год выхода",
                "Страна",
                "Категории",
                "Жанр",
                "Качество",
                "Озвучка",
                "Режиссер",
                "Актеры",
            )

        private val GENRE_NOISE =
            setOf(
                "Премьеры",
                "Смотреть онлайн",
                "Новинки",
                "Фильмы",
                "Сериалы",
            )

        private val RESOLUTION_REGEX = Regex("""RESOLUTION=(\d+x\d+)""")

        private val TITLE_TAIL_REGEX = Regex("""\s*\(\d{4}\S*\)\s*$""")
    }
}

@Serializable
data class PlayerConfig(
    val file: String = "",
    val key: String = "",
    val href: String = "",
)
