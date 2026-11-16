#!/bin/sh
# modem-watchdog.sh — автовосстановление модема T99W175 (Orange Pi Zero 3, OpenWrt 25.12)
#
# Зачем: модем периодически сваливается в состояние, когда USB-устройство
# остаётся на шине, но интерфейсы не поднимаются:
#
#   qmi_wwan wwan0: NETDEV WATCHDOG: transmit queue 0 timed out
#   usb 2-1: can't set config #1, error -71
#
# В этом состоянии /dev/ttyUSB* и /dev/cdc-wdm0 исчезают, ModemManager модем
# не видит, auto_recovery самого medvemodem не помогает — нужен силовой сброс
# (recover ... power), он переинициализирует питание USB.
#
# Скрипт работает как служба procd (см. /etc/init.d/modem-watchdog),
# проверяет состояние раз в INTERVAL секунд и лечит по возрастанию:
#   1. нет портов / нет модема в MM  -> medvemodem recover power
#   2. модем есть, интернета нет     -> перезапуск интерфейса mm0
#   3. интерфейс есть, ICMP не идёт  -> после 3 провалов перезапуск mm0

DEVICE="${DEVICE:-usb-serial:3065be06}"
IFACE="${IFACE:-wwan0}"
IFNAME="${IFNAME:-mm0}"
PING_HOST="${PING_HOST:-8.8.8.8}"
INTERVAL="${INTERVAL:-120}"
LOG="${LOG:-/var/log/modem-watchdog.log}"

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$*" >> "$LOG"
    # лог не должен расти бесконечно: держим последние ~2000 строк
    if [ -f "$LOG" ]; then
        lines=$(wc -l < "$LOG" 2>/dev/null || echo 0)
        if [ "$lines" -gt 2000 ]; then
            tail -n 1000 "$LOG" > "$LOG.tmp" 2>/dev/null && mv "$LOG.tmp" "$LOG"
        fi
    fi
}

modem_present() {
    mmcli -L 2>/dev/null | grep -q '/Modem/'
}

ports_present() {
    [ -c /dev/cdc-wdm0 ] && [ -c /dev/ttyUSB2 ]
}

net_up() {
    ip -4 addr show "$IFACE" 2>/dev/null | grep -q 'inet '
}

icmp_ok() {
    ping -c 1 -W 4 -I "$IFACE" "$PING_HOST" >/dev/null 2>&1
}

recover_power() {
    log "портов нет (ttyUSB*/cdc-wdm*) — силовой сброс модема"
    medvemodem recover "$DEVICE" power --no-wait >/dev/null 2>&1
    i=0
    while [ "$i" -lt 12 ]; do
        sleep 10
        i=$((i + 1))
        if ports_present && modem_present; then
            log "модем вернулся через $((i * 10)) с"
            return 0
        fi
    done
    log "модем НЕ вернулся после силового сброса (проверь питание/кабель)"
    return 1
}

restart_iface() {
    log "перезапуск интерфейса $IFNAME"
    ifdown "$IFNAME" >/dev/null 2>&1
    sleep 2
    ifup "$IFNAME" >/dev/null 2>&1
    sleep 25
    if net_up && icmp_ok; then
        log "интерфейс поднялся, связь есть"
        return 0
    fi
    log "интерфейс не поднялся"
    return 1
}

log "сторож запущен: device=$DEVICE iface=$IFACE/$IFNAME interval=${INTERVAL}s"

# даём системе догрузиться после старта
sleep 60

was_ok=""
icmp_fails=0
power_fails=0

while :; do
    if ! ports_present || ! modem_present; then
        if recover_power; then
            power_fails=0
            was_ok=""
            sleep 20
            if ! net_up; then
                restart_iface >/dev/null 2>&1
            fi
        else
            power_fails=$((power_fails + 1))
            log "неудачных силовых сбросов подряд: $power_fails"
            if [ "$power_fails" -ge 3 ]; then
                log "три неудачи подряд — модем, похоже, не отвечает физически"
                power_fails=0
            fi
        fi
    elif ! net_up; then
        log "модем в MM есть, но у $IFACE нет IPv4"
        restart_iface
        was_ok=""
    elif ! icmp_ok; then
        icmp_fails=$((icmp_fails + 1))
        log "ICMP через $IFACE не идёт (подряд: $icmp_fails)"
        if [ "$icmp_fails" -ge 3 ]; then
            restart_iface
            icmp_fails=0
        fi
        was_ok=""
    else
        icmp_fails=0
        if [ "$was_ok" != "1" ]; then
            log "связь в норме ($(mmcli -m any 2>/dev/null | awk -F': ' '/operator name/ {print $2; exit}'))"
            was_ok="1"
        fi
    fi

    sleep "$INTERVAL"
done
