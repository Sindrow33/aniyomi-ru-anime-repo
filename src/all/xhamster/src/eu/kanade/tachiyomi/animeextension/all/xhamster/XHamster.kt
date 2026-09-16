package eu.kanade.tachiyomi.animeextension.all.xhamster

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
import keiyoushi.utils.addEditTextPreference
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XHamster :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {

    override val name = "xHamster"

    override val baseUrl by lazy { domain() }

    override val lang = "all"

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

    override fun popularAnimeRequest(page: Int): Request = listRequest("/best/weekly", page)

    override fun popularAnimeParse(response: Response): AnimesPage = listParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = listRequest("/newest", page)

    override fun latestUpdatesParse(response: Response): AnimesPage = listParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = XHamsterFilters.getSearchParameters(filters)

        if (query.isNotBlank()) return GET(searchUrl(query, page, params.sort), headers)

        val path = params.category.takeIf { it.isNotBlank() }?.let { "/categories/$it" } ?: params.section

        return listRequest(path, page)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = listParse(response)

    override fun getFilterList(): AnimeFilterList = XHamsterFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val page = response.bodyString()
        val model = page.initials<WatchInitials>()?.videoModel

        return SAnime.create().apply {
            url = response.request.url.encodedPath
            title = model?.title?.takeIf { it.isNotBlank() } ?: page.metaTitle()
            thumbnail_url = model?.thumbURL?.takeIf { it.isNotBlank() }
            author = model?.author?.name
            genre = page.tagNames()
            status = SAnime.COMPLETED
            description = buildString {
                model?.description?.takeIf { it.isNotBlank() }?.let {
                    appendLine(it)
                    appendLine()
                }
                model?.duration?.takeIf { it > 0 }?.let { appendLine("Длительность: ${it.toDuration()}") }
                model?.resolution?.takeIf { it.size == 2 }?.let { appendLine("Разрешение: ${it[0]}x${it[1]}") }
                model?.rating?.value?.takeIf { it > 0 }?.let { appendLine("Рейтинг: $it%") }
                model?.views?.takeIf { it > 0 }?.let { appendLine("Просмотров: $it") }
                model?.created?.takeIf { it > 0 }?.let { appendLine("Добавлено: ${it.toDateString()}") }
                if (model?.isVR == true) appendLine("Формат: VR")
            }.trim()
        }
    }

    // ============================== Episodes ==============================

    // Every page holds a single clip.
    override fun episodeListRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun episodeListParse(response: Response): List<SEpisode> = listOf(
        SEpisode.create().apply {
            url = response.request.url.encodedPath
            episode_number = 1f
            name = "Видео"
        },
    )

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val page = client.newCall(GET(baseUrl + episode.url, headers)).awaitSuccess().bodyString()
        val sources = page.initials<WatchInitials>()?.xplayerSettings?.sources
            ?: throw Exception("Не удалось прочитать настройки плеера")

        // The progressive mp4 links are bound to the browser session and answer 403
        // outside of it, so the hls playlist is the only reliable source.
        val master = sources.hls.values.firstNotNullOfOrNull { UrlDeobfuscator.decode(it.url) }
            ?: sources.standard.h264.firstNotNullOfOrNull { source ->
                UrlDeobfuscator.decode(source.url)?.takeIf { it.contains(".m3u8") || it.contains("media=hls") }
            }
            ?: throw Exception("Ссылки на видео не найдены")

        val videos = playlistUtils.extractFromHls(master, referer = "$baseUrl/")
            .ifEmpty { listOf(Video(master, "Авто", master, headers = headers)) }

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

    /** Listing pages hang the number on the path tail: `/newest/2`. */
    private fun listRequest(path: String, page: Int): Request {
        val clean = path.removeSuffix("/")
        val url = if (page > 1) "$baseUrl$clean/$page" else "$baseUrl$clean"

        return GET(url, headers)
    }

    private fun searchUrl(query: String, page: Int, sort: String): String {
        val builder = "$baseUrl/search/${query.trim().replace(' ', '-')}".toHttpUrl().newBuilder()
        if (page > 1) builder.addQueryParameter("page", page.toString())
        if (sort.isNotBlank()) builder.addQueryParameter("q", sort)

        return builder.build().toString()
    }

    private fun listParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select("div.video-thumb")
            .mapNotNull { it.toSAnime() }
            .distinctBy { it.url }

        return AnimesPage(animes, animes.isNotEmpty() && document.hasNextPage())
    }

    private fun Document.hasNextPage(): Boolean = select("[data-page=next], .prev-next-list a[href]").isNotEmpty()

    private fun Element.toSAnime(): SAnime? {
        val link = selectFirst("a[data-role=thumb-link][href]") ?: return null
        val href = link.attr("href").takeIf { it.isNotBlank() } ?: return null
        val path = runCatching { href.toHttpUrl().encodedPath }.getOrDefault(href)
        val name = selectFirst("a.video-thumb-info__name")?.text()?.trim()
            ?: link.attr("aria-label").trim()
        if (name.isBlank()) return null

        return SAnime.create().apply {
            url = path
            title = name
            thumbnail_url = selectFirst("img[data-role=thumb-preview-img], img")?.let { img ->
                img.attr("src").takeIf { it.startsWith("http") } ?: img.attr("data-src")
            }
            genre = selectFirst("[data-role=video-duration]")?.text()?.trim()
        }
    }

    /** Pulls the `window.initials = {...}` blob and deserializes the part we need. */
    private inline fun <reified T> String.initials(): T? = jsonAfter(indexOf(INITIALS_MARKER))

    /** Deserializes the first balanced json object that starts at or after [from]. */
    private inline fun <reified T> String.jsonAfter(from: Int): T? {
        if (from < 0) return null
        val start = indexOf('{', from).takeIf { it >= 0 } ?: return null
        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until length) {
            val char = this[index]

            when {
                escaped -> escaped = false
                char == '\\' && inString -> escaped = true
                char == '"' -> inString = !inString
                inString -> Unit
                char == '{' -> depth++
                char == '}' -> {
                    depth--
                    if (depth == 0) return runCatching { substring(start, index + 1).parseAs<T>() }.getOrNull()
                }
            }
        }

        return null
    }

    private fun String.metaTitle(): String = TITLE_REGEX.find(this)?.groupValues?.get(1)?.trim().orEmpty()

    private fun String.tagNames(): String? = jsonAfter<TagsComponent>(indexOf(TAGS_MARKER))
        ?.tags
        ?.filter { it.isCategory || it.isTag || it.isPornstar }
        ?.map { it.name.trim() }
        ?.filter { it.isNotBlank() }
        ?.distinct()
        ?.take(15)
        ?.joinToString(", ")
        ?.takeIf { it.isNotBlank() }

    private fun Int.toDuration(): String = "%d:%02d".format(this / 60, this % 60)

    private fun Long.toDateString(): String = DATE_FORMAT.format(Date(this * 1000))

    private fun String.parseQuality(): Int = QUALITY_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    companion object {
        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "https://xhamster.com"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = listOf("2160p", "1440p", "1080p", "720p", "480p", "240p", "144p")

        private const val INITIALS_MARKER = "window.initials"
        private const val TAGS_MARKER = "\"videoTagsComponent\""

        private val TITLE_REGEX = Regex("""<meta property="og:title" content="([^"]+)"""")
        private val QUALITY_REGEX = Regex("""(\d+)p""")
        private val DATE_FORMAT = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    }
}
