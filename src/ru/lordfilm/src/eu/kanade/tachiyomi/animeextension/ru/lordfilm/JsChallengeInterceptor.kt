package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URLEncoder

/**
 * Антибот-защита DLE-зеркал LordFilm.
 *
 * На первый запрос сайт отдаёт 13-килобайтную заглушку со скриптом и cookie
 * `__js_p_=<code>,<max-age>,...`. Скрипт считает `get_jhash(code)` и ставит
 * ещё два cookie — `__jhash_` и `__jua_` (URL-кодированный User-Agent).
 * Только после этого сервер отдаёт настоящую страницу.
 *
 * Без этого расширение получало заглушку: в ней нет ни плеера, ни списка
 * серий — отсюда «unrecognized file format» в Tadami.
 */
class JsChallengeInterceptor(
    private val userAgent: String,
    private val cookieJar: CookieJar,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val code = response.challengeCode() ?: return response

        // Заглушка приходит с кодом 200 — тело надо закрыть перед повтором.
        response.close()

        val cookies = listOf(
            cookie(request.url.host, "__js_p_", "$code,$CHALLENGE_MAX_AGE,0,0,0"),
            cookie(request.url.host, "__jhash_", jhash(code).toString()),
            cookie(request.url.host, "__jua_", URLEncoder.encode(userAgent, "UTF-8")),
        )
        // Кладём в jar, чтобы остальные запросы сессии уже шли с ответом.
        runCatching { cookieJar.saveFromResponse(request.url, cookies) }

        val retry = request.newBuilder()
            .header("Cookie", cookies.joinToString("; ") { "${it.name}=${it.value}" })
            .build()

        return chain.proceed(retry)
    }

    private fun cookie(
        host: String,
        name: String,
        value: String,
    ): Cookie = Cookie.Builder()
        .domain(host)
        .path("/")
        .name(name)
        .value(value)
        .build()

    /** Заглушку узнаём по cookie `__js_p_` вместе со скриптом в теле. */
    private fun Response.challengeCode(): Int? {
        val raw = headers("Set-Cookie").firstOrNull { it.startsWith("__js_p_=") } ?: return null
        if ("get_jhash" !in peekBody(CHALLENGE_PEEK_BYTES).string()) return null

        return raw
            .substringAfter("__js_p_=")
            .substringBefore(';')
            .substringBefore(',')
            .toIntOrNull()
    }

    companion object {
        private const val CHALLENGE_MAX_AGE = 1800
        private const val CHALLENGE_PEEK_BYTES = 40_000L

        /** Точный порт get_jhash() из заглушки сайта. */
        fun jhash(code: Int): Int {
            var x = 123_456_789L
            var k = 0

            for (i in 0 until 1_677_696) {
                x = ((x + code) xor (x + (x % 3) + (x % 17) + code) xor i.toLong()) % 16_776_960
                if (x % 117 == 0L) k = (k + 1) % 1111
            }

            return k
        }
    }
}
