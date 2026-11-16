#!/bin/sh
# modem-collector.sh — собирает состояние модема в OpenWrt и печатает JSON.
#
# Источники данных (по убыванию приоритета):
#   1. ModemManager (mmcli -J / mmcli --signal-get) — основной путь для T99W175 (MBIM/QMI);
#   2. AT-порт (если задан AT_PORT) — добирает бэнд, PCI, Cell ID, которых нет в mmcli;
#   3. /sys/class/net/<iface>/statistics — скорость и объём трафика.
#
# Настройки — через переменные окружения или /etc/modemmon.conf:
#   MODEM=any        индекс модема для mmcli
#   IFACE=wwan0      сетевой интерфейс модема
#   AT_PORT=         напр. /dev/wwan0at0 (необязательно)
#
# Печатает один JSON-объект в stdout. Ничего не пишет на диск, кроме
# /tmp/modemmon.state (для расчёта скорости).

CONF=/etc/modemmon.conf
[ -f "$CONF" ] && . "$CONF"

MODEM=${MODEM:-any}
IFACE=${IFACE:-wwan0}
MM_IFACE=${MM_IFACE:-mm0}
AT_PORT=${AT_PORT:-}

STATE=/tmp/modemmon.state

json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/ /g'
}

# --- AT-запрос (необязательный) -------------------------------------------
# Возвращает строку ответа модема без эха и OK.
at_cmd() {
    [ -n "$AT_PORT" ] && [ -c "$AT_PORT" ] || return 1
    if command -v microcom >/dev/null 2>&1; then
        printf '%s\r' "$1" | microcom -t 800 "$AT_PORT" 2>/dev/null | tr -d '\r'
    elif command -v picocom >/dev/null 2>&1; then
        printf '%s\r' "$1" | timeout 2 picocom -q -b 115200 -t 800 "$AT_PORT" 2>/dev/null | tr -d '\r'
    else
        return 1
    fi
}

# --- 1. mmcli --------------------------------------------------------------
MMCLI=$(command -v mmcli 2>/dev/null)
MM_INFO=""
MM_SIG=""

if [ -n "$MMCLI" ]; then
    MM_INFO=$($MMCLI -m "$MODEM" 2>/dev/null)
    MM_SIG=$($MMCLI -m "$MODEM" --signal-get 2>/dev/null)
fi

ONLINE=false
[ -n "$MM_INFO" ] && ONLINE=true

# Извлекаем «Ключ: значение» из текстового вывода mmcli.
# Формат строки: "  Hardware |  model: T99W175" — слева категория,
# справа после единственной «|» идёт пара ключ/значение.
mm_field() {
    printf '%s\n' "$MM_INFO" | awk -v key="$1" '
        {
            line = $0
            sub(/^[^|]*\|/, "", line)
            gsub(/^[ \t]+|[ \t]+$/, "", line)
            idx = index(line, ":")
            if (idx == 0) next
            k = substr(line, 1, idx - 1)
            v = substr(line, idx + 1)
            gsub(/^[ \t]+|[ \t]+$/, "", k)
            gsub(/^[ \t]+|[ \t]+$/, "", v)
            if (k == key && v != "") { print v; exit }
        }'
}

# То же, но с привязкой к секции: ключ «state» есть и у модема, и у SIM.
mm_field_section() {
    printf '%s\n' "$MM_INFO" | awk -v sect="$1" -v key="$2" '
        {
            line = $0
            idx = index(line, "|")
            if (idx == 0) next
            left = substr(line, 1, idx - 1)
            right = substr(line, idx + 1)
            gsub(/^[ \t]+|[ \t]+$/, "", left)
            gsub(/^[ \t]+|[ \t]+$/, "", right)
            if (left != "") cur = left
            if (cur != sect) next
            k = key ":"
            if (index(right, k) == 1) {
                v = substr(right, length(k) + 1)
                gsub(/^[ \t]+|[ \t]+$/, "", v)
                print v
                exit
            }
        }'
}

OPERATOR=$(mm_field "operator name")
PLMN=$(mm_field "operator id")
MODEL=$(mm_field "model")
MANUF=$(mm_field "manufacturer")
FW=$(mm_field "firmware revision")
TEMP=$(mm_field "temperature")
SIM_STATE=$(mm_field_section "SIM" "state")
MODEM_STATE=$(mm_field "state")

# Не дублируем производителя, если он уже входит в модель («DELL DELL ...»).
case "$MODEL" in
    "$MANUF "*) MODEL_FULL="$MODEL" ;;
    *) MODEL_FULL="$MANUF $MODEL" ;;
esac
[ -z "$MODEL" ] && MODEL_FULL="$MANUF"
REG_STATE=$(mm_field "registration")
ACCESS_TECHS=$(mm_field "access tech")
[ -z "$ACCESS_TECHS" ] && ACCESS_TECHS=$(mm_field "access techs")

# --- 2. Метрики сигнала ----------------------------------------------------
# Формат `mmcli --signal-get` (MM 1.24):
#   LTE    |                 rsrp: -74.00 dBm
#   ...
#   5G     |                 rsrp: -32768.00 dBm   <- сентинел «нет данных»
# Секция стоит СЛЕВА от «|», SINR называется «s/n».
sig_metric() {
    printf '%s\n' "$MM_SIG" | awk -v sect="$1" -v keys="$2" '
        function norm(s,   t) {
            t = tolower(s)
            if (t == "lte") return "lte"
            if (t == "5g" || t == "nr5g" || t == "5gnr") return "nr5g"
            return t
        }
        BEGIN { n = split(keys, K, " ") }
        {
            line = $0
            if (line ~ /^[ \t]*-+[ \t]*$/) next
            idx = index(line, "|")
            if (idx == 0) next
            left = substr(line, 1, idx - 1)
            right = substr(line, idx + 1)
            gsub(/^[ \t]+|[ \t]+$/, "", left)
            gsub(/^[ \t]+|[ \t]+$/, "", right)
            if (left != "") cur = norm(left)
            if (cur != sect) next
            for (i = 1; i <= n; i++) {
                k = K[i] ":"
                if (index(right, k) == 1) {
                    v = substr(right, length(k) + 1)
                    gsub(/^[ \t]+|[ \t]+$/, "", v)
                    sub(/[ \t].*$/, "", v)
                    print v
                    exit
                }
            }
        }'
}

# Отсекаем сентинелы 32767/-32768 и пустые значения.
num_filter() {
    printf '%s' "$1" | awk '{
        if ($0 == "") { print ""; exit }
        v = $0 + 0
        if (v <= -200 || v >= 200) { print ""; exit }
        print $0
    }'
}

LTE_RSRP=$(num_filter "$(sig_metric lte 'rsrp')")
LTE_RSRQ=$(num_filter "$(sig_metric lte 'rsrq')")
LTE_RSSI=$(num_filter "$(sig_metric lte 'rssi')")
LTE_SINR=$(num_filter "$(sig_metric lte 's/n snr sinr')")

NR_RSRP=$(num_filter "$(sig_metric nr5g 'rsrp')")
NR_RSRQ=$(num_filter "$(sig_metric nr5g 'rsrq')")
NR_SINR=$(num_filter "$(sig_metric nr5g 's/n snr sinr')")

# --- 3. Бэнд / сота / PCI (только через AT) --------------------------------
LTE_BAND=""; LTE_PCI=""; LTE_CELL=""; LTE_EARFCN=""; LTE_TAC=""
NR_BAND=""; NR_PCI=""; NR_ARFCN=""

if [ -n "$AT_PORT" ]; then
    # Quectel/Qualcomm: AT+QENG="servingcell" -> +QENG: "servingcell","NOCONN","NR5G-SA",...
    # Формат отличается между прошивками, поэтому разбираем по позициям мягко.
    QENG=$(at_cmd 'AT+QENG="servingcell"')
    if printf '%s' "$QENG" | grep -q 'servingcell'; then
        LTE_BAND=$(printf '%s' "$QENG" | sed -n 's/.*"LTE"[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,[^,]*,"\([0-9]*\)".*/\1/p' | head -1)
        LTE_EARFCN=$(printf '%s' "$QENG" | awk -F, '/"LTE"/ {print $10; exit}' | tr -dc '0-9')
        LTE_PCI=$(printf '%s' "$QENG" | awk -F, '/"LTE"/ {print $11; exit}' | tr -dc '0-9')
    fi
    CPSI=$(at_cmd 'AT+CPSI?')
    if printf '%s' "$CPSI" | grep -q 'CPSI'; then
        # +CPSI: NR5G,Online,460-11,0x...,...  — берём бэнд и PCI, если нашлись
        NR_BAND=$(printf '%s' "$CPSI" | sed -n 's/.*NR5G[^,]*,\([^,]*\),.*/\1/p' | head -1)
    fi
fi

# --- 4. Трафик -------------------------------------------------------------
RX=0; TX=0; DL_RATE=0; UL_RATE=0
if [ -r "/sys/class/net/$IFACE/statistics/rx_bytes" ]; then
    RX=$(cat "/sys/class/net/$IFACE/statistics/rx_bytes" 2>/dev/null)
    TX=$(cat "/sys/class/net/$IFACE/statistics/tx_bytes" 2>/dev/null)
fi

NOW=$(date +%s)
if [ -f "$STATE" ]; then
    PREV=$(cat "$STATE")
    P_TS=$(echo "$PREV" | cut -d' ' -f1)
    P_RX=$(echo "$PREV" | cut -d' ' -f2)
    P_TX=$(echo "$PREV" | cut -d' ' -f3)
    DT=$((NOW - P_TS))
    if [ "$DT" -gt 0 ] && [ "$DT" -lt 120 ]; then
        DL_RATE=$(( (RX - P_RX) * 8 / DT ))
        UL_RATE=$(( (TX - P_TX) * 8 / DT ))
        [ "$DL_RATE" -lt 0 ] && DL_RATE=0
        [ "$UL_RATE" -lt 0 ] && UL_RATE=0
    fi
fi
echo "$NOW $RX $TX" > "$STATE"

CONNECT_TIME=$(ubus call "network.interface.$MM_IFACE" status 2>/dev/null |
    awk -F: '/"uptime"/ { gsub(/[^0-9]/, "", $2); print $2; exit }')

# --- 5. Технология ---------------------------------------------------------
# mmcli отдаёт список через запятую: «lte, 5gnr» = 5G NSA.
TECHS=$(printf '%s' "$ACCESS_TECHS" | tr 'A-Z' 'a-z')
HAS_LTE=0
HAS_NR=0
case "$TECHS" in *lte*) HAS_LTE=1 ;; esac
case "$TECHS" in *5gnr*|*nr5g*|*5g*) HAS_NR=1 ;; esac

if [ "$HAS_NR" -eq 1 ] && [ "$HAS_LTE" -eq 1 ]; then
    RAT="5G NSA"
elif [ "$HAS_NR" -eq 1 ]; then
    RAT="5G SA"
elif [ "$HAS_LTE" -eq 1 ]; then
    RAT="LTE"
else
    RAT=""
fi
[ -z "$RAT" ] && [ -n "$REG_STATE" ] && RAT="LTE"

# --- 6. Сборка JSON --------------------------------------------------------
num_or_null() { [ -n "$1" ] && printf '%s' "$1" || printf 'null'; }
str_or_empty() { printf '"%s"' "$(json_escape "$1")"; }

cat <<EOF
{
  "ts": ${NOW}000,
  "online": $ONLINE,
  "rat": $(str_or_empty "$RAT"),
  "operator": $(str_or_empty "$OPERATOR"),
  "plmn": $(str_or_empty "$PLMN"),
  "lte": {
    "rsrp": $(num_or_null "$LTE_RSRP"),
    "rsrq": $(num_or_null "$LTE_RSRQ"),
    "sinr": $(num_or_null "$LTE_SINR"),
    "rssi": $(num_or_null "$LTE_RSSI"),
    "band": $(str_or_empty "$LTE_BAND"),
    "earfcn": $(num_or_null "$LTE_EARFCN"),
    "pci": $(num_or_null "$LTE_PCI"),
    "cell_id": $(num_or_null "$LTE_CELL"),
    "tac": $(str_or_empty "$LTE_TAC")
  },
  "nr": {
    "rsrp": $(num_or_null "$NR_RSRP"),
    "rsrq": $(num_or_null "$NR_RSRQ"),
    "sinr": $(num_or_null "$NR_SINR"),
    "band": $(str_or_empty "$NR_BAND"),
    "arfcn": $(num_or_null "$NR_ARFCN"),
    "pci": $(num_or_null "$NR_PCI")
  },
  "traffic": {
    "dl_rate": $DL_RATE,
    "ul_rate": $UL_RATE,
    "total_dl": ${RX:-0},
    "total_ul": ${TX:-0},
    "connect_time": $(num_or_null "$CONNECT_TIME")
  },
  "device": {
    "model": $(str_or_empty "$MODEL_FULL"),
    "firmware": $(str_or_empty "$FW"),
    "temp": $(str_or_empty "$TEMP"),
    "state": $(str_or_empty "$MODEM_STATE"),
    "sim_state": $(str_or_empty "$SIM_STATE"),
    "data_enabled": true
  }
}
EOF
