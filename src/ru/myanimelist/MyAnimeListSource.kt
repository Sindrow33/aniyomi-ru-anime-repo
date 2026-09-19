package keiyoushi.myanimelist

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.AnimeHttpSource
import okhttp3.*

class MyAnimeList : AnimeHttpSource() {
    override val name = "MyAnimeList"
    override val baseUrl = "https://myanimelist.net"
    override val lang = "ru"

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/anime/list", headers)
    }

    override fun latestUpdatesParse(response: Response): List<Anime> {
        // parse response to get latest updates
    }

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/anime/popular", headers)
    }

    override fun popularAnimeParse(response: Response): List<Anime> {
        // parse response to get popular anime
    }

    // Additional methods can be implemented here according to the API structure
}
