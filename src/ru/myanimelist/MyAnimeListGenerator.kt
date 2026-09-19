package keiyoushi.myanimelist

import eu.kanade.tachiyomi.source.SourceFactory

class MyAnimeListGenerator : SourceFactory {
    override fun createSources(): List<Source> {
        return listOf(MyAnimeList())
    }
}
