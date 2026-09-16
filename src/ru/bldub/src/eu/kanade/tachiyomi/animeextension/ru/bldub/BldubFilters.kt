package eu.kanade.tachiyomi.animeextension.ru.bldub

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object BldubFilters {

    class GenreFilter : AnimeFilter.Select<String>("Жанр", GENRES.toTypedArray(), 0)
    class CountryFilter : AnimeFilter.Select<String>("Страна", COUNTRIES.toTypedArray(), 0)
    class TypeFilter : AnimeFilter.Select<String>("Тип", TYPES.toTypedArray(), 0)
    class StatusFilter : AnimeFilter.Select<String>("Статус", STATUSES.toTypedArray(), 0)
    class YearFilter : AnimeFilter.Select<String>("Год", YEARS.toTypedArray(), 0)
    class CoupleFilter : AnimeFilter.Select<String>("Пара", COUPLES.toTypedArray(), 0)
    class SortFilter : AnimeFilter.Select<String>("Сортировка", SORTS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры работают вместе с текстовым поиском"),
            GenreFilter(),
            CountryFilter(),
            TypeFilter(),
            StatusFilter(),
            YearFilter(),
            CoupleFilter(),
            SortFilter(),
        )

    data class SearchParams(
        val genre: String = "",
        val country: String = "",
        val type: String = "",
        val status: String = "",
        val year: String = "",
        val couple: String = "",
        val sort: String = "0",
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<String>) = if (state > 0) values.getOrElse(state) { "" } else ""

        val sortState = filters.firstOrNullAs<SortFilter>()?.state ?: 0

        return SearchParams(
            genre = pick(filters.firstOrNullAs<GenreFilter>()?.state ?: 0, GENRES),
            country = pick(filters.firstOrNullAs<CountryFilter>()?.state ?: 0, COUNTRIES),
            type = pick(filters.firstOrNullAs<TypeFilter>()?.state ?: 0, TYPES),
            status = pick(filters.firstOrNullAs<StatusFilter>()?.state ?: 0, STATUSES),
            year = pick(filters.firstOrNullAs<YearFilter>()?.state ?: 0, YEARS),
            couple = pick(filters.firstOrNullAs<CoupleFilter>()?.state ?: 0, COUPLES),
            sort = SORTS.getOrElse(sortState) { SORTS[0] }.second,
        )
    }

    private val GENRES = listOf(
        "Любой",
        "BL", "GL",
        "Боевик", "Детектив", "Драма", "Дружба", "Исторический", "Историческое",
        "Комедия", "Криминал", "Мелодрама", "Мистика", "Научная фантастика",
        "Омегаверс", "Повседневность", "Психология", "Романтика",
        "Сверхъестественное", "Спорт", "Триллер", "Ужасы", "Фантастика",
        "Фэнтези", "Школа", "Экшн",
    )

    private val COUNTRIES = listOf(
        "Любая",
        "Таиланд", "Южная Корея", "Китай", "Япония", "Тайвань",
        "Филиппины", "Сингапур", "США", "Канада",
    )

    private val TYPES = listOf("Любой", "Сериал", "Фильм")

    private val STATUSES = listOf("Любой", "Выходит", "Завершён", "Скоро")

    private val YEARS = listOf("Любой") + listOf(
        "2027", "2026", "2025", "2024", "2023", "2022", "2021",
        "2019", "2016", "2015", "2014", "2013", "2012", "2010", "2003",
    )

    private val COUPLES = listOf(
        "Любая",
        "Englot", "FayMay", "FortPeat", "FreenBecky", "JanJingJing", "JoongDunk",
        "KhaoFirst", "LMSY", "LenaMiu", "LillyBelle", "LingOrm", "MilkLove",
        "NamFilm", "OffGun", "PoomUp", "ZeeNuNew",
    )

    private val SORTS = listOf(
        "Сначала новые" to "0",
        "По обновлению" to "1",
        "По названию (А-Я)" to "2",
        "По названию (Я-А)" to "3",
        "По рейтингу" to "4",
        "По популярности" to "5",
        "По году" to "7",
        "Случайно" to "8",
    )
}
