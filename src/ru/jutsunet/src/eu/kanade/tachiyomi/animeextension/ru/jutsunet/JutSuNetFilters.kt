package eu.kanade.tachiyomi.animeextension.ru.jutsunet

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object JutSuNetFilters {

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
        "Все аниме" to "/anime/",
        "Онгоинги" to "/ongoing/",
        "ТОП 100" to "/top100/",
        "Анонсы" to "/announce/",
        "ТВ сериалы" to "/tv-series/",
        "Фильмы" to "/movie/",
        "Китайское" to "/anime/chinese/",
        "С субтитрами" to "/anime/subtitles/",
    )

    private val YEARS = listOf(
        "Любой" to "",
    ) + (2026 downTo 2000).map { it.toString() to "/anime/$it/" }

    private val GENRES = listOf(
        "Любой" to "",
        "Боевые искусства" to "/martial-arts/",
        "Военное" to "/military/",
        "Гарем" to "/harem/",
        "Детектив" to "/detective/",
        "Драма" to "/drama/",
        "Игры" to "/game/",
        "Исторический" to "/historical/",
        "Комедия" to "/comedy/",
        "Магия" to "/magic/",
        "Мистика" to "/mystery/",
        "Повседневность" to "/slice-of-life/",
        "Приключения" to "/adventure/",
        "Психологическое" to "/psychological/",
        "Романтика" to "/romance/",
        "Сверхъестественное" to "/supernatural/",
        "Спорт" to "/sports/",
        "Супер сила" to "/super-power/",
        "Триллер" to "/suspense/",
        "Ужасы" to "/horror/",
        "Фантастика" to "/sci-fi/",
        "Фэнтези" to "/fantasy/",
        "Школа" to "/school/",
        "Экшен" to "/action/",
    )
}
