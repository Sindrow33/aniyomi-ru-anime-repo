package eu.kanade.tachiyomi.animeextension.ru.anime365

import android.text.InputType
import androidx.preference.PreferenceScreen
import aniyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import keiyoushi.utils.AnimeHttpLegacySource
import keiyoushi.utils.addEditTextPreference
import keiyoushi.utils.addListPreference
import keiyoushi.utils.addSwitchPreference
import keiyoushi.utils.bodyString
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parallelCatchingFlatMap
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Anime365 :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {

    override val name = "Anime365"

    override val lang = "ru"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val domain: String
        get() = preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT)!!

    override val baseUrl: String
        get() = "https://$domain"

    private val apiUrl: String
        get() = "$baseUrl/api"

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    private val dateFormatter by lazy {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Accept", "application/json")

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = seriesRequest(page, Anime365Filters.SearchParams())

    override fun popularAnimeParse(response: Response): AnimesPage = seriesParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request =
        seriesRequest(page, Anime365Filters.SearchParams(onlyAiring = true))

    override fun latestUpdatesParse(response: Response): AnimesPage = seriesParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/series".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("limit", PAGE_SIZE.toString())
                .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
                .addQueryParameter("fields", LIST_FIELDS)
                .build()
            return GET(url, headers)
        }

        return seriesRequest(page, Anime365Filters.getSearchParameters(filters))
    }

    override fun searchAnimeParse(response: Response): AnimesPage = seriesParse(response)

    override fun getFilterList(): AnimeFilterList = Anime365Filters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET("$apiUrl/series/${anime.url}", headers)

    override fun getAnimeUrl(anime: SAnime): String = "$baseUrl/catalog/${anime.url}"

    override fun animeDetailsParse(response: Response): SAnime {
        val series = response.parseAs<ObjectResponse<SeriesDto>>().data
            ?: return SAnime.create()
        return series.toSAnimeDetails()
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = GET("$apiUrl/series/${anime.url}", headers)

    override fun episodeListParse(response: Response): List<SEpisode> {
        val series = response.parseAs<ObjectResponse<SeriesDto>>().data ?: return emptyList()
        val isMovie = series.type == "movie"

        return series.episodes
            .filter { it.isActive != 0 }
            .filterNot { it.episodeType == "preview" }
            .map { episode ->
                SEpisode.create().apply {
                    url = episode.id.toString()
                    episode_number = episode.episodeInt ?: 1f
                    name = buildString {
                        when {
                            isMovie -> append("Фильм")
                            !episode.episodeFull.isNullOrBlank() -> append(episode.episodeFull)
                            else -> append("Серия ${episode.episodeInt?.formatNumber()}")
                        }
                        episode.episodeTitle?.takeIf { it.isNotBlank() }?.let { append(" - $it") }
                    }
                    date_upload = episode.firstUploadedDateTime
                        ?.takeIf { !it.startsWith("2000-01-01") }
                        ?.let { dateFormatter.tryParse(it) }
                        ?: 0L
                }
            }
            .sortedByDescending { it.episode_number }
    }

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val token = accessToken()
            ?: throw Exception("Укажите e-mail и пароль Anime365 в настройках расширения")

        val episodeData = client.newCall(GET("$apiUrl/episodes/${episode.url}", headers))
            .awaitSuccess()
            .parseAs<ObjectResponse<EpisodeDto>>()
            .data
            ?: return emptyList()

        val ignoreSubs = preferences.getBoolean(PREF_IGNORE_SUBS_KEY, PREF_IGNORE_SUBS_DEFAULT)
        val ignoreRaw = preferences.getBoolean(PREF_IGNORE_RAW_KEY, PREF_IGNORE_RAW_DEFAULT)

        val translations = episodeData.translations
            .filter { it.isActive != 0 }
            .filterNot { ignoreSubs && it.typeKind == "sub" }
            .filterNot { ignoreRaw && it.typeKind == "raw" }
            .sortedByDescending { it.priority ?: 0L }
            .take(MAX_TRANSLATIONS)

        if (translations.isEmpty()) return emptyList()

        val videos = translations.parallelCatchingFlatMap { translation ->
            translationVideos(translation, token)
        }

        if (videos.isEmpty()) {
            throw Exception("Ссылки на видео недоступны: нужен аккаунт Anime365 с активной подпиской")
        }

        return videos.sortedWith(
            compareByDescending<Video> { it.videoTitle.parseQuality() == preferredQuality }
                .thenByDescending { it.videoTitle.parseQuality() },
        )
    }

    private suspend fun translationVideos(translation: TranslationDto, token: String): List<Video> {
        val url = "$apiUrl/translations/embed/${translation.id}".toHttpUrl().newBuilder()
            .addQueryParameter("access_token", token)
            .build()

        val body = client.newCall(GET(url, headers)).awaitSuccess().bodyString()

        // The API answers 200 with an error envelope for missing subscription.
        body.runCatching { parseAs<ErrorEnvelope>() }.getOrNull()?.error?.let { return emptyList() }

        val embed = body.runCatching { parseAs<ObjectResponse<EmbedDto>>().data }.getOrNull()
            ?: return emptyList()

        val subtitles = listOfNotNull(
            embed.subtitlesVttUrl?.takeIf { it.isNotBlank() }?.let { Track(it.toAbsoluteUrl(), "Субтитры (VTT)") },
            embed.subtitlesUrl?.takeIf { it.isNotBlank() }?.let { Track(it.toAbsoluteUrl(), "Субтитры (ASS)") },
        )

        val label = "${translation.author} (${translation.kindLabel})"

        val streamVideos = embed.stream.flatMap { stream ->
            val streamUrl = stream.urls.firstOrNull()?.takeIf { it.isNotBlank() } ?: return@flatMap emptyList()
            val quality = "$label - ${stream.height ?: 0}p"

            if (streamUrl.contains(".m3u8")) {
                playlistUtils.extractFromHls(
                    playlistUrl = streamUrl.toAbsoluteUrl(),
                    referer = "$baseUrl/",
                    videoNameGen = { "$label - $it" },
                    subtitleList = subtitles,
                ).ifEmpty {
                    listOf(Video(streamUrl.toAbsoluteUrl(), quality, streamUrl.toAbsoluteUrl(), subtitleTracks = subtitles))
                }
            } else {
                listOf(Video(streamUrl.toAbsoluteUrl(), quality, streamUrl.toAbsoluteUrl(), subtitleTracks = subtitles))
            }
        }

        if (streamVideos.isNotEmpty()) return streamVideos

        return embed.download.mapNotNull { download ->
            val downloadUrl = download.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Video(
                downloadUrl.toAbsoluteUrl(),
                "$label - ${download.height ?: 0}p",
                downloadUrl.toAbsoluteUrl(),
                subtitleTracks = subtitles,
            )
        }
    }

    /**
     * Exchanges the stored credentials for a long-lived token once and caches it.
     * The token only expires when the account password changes.
     */
    private suspend fun accessToken(): String? {
        preferences.getString(PREF_TOKEN_KEY, "")!!.takeIf { it.isNotBlank() }?.let { return it }

        val email = preferences.getString(PREF_EMAIL_KEY, "")!!.trim()
        val password = preferences.getString(PREF_PASSWORD_KEY, "")!!
        if (email.isBlank() || password.isBlank()) return null

        val url = "$apiUrl/login".toHttpUrl().newBuilder()
            .addQueryParameter("app", API_APP)
            .addQueryParameter("email", email)
            .addQueryParameter("password", password)
            .build()

        val body = client.newCall(GET(url, headers)).awaitSuccess().bodyString()

        body.runCatching { parseAs<ErrorEnvelope>() }.getOrNull()?.error?.let {
            throw Exception("Anime365: ${it.message ?: "не удалось войти"}")
        }

        val token = body.runCatching { parseAs<AccessTokenDto>().access_token }.getOrNull()
            ?: body.runCatching { parseAs<ObjectResponse<AccessTokenDto>>().data?.access_token }.getOrNull()
            ?: return null

        preferences.edit().putString(PREF_TOKEN_KEY, token).apply()
        return token
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addListPreference(
            key = PREF_DOMAIN_KEY,
            default = PREF_DOMAIN_DEFAULT,
            title = "Домен сайта",
            summary = "%s\nСменить, если текущее зеркало недоступно. Требуется перезапуск.",
            entries = PREF_DOMAIN_ENTRIES,
            entryValues = PREF_DOMAIN_ENTRIES,
            restartRequired = true,
        )

        screen.addEditTextPreference(
            key = PREF_EMAIL_KEY,
            default = "",
            title = "E-mail аккаунта",
            summary = "Нужен для доступа к видео: каталог и поиск работают и без него",
            getSummary = { it.ifBlank { "Не указан — видео будет недоступно" } },
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            onComplete = { clearToken() },
        )

        screen.addEditTextPreference(
            key = PREF_PASSWORD_KEY,
            default = "",
            title = "Пароль аккаунта",
            summary = "Хранится только на этом устройстве и обменивается на токен доступа",
            getSummary = { if (it.isBlank()) "Не указан" else "Сохранён" },
            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD,
            onComplete = { clearToken() },
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
            title = "Скрывать переводы субтитрами",
            summary = "Показывать только озвучки",
        )

        screen.addSwitchPreference(
            key = PREF_IGNORE_RAW_KEY,
            default = PREF_IGNORE_RAW_DEFAULT,
            title = "Скрывать оригинал без перевода",
            summary = "Убирает RAW-дорожки из списка",
        )
    }

    private fun clearToken() {
        preferences.edit().remove(PREF_TOKEN_KEY).apply()
    }

    private val preferredQuality: Int
        get() = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!.filter { it.isDigit() }.toIntOrNull() ?: 1080

    // =============================== Utils ================================

    private fun seriesRequest(page: Int, params: Anime365Filters.SearchParams): Request {
        val chips = buildList {
            if (params.genres.isNotEmpty()) {
                add("genre@=${params.genres.joinToString(",")}")
                add("genre_op=${if (params.strictGenres) "and" else "or"}")
            }
            if (params.types.isNotEmpty()) add("type@=${params.types.joinToString(",")}")
            if (params.year.isNotEmpty()) add("yearseason@=${params.year}")
        }

        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("offset", ((page - 1) * PAGE_SIZE).toString())
            .addQueryParameter("fields", LIST_FIELDS)
            .apply {
                if (chips.isNotEmpty()) addQueryParameter("chips", chips.joinToString(";"))
                if (params.onlyAiring) addQueryParameter("isAiring", "1")
            }
            .build()

        return GET(url, headers)
    }

    private fun seriesParse(response: Response): AnimesPage {
        val series = response.parseAs<ListResponse<SeriesDto>>().data
        val animes = series
            .filter { it.isHentai != 1 }
            .map { it.toSAnime() }

        return AnimesPage(animes, series.size >= PAGE_SIZE)
    }

    private fun String.toAbsoluteUrl(): String = when {
        startsWith("//") -> "https:$this"
        startsWith("http") -> this
        startsWith("/") -> baseUrl + this
        else -> "https://$this"
    }

    private fun Float.formatNumber(): String = if (this % 1f == 0f) toInt().toString() else toString()

    private fun String.parseQuality(): Int = QUALITY_REGEX.find(this)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    companion object {
        private const val PAGE_SIZE = 30
        private const val MAX_TRANSLATIONS = 30
        private const val API_APP = "universal"

        private const val LIST_FIELDS = "id,titles,title,posterUrl,posterUrlSmall,year,type,typeTitle,isHentai"

        private const val PREF_DOMAIN_KEY = "pref_domain"
        private const val PREF_DOMAIN_DEFAULT = "smotret-anime.online"
        private val PREF_DOMAIN_ENTRIES = listOf(
            "smotret-anime.online",
            "smotretanime.ru",
            "smotret-anime.ru",
            "smotret-anime.app",
            "anime365.ru",
        )

        private const val PREF_EMAIL_KEY = "pref_email"
        private const val PREF_PASSWORD_KEY = "pref_password"
        private const val PREF_TOKEN_KEY = "pref_access_token"

        private const val PREF_QUALITY_KEY = "pref_quality"
        private const val PREF_QUALITY_DEFAULT = "1080p"
        private val PREF_QUALITY_ENTRIES = listOf("1080p", "720p", "480p", "360p")

        private const val PREF_IGNORE_SUBS_KEY = "pref_ignore_subs"
        private const val PREF_IGNORE_SUBS_DEFAULT = false

        private const val PREF_IGNORE_RAW_KEY = "pref_ignore_raw"
        private const val PREF_IGNORE_RAW_DEFAULT = true

        private val QUALITY_REGEX = Regex("""(\d+)p""")
    }
}
