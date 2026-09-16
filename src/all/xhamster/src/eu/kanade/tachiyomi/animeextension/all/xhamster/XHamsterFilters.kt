package eu.kanade.tachiyomi.animeextension.all.xhamster

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object XHamsterFilters {

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class CategoryFilter : AnimeFilter.Select<String>("Категория", CATEGORIES.map { it.first }.toTypedArray(), 0)
    class SortFilter : AnimeFilter.Select<String>("Сортировка поиска", SORTS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Раздел и категория работают, когда строка поиска пуста"),
            SectionFilter(),
            CategoryFilter(),
            AnimeFilter.Separator(),
            SortFilter(),
        )

    data class SearchParams(
        val section: String = "/newest",
        val category: String = "",
        val sort: String = "",
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<Pair<String, String>>) = values.getOrElse(state) { values[0] }.second

        return SearchParams(
            section = pick(filters.firstOrNullAs<SectionFilter>()?.state ?: 0, SECTIONS),
            category = pick(filters.firstOrNullAs<CategoryFilter>()?.state ?: 0, CATEGORIES),
            sort = pick(filters.firstOrNullAs<SortFilter>()?.state ?: 0, SORTS),
        )
    }

    val SECTIONS = listOf(
        "Новые" to "/newest",
        "Лучшие за неделю" to "/best/weekly",
        "Лучшие за месяц" to "/best/monthly",
        "Лучшие за год" to "/best/yearly",
        "Лучшие за всё время" to "/best",
        "Популярные" to "/popular",
    )

    private val SORTS = listOf(
        "По релевантности" to "",
        "Новые" to "newest",
        "Лучшие" to "best",
    )

    private val CATEGORIES = listOf(
        "Все" to "",
        "18 Year Old" to "18-year-old",
        "3D" to "3d",
        "Amateur" to "amateur",
        "Anal" to "anal",
        "Arab" to "arab",
        "Asian" to "asian",
        "ASMR" to "asmr",
        "Ass Licking" to "ass-licking",
        "Babe" to "babe",
        "BBC" to "bbc",
        "BBW" to "bbw",
        "BDSM" to "bdsm",
        "Beauty" to "beauty",
        "Big Ass" to "big-ass",
        "Big Cock" to "big-cock",
        "Big Tits" to "big-tits",
        "Bisexual" to "bisexual",
        "Blonde" to "blonde",
        "Blowjob" to "blowjob",
        "Bondage" to "bondage",
        "Brunette" to "brunette",
        "Bukkake" to "bukkake",
        "Casting" to "casting",
        "Cheating" to "cheating",
        "Compilation" to "compilation",
        "Cosplay" to "cosplay",
        "Creampie" to "creampie",
        "Cuckold" to "cuckold",
        "Cumshot" to "cumshot",
        "Deepthroat" to "deepthroat",
        "Double Penetration" to "double-penetration",
        "Ebony" to "ebony",
        "Facial" to "facial",
        "Femdom" to "femdom",
        "Fetish" to "fetish",
        "Fisting" to "fisting",
        "Footjob" to "footjob",
        "Gangbang" to "gangbang",
        "Gay" to "gay",
        "German" to "german",
        "Granny" to "granny",
        "Group Sex" to "group-sex",
        "Handjob" to "handjob",
        "Hardcore" to "hardcore",
        "Hentai" to "hentai",
        "Indian" to "indian",
        "Interracial" to "interracial",
        "Japanese" to "japanese",
        "Latina" to "latina",
        "Lesbian" to "lesbian",
        "Massage" to "massage",
        "Masturbation" to "masturbation",
        "Mature" to "mature",
        "MILF" to "milf",
        "Nipples" to "nipples",
        "Orgasm" to "orgasm",
        "Orgy" to "orgy",
        "Pantyhose" to "pantyhose",
        "POV" to "pov",
        "Public" to "public",
        "Redhead" to "redhead",
        "Rough Sex" to "rough-sex",
        "Russian" to "russian",
        "Shemale" to "shemale",
        "Squirt" to "squirt",
        "Stockings" to "stockings",
        "Striptease" to "striptease",
        "Swinger" to "swingers",
        "Teen" to "teen",
        "Threesome" to "threesome",
        "Toys" to "toys",
        "Uniform" to "uniform",
        "Vintage" to "vintage",
        "Voyeur" to "voyeur",
        "Webcam" to "webcam",
    )
}
