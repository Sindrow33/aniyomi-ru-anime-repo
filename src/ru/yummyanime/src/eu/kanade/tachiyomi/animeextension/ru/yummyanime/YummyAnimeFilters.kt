package eu.kanade.tachiyomi.animeextension.ru.yummyanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Фильтры каталога YummyAnime: endpoint `GET /anime` того же API, что
 * использует сайт (`/anime/catalog` отдаёт только первую страницу без
 * параметров, поэтому раньше каталог и не листался).
 *
 * Параметры: `genres` (можно несколько — работает как И), `types` (алиасы
 * tv/movie/ova/…), `from_year`/`to_year`, `season`, `status`, `min_age`,
 * `ep_from`/`ep_to`, `sort` + `sort_forward`.
 */
object YummyAnimeFilters {

    class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    open class CheckBoxGroup(name: String, values: List<CheckBoxVal>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    class GenreFilter : CheckBoxGroup("Жанры", GENRES.map { CheckBoxVal(it.first) })
    class TypeFilter : CheckBoxGroup("Тип", TYPES.map { CheckBoxVal(it.first) })

    class StatusFilter : AnimeFilter.Select<String>("Статус", STATUSES.map { it.first }.toTypedArray(), 0)
    class SeasonFilter : AnimeFilter.Select<String>("Сезон года", SEASONS.map { it.first }.toTypedArray(), 0)
    class AgeFilter : AnimeFilter.Select<String>("Возрастной рейтинг", AGES.map { it.first }.toTypedArray(), 0)
    class SortFilter : AnimeFilter.Select<String>("Сортировать по", SORTS.map { it.first }.toTypedArray(), 0)
    class OrderFilter : AnimeFilter.Select<String>("Порядок", arrayOf("По убыванию", "По возрастанию"), 0)

    class YearFromFilter : AnimeFilter.Text("Год от")
    class YearToFilter : AnimeFilter.Text("Год до")
    class EpisodesFromFilter : AnimeFilter.Text("Эпизодов от")
    class EpisodesToFilter : AnimeFilter.Text("Эпизодов до")

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры работают, когда строка поиска пуста"),
            SortFilter(),
            OrderFilter(),
            GenreFilter(),
            TypeFilter(),
            StatusFilter(),
            SeasonFilter(),
            AgeFilter(),
            YearFromFilter(),
            YearToFilter(),
            EpisodesFromFilter(),
            EpisodesToFilter(),
        )

    data class SearchParams(
        val genres: List<Int> = emptyList(),
        val types: List<String> = emptyList(),
        val status: String = "",
        val season: String = "",
        val minAge: String = "",
        val yearFrom: String = "",
        val yearTo: String = "",
        val episodesFrom: String = "",
        val episodesTo: String = "",
        val sort: String = "top",
        val ascending: Boolean = false,
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    private fun <T> AnimeFilter.Group<AnimeFilter.CheckBox>.checked(options: List<Pair<String, T>>): List<T> = state
        .filter { it.state }
        .mapNotNull { box -> options.firstOrNull { it.first == box.name }?.second }

    private fun AnimeFilterList.number(predicate: (AnimeFilter<*>) -> Boolean): String = filterIsInstance<AnimeFilter.Text>()
        .firstOrNull(predicate)
        ?.state
        ?.trim()
        ?.takeIf { it.toIntOrNull() != null }
        .orEmpty()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<Pair<String, String>>) = values.getOrElse(state) { values[0] }.second

        return SearchParams(
            genres = filters.firstOrNullAs<GenreFilter>()?.checked(GENRES).orEmpty(),
            types = filters.firstOrNullAs<TypeFilter>()?.checked(TYPES).orEmpty(),
            status = pick(filters.firstOrNullAs<StatusFilter>()?.state ?: 0, STATUSES),
            season = pick(filters.firstOrNullAs<SeasonFilter>()?.state ?: 0, SEASONS),
            minAge = pick(filters.firstOrNullAs<AgeFilter>()?.state ?: 0, AGES),
            yearFrom = filters.number { it is YearFromFilter },
            yearTo = filters.number { it is YearToFilter },
            episodesFrom = filters.number { it is EpisodesFromFilter },
            episodesTo = filters.number { it is EpisodesToFilter },
            sort = pick(filters.firstOrNullAs<SortFilter>()?.state ?: 0, SORTS),
            ascending = filters.firstOrNullAs<OrderFilter>()?.state == 1,
        )
    }

    private val SORTS = listOf(
        "Популярности" to "top",
        "Рейтингу" to "rating",
        "Дате добавления" to "id",
        "Названию" to "title",
        "Просмотрам" to "views",
        "Количеству оценок" to "rating_counters",
        "Году выхода" to "year",
    )

    private val TYPES = listOf(
        "Сериал" to "tv",
        "Полнометражный фильм" to "movie",
        "Короткометражка" to "shortfilm",
        "Короткий сериал" to "shorttv",
        "OVA" to "ova",
        "ONA" to "ona",
        "Спешл" to "special",
    )

    private val STATUSES = listOf(
        "Любой" to "",
        "Онгоинг" to "ongoing",
        "Вышел" to "released",
    )

    private val SEASONS = listOf(
        "Любой" to "",
        "Зима" to "1",
        "Весна" to "2",
        "Лето" to "3",
        "Осень" to "4",
    )

    private val AGES = listOf(
        "Любой" to "",
        "G — без ограничений" to "1",
        "PG — для детей" to "2",
        "PG-13 — от 13 лет" to "3",
        "R-17+ — от 17 лет" to "4",
        "R+ — от 18 лет" to "5",
    )

    /** Список из /anime/genres (проверено 2026-09-27). */
    private val GENRES = listOf(
        "Безумие" to 110,
        "Бисёнэн" to 2,
        "Боевые искусства" to 64,
        "Вампиры" to 46,
        "Ведьмы" to 47,
        "Вестерн" to 13,
        "Виртуальная реальность" to 61,
        "Война" to 75,
        "Военная тематика" to 76,
        "Гарем" to 77,
        "Гарем (для девочек)" to 96,
        "Гендерная интрига" to 102,
        "Демоны" to 48,
        "Детектив" to 14,
        "Дзёсэй" to 4,
        "Драконы" to 49,
        "Драма" to 15,
        "Зомби" to 50,
        "Игры" to 104,
        "Инопланетные расы" to 38,
        "Искусственный интеллект" to 29,
        "Искусство" to 78,
        "Исторический" to 80,
        "Исэкай" to 106,
        "Киберпанк" to 81,
        "Киборги" to 39,
        "Китайское 3D" to 107,
        "Комедия" to 16,
        "Космос" to 40,
        "Кулинария" to 82,
        "Любовный треугольник" to 34,
        "Магия" to 51,
        "Мафия/Якудза" to 20,
        "Махо-сёдзё" to 5,
        "Меха" to 25,
        "Мистика" to 31,
        "Мотоциклы" to 108,
        "Музыка" to 79,
        "Не японское" to 101,
        "Нелинейный сюжет" to 85,
        "Ниндзя" to 65,
        "Пародия" to 17,
        "Параллельный мир" to 62,
        "Перестрелки" to 66,
        "Пилотируемые роботы" to 27,
        "Пираты" to 22,
        "Повседневность" to 86,
        "Политика" to 87,
        "Полицейские" to 88,
        "Полулюди" to 97,
        "Постапокалиптика" to 89,
        "Преступный мир" to 18,
        "Приключения" to 32,
        "Призраки" to 52,
        "Психология" to 98,
        "Путешествия во времени" to 41,
        "Романтика" to 33,
        "Русалки" to 53,
        "Русские в аниме" to 90,
        "Самураи" to 68,
        "Сверхъестественное" to 103,
        "Сёдзё" to 6,
        "Сёдзё-ай" to 7,
        "Сёнэн" to 8,
        "Сёнэн-ай" to 9,
        "Силовые костюмы" to 28,
        "Современное фэнтези" to 54,
        "Спорт" to 91,
        "Сражения на мечах" to 69,
        "Стимпанк" to 93,
        "Суперспособности" to 70,
        "Сэйнэн" to 10,
        "Тайный заговор" to 94,
        "Тёмное фэнтези" to 56,
        "Тёмные эльфы" to 57,
        "Террористы" to 23,
        "Трансформеры" to 30,
        "Триллер" to 35,
        "Убийцы" to 24,
        "Ужасы" to 36,
        "Фантастика" to 37,
        "Феи" to 58,
        "Фэнтези" to 42,
        "Хулиганы" to 109,
        "Целый фэнтези мир" to 59,
        "Школьная жизнь" to 95,
        "Эльфы" to 60,
        "Эротика" to 100,
        "Этти" to 11,
        "Экшен" to 63,
    )
}
