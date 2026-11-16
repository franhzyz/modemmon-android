# ModemMon — монитор модема в OpenWrt

Android-приложение для наведения антенны и контроля модема, который стоит в
роутере с OpenWrt (тестировалось под Foxconn T99W175, Snapdragon X55, QMI).
Идея и набор экранов — по образцу [Hua4GMon](https://github.com/Sp0Xik/Hua4GMon),
но источник данных не HiLink-API Huawei, а JSON-эндпоинт на самом роутере.


## Требования

Приложение работает только с пакетами из репозитория
[https://github.com/Medvedolog/medvemodem](https://github.com/Medvedolog/medvemodem)
(medvemodem, luci-app-forkop, sing-box-forkop и связанные пакеты).

```
телефон ──HTTP──> OpenWrt (/cgi-bin/modem-telemetry) ──> mmcli + qmicli ──> модем
```

**Текущий статус:** ModemMon 2.0 поддерживает schema v1 и v2. 30.10.2026
проверенная schema v2 продвинута в production на реальном T99W175 и физически
проверена в Android-приложении. Совместимые поля schema v1 сохранены.

Финальная production-ревизия от 30.10.2026:

```text
e3952ce609f0a1beb63794baff2b2bfbe850bed8313204320fdb70338baf6a0a  openwrt/modem-telemetry.sh
fe379f77ee06fc3b23ba67eb59096d25be487f6e92f8040c384d7cd8aadf8406  openwrt/modem-telemetry.cgi
5c44e4cefa433d639d798d3c628a906bef86694edf3180e59b247af164b02665  dist/ModemMon-2.0-debug.apk
9dcd2b070339a09aea6b83341cadd051b7a243924e40ca09a31a006fce90c822  ../ModemMon-2.0-schema-v2-deployment.zip
```

## Экраны

| Экран | Что показывает |
|---|---|
| **Монитор** | Крупная выбранная метрика (SINR/RSRP/RSRQ/RSSI), оценка качества, стрелка тренда, пик сессии и «Δ до пика», график с автомасштабом, советы |
| **Сота** | Регистрация и оператор; LTE serving cell; 5G NSA/SA; primary/secondary CA; соседи; активная SIM; bearer/IP; трафик; модель, прошивка и аппаратная ревизия модема; источник/статус температуры |
| **Трафик** | Скорость ↓/↑, объём за сессию, время соединения, температура, uptime |
| **Настройки** | Адрес роутера и путь к JSON, интервал опроса, демо-режим, звук-парктроник, проверка соединения |

Звук-парктроник: чем ближе значение к пику, тем чаще тон — можно крутить
антенну, не глядя на экран. Экран не гаснет во время работы.

## Установка коллектора на роутер

Существующий `openwrt/install.sh` устанавливает прежний endpoint
`/cgi-bin/modem`; он не продвигает schema v2. Проверка legacy-установки:

```sh
scp -r openwrt root@<роутер>:/tmp/
ssh root@<роутер>
cd /tmp/openwrt && sh install.sh
curl http://127.0.0.1/cgi-bin/modem
```

Schema v2 из `../ModemMon-2.0-schema-v2-deployment.zip` установлена в
`/usr/bin/modem-telemetry.sh` и `/www/cgi-bin/modem-telemetry`. Исходная пара
schema v1 сохранена в `/root/modem-telemetry-backups/20261030-194233`; будущие
обновления выполнять только парой после staged-проверки и резервного копирования.
Подробности и rollback описаны в `openwrt/DEPLOYED.md`.

Настройки legacy-коллектора — `/etc/modemmon.conf`:

```sh
MODEM=any            # индекс модема для mmcli
IFACE=wwan0          # сетевой интерфейс модема
# AT_PORT=/dev/wwan0at0   # необязательно: добирает бэнд/PCI/Cell ID
```

### Откуда берутся данные

1. **ModemManager** (`mmcli -m any`, `--signal-get`) — оператор, PLMN, RAT,
   регистрация, LTE-сигнал, сведения о SIM и bearer.
2. **QMI NAS/UIM** (`qmicli -p -d /dev/cdc-wdm0`) — fallback сигнала, serving
   cell, band/EARFCN/PCI/Cell ID/TAC, LTE CA, соседи, 5G-состояние и SIM slots.
3. **`/sys/class/net/wwan0/statistics`** — приоритетный источник RX/TX totals;
   скорости вычисляются по разнице счётчиков и времени между валидными опросами.
4. **`AT^TEMP?` через `medvemodem`** — настоящие температуры T99W175: `TSENS`
   используется как основная, дополнительно публикуются `PA` и `Skin Sensor`.
   Запрос кэшируется на 60 с; modeminfo/MQTT остаются fallback. Thermal zone
   Orange Pi намеренно не используется.

## Формат JSON

Приложение понимает текущую production schema v2, legacy schema v1 и прежнюю
вложенную схему `/cgi-bin/modem`. В schema v2 добавлены `network_info`, `lte`,
`nr`, `ca`, `neighbors`, `device`, `sim`, `bearer` и `traffic`, при этом все
поля schema v1 сохранены на верхнем уровне. API использует zero-based SIM slot,
а интерфейс приложения показывает номера SIM с единицы.

```json
{
  "ts": 1761688800000,
  "online": true,
  "rat": "5G NSA",
  "operator": "MTS",
  "plmn": "25001",
  "lte": {
    "rsrp": -95.0, "rsrq": -11.0, "sinr": 8.0, "rssi": -60.0,
    "band": "3", "earfcn": 1650, "pci": 217, "cell_id": 128451073,
    "enodeb": 501762, "sector": 3, "tac": "6A1F", "bw_dl": "20", "bw_ul": "20"
  },
  "nr": {
    "rsrp": -88.0, "rsrq": -9.0, "sinr": 14.0,
    "band": "n78", "arfcn": 636666, "pci": 41, "cell_id": 8812345678901
  },
  "traffic": {
    "dl_rate": 118000000, "ul_rate": 18000000,
    "total_dl": 12480000000, "total_ul": 1940000000, "connect_time": 4820
  },
  "device": {
    "model": "DELL Snapdragon X55", "firmware": "T99W175.F0.6.0.0.6.GC.004",
    "temp_c": 36, "temp_source": "at_temp", "temp_sensor": "TSENS",
    "temp_pa_c": 36, "temp_skin_c": 36, "temp_tsens_c": 36
  }
}
```

Скорости — в битах/с, объёмы — в байтах, `connect_time` и `uptime` — в секундах.
Любое поле можно не отдавать: приложение покажет «—». Полные IMEI/IMSI/ICCID
не публикуются; IMSI/ICCID в schema v2 только маскируются. Температура берётся
из подтверждённой команды `AT^TEMP?`; modeminfo/MQTT — резервные источники,
но thermal zone роутера никогда не используется.

## Проверено на живом роутере

Orange Pi Zero 3 (sunxi/cortexa53), OpenWrt 25.12.5, модем DELL Snapdragon X55
(T99W175), Tele2 RU. Что выяснилось по факту:

- **Управление модемом — `medvemodem`** (v0.15.0), а не напрямую ModemManager.
  Он отбирает порты у MM и отдаёт их по профилю; интерфейс называется `mm0`
  (`proto=modemmanager`, APN в `/etc/config/network`).
- Формат `mmcli --signal-get` в MM 1.24: секция стоит **слева** от `|`
  (`LTE | ...`, `5G | ...`), SINR называется `s/n`, а недоступные 5G-метрики
  приходят сентинелом `-32768` — коллектор их отбрасывает.
- **NR-метрики этот модем через MM не отдаёт** (только LTE: RSRP/RSRQ/RSSI/SINR).
  Технологию при этом видно корректно — `access tech: lte, 5gnr` → «5G NSA».
- **Бэнд, PCI, Cell ID** в mmcli отсутствуют и берутся из QMI NAS. Последовательные
  порты напрямую не открываются; штатный `medvemodem at` подтверждён для
  `AT^TEMP?`, хотя отдельные команды идентификации вроде `ATI` могут завершаться
  тайм-аутом.
- **Не открывай `ttyUSB*` вручную** (`picocom`, `microcom`, `cat`). Порт
  остаётся занятым, medvemodem перестаёт им распоряжаться, а в его
  диагностике появляется предупреждение «Другие модемные помощники».
  Если такое случилось — `kill` этого процесса, порт освободится.
  Для AT есть штатный путь: `medvemodem at usb-serial:3065be06 <команда>`.

### Если модем «не подымается»

Симптом в dmesg:

```
qmi_wwan wwan0: NETDEV WATCHDOG: transmit queue 0 timed out
usb 2-1: can't set config #1, error -71
```

Модем сваливается в аварийную композицию `05c6:9025`, порты (`ttyUSB*`,
`cdc-wdm0`) исчезают, ModemManager его не видит. USB rebind не помогает —
нужен силовой сброс:

```sh
medvemodem recover usb-serial:3065be06 power
```

Через ~25 секунд порты возвращаются, `mm0` поднимается сам.

### Сторож (автовосстановление)

Чтобы не ждать этого вручную, на роутере стоит служба `modem-watchdog`:

```
/etc/init.d/modem-watchdog   # procd-обёртка, respawn при падении
/usr/bin/modem-watchdog.sh   # сам цикл проверок
/var/log/modem-watchdog.log  # журнал
```

Раз в 2 минуты проверяет три вещи и лечит по возрастанию:

| Что не так | Что делает |
|---|---|
| нет `/dev/ttyUSB*` или `/dev/cdc-wdm0`, MM не видит модем | `medvemodem recover ... power`, ждёт до 120 с |
| модем есть, у `wwan0` нет IPv4 | `ifdown mm0; ifup mm0` |
| IP есть, но ICMP не идёт (3 раза подряд) | `ifdown mm0; ifup mm0` |

Проверка: `tail -f /var/log/modem-watchdog.log`, перезапуск —
`/etc/init.d/modem-watchdog restart`. Служба включена в автозапуск.

## Сборка APK

Требуется JDK 17 и Android SDK (platform 35, build-tools 34.0.0).

```sh
export JAVA_HOME=/c/android-tools/jdk17
export ANDROID_HOME=/c/android-tools/sdk
gradle assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` уже указывает на локальный SDK.

## Структура

```
app/src/main/java/ru/franh/modemmon/
  data/      ModemSnapshot (модель + разбор JSON), ModemApi (HTTP), DemoSource, Settings
  util/      SignalEval (пороги и проценты), Fmt (байты, частоты, длительности)
  vm/        ModemViewModel (опрос, история, пики, тренд)
  ui/        MonitorScreen, CellScreen, TrafficScreen, SettingsScreen, Components
openwrt/     modem-collector.sh, cgi-bin/modem, install.sh,
             modem-watchdog.sh, modem-watchdog.init, DEPLOYED.md
```

## Пороги качества

| Метрика | Отлично | Хорошо | Средне | Плохо |
|---|---|---|---|---|
| RSRP, дБм | ≥ −80 | ≥ −90 | ≥ −100 | < −100 |
| SINR, дБ | ≥ 20 | ≥ 13 | ≥ 0 | < 0 |
| RSRQ, дБ | ≥ −10 | ≥ −15 | ≥ −20 | < −20 |
| RSSI, дБм | ≥ −65 | ≥ −75 | ≥ −85 | < −85 |
