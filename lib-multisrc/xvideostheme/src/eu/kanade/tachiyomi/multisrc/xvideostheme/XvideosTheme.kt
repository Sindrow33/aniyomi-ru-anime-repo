package eu.kanade.tachiyomi.multisrc.xvideostheme

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
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
import keiyoushi.utils.useAsJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Shared source for the sites running the xvideos engine (xvideos, xnxx and their
 * regional front-ends). They serve identical markup: a `div.thumb-block` grid for
 * listings and `setVideoUrl*` / `setVideoHLS` calls on the watch page.
 */
abstract class XvideosTheme(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : AnimeHttpLegacySource(),
    ConfigurableAnimeSource {

    override val supportsLatest = true

    protected val preferences by getPreferencesLazy()

    /** Listing path used for the "popular" tab. */
    protected open val popularPath = "/best"

    /** Listing path used for the "latest" tab. */
    protected open val latestPath = "/new"

    /** Extra sections offered in the filter sheet. */
    protected open val sections: List<Pair<String, String>> = listOf(
        "Лучшее" to "/best",
        "Новое" to "/new",
    )

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set(
            "User-Agent",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36",
        )

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = listRequest(popularPath, page)

    override fun popularAnimeParse(response: Response): AnimesPage = listParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = listRequest(latestPath, page)

    override fun latestUpdatesParse(response: Response): AnimesPage = listParse(response)

    // =============================== Search ===============================

    /** Search uses a different url shape per site. */
    protected abstract fun searchUrl(query: String, page: Int): String

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        if (query.isNotBlank()) return GET(searchUrl(query, page), headers)

        val section = filters.filterIsInstance<SectionFilter>().firstOrNull()?.selected(sections)
            ?: sections.first().second

        return listRequest(section, page)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = listParse(response)

    override fun getFilterList(): AnimeFilterList = AnimeFilterList(
        AnimeFilter.Header("Раздел учитывается, когда строка поиска пуста"),
        SectionFilter(sections),
    )

    protected class SectionFilter(sections: List<Pair<String, String>>) :
        AnimeFilter.Select<String>("Раздел", sections.map { it.first }.toTypedArray(), 0) {
        fun selected(all: List<Pair<String, String>>) = all.getOrElse(state) { all[0] }.second
    }

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET(baseUrl + anime.url, headers)

    override fun getAnimeUrl(anime: SAnime): String = baseUrl + anime.url

    override fun animeDetailsParse(response: Response): SAnime {
        val document = response.useAsJsoup()

        return SAnime.create().apply {
            url = response.request.url.encodedPath
            title = document.pageTitle()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            genre = document.select("a.is-keyword, .video-tags-list a.btn")
                .map { it.text().trim() }
                .filter { it.isNotBlank() && !it.startsWith("+") }
                .distinct()
                .take(15)
                .joinToString(", ")
            author = document.selectFirst(".uploader-tag .name, .video-uploader a .name")?.text()?.trim()
            status = SAnime.COMPLETED
            description = buildString {
                document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        appendLine(it)
                        appendLine()
                    }
                document.selectFirst(".video-hd-mark, .video-metadata .duration")?.text()?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { appendLine("Качество: $it") }
            }.trim()
        }
    }

    // ============================== Episodes ==============================

    // Every page is a single clip.
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

        val videos = buildList {
            // Progressive mp4 first: it needs no playlist round-trip.
            SETTER_REGEXES.forEach { (label, regex) ->
                regex.find(page)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }?.let {
                    add(Video(it, label, it, headers = headers))
                }
            }

            HLS_REGEX.find(page)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }?.let {
                add(Video(it, "HLS (авто)", it, headers = headers))
            }
        }

        if (videos.isEmpty()) throw Exception("Ссылки на видео не найдены")

        return videos.sortedWith(
            compareByDescending<Video> { it.videoTitle.parseQuality() == preferredQuality }
                .thenByDescending { it.videoTitle.parseQuality() },
        )
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

    protected val preferredQuality: Int
        get() = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!
            .filter { it.isDigit() }
            .toIntOrNull()
            ?: 720

    // =============================== Utils ================================

    /** Listing pages number from zero and hang the page on the path. */
    protected open fun listRequest(path: String, page: Int): Request {
        val clean = path.removeSuffix("/")
        val url = if (page > 1) "$baseUrl$clean/${page - 1}" else "$baseUrl$clean"

        return GET(url, headers)
    }

    protected open fun listParse(response: Response): AnimesPage {
        val document = response.useAsJsoup()
        val animes = document.select("div.thumb-block")
            .mapNotNull { it.toSAnime() }
            .distinctBy { it.url }

        return AnimesPage(animes, animes.isNotEmpty() && document.hasNextPage())
    }

    protected open fun Document.hasNextPage(): Boolean =
        selectFirst(".pagination a.next-page, .pagination li a[rel=next]") != null ||
            select(".pagination a").isNotEmpty()

    protected open fun Element.toSAnime(): SAnime? {
        val link = selectFirst(".thumb a[href], .thumb-under a[href]") ?: return null
        val href = link.attr("href").takeIf { it.isNotBlank() } ?: return null
        val path = runCatching { href.toHttpUrl().encodedPath }.getOrDefault(href)

        return SAnime.create().apply {
            url = path
            title = selectFirst(".thumb-under .title a")?.attr("title")?.trim()
                ?: selectFirst(".thumb-under .title a")?.text()?.trim()
                ?: link.attr("title").trim()
            thumbnail_url = selectFirst(".thumb img")?.let { img ->
                img.attr("data-src").takeIf { it.isNotBlank() } ?: img.absUrl("src")
            }
            genre = selectFirst(".thumb-under .metadata")?.text()?.trim()
        }
    }

    protected open fun Document.pageTitle(): String = selectFirst("h2.page-title, h1")?.text()?.trim()
        ?: selectFirst("title")?.text()?.substringBefore(" - ")?.trim()
        ?: ""

    protected fun String.parseQuality(): Int = QUALITY_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    companion object {
        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "720p"
        private val PREF_QUALITY_ENTRIES = listOf("1080p", "720p", "480p", "360p", "240p")

        private val SETTER_REGEXES = listOf(
            "Высокое (mp4)" to Regex("""setVideoUrlHigh\('([^']+)'\)"""),
            "Низкое (mp4)" to Regex("""setVideoUrlLow\('([^']+)'\)"""),
        )
        private val HLS_REGEX = Regex("""setVideoHLS\('([^']+)'\)""")
        private val QUALITY_REGEX = Regex("""(\d+)p""")
    }
}
