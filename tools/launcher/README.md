# PriceTracker Launcher (`PriceTracker.exe`)

Небольшой Windows-лаунчер, который одним двойным кликом поднимает весь проект и открывает сайт.

## Что делает

1. Проверяет, что рядом лежит `docker-compose.yml`.
2. Проверяет доступность Docker; если Docker Desktop выключен — запускает его.
3. Выполняет `docker compose up -d` в корне проекта.
4. Опрашивает `http://localhost/actuator/health` до появления `"UP"` (до 180 секунд).
5. Открывает **http://localhost** в браузере по умолчанию.

## Использование

Просто дважды кликни `PriceTracker.exe` в корне проекта.

Без автоматического открытия браузера:

```powershell
.\PriceTracker.exe --no-open
```

## Важно

- Файл должен оставаться в корне проекта рядом с `docker-compose.yml`.
- Docker Desktop должен быть установлен (лаунчер сам попробует его запустить).
- Секреты берутся из локального `.env`; он не попадает в GitHub.

## Пересборка из исходника

Если изменил `PriceTrackerLauncher.cs`:

```powershell
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$src = "tools\launcher\PriceTrackerLauncher.cs"
$out = "PriceTracker.exe"

& $csc /nologo /target:exe /out:$out `
  /reference:System.dll /reference:System.Core.dll /reference:System.Net.Http.dll $src
```
