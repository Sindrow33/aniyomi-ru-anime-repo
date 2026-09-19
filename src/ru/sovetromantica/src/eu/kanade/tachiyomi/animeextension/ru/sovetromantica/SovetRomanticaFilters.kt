package eu.kanade.tachiyomi.animeextension.ru.sovetromantica

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object SovetRomanticaFilters {
    class CategoryFilter : AnimeFilter.Select<String>("Категория", CATEGORIES.map { it.first }.toTypedArray(), 0)
    class OrderFilter : AnimeFilter.Select<String>("Сортировка", ORDERS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры"),
            CategoryFilter(),
            OrderFilter(),
        )

    private val ORDERS = listOf(
        "Новые" to "latest",
        "Популярные" to "popular",
    )

    private val CATEGORIES = listOf(
        "Все" to "all",
        "Драма" to "drama",
        "Комедия" to "comedy",
        "Боевые искуства" to "martial-arts",
    )
}
