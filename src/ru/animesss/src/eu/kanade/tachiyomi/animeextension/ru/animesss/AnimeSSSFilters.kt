package eu.kanade.tachiyomi.animeextension.ru.animesss

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimeSSSFilters {

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class GenreFilter : AnimeFilter.Select<String>("Жанр", GENRES.map { it.first }.toTypedArray(), 0)
    class YearFilter : AnimeFilter.Select<String>("Год", YEARS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Жанр и год имеют приоритет над разделом"),
            SectionFilter(),
            GenreFilter(),
            YearFilter(),
        )

    data class SearchParams(
        val path: String = SECTIONS[0].second,
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        val genre = filters.firstOrNullAs<GenreFilter>()?.state ?: 0
        if (genre > 0) return SearchParams(GENRES[genre].second)

        val year = filters.firstOrNullAs<YearFilter>()?.state ?: 0
        if (year > 0) return SearchParams(YEARS[year].second)

        val section = filters.firstOrNullAs<SectionFilter>()?.state ?: 0
        return SearchParams(SECTIONS.getOrElse(section) { SECTIONS[0] }.second)
    }

    private val SECTIONS = listOf(
        "Онгоинги" to "/ongoing/",
        "Многосерийные" to "/aniserials/mnogoseriynye/",
        "Китайские" to "/aniserials/chinese/",
        "Фильмы" to "/film-vv/",
    )

    private val YEARS = listOf(
        "Любой" to "",
    ) + (2026 downTo 2000).map { it.toString() to "/xfsearch/year/$it/" }

    private val GENRES = listOf(
        "Любой" to "",
        "Боевые искусства" to "/aniserials/video/martial_arts/",
        "Вампиры" to "/aniserials/video/vampires/",
        "Война" to "/aniserials/video/war/",
        "Гарем" to "/aniserials/video/garems/",
        "Детектив" to "/aniserials/video/detective/",
        "Дзёсэй" to "/aniserials/video/josei/",
        "Драма" to "/aniserials/video/drama/",
        "Игра" to "/aniserials/video/game/",
        "История" to "/aniserials/video/historical/",
        "Киберпанк" to "/aniserials/video/cyberpunk/",
        "Комедия" to "/aniserials/video/comedy/",
        "Меха" to "/aniserials/video/mecha/",
        "Мистика" to "/aniserials/video/mystery/",
        "Пародия" to "/aniserials/video/parody/",
        "Повседневность" to "/aniserials/video/natural/",
        "Постапокалиптика" to "/aniserials/video/postapocalypse/",
        "Приключения" to "/aniserials/video/adventure/",
        "Психология" to "/aniserials/video/psychological/",
        "Романтика" to "/aniserials/video/romance/",
        "Самураи" to "/aniserials/video/samurai/",
        "Сёдзё" to "/aniserials/video/shoujo/",
        "Сёнэн" to "/aniserials/video/shounen/",
        "Спорт" to "/aniserials/video/sports/",
        "Сэйнэн" to "/aniserials/video/seinen/",
        "Трагедия" to "/aniserials/video/tragedy/",
        "Триллер" to "/aniserials/video/thriller/",
        "Ужасы" to "/aniserials/video/horror/",
        "Фантастика" to "/aniserials/video/fantastic/",
        "Фэнтези" to "/aniserials/video/fantasy/",
        "Школа" to "/aniserials/video/school/",
        "Экшен" to "/aniserials/video/action/",
    )
}
