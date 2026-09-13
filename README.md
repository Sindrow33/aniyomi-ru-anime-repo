# Русские аниме-расширения для Aniyomi

Репозиторий расширений (только аниме, только русские источники) для Aniyomi / Anikku / Mihon-форков.

## Ссылка для Aniyomi

Настройки → Браузер → Репозитории расширений → добавить:

```
https://raw.githubusercontent.com/Sindrow33/aniyomi-ru-anime-repo/main/index.min.json
```

## Что внутри

| Расширение | Источники | Версия |
|---|---|---|
| Animevost | animevost.org + зеркало v13.vost.pw | 14.10 |
| YummyAnime | ru.yummyani.me | 14.2 |
| Animelib | animelib.org/ru (18+) | 14.16 |

APK взяты из репозитория расширений [yuzono/anime-extensions](https://github.com/yuzono/anime-extensions)
(Apache-2.0) и подписаны его ключом — `signingKeyFingerprint` в `repo.json` соответствует,
поэтому Aniyomi устанавливает их без предупреждений о подписи.

## Структура

```
apk/              собранные .apk
icon/             иконки 512x512, имя = <package>.png
index.json        читаемый индекс
index.min.json    индекс для Aniyomi
repo.json         метаданные репозитория + фингерпринт ключа подписи
```

## Лицензия

Код расширений — Apache-2.0, см. LICENSE.
