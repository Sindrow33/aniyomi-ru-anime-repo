package eu.kanade.tachiyomi.animeextension.ru.doramyclub

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object DoramyClubFilters {

    class GenreFilter : AnimeFilter.Select<String>("Жанр", GENRES.map { it.first }.toTypedArray(), 0)
    class CountryFilter : AnimeFilter.Select<String>("Страна", COUNTRIES.map { it.first }.toTypedArray(), 0)
    class TypeFilter : AnimeFilter.Select<String>("Тип", TYPES.map { it.first }.toTypedArray(), 0)
    class StatusFilter : AnimeFilter.Select<String>("Статус", STATUSES.map { it.first }.toTypedArray(), 0)
    class YearFilter : AnimeFilter.Select<String>("Год", YEARS.toTypedArray(), 0)
    class SortFilter : AnimeFilter.Select<String>("Сортировка", SORTS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры игнорируются при текстовом поиске"),
            GenreFilter(),
            CountryFilter(),
            TypeFilter(),
            StatusFilter(),
            YearFilter(),
            SortFilter(),
        )

    data class SearchParams(
        val genre: String = "",
        val country: String = "",
        val type: String = "",
        val status: String = "",
        val year: String = "",
        val sort: String = "date",
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<Pair<String, String>>) = values.getOrElse(state) { values[0] }.second

        val year = filters.firstOrNullAs<YearFilter>()?.state ?: 0

        return SearchParams(
            genre = pick(filters.firstOrNullAs<GenreFilter>()?.state ?: 0, GENRES),
            country = pick(filters.firstOrNullAs<CountryFilter>()?.state ?: 0, COUNTRIES),
            type = pick(filters.firstOrNullAs<TypeFilter>()?.state ?: 0, TYPES),
            status = pick(filters.firstOrNullAs<StatusFilter>()?.state ?: 0, STATUSES),
            year = YEARS.getOrElse(year) { YEARS[0] }.takeIf { year > 0 }.orEmpty(),
            sort = pick(filters.firstOrNullAs<SortFilter>()?.state ?: 0, SORTS),
        )
    }

    private val GENRES = listOf(
        "Любой" to "",
        "Романтика" to "romance",
        "Драма" to "drama",
        "Комедия" to "comedy",
        "Мелодрама" to "melodrama",
        "Боевик" to "action",
        "Триллер" to "thriller",
        "Мистика" to "mystery",
        "Криминал" to "crime",
        "Ужасы" to "horror",
        "Фэнтези" to "fantasy",
        "Фантастика" to "sci-fi",
        "Сверхъестественное" to "supernatural",
        "Исторический" to "historical",
        "Приключения" to "adventure",
        "Повседневность" to "slice-of-life",
        "Молодость" to "youth",
        "Психология" to "psychological",
        "Медицина" to "medical",
        "Музыка" to "musical",
        "Спорт" to "sport",
        "Детектив" to "detektiv",
        "Школа" to "shkola",
    )

    private val COUNTRIES = listOf(
        "Любая" to "",
        "Корея" to "korea",
        "Китай" to "china",
        "Япония" to "japan",
        "Таиланд" to "thailand",
        "Тайвань" to "taiwan",
        "Филиппины" to "philippines",
    )

    private val TYPES = listOf(
        "Любой" to "",
        "Сериал" to "serials",
        "Фильм" to "films",
    )

    private val STATUSES = listOf(
        "Любой" to "",
        "Завершён" to "released",
        "Анонс" to "announce",
        "Онгоинг" to "ongoing",
    )

    private val SORTS = listOf(
        "Сначала новые" to "date",
        "Сначала старые" to "date_asc",
        "По рейтингу" to "rating",
        "По популярности" to "reads",
        "По названию" to "title",
    )

    private val YEARS = listOf("Любой") + (2026 downTo 1990).map { it.toString() }
}
