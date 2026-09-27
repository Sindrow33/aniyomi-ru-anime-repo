package eu.kanade.tachiyomi.animeextension.all.noodlemagazine

import androidx.preference.PreferenceScreen
import aniyomi.lib.cloudflareinterceptor.CloudflareInterceptor
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
import keiyoushi.utils.useAsJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * NoodleMagazine — поисковик видео 18+.
 *
 * `hot.noodlemagazine.com` редиректит на основной домен, он и используется.
 *
 * Структура сайта:
 *  - подборки: `/home?range=day|week|month|recent|explore`, `/popular/trending`, `/now`;
 *  - поиск и теги: `/video/<запрос+через+плюс>` с параметрами `sort`, `len`, `hd`;
 *  - пагинация: `?p=N` (сайт использует POST для «Показать ещё», но тот же
 *    номер страницы работает и обычным GET);
 *  - страница просмотра: `/watch/<id>`, внутри `window.playlist` с прямыми
 *    mp4 по качествам (240/360/480/720), так что веб-плеер не нужен.
 *
 * Весь сайт закрыт Cloudflare, поэтому запросы идут через
 * [CloudflareInterceptor] — иначе вместо страниц приходит проверка браузера.
 */
class NoodleMagazine :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {

    override val name = "NoodleMagazine"

    override val baseUrl = "https://noodlemagazine.com"

    override val lang = "all"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client by lazy {
        network.client
            .newBuilder()
            .addInterceptor(CloudflareInterceptor(network.client, USER_AGENT))
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("User-Agent", USER_AGENT)

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = sectionRequest(NoodleMagazineFilters.SECTION_DEFAULT, page)

    override fun popularAnimeParse(response: Response): AnimesPage = listParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = sectionRequest("/home?range=recent", page)

    override fun latestUpdatesParse(response: Response): AnimesPage = listParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        val params = NoodleMagazineFilters.getSearchParameters(filters)
        // Отмеченные теги — часть того же поискового запроса, что и текст.
        val terms = (listOf(query.trim()) + params.tags).filter { it.isNotBlank() }

        if (terms.isEmpty()) return sectionRequest(params.section, page)

        val url = "$baseUrl/video/${terms.joinToString(" ").toSearchSlug()}"
            .toHttpUrl()
            .newBuilder()
            .apply {
                if (page > 1) addQueryParameter("p", (page - 1).toString())
                params.sort.takeIf { it.isNotBlank() }?.let { addQueryParameter("sort", it) }
                params.duration.takeIf { it.isNotBlank() }?.let { addQueryParameter("len", it) }
                if (params.onlyHd) addQueryParameter("hd", "1")
            }.build()

        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = listParse(response)

    override fun getFilterList(): AnimeFilterList = NoodleMagazineFilters.FILTER_LIST

    /** Сайт ждёт слова, разделённые «+»: /video/big+tits. */
    private fun String.toSearchSlug(): String = trim()
        .split(Regex("""[\s,]+"""))
        .filter { it.isNotBlank() }
        .joinToString("+")

    private fun sectionRequest(section: String, page: Int): Request {
        val url = (baseUrl + section).toHttpUrl().newBuilder().apply {
            if (page > 1) addQueryParameter("p", (page - 1).toString())
        }.build()

        return GET(url, headers)
    }

    private fun listParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val entries = document.select("#list_videos .item, .list_videos .item").mapNotNull { it.toSAnime() }

        // Кнопка «Показать ещё» есть, пока страница заполнена целиком.
        val hasNext = document.selectFirst(".more:not([hidden])") != null && entries.size >= PER_PAGE

        return AnimesPage(entries, hasNext)
    }

    private fun Element.toSAnime(): SAnime? {
        val href = selectFirst("a[href*=/watch/]")?.attr("href")?.takeIf { it.isNotBlank() } ?: return null
        val name = attr("data-video-title-base")
            .ifBlank { selectFirst(".title")?.text().orEmpty() }
            .trim()
        if (name.isBlank()) return null

        return SAnime.create().apply {
            url = href.substringBefore('?')
            title = name.toDisplayTitle()
            thumbnail_url = selectFirst(".i_img img")?.let { img ->
                img.attr("data-src").ifBlank { img.attr("src") }
            }
            // Длительность и просмотры показываем в описании: у сайта нет ни
            // жанров, ни авторов в выдаче — только теги на странице видео.
            description = listOfNotNull(
                selectFirst(".m_time")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { "Длительность: $it" },
                selectFirst(".m_views")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { "Просмотров: $it" },
                "HD".takeIf { selectFirst(".hd_mark") != null },
            ).joinToString("\n")
            status = SAnime.COMPLETED
        }
    }

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.useAsJsoup()
        val info = document.selectFirst(".video_info")
        val meta = document.videoObject()

        val tags = document
            .select("#videoTagsBar .vtag, .video-tags-bar .vtag")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
            .distinct()

        return SAnime.create().apply {
            url = response.request.url.encodedPath
            title = (info?.selectFirst("h1")?.text() ?: meta?.name ?: document.title())
                .removeSuffix(" watch online")
                .trim()
                .toDisplayTitle()
            thumbnail_url = meta?.thumbnailUrl ?: document.playlist()?.image
            // Теги сайта — единственное, что похоже на жанры; в них же язык
            // и студия, поэтому отдаём их целиком.
            genre = tags.joinToString(", ")
            status = SAnime.COMPLETED
            description = buildString {
                meta?.duration?.toReadableDuration()?.let { appendLine("Длительность: $it") }
                info
                    ?.selectFirst(".meta")
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { appendLine("Просмотров: ${it.substringBefore(' ')}") }
                meta?.uploadDate?.takeIf { it.isNotBlank() }?.let { appendLine("Добавлено: $it") }
                if (document.selectFirst(".hd_mark") != null) appendLine("Качество: HD")
                val likes = info?.selectFirst(".like span")?.text()?.trim()
                val dislikes = info?.selectFirst(".dislike span")?.text()?.trim()
                if (!likes.isNullOrBlank()) appendLine("Оценки: +$likes / -${dislikes.orEmpty().ifBlank { "0" }}")
                if (tags.isNotEmpty()) {
                    appendLine()
                    appendLine("Теги: ${tags.joinToString(", ")}")
                }
            }.trim()
        }
    }

    /** Разметка schema.org в теге <script type="application/ld+json">. */
    private fun Document.videoObject(): VideoObjectDto? = selectFirst("""script[type="application/ld+json"]""")
        ?.data()
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { it.parseAs<VideoObjectDto>() }.getOrNull() }

    /** ISO-8601 (PT10M21S) -> 10:21 */
    private fun String.toReadableDuration(): String? {
        val match = DURATION_REGEX.find(this) ?: return null
        val (h, m, s) = match.destructured
        val hours = h.toIntOrNull() ?: 0
        val minutes = m.toIntOrNull() ?: 0
        val seconds = s.toIntOrNull() ?: 0
        if (hours == 0 && minutes == 0 && seconds == 0) return null

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /**
     * Заголовки на сайте — это свалка ключевых слов вида
     * «Название | tag,tag,tag». Для списка берём осмысленную часть и
     * приводим первую букву к верхнему регистру.
     */
    private fun String.toDisplayTitle(): String {
        val head = split('|', '[')
            .firstOrNull { it.trim().length >= MIN_TITLE_LENGTH }
            ?.trim()
            ?: trim()

        return head
            .replace(MULTI_SPACE_REGEX, " ")
            .take(MAX_TITLE_LENGTH)
            .replaceFirstChar { it.uppercase() }
    }

    // ============================== Episodes ==============================

    // Каждая запись — отдельный ролик, поэтому эпизод всегда один.
    override fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val document = response.useAsJsoup()
        // Именно из schema.org: .m_time на этой странице принадлежит плиткам
        // «похожего», а не самому ролику.
        val duration = document.videoObject()?.duration?.toReadableDuration()

        return listOf(
            SEpisode.create().apply {
                url = response.request.url.encodedPath
                episode_number = 1f
                name = listOfNotNull("Видео", duration?.takeIf { it.isNotBlank() }).joinToString(" · ")
            },
        )
    }

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val page = client.newCall(GET(baseUrl + episode.url, headers)).awaitSuccess().bodyString()

        val playlist = page.parsePlaylist()
            ?: throw Exception("Не удалось получить ссылки на видео — возможно, ролик удалён")

        val videoHeaders = headers
            .newBuilder()
            .set("Referer", baseUrl + episode.url)
            .build()

        val videos = playlist.sources
            .mapNotNull { source ->
                val file = source.file.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val height = source.label.filter { it.isDigit() }.toIntOrNull() ?: 0
                val quality = if (height > 0) "${height}p" else "Видео"

                height to Video(file, quality, file, headers = videoHeaders)
            }.sortedByDescending { (height, _) -> height }

        if (videos.isEmpty()) throw Exception("Ссылки на видео не найдены")

        val preferred = preferredQuality

        return videos
            .sortedWith(
                compareByDescending<Pair<Int, Video>> { (height, _) -> height == preferred }
                    .thenByDescending { (height, _) -> height },
            ).map { (_, video) -> video }
    }

    private fun Document.playlist(): PlaylistDto? = html().parsePlaylist()

    /** `window.playlist = { ... }` в инлайновом скрипте страницы. */
    private fun String.parsePlaylist(): PlaylistDto? {
        val payload = substringAfter(PLAYLIST_MARKER, "").takeIf { it.trimStart().startsWith("{") } ?: return null

        return runCatching { payload.extractJsonObject().parseAs<PlaylistDto>() }.getOrNull()
    }

    private fun String.extractJsonObject(): String {
        var depth = 0
        var inString = false
        var escaped = false

        forEachIndexed { index, c ->
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return substring(0, index + 1)
                }
            }
        }

        return this
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
        get() = preferences
            .getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
            .filter { it.isDigit() }
            .toIntOrNull()
            ?: DEFAULT_QUALITY

    companion object {
        private const val PER_PAGE = 24
        private const val DEFAULT_QUALITY = 720
        private const val MIN_TITLE_LENGTH = 8
        private const val MAX_TITLE_LENGTH = 120
        private const val PLAYLIST_MARKER = "window.playlist ="

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = listOf("720p", "480p", "360p", "240p")

        private val MULTI_SPACE_REGEX = Regex("""\s+""")
        private val DURATION_REGEX = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")
    }
}
