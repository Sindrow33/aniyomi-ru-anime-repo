package eu.kanade.tachiyomi.animeextension.all.eporner

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object EpornerFilters {

    class CategoryFilter : AnimeFilter.Select<String>("Категория", CATEGORIES.map { it.first }.toTypedArray(), 0)
    class OrderFilter : AnimeFilter.Select<String>("Сортировка", ORDERS.map { it.first }.toTypedArray(), 0)
    class QualityFilter : AnimeFilter.Select<String>("Качество", QUALITIES.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Категория используется, когда строка поиска пуста"),
            CategoryFilter(),
            OrderFilter(),
            QualityFilter(),
        )

    data class SearchParams(
        val category: String = "all",
        val order: String = "latest",
        val quality: String = "",
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<Pair<String, String>>) = values.getOrElse(state) { values[0] }.second

        return SearchParams(
            category = pick(filters.firstOrNullAs<CategoryFilter>()?.state ?: 0, CATEGORIES),
            order = pick(filters.firstOrNullAs<OrderFilter>()?.state ?: 0, ORDERS),
            quality = pick(filters.firstOrNullAs<QualityFilter>()?.state ?: 0, QUALITIES),
        )
    }

    private val ORDERS = listOf(
        "Новые" to "latest",
        "Популярные" to "most-popular",
        "Лучшие по рейтингу" to "top-rated",
        "Лучшие за неделю" to "top-weekly",
        "Лучшие за месяц" to "top-monthly",
        "Самые длинные" to "longest",
    )

    private val QUALITIES = listOf(
        "Любое" to "",
        "4K" to "4k-porn",
        "60 FPS" to "60fps",
        "HD" to "hd-porn",
        "VR" to "vr-porn",
    )

    private val CATEGORIES = listOf(
        "Все" to "all",
        "Amateur" to "amateur",
        "Anal" to "anal",
        "Asian" to "asian",
        "ASMR" to "asmr",
        "BBW" to "bbw",
        "BDSM" to "bdsm",
        "Big Ass" to "big-ass",
        "Big Dick" to "big-dick",
        "Big Tits" to "big-tits",
        "Bisexual" to "bisexual",
        "Blonde" to "blonde",
        "Blowjob" to "blowjob",
        "Bondage" to "bondage",
        "Brunette" to "brunette",
        "Bukkake" to "bukkake",
        "Casting" to "casting",
        "Compilation" to "compilation",
        "Cosplay" to "cosplay",
        "Creampie" to "creampie",
        "Cumshot" to "cumshot",
        "Deepthroat" to "deepthroat",
        "Double Penetration" to "double-penetration",
        "Fetish" to "fetish",
        "Fisting" to "fisting",
        "Gangbang" to "gangbang",
        "Group Sex" to "group-sex",
        "Handjob" to "handjob",
        "Hardcore" to "hardcore",
        "Hentai" to "hentai",
        "Interracial" to "interracial",
        "Japanese" to "japanese",
        "Latina" to "latina",
        "Lesbian" to "lesbian",
        "Massage" to "massage",
        "Mature" to "mature",
        "MILF" to "milf",
        "Orgasm" to "orgasm",
        "POV" to "pov",
        "Public" to "public",
        "Redhead" to "redhead",
        "Rough Sex" to "rough-sex",
        "Russian" to "russian",
        "Squirt" to "squirt",
        "Teen" to "teen",
        "Threesome" to "threesome",
        "Toys" to "toys",
        "Webcam" to "webcam",
    )
}
