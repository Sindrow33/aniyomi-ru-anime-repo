package eu.kanade.tachiyomi.animeextension.ru.animakima

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimakimaFilters {

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class GenreFilter : AnimeFilter.Select<String>("Жанр", GENRES.map { it.first }.toTypedArray(), 0)
    class CountryFilter : AnimeFilter.Select<String>("Страна", COUNTRIES.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Жанр и страна имеют приоритет над разделом"),
            SectionFilter(),
            GenreFilter(),
            CountryFilter(),
        )

    data class SearchParams(
        val path: String = SECTIONS[0].second,
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        val genre = filters.firstOrNullAs<GenreFilter>()?.state ?: 0
        if (genre > 0) return SearchParams(GENRES[genre].second)

        val country = filters.firstOrNullAs<CountryFilter>()?.state ?: 0
        if (country > 0) return SearchParams(COUNTRIES[country].second)

        val section = filters.firstOrNullAs<SectionFilter>()?.state ?: 0
        return SearchParams(SECTIONS.getOrElse(section) { SECTIONS[0] }.second)
    }

    private val SECTIONS = listOf(
        "Топ (Япония)" to "/top/japan/",
        "Топ (Китай)" to "/top/china/",
        "Последние обновления" to "/last/",
        "ТВ сериалы" to "/serials/",
        "Фильмы" to "/films/",
        "OVA" to "/ova/",
        "Анонсы" to "/announcement/",
    )

    private val COUNTRIES = listOf(
        "Любая" to "",
        "Япония" to "/country/japan/",
        "Китай" to "/country/china/",
        "Корея" to "/country/rk/",
        "США" to "/country/usa/",
    )

    private val GENRES = listOf(
        "Любой" to "",
        "Безумие" to "/genres/madness/",
        "Боевые искусства" to "/genres/martial-arts/",
        "Вампиры" to "/genres/vampires/",
        "Военное" to "/genres/military/",
        "Гарем" to "/genres/harem/",
        "Детектив" to "/genres/detective/",
        "Демоны" to "/genres/demons/",
        "Драма" to "/genres/drama/",
        "Дзёсей" to "/genres/josei/",
        "Игры" to "/genres/games/",
        "Исекай" to "/genres/isekaj/",
        "История" to "/genres/story/",
        "Киберпанк" to "/genres/cyberpunk/",
        "Комедия" to "/genres/comedy/",
        "Космос" to "/genres/space/",
        "Магия" to "/genres/magic/",
        "Махо-сёдзё" to "/genres/maho-shoujo/",
        "Машины" to "/genres/cars/",
        "Меха" to "/genres/mech/",
        "Мистика" to "/genres/mystic/",
        "Музыка" to "/genres/music/",
        "Пародия" to "/genres/parody/",
        "Повседневность" to "/genres/routine/",
        "Полиция" to "/genres/police/",
        "Приключения" to "/genres/adventures/",
        "Психологическое" to "/genres/psychological/",
        "Романтика" to "/genres/romance/",
        "Самураи" to "/genres/samurai-action-muvie/",
        "Сверхъестественное" to "/genres/supernatural/",
        "Сёдзё" to "/genres/shoujo/",
        "Сёнен" to "/genres/shonen/",
        "Спорт" to "/genres/sport/",
        "Супер сила" to "/genres/superpower/",
        "Сэйнэн" to "/genres/seinen/",
        "Триллер" to "/genres/thriller/",
        "Ужасы" to "/genres/horror/",
        "Фантастика" to "/genres/fantastic/",
        "Фэнтези" to "/genres/fantasy/",
        "Школа" to "/genres/school/",
        "Экшен" to "/genres/action/",
        "Этти" to "/genres/etty/",
    )
}
