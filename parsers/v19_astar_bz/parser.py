import requests
from bs4 import BeautifulSoup
import sys
# from playwright.sync_api import sync_playwright # Закомментировано, так как FETCH_MODE = "static"

# 1. FETCH_MODE = "static" или "playwright"
# Выбираем "static", так как контент страницы доступен в статическом HTML.
FETCH_MODE = "static"

# 2. SELECTORS — словарь {<человеческое имя по-русски>: <CSS-селектор>}
SELECTORS = {
    "Заголовок страницы": "h1.entry-title",
    "Аниме-серии (параграфы)": "div.entry-content p",
    "Футер": "div#footer",
}

# URL страницы для парсинга
URL = "https://v19.astar.bz/raspisanie-vyhoda-seriy-ongoingov.html"

# 3. def get_html() -> str
def get_html() -> str:
    """
    Скачивает и возвращает HTML страницы.
    Выбирает метод загрузки в зависимости от FETCH_MODE.
    """
    if FETCH_MODE == "static":
        headers = {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0.4896.127 Safari/537.36"
        }
        try:
            response = requests.get(URL, headers=headers, timeout=30)
            response.raise_for_status()  # Проверка на HTTP ошибки (4xx, 5xx)
            return response.text
        except requests.exceptions.RequestException as e:
            print(f"Ошибка при загрузке страницы с помощью requests: {e}", file=sys.stderr)
            return ""
    elif FETCH_MODE == "playwright":
        # Этот блок будет выполнен, если FETCH_MODE будет изменен на "playwright"
        # Импорт playwright.sync_api происходит здесь, чтобы избежать ошибок,
        # если playwright не установлен, а FETCH_MODE = "static".
        from playwright.sync_api import sync_playwright
        playwright_instance = None
        browser = None
        try:
            playwright_instance = sync_playwright().start()
            browser = playwright_instance.chromium.launch(headless=True)
            page = browser.new_page()
            page.goto(URL, wait_until="networkidle", timeout=45000)
            return page.content()
        except Exception as e:
            print(f"Ошибка при загрузке страницы с помощью Playwright: {e}", file=sys.stderr)
            return ""
        finally:
            if browser:
                browser.close()
            if playwright_instance:
                playwright_instance.stop()
    return "" # Возвращаем пустую строку, если FETCH_MODE не распознан или произошла ошибка

# 4. def check(html: str) -> dict
def check(html: str) -> dict:
    """
    Принимает HTML-СТРОКУ и возвращает словарь {имя: число совпадений}
    для каждого селектора из SELECTORS с использованием BeautifulSoup.
    """
    if not html:
        # Если HTML пустой, возвращаем 0 совпадений для всех селекторов
        return {name: 0 for name in SELECTORS}

    soup = BeautifulSoup(html, "lxml")
    results = {}
    for name, selector in SELECTORS.items():
        try:
            elements = soup.select(selector)
            results[name] = len(elements)
        except Exception as e:
            # Обработка ошибок, если селектор некорректен или произошла другая ошибка при поиске
            print(f"Ошибка при применении селектора '{selector}' для '{name}': {e}", file=sys.stderr)
            results[name] = 0 # В случае ошибки считаем 0 совпадений
    return results

# 5. if __name__ == "__main__":
if __name__ == "__main__":
    html_content = get_html()
    if html_content: # Проверяем, что HTML был успешно получен
        checked_data = check(html_content)
        print(checked_data)
    else:
        print("Не удалось получить HTML-контент для анализа. Программа завершена.", file=sys.stderr)
