package eu.kanade.tachiyomi.animeextension.ru.lordfilmmg

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object LordFilmMGFilters {

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class GenreFilter : AnimeFilter.Select<String>("Жанр фильма", GENRES.map { it.first }.toTypedArray(), 0)
    class YearFilter : AnimeFilter.Select<String>("Год", YEARS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры работают, когда строка поиска пуста"),
            AnimeFilter.Header("Действует только самый нижний заполненный фильтр"),
            SectionFilter(),
            GenreFilter(),
            YearFilter(),
        )

    data class SearchParams(
        val section: String = "",
        val genre: String = "",
        val year: String = "",
    ) {
        /** The site has no combined filter page, so the most specific pick wins. */
        val path: String
            get() = listOf(year, genre, section).firstOrNull { it.isNotBlank() } ?: ""
    }

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<Pair<String, String>>) = values.getOrElse(state) { values[0] }.second

        return SearchParams(
            section = pick(filters.firstOrNullAs<SectionFilter>()?.state ?: 0, SECTIONS),
            genre = pick(filters.firstOrNullAs<GenreFilter>()?.state ?: 0, GENRES),
            year = pick(filters.firstOrNullAs<YearFilter>()?.state ?: 0, YEARS),
        )
    }

    private val SECTIONS = listOf(
        "Всё" to "",
        "Фильмы" to "/filmy",
        "Сериалы" to "/serialy",
        "Мультфильмы" to "/multfilmy",
        "Мультсериалы" to "/multserial",
        "Аниме" to "/anime",
    )

    private val GENRES = listOf(
        "Любой" to "",
        "Боевик" to "/filmy/boevik",
        "Биография" to "/filmy/biografija",
        "Вестерн" to "/filmy/vestern",
        "Военный" to "/filmy/voennyj",
        "Детектив" to "/filmy/detektiv",
        "Драма" to "/filmy/drama",
        "Комедия" to "/filmy/komedija",
        "Короткометражка" to "/filmy/korotkometrazhka",
        "Криминал" to "/filmy/kriminal",
        "Мелодрама" to "/filmy/melodrama",
        "Мюзикл" to "/filmy/mjuzikl",
        "Приключения" to "/filmy/prikljuchenija",
        "Семейный" to "/filmy/semejnyj",
        "Триллер" to "/filmy/triller",
        "Ужасы" to "/filmy/uzhasy",
        "Фантастика" to "/filmy/fantastika",
    )

    private val YEARS = listOf(
        "Любой" to "",
        "Сериалы 2026" to "/serialy/serialy-2026",
        "Сериалы 2025" to "/serialy/serialy-2025",
    )
}
