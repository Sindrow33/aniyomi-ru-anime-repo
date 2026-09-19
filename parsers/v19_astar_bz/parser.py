import requests
from bs4 import BeautifulSoup
import sys

# 1. SELECTORS — словарь {<человеческое имя по-русски>: <CSS-селектор>}
# Минимум 3 ключевых элемента (заголовок, основной контент/цена/главные данные, навигация или футер).
# SELECTORS и def check обязаны быть определены ПРЯМО на верхнем уровне модуля.
SELECTORS = {
    'Заголовок страницы': 'div.full-story-title h1',
    'Основное расписание': 'div.full-story-text',
    'Футер сайта': 'div.footer'
}

# 2. def check(html: str) -> dict — принимает HTML-строку, возвращает {имя: число найденных совпадений}
# (используй soup.select()).
def check(html: str) -> dict:
    """
    Проверяет HTML-строку на наличие элементов, определенных в SELECTORS,
    и возвращает количество найденных совпадений для каждого селектора.
    """
    soup = BeautifulSoup(html, 'html.parser')
    results = {}
    for name, selector in SELECTORS.items():
        found_elements = soup.select(selector)
        results[name] = len(found_elements)
    return results

# 3. if __name__ == "__main__": — скачивает страницу через requests.get
# (реалистичный User-Agent, timeout=30) и печатает check(html).
if __name__ == "__main__":
    URL = "https://v19.astar.bz/raspisanie-vyhoda-seriy-ongoingov.html"
    HEADERS = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    }
    TIMEOUT = 30

    print(f"Попытка загрузить страницу: {URL}")
    try:
        response = requests.get(URL, headers=HEADERS, timeout=TIMEOUT)
        response.raise_for_status()  # Проверка на HTTP ошибки (4xx, 5xx)
        html_content = response.text
        print("Страница успешно загружена.")
        
        # Печать результатов проверки
        checked_data = check(html_content)
        print("\nРезультаты проверки элементов на странице:")
        for name, count in checked_data.items():
            print(f"- {name}: Найдено {count} совпадений")

    except requests.exceptions.Timeout:
        print(f"Ошибка: Превышен таймаут ({TIMEOUT} секунд) при загрузке {URL}", file=sys.stderr)
    except requests.exceptions.HTTPError as e:
        print(f"Ошибка HTTP при загрузке {URL}: {e}", file=sys.stderr)
    except requests.exceptions.RequestException as e:
        print(f"Произошла ошибка при запросе к {URL}: {e}", file=sys.stderr)
    except Exception as e:
        print(f"Произошла непредвиденная ошибка: {e}", file=sys.stderr)
