package eu.kanade.tachiyomi.animeextension.ru.tvigle

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Фильтры соответствуют параметрам каталога закрытого API Tvigle:
 * `/api/category/<id>/product/?release_year=..&country=..&o=..`.
 *
 * Жанры на Tvigle — это подкатегории раздела (Боевики, Драмы, …), поэтому
 * «Раздел» и «Жанр» вместе задают id категории. Страны и годы допускают
 * несколько значений сразу, отсюда CheckBox-группы вместо Select.
 */
object TvigleFilters {

    class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    open class CheckBoxGroup(name: String, values: List<CheckBoxVal>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    class SectionFilter : AnimeFilter.Select<String>("Раздел и жанр", SECTIONS.map { it.first }.toTypedArray(), 0)
    class CountryFilter : CheckBoxGroup("Страны", COUNTRIES.map { CheckBoxVal(it.first) })
    class YearFilter : CheckBoxGroup("Годы", YEARS.map { CheckBoxVal(it.first) })
    class SortFilter : AnimeFilter.Select<String>("Сортировать по", SORTS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры работают, когда строка поиска пуста"),
            SectionFilter(),
            SortFilter(),
            CountryFilter(),
            YearFilter(),
        )

    data class SearchParams(
        val categoryId: Int = CATEGORY_FILMS,
        val countries: List<Int> = emptyList(),
        val years: List<String> = emptyList(),
        val sort: String = "",
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    private fun <T> AnimeFilter.Group<AnimeFilter.CheckBox>.checked(options: List<Pair<String, T>>): List<T> = state
        .filter { it.state }
        .mapNotNull { box -> options.firstOrNull { it.first == box.name }?.second }

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        val sectionIndex = filters.firstOrNullAs<SectionFilter>()?.state ?: 0
        val sortIndex = filters.firstOrNullAs<SortFilter>()?.state ?: 0

        return SearchParams(
            categoryId = SECTIONS.getOrElse(sectionIndex) { SECTIONS[0] }.second,
            countries = filters.firstOrNullAs<CountryFilter>()?.checked(COUNTRIES).orEmpty(),
            years = filters.firstOrNullAs<YearFilter>()?.checked(YEARS).orEmpty(),
            sort = SORTS.getOrElse(sortIndex) { SORTS[0] }.second,
        )
    }

    const val CATEGORY_FILMS = 1
    const val CATEGORY_SERIES = 22

    /** Дерево категорий из /api/category/ (проверено 2026-09-27). */
    private val SECTIONS = listOf(
        "Фильмы" to CATEGORY_FILMS,
        "Фильмы — Артхаус" to 2,
        "Фильмы — Биографии" to 3,
        "Фильмы — Боевики" to 4,
        "Фильмы — Военные и политические" to 5,
        "Фильмы — Детективы" to 6,
        "Фильмы — Документальные" to 7,
        "Фильмы — Драмы" to 8,
        "Фильмы — Исторические" to 9,
        "Фильмы — Комедии" to 10,
        "Фильмы — Короткометражные" to 11,
        "Фильмы — Криминал" to 12,
        "Фильмы — Мелодрамы" to 13,
        "Фильмы — Приключения" to 14,
        "Фильмы — Семейные/Детские" to 15,
        "Фильмы — Спорт" to 16,
        "Фильмы — Триллеры" to 17,
        "Фильмы — Ужасы/Мистика" to 18,
        "Фильмы — Фантастика" to 19,
        "Фильмы — Фэнтези" to 20,
        "Фильмы — Эротика" to 21,
        "Сериалы" to CATEGORY_SERIES,
        "Сериалы — Боевики" to 23,
        "Сериалы — Военные" to 24,
        "Сериалы — Детективы" to 25,
        "Сериалы — Дорамы" to 110,
        "Сериалы — Драмы" to 26,
        "Сериалы — Исторические" to 27,
        "Сериалы — Комедии" to 28,
        "Сериалы — Криминал" to 111,
        "Сериалы — Мелодрамы" to 29,
        "Сериалы — Приключения" to 30,
        "Сериалы — Триллеры" to 31,
        "Сериалы — Ужасы/Мистика" to 125,
        "Сериалы — Фантастика" to 124,
        "Сериалы — Фэнтези" to 123,
        "Мультфильмы" to 32,
        "Мультфильмы — Аниме" to 108,
        "Мультфильмы — Мультсериалы" to 132,
        "Мультфильмы — Полнометражные" to 131,
        "Мультфильмы — Для всей семьи" to 121,
        "Мультфильмы — Развивающие" to 34,
        "Мультфильмы — Для взрослых" to 33,
        "Мультфильмы — Русские" to 36,
        "Мультфильмы — Советские" to 37,
        "Мультфильмы — Зарубежные" to 35,
        "Русское кино" to 38,
        "Русское кино — Российское" to 39,
        "Русское кино — Советское" to 40,
        "Русское кино — Российские сериалы" to 120,
        "START" to 126,
        "START — Сериалы" to 127,
        "START — Фильмы" to 128,
        "START — Зарубежные фильмы" to 129,
        "START — Для детей" to 130,
    )

    /**
     * Параметр `o` принимает только три ключа рейтингов (как и фильтр на
     * сайте) и всегда сортирует по убыванию; любое другое значение API
     * молча игнорирует, отдавая порядок по умолчанию — сначала новинки.
     */
    private val SORTS = listOf(
        "Новинкам" to "",
        "Рейтингу Кинопоиска" to "kinopoisk_rating",
        "Рейтингу IMDB" to "imdb_rating",
        "Рейтингу Tvigle" to "tvigle_rating",
    )

    private val YEARS = listOf(
        "2026" to "2026",
        "2025" to "2025",
        "2024" to "2024",
        "2023" to "2023",
        "2022" to "2022",
        "2021" to "2021",
        "2020" to "2020",
        "2019" to "2019",
        "2018" to "2018",
        "2017" to "2017",
        "2016" to "2016",
        "2010-2015" to "2010-2015",
        "2000-2010" to "2000-2010",
        "1990-2000" to "1990-2000",
        "1980-1990" to "1980-1990",
        "до 1980" to "1980",
    )

    /** id стран из filtersList каталога. */
    private val COUNTRIES = listOf(
        "Россия" to 4,
        "США" to 27,
        "СССР" to 1,
        "Франция" to 16,
        "Великобритания" to 23,
        "Канада" to 37,
        "Германия" to 6,
        "Южная Корея" to 28,
        "Испания" to 32,
        "Италия" to 3,
        "Бельгия" to 36,
        "Китай" to 33,
        "Украина" to 50,
        "Япония" to 11,
        "Австралия" to 30,
        "Индия" to 20,
        "Нидерланды" to 29,
        "Швеция" to 12,
        "Дания" to 31,
        "Ирландия" to 35,
        "Мексика" to 22,
        "Норвегия" to 15,
        "Польша" to 17,
        "Турция" to 44,
        "Гонконг" to 34,
        "Казахстан" to 24,
        "Швейцария" to 9,
        "Аргентина" to 60,
        "Финляндия" to 7,
        "Чехия" to 69,
        "Новая Зеландия" to 42,
        "Бразилия" to 47,
        "Венгрия" to 43,
        "Беларусь" to 41,
        "Тайланд" to 80,
    )
}
