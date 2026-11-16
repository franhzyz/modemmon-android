# Развёртывание на роутере — как есть на 2026-10-30

Снимок рабочей конфигурации, чтобы можно было восстановить всё с нуля.

## Железо и система

| | |
|---|---|
| Плата | Orange Pi Zero 3, `sunxi/cortexa53`, aarch64 |
| OpenWrt | 25.12.5 (r33051-f5dae5ece4), ядро 6.12.94 |
| Адрес | `192.168.2.1` (LAN `192.168.2.0/24`, br-lan на eth0) |
| Модем | DELL Snapdragon X55 (Foxconn T99W175), `usb 2-1`, vid:pid `05c6:9025` |
| Прошивка модема | `T99W175.F0.6.0.0.6.GC.004` |
| SIM | Tele2 RU, PLMN 25020, APN `internet.tele2.ru` |

Модемом управляет **`medvemodem` v0.15.0**, ModemManager используется как
бэкенд. Порты `ttyUSB0-3` и `cdc-wdm0` принадлежат medvemodem.

## Что установлено коллектором

```
/usr/bin/modem-collector.sh   (755, md5 e703eee14128bc37eb990fb43d74738d)
/www/cgi-bin/modem            (755, md5 71b91335c4598029abd849e53f2318a6)
/etc/modemmon.conf            (644)

/usr/bin/modem-watchdog.sh    (755, md5 f2c276a76f9b779f3cb6ca981148afa7)
/etc/init.d/modem-watchdog    (755, md5 dcc97861ea0e901024b4f597fd5f608a)
/etc/rc.d/S99modem-watchdog   (автозапуск, включён)
```

`/etc/modemmon.conf`:

```sh
MODEM=any
IFACE=wwan0
# AT_PORT=/dev/wwan0at0
```

Эндпоинт: `http://192.168.2.1/cgi-bin/modem` → HTTP 200, JSON ~740 байт.

## Интерфейс mm0

```
network.mm0=interface
network.mm0.proto='modemmanager'
network.mm0.device='/sys/devices/platform/soc/5200000.usb/usb2/2-1'
network.mm0.disable_modem='0'
network.mm0.apn='internet.tele2.ru'
network.mm0.metric='100'
network.mm0.allowedauth='none'
network.mm0.iptype='ipv4v6'
network.mm0.loglevel='ERR'
network.mm0.multipath='off'
```

`/etc/config/medvemodem` — ключевое:

```
medvemodem.core.enabled=1
medvemodem.core.owner_preference='modemmanager'
medvemodem.core.auto_connect=1
medvemodem.core.auto_recovery=1
medvemodem.@device[0].id='usb-serial:3065be06'
```

## Восстановление после сбоя модема

Симптомы: `mmcli -L` пустой, нет `/dev/ttyUSB*` и `/dev/cdc-wdm0`,
в dmesg `usb 2-1: can't set config #1, error -71`.

```sh
medvemodem recover usb-serial:3065be06 power
# через ~25 секунд проверить:
ls /dev/ttyUSB* /dev/cdc-wdm0
mmcli -L
```

USB-rebind и `recover usb-reset` не помогают — профиль запрещает этот уровень.

## Сторож модема

```
/etc/init.d/modem-watchdog   procd, START=99, respawn 3600 5 0
/usr/bin/modem-watchdog.sh   цикл проверок раз в 120 с
/var/log/modem-watchdog.log  журнал (обрезается до 2000 строк)
```

Логика: нет портов или MM не видит модем → `recover power` (ждёт до 120 с);
нет IPv4 на `wwan0` → `ifdown/ifup mm0`; ICMP не идёт 3 раза подряд →
`ifdown/ifup mm0`. Параметры переопределяются переменными окружения
(`DEVICE`, `IFACE`, `IFNAME`, `PING_HOST`, `INTERVAL`).

Управление: `/etc/init.d/modem-watchdog restart|stop|start`,
`tail -f /var/log/modem-watchdog.log`.

`crond` на этой сборке не запускается без `/etc/crontabs/root`,
поэтому сторож сделан службой procd, а не заданием cron.

## Чего не делать

Не открывать `/dev/ttyUSB*` вручную (`picocom`, `microcom`, `cat`) — порт
остаётся занятым, medvemodem теряет над ним контроль, а в его диагностике
появляется блок «Другие модемные помощники». Лечится `kill` процесса.
Штатный путь для AT: `medvemodem at usb-serial:3065be06 ATI`.

## Endpoint `/cgi-bin/modem-telemetry`

Production работает на schema v2 с 30.10.2026:

```text
/usr/bin/modem-telemetry.sh      755  e3952ce609f0a1beb63794baff2b2bfbe850bed8313204320fdb70338baf6a0a
/www/cgi-bin/modem-telemetry     755  fe379f77ee06fc3b23ba67eb59096d25be487f6e92f8040c384d7cd8aadf8406
```

Schema v2 добавляет LTE/NR, CA, соседей, SIM, bearer, traffic и modem/device,
сохраняя все поля schema v1. API SIM slot zero-based; Android показывает
one-based. Полные IMEI/IMSI/ICCID не выдаются, IMSI/ICCID только маскируются.

`AT^TEMP?` через штатный `medvemodem` возвращает настоящие температуры `PA`,
`Skin Sensor` и `TSENS`. Основное `temp_c` берётся из `TSENS`, запрос кэшируется
на 60 секунд. Температура SoC Orange Pi не используется; modeminfo/MQTT остаются
резервными источниками.

Проверка после promotion: HTTP 200, `schema_version=2`, LTE/5G NSA, источник
температуры `at_temp`, основной датчик `TSENS`; privacy scan пройден. Пять
последовательных запросов заняли 2.085–2.359 с, температурный кэш повторно
использован. На Xiaomi физически проверены LTE/NR, CA, соседи, SIM, bearer,
traffic, modem/device и `AT^TEMP?` (`TSENS`, `PA`, `Skin Sensor`); crash/ANR и
ошибок JSON/сети нет.

Исходная production-пара schema v1 сохранена в:

```text
/root/modem-telemetry-backups/20261030-194233/modem-telemetry.sh.schema-v1
/root/modem-telemetry-backups/20261030-194233/modem-telemetry.cgi.schema-v1
/root/modem-telemetry-backups/20261030-194233/production-before.txt
```

Хеши backup: collector `8864ab0f5fb2ec83966aa52dce68f1687f229175cdada12f9a6f5e681178acea`,
CGI `bfa85435c03aa7dc3f460fa0d2d094c5dc1f51a2d58ae9c0f7fa9cf4887cfa19`.
Rollback выполнять только парой: скопировать оба `.schema-v1` в production,
выставить права `755`, затем проверить HTTP и хеши.

### Проверенная production-ревизия schema v2

Контрольные суммы финальных артефактов от 30.10.2026:

```text
e3952ce609f0a1beb63794baff2b2bfbe850bed8313204320fdb70338baf6a0a  openwrt/modem-telemetry.sh
fe379f77ee06fc3b23ba67eb59096d25be487f6e92f8040c384d7cd8aadf8406  openwrt/modem-telemetry.cgi
5c44e4cefa433d639d798d3c628a906bef86694edf3180e59b247af164b02665  modemmon/dist/ModemMon-2.0-debug.apk
9dcd2b070339a09aea6b83341cadd051b7a243924e40ca09a31a006fce90c822  ModemMon-2.0-schema-v2-deployment.zip
```

ZIP содержит те же collector, CGI и APK, внутренний `SHA256SUMS.txt` проверен;
хеши collector/CGI совпадают с текущими production-файлами роутера.

## Переустановка старого `/cgi-bin/modem` с нуля

```sh
scp -r openwrt root@192.168.2.1:/tmp/
ssh root@192.168.2.1 'cd /tmp/openwrt && sh install.sh'
```
