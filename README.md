# ModemMon — монитор модема и роутера в OpenWrt

**Русский** | [English](README.en.md)

Android-приложение для наведения антенны, контроля модема и управления
роутером с OpenWrt (тестировалось под Foxconn T99W175, Snapdragon X55, QMI,
OrangePi Zero3).

## Скриншоты

| Монитор сигнала | RSRP (наведение антенны) |
|---|---|
| ![Монитор](docs/screenshots/monitor.png) | ![RSRP](docs/screenshots/rsrp.png) |

## Возможности

- Монитор сигнала: SINR/RSRP/RSRQ/RSSI, графики, пик сигнала
- **Звук-парктроник** (настройка звука): при наведении антенны писк звучит
  чаще, чем ближе к пику сигнала + вибрация (включается в опциях приложения)
- Переключение модемов: основной ↔ резервный канал, в один тап
- Спидтест (Яндекс.Интернетометр) с роутера по выбранному каналу
- Форкоп: статус, дашборд, тест секций, диагностика, редактор секций и ссылок
- SMS · USSD · AT-команды, соты/бэнды
- Сеть: интерфейсы, Wi-Fi, маршруты, DHCP, DNS, Firewall
- VPN (WireGuard/AmneziaWG), Службы, Конфиг UCI, Терминал, Файлы, Wake-on-LAN

## Требования

Приложение использует две независимые части:

- **Движок модема** — `medvemodem`, `luci-app-medvemodem` и связанные пакеты из
  [Medvedolog/medvemodem](https://github.com/Medvedolog/medvemodem). Они дают
  ubus-объект `medvemodem` и управление каналами.
- **Наш пакет телеметрии** — `modem-telemetry`. Он отдаёт
  `/cgi-bin/modem-telemetry` (schema v2) и прикреплён к
  [релизам этого репозитория](https://github.com/franhzyz/modemmon-android/releases).
  В архивах Medvedolog его нет.

## Установка пакетов на роутер

Скрипт [`openwrt/install-modemmon.sh`](openwrt/install-modemmon.sh) ставит
пакеты Medvedolog; он сам определяет архитектуру и версию OpenWrt:

```sh
sh install-modemmon.sh
```

Дополнительно установите наш пакет телеметрии:

```sh
apk add --allow-untrusted ./modem-telemetry-2.6.1-r0.apk
```

`modem-telemetry` собран с `arch=noarch`: один и тот же apk подходит для
aarch64_cortex-a53, aarch64_generic и x86_64, OpenWrt 24.10 и 25.12. Архивы с
указанием архитектуры — всего лишь псевдонимы с тем же apk внутри.

## Установка приложения

Скачай APK из [релизов](https://github.com/franhzyz/modemmon-android/releases)
и установи на телефон. Сборка альфа/тест.

## Языки / Languages

Приложение двуязычное: русский и английский. Переключатель — Опции → «Язык / Language».

## Отзывы и предложения

Приложение развивается — **пиши отзывы и предложения** по доработке:
какие функции хотелось бы видеть, что неудобно, что добавить.
Кладём всё в [Issues](https://github.com/franhzyz/modemmon-android/issues) —
то, что чаще просят, уходит в ближайшие версии.

## Сборка из исходников

```sh
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`
