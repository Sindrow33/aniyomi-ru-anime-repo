package eu.kanade.tachiyomi.animeextension.all.xnxx

import eu.kanade.tachiyomi.multisrc.xvideostheme.XvideosTheme

class Xnxx :
    XvideosTheme(
        name = "XNXX",
        baseUrl = "https://www.xnxx.com",
        lang = "all",
    ) {
    override val popularPath = "/best"

    override val latestPath = "/hits"

    override val sections = listOf(
        "Популярное" to "/best",
        "Хиты" to "/hits",
        "Теги" to "/tags",
    )

    // Search pages are numbered from zero on the path tail.
    override fun searchUrl(query: String, page: Int): String = "$baseUrl/search/${query.trim().replace(' ', '+')}/${page - 1}"
}
