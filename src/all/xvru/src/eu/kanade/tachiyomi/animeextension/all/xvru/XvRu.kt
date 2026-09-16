package eu.kanade.tachiyomi.animeextension.all.xvru

import eu.kanade.tachiyomi.multisrc.xvideostheme.XvideosTheme

class XvRu :
    XvideosTheme(
        name = "XV-RU",
        baseUrl = "https://www.xv-ru.com",
        lang = "all",
    ) {
    override val popularPath = "/best"

    override val latestPath = "/new"

    override val sections = listOf(
        "Лучшее" to "/best",
        "Новое" to "/new",
    )

    // This front-end keeps search in the query string instead of the path.
    override fun searchUrl(query: String, page: Int): String = "$baseUrl/?k=${query.trim().replace(' ', '+')}&p=${page - 1}"
}
