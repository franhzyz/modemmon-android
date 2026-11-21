#!/bin/sh
# Modem telemetry collector for OpenWrt (BusyBox ash).
# Read-only queries. Unknown values are emitted as JSON null.
# Schema v2 keeps every schema-v1 top-level field for old clients.
# Data comes from the MedveModem engine over ubus when it is available, and
# from mmcli/qmicli otherwise; the engine record is a memory read, no I/O.

# Load UCI config if available
if command -v uci >/dev/null 2>&1; then
	uci_qmi=$(uci get modem-telemetry.main.qmi_device 2>/dev/null)
	uci_net=$(uci get modem-telemetry.main.net_device 2>/dev/null)
	uci_mm=$(uci get modem-telemetry.main.mm_iface 2>/dev/null)
	uci_medv=$(uci get modem-telemetry.main.medvemodem_device 2>/dev/null)
	uci_cmdto=$(uci get modem-telemetry.main.command_timeout 2>/dev/null)
	uci_atto=$(uci get modem-telemetry.main.at_temp_timeout 2>/dev/null)
	uci_reqto=$(uci get modem-telemetry.main.request_timeout 2>/dev/null)
	uci_qmidetail=$(uci get modem-telemetry.main.qmi_detail 2>/dev/null)
	uci_qmiextra=$(uci get modem-telemetry.main.qmi_extra 2>/dev/null)
	uci_ubuscells=$(uci get modem-telemetry.main.ubus_cells 2>/dev/null)
	uci_snapttl=$(uci get modem-telemetry.main.snapshot_ttl 2>/dev/null)
	uci_useengine=$(uci get modem-telemetry.main.use_engine 2>/dev/null)
fi

QMI_DEVICE="${QMI_DEVICE:-${uci_qmi:-/dev/cdc-wdm0}}"
MODEM="${MODEM:-any}"
NET_DEVICE="${NET_DEVICE:-${uci_net:-wwan0}}"
MM_IFACE="${MM_IFACE:-${uci_mm:-mm0}}"
MEDVEMODEM_DEVICE="${MEDVEMODEM_DEVICE:-${uci_medv:-auto}}"
COMMAND_TIMEOUT="${COMMAND_TIMEOUT:-${uci_cmdto:-5}}"
AT_TEMP_TIMEOUT="${AT_TEMP_TIMEOUT:-${uci_atto:-1}}"
REQUEST_TIMEOUT="${REQUEST_TIMEOUT:-${uci_reqto:-4}}"
MAX_BEARERS="${MAX_BEARERS:-4}"
case "$COMMAND_TIMEOUT" in ''|*[!0-9]*) COMMAND_TIMEOUT=5 ;; esac
case "$AT_TEMP_TIMEOUT" in ''|*[!0-9]*) AT_TEMP_TIMEOUT=1 ;; esac
case "$REQUEST_TIMEOUT" in ''|*[!0-9]*) REQUEST_TIMEOUT=4 ;; esac
case "$MAX_BEARERS" in ''|*[!0-9]*) MAX_BEARERS=4 ;; esac
[ "$COMMAND_TIMEOUT" -gt 0 ] || COMMAND_TIMEOUT=5
[ "$AT_TEMP_TIMEOUT" -gt 0 ] || AT_TEMP_TIMEOUT=1
[ "$REQUEST_TIMEOUT" -gt 0 ] || REQUEST_TIMEOUT=4
[ "$MAX_BEARERS" -gt 0 ] || MAX_BEARERS=4
REQUEST_STARTED=$(date +%s)
REQUEST_DEADLINE=$((REQUEST_STARTED + REQUEST_TIMEOUT))
TIMEOUT_K_SUPPORTED=false
if command -v timeout >/dev/null 2>&1 &&
   timeout -k 1 1 true >/dev/null 2>&1; then
    TIMEOUT_K_SUPPORTED=true
fi
TRAFFIC_STATE="${TRAFFIC_STATE:-/tmp/modem-telemetry-traffic.state}"
TRAFFIC_LOCK="${TRAFFIC_LOCK:-${TRAFFIC_STATE}.lock}"
TEMP_CACHE="${TEMP_CACHE:-/tmp/modem-telemetry-temperature.cache}"
TEMP_CACHE_TTL="${TEMP_CACHE_TTL:-60}"
# Slow QMI blocks (serving system, band info, CA, SIM slots) are cached: they
# change on the scale of tens of seconds, while each costs a modem transaction.
# Direct QMI access. MedveModem's contract says the engine owns the modem's
# ports and a parallel client can drop the session on some modems, so the
# default keeps only two cheap calls: signal (needed for antenna aiming, the
# engine samples it every 30 s) and cell location (neighbour cells, which the
# engine cannot read over AT yet). Set QMI_DETAIL=0 for a pure-ubus collector,
# QMI_EXTRA=1 to also query serving/system/band/CA/slot data directly.
USE_ENGINE="${USE_ENGINE:-${uci_useengine:-1}}"
case "$USE_ENGINE" in 0|1) ;; *) USE_ENGINE=1 ;; esac
QMI_DETAIL="${QMI_DETAIL:-${uci_qmidetail:-1}}"
QMI_EXTRA="${QMI_EXTRA:-${uci_qmiextra:-0}}"
# AT^DEBUG? cell read through the engine. Off by default: on this modem
# (T99W175) frequent cell reads are known to hang the module - the MedveModem
# contract warns about exactly this. Enable only for experiments.
UBUS_CELLS="${UBUS_CELLS:-${uci_ubuscells:-0}}"
case "$QMI_DETAIL" in 0|1) ;; *) QMI_DETAIL=1 ;; esac
case "$QMI_EXTRA" in 0|1) ;; *) QMI_EXTRA=0 ;; esac
case "$UBUS_CELLS" in 0|1) ;; *) UBUS_CELLS=0 ;; esac

QMI_CACHE_DIR="${QMI_CACHE_DIR:-/tmp/modem-telemetry-qmi}"
QMI_CACHE_TTL="${QMI_CACHE_TTL:-60}"
# The ubus answers are written to files so jsonfilter can read them without a
# pipe (one process per query instead of three).
MODEM_ID="${MODEM_ID:-}"
UBUS_DEVICES_FILE="${UBUS_DEVICES_FILE:-/tmp/modem-telemetry-ubus-devices.json}"
METRICS_STAMP="${METRICS_STAMP:-/tmp/modem-telemetry-metrics.stamp}"
METRICS_TTL="${METRICS_TTL:-20}"
UBUS_RECORD_FILE="${UBUS_RECORD_FILE:-/tmp/modem-telemetry-ubus.json}"
UBUS_CELLS_FILE="${UBUS_CELLS_FILE:-/tmp/modem-telemetry-ubus-cells.json}"
# AT^DEBUG? cell reads are expensive and can hang the T99W175 when repeated
# every snapshot (3 s). Reuse the answer for this many seconds.
UBUS_CELLS_TTL="${UBUS_CELLS_TTL:-30}"
case "$UBUS_CELLS_TTL" in ''|*[!0-9]*) UBUS_CELLS_TTL=30 ;; esac
[ "$UBUS_CELLS_TTL" -gt 0 ] || UBUS_CELLS_TTL=30
QMI_CA_CACHE_TTL="${QMI_CA_CACHE_TTL:-20}"
QMI_SLOT_CACHE_TTL="${QMI_SLOT_CACHE_TTL:-300}"
# Optional plain-text temperature cache maintained by a local MQTT subscriber.
MQTT_TEMP_FILE="${MQTT_TEMP_FILE:-/tmp/modem-telemetry-mqtt-temperature}"
MQTT_TEMP_TTL="${MQTT_TEMP_TTL:-180}"

ESC=$(printf '\033')
TAB=$(printf '\t')
NL='
'

json_escape() {
    # Text values are single-line. The common case (plain ASCII, no quotes or
    # backslashes) is handled by the shell itself: the sed/tr pipeline costs
    # three processes, and this function runs for every string in the output.
    value=$1
    case "$value" in
        *"$ESC"*|*"$TAB"*|*"$NL"*|*\\*|*\"*)
            printf '%s' "$value" |
                sed "s/${ESC}\\[[0-9;]*[[:alpha:]]//g" |
                tr -d '\000-\010\013-\037' |
                sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' -e 's/\t/ /g' -e ':a;N;$!ba;s/\n/\\n/g'
            ;;
        *)
            printf '%s' "$value"
            ;;
    esac
}

number_or_null() {
    # Pure shell: this runs for every numeric field in the JSON.
    value=$1
    case "$value" in
        *[0-9]*) ;;
        *) printf 'null'; return ;;
    esac
    case "$value" in
        [+-]*) value=${value#?} ;;
    esac
    case "$value" in
        ''|*[!0-9.]*|*.*.*) printf 'null'; return ;;
    esac
    printf '%s' "$1"
}

numeric_value() {
    printf '%s\n' "$1" | awk '
        {
            value=$0
            gsub(/,/, ".", value)
            if (match(value, /[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)/)) {
                printf "%.15g", substr(value, RSTART, RLENGTH) + 0
            }
        }'
}

bandwidth_value() {
    value=$(printf '%s' "$1" | tr ',' '.')
    case "$value" in
        *"1-4"*|*"1.4"*) printf '1.4' ;;
        *) numeric_value "$value" ;;
    esac
}

uint_value() {
    printf '%s\n' "$1" | awk '
        {
            value=$0
            if (match(value, /[0-9]+/)) {
                print substr(value, RSTART, RLENGTH)
                exit
            }
        }'
}

is_uint() {
    case "$1" in
        ''|*[!0-9]*) return 1 ;;
        *) return 0 ;;
    esac
}

uint_or_null() {
    if is_uint "$1"; then
        printf '%s' "$1"
    else
        printf 'null'
    fi
}

string_or_null() {
    [ -n "$1" ] || { printf 'null'; return; }
    case "$1" in
        *"$ESC"*|*"$TAB"*|*"$NL"*|*\\*|*\"*)
            printf '"%s"' "$(json_escape "$1")"
            ;;
        *)
            printf '"%s"' "$1"
            ;;
    esac
}

bool_or_null() {
    case "$1" in
        true|false) printf '%s' "$1" ;;
        *) printf 'null' ;;
    esac
}

run_limited() {
    command_now=$(date +%s)
    command_remaining=$((REQUEST_DEADLINE - command_now))
    [ "$command_remaining" -gt 0 ] || return 124

    command_limit=$COMMAND_TIMEOUT
    [ "$command_limit" -le "$command_remaining" ] || command_limit=$command_remaining

    if [ "$TIMEOUT_K_SUPPORTED" = true ]; then
        timeout -k 1 "$command_limit" "$@" 2>/dev/null
        return $?
    fi

    # BusyBox builds may omit timeout or -k. Keep every child bounded by the
    # remaining request budget and escalate from TERM to KILL.
    "$@" 2>/dev/null &
    command_pid=$!
    (
        timer_pid=""
        trap '[ -n "$timer_pid" ] && kill "$timer_pid" 2>/dev/null; exit 0' HUP INT TERM
        sleep "$command_limit" &
        timer_pid=$!
        wait "$timer_pid" 2>/dev/null
        kill -TERM "$command_pid" 2>/dev/null
        sleep 1
        kill -KILL "$command_pid" 2>/dev/null
    ) </dev/null >/dev/null 2>&1 &
    watchdog_pid=$!
    wait "$command_pid" 2>/dev/null
    command_status=$?
    kill -TERM "$watchdog_pid" 2>/dev/null
    wait "$watchdog_pid" 2>/dev/null
    return "$command_status"
}

file_age() {
    [ -f "$1" ] || return 1
    modified=$(date -r "$1" +%s 2>/dev/null)
    if ! is_uint "$modified"; then
        modified=$(stat -c %Y "$1" 2>/dev/null)
    fi
    is_uint "$modified" || return 1
    now=$(date +%s)
    printf '%s' "$((now - modified))"
}

qmi_cached() {
    # qmi_cached <cache-name> <ttl> <command...>
    # Serving/system/band/CA/slot data changes slowly and costs a QMI
    # transaction each. Cache it; signal and cell location stay live.
    cache_name=$1
    cache_ttl=$2
    shift 2
    cache_file="$QMI_CACHE_DIR/$cache_name"
    cache_age=$(file_age "$cache_file" 2>/dev/null)
    if is_uint "$cache_age" && [ "$cache_age" -le "$cache_ttl" ]; then
        cat "$cache_file" 2>/dev/null
        return 0
    fi
    cache_out=$(run_limited "$@" 2>/dev/null)
    if [ -n "$cache_out" ]; then
        mkdir -p "$QMI_CACHE_DIR" 2>/dev/null
        printf '%s\n' "$cache_out" > "$cache_file" 2>/dev/null
    fi
    printf '%s\n' "$cache_out"
}

# cf <jsonfilter expression> -> first value from the cells answer
cf() {
    [ -s "$UBUS_CELLS_FILE" ] || return 0
    cell_raw=$(jsonfilter -i "$UBUS_CELLS_FILE" -e "$1" 2>/dev/null)
    printf '%s' "${cell_raw%%"$NL"*}"
}

secondary_carriers_from_ubus() {
    # Build the CA secondary list from the engine's AT cell read when the
    # direct QMI CA query is disabled. AT^DEBUG? does not report per-carrier
    # activation state, so the state stays "configured".
    [ -n "$ubus_cells" ] || { printf '[]'; return; }
    i=0
    index=0
    out=""
    while :; do
        block=$(cf "@.cells[$i].pci")
        [ -n "$block" ] || break
        if [ "$(cf "@.cells[$i].role")" = "secondary" ]; then
            c_band=$(cf "@.cells[$i].band")
            c_arfcn=$(cf "@.cells[$i].earfcn")
            c_pci=$(cf "@.cells[$i].pci")
            c_bw=$(cf "@.cells[$i].bandwidth")
            index=$((index + 1))
            out="$out${out:+,}{\"index\":$index,\"band\":$(string_or_null "B$c_band"),\"arfcn\":$(number_or_null "$c_arfcn"),\"pci\":$(number_or_null "$c_pci"),\"bw_dl_mhz\":$(number_or_null "$c_bw"),\"state\":\"configured\"}"
        fi
        i=$((i + 1))
    done
    printf '[%s]' "$out"
}

normalize_temperature() {
    printf '%s' "$1" | awk '
        {
            value=$0
            gsub(/,/, ".", value)
            if (match(value, /-?[0-9]+([.][0-9]+)?/)) {
                value=substr(value, RSTART, RLENGTH) + 0
                if (value >= -40 && value <= 125) {
                    printf "%g", value
                }
            }
        }'
}

mask_id() {
    value=$(printf '%s' "$1" | tr -cd '0-9A-Za-z')
    [ -n "$value" ] || return
    last4=$(printf '%s' "$value" | sed 's/.*\(....\)$/\1/')
    if [ "${#value}" -le 4 ]; then
        printf '%s' "$last4"
    else
        printf '****%s' "$last4"
    fi
}

mm_field() {
    key=$1
    legacy_key=$2
    value=$(kv_field "$mm_info" "$key")
    if [ -n "$value" ]; then
        printf '%s\n' "$value"
        return
    fi

    # Compatibility with older ModemManager builds and local fixtures.
    [ -n "$legacy_key" ] || return
    printf '%s\n' "$mm_info" | awk -v key="$legacy_key" '
        {
            line=$0
            sub(/^[^|]*\|/, "", line)
            gsub(/^[ \t]+|[ \t]+$/, "", line)
            p=index(line, ":")
            if (!p) next
            k=substr(line, 1, p-1)
            v=substr(line, p+1)
            gsub(/^[ \t]+|[ \t]+$/, "", k)
            gsub(/^[ \t]+|[ \t]+$/, "", v)
            if (k == key && v != "") { print v; exit }
        }'
}

kv_values() {
    printf '%s\n' "$1" | awk -v prefix="$2" '
        {
            p=index($0, ":")
            if (!p) next
            key=substr($0, 1, p-1)
            value=substr($0, p+1)
            gsub(/^[ \t]+|[ \t]+$/, "", key)
            gsub(/^[ \t]+|[ \t]+$/, "", value)
            if (index(key, prefix) == 1 && value != "" && value != "--") print value
        }'
}

kv_field() {
    printf '%s\n' "$1" | awk -v key="$2" '
        {
            p=index($0, ":")
            if (!p) next
            k=substr($0, 1, p-1)
            v=substr($0, p+1)
            gsub(/^[ \t]+|[ \t]+$/, "", k)
            gsub(/^[ \t]+|[ \t]+$/, "", v)
            if (k == key && v != "" && v != "--") { print v; exit }
        }'
}

quoted_field() {
    printf '%s\n' "$1" | awk -v key="$2" '
        {
            line=$0
            gsub(/^[ \t]+|[ \t]+$/, "", line)
            prefix=key ":"
            if (index(line, prefix) != 1) next
            value=substr(line, length(prefix)+1)
            gsub(/^[ \t]+|[ \t]+$/, "", value)
            sub(/^\047/, "", value)
            sub(/\047.*/, "", value)
            if (value != "" && value != "n/a" && value != "unavailable") {
                print value
                exit
            }
        }'
}

section_quoted_field() {
    printf '%s\n' "$1" | awk -v wanted_section="$2" -v key="$3" '
        {
            line=$0
            gsub(/^[ \t]+|[ \t]+$/, "", line)
            if (line == wanted_section) { inside=1; next }
            if (inside && line ~ /service:$/ && line != wanted_section) exit
            if (!inside) next
            prefix=key ":"
            if (index(line, prefix) != 1) next
            value=substr(line, length(prefix)+1)
            gsub(/^[ \t]+|[ \t]+$/, "", value)
            sub(/^\047/, "", value)
            sub(/\047.*/, "", value)
            if (value != "" && value != "n/a" && value != "unavailable") {
                print value
                exit
            }
        }'
}

active_slot_number() {
    printf '%s\n' "$1" | awk '
        /Physical slot [0-9]+:/ {
            slot=$0
            sub(/.*Physical slot /, "", slot)
            sub(/:.*/, "", slot)
            next
        }
        /Slot status:/ && slot != "" {
            value=$0
            sub(/.*Slot status:[ \t]*/, "", value)
            gsub(/^[ \t]+|[ \t]+$/, "", value)
            sub(/^\047/, "", value)
            sub(/\047.*/, "", value)
            if (value == "active") { print slot - 1; exit }
        }'
}

active_slot_field() {
    printf '%s\n' "$1" | awk -v key="$2" '
        function emit() {
            if (!emitted && status == "active" && wanted != "") {
                print wanted
                emitted=1
            }
        }
        /Physical slot [0-9]+:/ {
            if (inside) {
                emit()
                if (emitted) exit
            }
            inside=1
            status=""
            wanted=""
            next
        }
        inside {
            line=$0
            gsub(/^[ \t]+|[ \t]+$/, "", line)
            p=index(line, ":")
            if (!p) next
            field=substr(line, 1, p-1)
            value=substr(line, p+1)
            gsub(/^[ \t]+|[ \t]+$/, "", field)
            gsub(/^[ \t]+|[ \t]+$/, "", value)
            sub(/^\047/, "", value)
            sub(/\047.*/, "", value)
            if (field == "Slot status") status=value
            if (field == key && value != "" && value != "n/a" && value != "unavailable") wanted=value
        }
        END { emit() }'
}

signal_field() {
    printf '%s\n' "$mm_signal" | awk -v wanted="$1" '
        {
            p=index($0, "|")
            if (!p) next
            left=substr($0, 1, p-1)
            right=substr($0, p+1)
            gsub(/^[ \t]+|[ \t]+$/, "", left)
            gsub(/^[ \t]+|[ \t]+$/, "", right)
            if (left != "") section=tolower(left)
            if (section != "lte") next
            q=index(right, ":")
            if (!q) next
            key=substr(right, 1, q-1)
            value=substr(right, q+1)
            gsub(/^[ \t]+|[ \t]+$/, "", key)
            gsub(/^[ \t]+|[ \t]+$/, "", value)
            if (key == wanted || (wanted == "sinr" && (key == "s/n" || key == "snr"))) {
                sub(/[ \t].*$/, "", value)
                if (value ~ /^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)$/ &&
                    value + 0 > -200 && value + 0 < 200) print value
                exit
            }
        }'
}

qmi_signal_field() {
    printf '%s\n' "$qmi_signal" | awk -v wanted="$1" '
        /^[^ \t].*:$/ {
            section=$0
            gsub(/^[ \t]+|[ \t]+$/, "", section)
            next
        }
        section == "LTE:" {
            line=$0
            gsub(/^[ \t]+|[ \t]+$/, "", line)
            prefix=wanted ":"
            if (index(line, prefix) != 1) next
            value=substr(line, length(prefix)+1)
            gsub(/^[ \t]+|[ \t]+$/, "", value)
            sub(/^\047/, "", value)
            sub(/\047.*/, "", value)
            if (value != "" && value != "n/a" && value != "unavailable" &&
                match(value, /[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)/)) {
                print substr(value, RSTART, RLENGTH)
                exit
            }
        }'
}

ca_primary_field() {
    printf '%s\n' "$qmi_ca" | awk -v key="$1" '
        /Primary Cell Info/ { inside=1; next }
        /Secondary Cell|No Secondary Cells/ { inside=0 }
        inside {
            line=$0
            gsub(/^[ \t]+|[ \t]+$/, "", line)
            prefix=key ":"
            if (index(line, prefix) != 1) next
            value=substr(line, length(prefix)+1)
            gsub(/^[ \t]+|[ \t]+$/, "", value)
            sub(/^\047/, "", value)
            sub(/\047.*/, "", value)
            print value
            exit
        }'
}

lte_frequency_mhz() {
    band_value="$1"
    channel="$2"
    [ -n "$band_value" ] && [ -n "$channel" ] || return
    # Full E-UTRA downlink table, 3GPP TS 36.101 Table 5.7.3-1.
    # F_DL = F_DL_low + 0.1 * (N_DL - N_DL_offs), raster 100 kHz.
    # For TDD bands the downlink and uplink frequencies are identical, so the
    # same formula gives the channel centre frequency for both directions.
    awk -v b="$band_value" -v n="$channel" 'BEGIN {
        split("1:2110:0 2:1930:600 3:1805:1200 4:2110:1950 5:869:2400" \
              " 6:875:2650 7:2620:2750 8:925:3450 9:1844.9:3800 10:2110:4150" \
              " 11:1475.9:4750 12:729:5010 13:746:5180 14:758:5280 17:734:5730" \
              " 18:860:5850 19:875:6000 20:791:6150 21:1495.9:6450 22:3510:6600" \
              " 23:2180:7500 24:1525:7700 25:1930:8040 26:859:8690 27:852:9040" \
              " 28:758:9210 29:717:9660 30:2350:9770 31:462.5:9870 32:1452:9920" \
              " 33:1900:36000 34:2010:36200 35:1850:36350 36:1930:36950" \
              " 37:1910:37550 38:2570:37750 39:1880:38250 40:2300:38650" \
              " 41:2496:39650 42:3400:41590 43:3600:43590 44:703:45590" \
              " 45:1447:46590 46:5150:46790 47:5855:54540 48:3550:55240" \
              " 49:3550:56740 50:1432:58240 51:1427:59090 52:3300:59140" \
              " 53:2483.5:60140 65:2110:65536 66:2110:66436 67:738:67336" \
              " 68:753:67536 69:2570:67836 70:1995:68336 71:617:68586" \
              " 72:461:68936 73:460:68986 74:1475:69036 75:1432:69466" \
              " 76:1427:70316 85:698:70366 87:420:70546 88:422:70596", t, " ")
        for (i in t) { split(t[i], p, ":"); low[p[1]]=p[2]; off[p[1]]=p[3] }
        if ((b in low) && n != "") {
            printf "%.1f", low[b] + 0.1 * (n - off[b])
        }
    }'
}

secondary_carriers_json() {
    printf '%s\n' "$qmi_ca" | awk '
        function clean(line, key, value) {
            gsub(/^[ \t]+|[ \t]+$/, "", line)
            sub("^" key ":[ \t]*\047", "", line)
            sub(/\047.*/, "", line)
            return line
        }
        function num(value) {
            gsub(/,/, ".", value)
            if (match(value, /[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)/)) {
                return substr(value, RSTART, RLENGTH) + 0
            }
            return "null"
        }
        function bandwidth(value) {
            gsub(/,/, ".", value)
            if (value ~ /1-4/ || value ~ /1[.]4/) return 1.4
            return num(value)
        }
        function enumstr(value) {
            if (value == "") return "null"
            if (value !~ /^[A-Za-z0-9_.+ -]+$/) return "null"
            return "\"" value "\""
        }
        function flush() {
            if (!inside || (pci == "" && channel == "" && band == "")) return
            if (!first) printf ","
            printf "{\"index\":%s,\"band\":%s,\"arfcn\":%s,\"pci\":%s,\"bw_dl_mhz\":%s,\"state\":%s}", num(idx), enumstr(band), num(channel), num(pci), bandwidth(bw), enumstr(state)
            first=0
        }
        BEGIN { printf "["; first=1; generated=1 }
        /^[^ \t]/ && $0 !~ /Secondary Cell( [0-9]+)? Info/ {
            flush(); inside=0
        }
        /Secondary Cell( [0-9]+)? Info/ {
            flush()
            inside=1; idx=$0; gsub(/[^0-9]/, "", idx)
            if (idx == "") idx=generated++
            pci=""; channel=""; band=""; bw=""; state=""
            next
        }
        inside && /Physical Cell ID:/ { pci=clean($0, "Physical Cell ID"); next }
        inside && /RX Channel:/ { channel=clean($0, "RX Channel"); next }
        inside && /DL Bandwidth:/ { bw=clean($0, "DL Bandwidth"); next }
        inside && /LTE Band:/ {
            band=clean($0, "LTE Band")
            sub(/^eutran-/, "B", band)
            next
        }
        inside && /State:/ { state=clean($0, "State"); next }
        inside && /Cell index:/ { idx=clean($0, "Cell index"); next }
        END { flush(); printf "]" }'
}

neighbor_cells_json() {
    printf '%s\n' "$qmi_cell" | awk -v serving_pci="$pci" '
        function quoted(line, value) {
            value=line
            sub(/^[^\047]*\047/, "", value)
            sub(/\047.*/, "", value)
            return value
        }
        function num(value) {
            gsub(/,/, ".", value)
            if (match(value, /[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)/)) {
                return substr(value, RSTART, RLENGTH) + 0
            }
            return "null"
        }
        function enumstr(value) {
            if (value == "") return "null"
            if (value !~ /^[A-Za-z0-9_.+ -]+$/) return "null"
            return "\"" value "\""
        }
        function flush() {
            if (!in_cell || cell_pci == "" || arfcn == "" || count >= 16) return
            if (relation == "intra" && cell_pci == serving_pci) return
            if (!first) printf ","
            printf "{\"rat\":\"LTE\",\"relation\":%s,\"band\":%s,\"arfcn\":%s,\"pci\":%s,\"rsrp\":%s,\"rsrq\":%s,\"rssi\":%s}", enumstr(relation), enumstr(band), num(arfcn), num(cell_pci), num(rsrp), num(rsrq), num(rssi)
            first=0; count++
        }
        BEGIN { printf "["; first=1; relation="" }
        /^Intrafrequency LTE Info/ { flush(); relation="intra"; in_cell=0; next }
        /^Interfrequency LTE Info/ { flush(); relation="inter"; in_cell=0; next }
        /^LTE Info Neighboring/ { flush(); relation=""; in_cell=0; next }
        relation != "" && /EUTRA Absolute RF Channel Number:/ {
            if (relation == "inter") { flush(); in_cell=0 }
            arfcn=quoted($0)
            band=$0
            sub(/.*\(E-UTRA band /, "", band)
            sub(/:.*/, "", band)
            if (band ~ /^[0-9]+$/) band="B" band; else band=""
            next
        }
        relation != "" && /^[ \t]*Cell \[[0-9]+\]:/ {
            flush(); in_cell=1; cell_pci=""; rsrp=""; rsrq=""; rssi=""; next
        }
        in_cell && /Physical Cell ID:/ { cell_pci=quoted($0); next }
        in_cell && /RSRP:/ { rsrp=quoted($0); next }
        in_cell && /RSRQ:/ { rsrq=quoted($0); next }
        in_cell && /RSSI:/ { rssi=quoted($0); next }
        END { flush(); printf "]" }'
}

temperature_line_value() {
    temperature_label=$1
    printf '%s\n' "$at_temperature_output" | awk -v label="$temperature_label:" '
        {
            marker="\"" label
            position=index($0, marker)
            if (!position) next
            value=substr($0, position + length(marker))
            gsub(/,/, ".", value)
            if (match(value, /-?[0-9]+([.][0-9]+)?/)) {
                value=substr(value, RSTART, RLENGTH) + 0
                if (value >= -40 && value <= 125) print value
            }
            exit
        }'
}

save_temperature_cache() {
    temperature_cache_tmp="${TEMP_CACHE}.tmp.$$"
    if printf '%s|%s|%s|%s|%s|%s|%s|%s\n' \
        "$temperature_now" "$temp_c" "$temp_source" "$temp_status" \
        "$temp_sensor" "$temp_pa_c" "$temp_skin_c" "$temp_tsens_c" \
        > "$temperature_cache_tmp" 2>/dev/null; then
        mv "$temperature_cache_tmp" "$TEMP_CACHE" 2>/dev/null || rm -f "$temperature_cache_tmp"
    fi
}

rndis_eng_id() {
    jsonfilter -i /tmp/modem-telemetry-ubus-devices.json -e '@.devices[0].device_id' 2>/dev/null
}
rndis_at_cmd() {
    _cmd="$1"
    _eid="$(rndis_eng_id)"
    [ -z "$_eid" ] && _eid="$MEDVEMODEM_DEVICE"
    ubus call medvemodem at_command "{"id":"$_eid","command":"$_cmd","timeout_ms":4000}" 2>/dev/null
}
mm_temp_of() {
    printf '%s
' "$1" | awk '
        /+GTSENRDTEMP:/ {
            body = $0
            sub(/^[^:]*:/, "", body)
            sub(/^[[:space:]]*[0-9]+[[:space:]]*,/, "", body)
            n = split(body, a, ",")
            for (i = 1; i <= n; i++) {
                v = a[i] + 0
                if (v == 0) continue
                if (v >= 65000 || v == 32767 || v == -32767 || v == -32768 || v == 65535 || v == -127 || v == -28479) continue
                if (v >= 1000 || v <= -1000) v /= 1000
                if (v >= -40 && v <= 125 && (best == "" || v > best)) best = v
            }
        }
        END { if (best != "") printf "%.1f", best }'
}
rndis_temp_cmd() {
    # Температура через движок: перебор AT-команд (пресеты у разных моделей свои)
    for _tc in 'AT+GTSENRDTEMP=1' 'AT^TEMP?' 'AT+ETHERNAL?' 'AT+QTEMP?'; do
        _out=$(rndis_at_cmd "$_tc")
        _t=$(mm_temp_of "$_out")
        if [ -n "$_t" ]; then printf '%s
' "$_out"; return; fi
    done
}
rndis_eng_id() {
    jsonfilter -i /tmp/modem-telemetry-ubus-devices.json -e '@.devices[0].device_id' 2>/dev/null
}
rndis_at_cmd() {
    _cmd="$1"
    _eid="$(rndis_eng_id)"
    [ -z "$_eid" ] && _eid="$MEDVEMODEM_DEVICE"
    ubus call medvemodem at_command "{"id":"$_eid","command":"$_cmd","timeout_ms":4000}" 2>/dev/null
}
mm_temp_of() {
    printf '%s
' "$1" | awk '
        /+GTSENRDTEMP:/ {
            body = $0
            sub(/^[^:]*:/, "", body)
            sub(/^[[:space:]]*[0-9]+[[:space:]]*,/, "", body)
            n = split(body, a, ",")
            for (i = 1; i <= n; i++) {
                v = a[i] + 0
                if (v == 0) continue
                if (v >= 65000 || v == 32767 || v == -32767 || v == -32768 || v == 65535 || v == -127 || v == -28479) continue
                if (v >= 1000 || v <= -1000) v /= 1000
                if (v >= -40 && v <= 125 && (best == "" || v > best)) best = v
            }
        }
        END { if (best != "") printf "%.1f", best }'
}
rndis_temp_cmd() {
    # Температура через движок: перебор AT-команд (пресеты у разных моделей свои)
    for _tc in 'AT+GTSENRDTEMP=1' 'AT^TEMP?' 'AT+ETHERNAL?' 'AT+QTEMP?'; do
        _out=$(rndis_at_cmd "$_tc")
        _t=$(mm_temp_of "$_out")
        if [ -n "$_t" ]; then printf '%s
' "$_out"; return; fi
    done
}
read_temperature() {
    temp_c=""
    temp_source=""
    temp_sensor=""
    temp_status="unavailable"
    temp_pa_c=""
    temp_skin_c=""
    temp_tsens_c=""
    temperature_now=$(date +%s)

    # Cache AT^TEMP? because Android polls every two seconds. New cache lines
    # include all three T99W175 sensors; older four/three-field lines remain valid.
    if [ -r "$TEMP_CACHE" ]; then
        cache_line=$(head -n 1 "$TEMP_CACHE" 2>/dev/null)
        IFS='|' read -r cache_first cache_second cache_third cache_fourth \
            cache_fifth cache_sixth cache_seventh cache_eighth <<EOF_CACHE
$cache_line
EOF_CACHE
        if is_uint "$cache_first" && [ -n "$cache_fourth" ]; then
            cache_age=$((temperature_now - cache_first))
            cached_temp=$cache_second
            cached_source=$cache_third
            cached_status=$cache_fourth
            cached_sensor=$cache_fifth
            cached_pa=$cache_sixth
            cached_skin=$cache_seventh
            cached_tsens=$cache_eighth
        else
            cache_age=$(file_age "$TEMP_CACHE" 2>/dev/null)
            cached_temp=$cache_first
            cached_source=$cache_second
            cached_status=$cache_third
            cached_sensor=""
            cached_pa=""
            cached_skin=""
            cached_tsens=""
        fi
        if is_uint "$cache_age" && [ "$cache_age" -ge 0 ] &&
           [ "$cache_age" -le "$TEMP_CACHE_TTL" ]; then
            temp_c=$(normalize_temperature "$cached_temp")
            temp_source=$cached_source
            temp_sensor=$cached_sensor
            temp_status=$cached_status
            temp_pa_c=$(normalize_temperature "$cached_pa")
            temp_skin_c=$(normalize_temperature "$cached_skin")
            temp_tsens_c=$(normalize_temperature "$cached_tsens")
            return
        fi
    fi

    # T99W175 exposes real modem temperatures through its managed AT port.
    # TSENS is the chipset sensor and therefore the primary temp_c value; PA and
    # Skin Sensor remain available as separate schema-v2 fields.
    at_temp_status=""
    if command -v medvemodem >/dev/null 2>&1; then
        at_temperature_output=$(COMMAND_TIMEOUT="$AT_TEMP_TIMEOUT" \
            run_limited medvemodem at "$MEDVEMODEM_DEVICE" 'AT+GTSENRDTEMP=1')
        if printf '%s\n' "$at_temperature_output" |
            grep -q '"ok"[[:space:]]*:[[:space:]]*true'; then
            temp_c=$(printf '%s\n' "$at_temperature_output" | awk '
                /\+GTSENRDTEMP:/ {
                    body = $0
                    sub(/^[^:]*:/, "", body)
                    sub(/^[[:space:]]*[0-9]+[[:space:]]*,/, "", body)
                    n = split(body, a, ",")
                    for (i = 1; i <= n; i++) {
                        v = a[i] + 0
                        if (v == 0) continue
                        if (v >= 65000 || v == 32767 || v == -32767 || v == -32768 || v == 65535 || v == -127 || v == -28479) continue
                        if (v >= 1000 || v <= -1000) v /= 1000
                        if (v >= -40 && v <= 125 && (best == "" || v > best)) best = v
                    }
                }
                END { if (best != "") printf "%.1f", best }')
            temp_sensor="GTSENRDTEMP"
            if [ -n "$temp_c" ]; then
                temp_source="at_temp"
                temp_status="available"
                save_temperature_cache
                return
            fi
            at_temp_status="at_not_reported"
        else
            at_temp_status="at_command_failed"
        fi
    else
        at_temp_status="medvemodem_not_installed"
    fi

    # Optional MQTT fallback. Accept a legacy plain value or epoch|value.
    mqtt_value=""
    mqtt_age=""
    if [ -r "$MQTT_TEMP_FILE" ]; then
        mqtt_line=$(head -n 1 "$MQTT_TEMP_FILE" 2>/dev/null)
        case "$mqtt_line" in
            *'|'*)
                mqtt_timestamp=${mqtt_line%%|*}
                if is_uint "$mqtt_timestamp"; then
                    mqtt_age=$((temperature_now - mqtt_timestamp))
                    mqtt_value=${mqtt_line#*|}
                fi
                ;;
            *)
                mqtt_age=$(file_age "$MQTT_TEMP_FILE" 2>/dev/null)
                mqtt_value=$mqtt_line
                ;;
        esac
    fi
    if is_uint "$mqtt_age" && [ "$mqtt_age" -ge 0 ] &&
       [ "$mqtt_age" -le "$MQTT_TEMP_TTL" ]; then
        temp_c=$(normalize_temperature "$mqtt_value")
        if [ -n "$temp_c" ]; then
            temp_source="mqtt"
            temp_status="available"
            save_temperature_cache
            return
        fi
    fi

    if command -v modeminfo >/dev/null 2>&1; then
        modeminfo_data=$(run_limited modeminfo)
        if command -v jsonfilter >/dev/null 2>&1; then
            raw_temp=$(jsonfilter -s "$modeminfo_data" -e '@.modem[0].chiptemp' 2>/dev/null | head -n 1)
        else
            raw_temp=$(printf '%s\n' "$modeminfo_data" | sed -n 's/.*"chiptemp"[ ]*:[ ]*"\([^"]*\)".*/\1/p' | head -n 1)
        fi
        temp_c=$(normalize_temperature "$raw_temp")
        if [ -n "$temp_c" ]; then
            temp_source="modeminfo"
            temp_status="available"
            save_temperature_cache
            return
        fi
    fi

    temp_source="at_temp"
    temp_status=$at_temp_status
    [ -n "$temp_status" ] || temp_status="unavailable"
    save_temperature_cache
}

mm_info=""
mm_signal=""
qmi_signal=""
mm_sim=""
mm_bearer=""
bearer_sample_ts=""
qmi_serving=""
qmi_system=""
qmi_cell=""
qmi_rf=""
qmi_ca=""
qmi_slots=""

# select_device: pick the modem worth showing.
# Order: connected and able to report telemetry > able to report telemetry >
# connected > first present. A router-mode (HiLink) modem carries traffic but
# has no signal data at all, so it must not shadow a modem that does.
select_device() {
    dev_count=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e '@.devices[*].device_id' 2>/dev/null | wc -l)
    dev_index=0
    dev_first=""
    dev_connected=""
    dev_telem=""
    while [ "$dev_index" -lt "$dev_count" ]; do
        dev_id=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e "@.devices[$dev_index].device_id" 2>/dev/null)
        dev_present=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e "@.devices[$dev_index].present" 2>/dev/null)
        dev_state=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e "@.devices[$dev_index].session.state" 2>/dev/null)
        # A disabled modem still has a modemmanager.state, but no signal at
        # all: only a modem that actually reports RSRP is worth showing.
        dev_sig=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e "@.devices[$dev_index].modemmanager.signal.lte.rsrp" 2>/dev/null)
        if [ -z "$dev_sig" ]; then
            # NR-only modem: the engine files NR under "signal.5g".
            dev_sig=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e "@.devices[$dev_index].modemmanager.signal[\"5g\"].rsrp" 2>/dev/null)
        fi
        if [ "$dev_present" = "true" ] && [ -n "$dev_id" ]; then
            [ -n "$dev_first" ] || dev_first=$dev_id
            if [ -n "$dev_sig" ]; then
                [ -n "$dev_telem" ] || dev_telem=$dev_id
                if [ "$dev_state" = "connected" ]; then
                    printf '%s' "$dev_id"
                    return
                fi
            elif [ "$dev_state" = "connected" ]; then
                [ -n "$dev_connected" ] || dev_connected=$dev_id
            fi
        fi
        dev_index=$((dev_index + 1))
    done
    if [ -n "$dev_telem" ]; then
        printf '%s' "$dev_telem"
    elif [ -n "$dev_connected" ]; then
        printf '%s' "$dev_connected"
    else
        printf '%s' "$dev_first"
    fi
}

# --- MedveModem ubus record ------------------------------------------------
# The medvemodemd engine already polls ModemManager and QMI and keeps the
# result in memory: "ubus call medvemodem device" answers in ~10 ms with no
# modem I/O at all. Prefer it over spawning mmcli/qmicli, and resolve the
# device id from it instead of grepping "medvemodem list" output.
ubus_devices=""
ubus_record=""
ubus_ok=false
if [ "$USE_ENGINE" = 1 ] && command -v ubus >/dev/null 2>&1 &&
   command -v jsonfilter >/dev/null 2>&1; then
    ubus_devices=$(run_limited ubus call medvemodem devices 2>/dev/null)
    if [ -n "$ubus_devices" ]; then
        printf '%s' "$ubus_devices" > "$UBUS_DEVICES_FILE" 2>/dev/null

        # The engine reads ModemManager on demand, so its record can sit at a
        # stale "disabled" long after the modem came back. Ask it to refresh
        # (metrics reads ModemManager for an MM-owned modem, no port access)
        # and re-read the list. Cached: at most once per METRICS_TTL seconds.
        metrics_age=$(file_age "$METRICS_STAMP" 2>/dev/null)
        if ! is_uint "$metrics_age" || [ "$metrics_age" -gt "$METRICS_TTL" ]; then
            refresh_index=0
            refresh_count=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e '@.devices[*].device_id' 2>/dev/null | wc -l)
            while [ "$refresh_index" -lt "$refresh_count" ]; do
                refresh_id=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e "@.devices[$refresh_index].device_id" 2>/dev/null)
                refresh_mm=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e "@.devices[$refresh_index].modemmanager.state" 2>/dev/null)
                if [ -n "$refresh_id" ] && [ -n "$refresh_mm" ]; then
                    run_limited ubus call medvemodem metrics \
                        "{\"id\":\"$refresh_id\",\"max_age_ms\":15000}" >/dev/null 2>&1
                fi
                refresh_index=$((refresh_index + 1))
            done
            : > "$METRICS_STAMP" 2>/dev/null
            ubus_devices=$(run_limited ubus call medvemodem devices 2>/dev/null)
            [ -n "$ubus_devices" ] && printf '%s' "$ubus_devices" > "$UBUS_DEVICES_FILE" 2>/dev/null
        fi
        if [ -n "$MODEM_ID" ]; then
            # Explicit choice: ?id=<device_id>
            MEDVEMODEM_DEVICE=$MODEM_ID
        elif [ -n "$MEDVEMODEM_DEVICE" ] && [ "$MEDVEMODEM_DEVICE" != "auto" ]; then
            # A pinned id wins only while the engine still knows that modem;
            # an unplugged modem must not leave the endpoint blind.
            case "$(cat "$UBUS_DEVICES_FILE" 2>/dev/null)" in
                *"$MEDVEMODEM_DEVICE"*) ;;
                *) MEDVEMODEM_DEVICE=$(select_device) ;;
            esac
        else
            MEDVEMODEM_DEVICE=$(select_device)
        fi
        if [ -n "$MEDVEMODEM_DEVICE" ]; then
            ubus_record=$(run_limited ubus call medvemodem device \
                "{\"id\":\"$MEDVEMODEM_DEVICE\"}" 2>/dev/null)
            case "$ubus_record" in
                *'"device_id"'*) ubus_ok=true ;;
                *) ubus_record="" ;;
            esac
            if [ "$ubus_ok" = true ]; then
                printf '%s' "$ubus_record" > "$UBUS_RECORD_FILE" 2>/dev/null
                if [ "$UBUS_CELLS" = 1 ]; then
                    # Serving cells over the engine's AT port (AT^DEBUG?).
                    # An AT cell read every snapshot (3 s) is known to hang the
                    # T99W175, so reuse the cached answer for UBUS_CELLS_TTL
                    # seconds and only then ask the engine again. cf() reads
                    # UBUS_CELLS_FILE directly, so the cache serves both paths.
                    cell_age=$(file_age "$UBUS_CELLS_FILE" 2>/dev/null)
                    if ! is_uint "$cell_age" || [ "$cell_age" -gt "$UBUS_CELLS_TTL" ]; then
                        fresh_cells=$(run_limited ubus call medvemodem cells \
                            "{\"id\":\"$MEDVEMODEM_DEVICE\"}" 2>/dev/null)
                        [ -n "$fresh_cells" ] &&
                            printf '%s' "$fresh_cells" > "$UBUS_CELLS_FILE" 2>/dev/null
                    fi
                    ubus_cells=$(cat "$UBUS_CELLS_FILE" 2>/dev/null)
                fi
            fi
        fi
    fi
fi

# uf <jsonfilter expression> -> first value or empty string
# The record is written to a file once: jsonfilter reading a file is a single
# process, while piping it in costs three (printf + jsonfilter + head).
uf() {
    [ -n "$ubus_record" ] || return 0
    ubus_value=$(jsonfilter -i "$UBUS_RECORD_FILE" -e "$1" 2>/dev/null)
    printf '%s' "${ubus_value%%"$NL"*}"
}

# Caches must not be shared between modems: a temperature read from one modem
# must never be served for another.
if [ -n "$MEDVEMODEM_DEVICE" ]; then
    DEVICE_KEY=$(printf '%s' "$MEDVEMODEM_DEVICE" | tr -c 'A-Za-z0-9._-' '_')
    TEMP_CACHE="${TEMP_CACHE}.${DEVICE_KEY}"
    QMI_CACHE_DIR="${QMI_CACHE_DIR}.${DEVICE_KEY}"
fi

# A modem in router (HiLink) mode has no ModemManager record and no QMI/AT
# port: querying /dev/cdc-wdm would hit a different modem, so disable it.
if [ "$ubus_ok" = true ] && [ -z "$(uf '@.modemmanager.state')" ]; then
    QMI_DEVICE=""
    QMI_DETAIL=0
    QMI_EXTRA=0
fi

# Take the QMI control device from the engine record of the *selected* modem,
# not from the fixed UCI default. With two modems attached /dev/cdc-wdm0 can
# belong to the other one, and a qmicli call would then read the wrong modem.
# ports.control[] holds full /dev paths; modemmanager.ports[] lists the same
# ports by bare name, so scan it for the cdc-wdm entry as a fallback.
if [ "$ubus_ok" = true ] && [ -n "$(uf '@.modemmanager.state')" ]; then
    ubus_wdm=$(uf '@.ports.control[0]')
    if [ -z "$ubus_wdm" ]; then
        ubus_port_count=$(jsonfilter -i "$UBUS_RECORD_FILE" \
            -e '@.modemmanager.ports[*]' 2>/dev/null | wc -l)
        ubus_pi=0
        while [ "$ubus_pi" -lt "$ubus_port_count" ]; do
            ubus_port_name=$(jsonfilter -i "$UBUS_RECORD_FILE" \
                -e "@.modemmanager.ports[$ubus_pi]" 2>/dev/null)
            case "$ubus_port_name" in
                cdc-wdm*) ubus_wdm="/dev/$ubus_port_name"; break ;;
            esac
            ubus_pi=$((ubus_pi + 1))
        done
    fi
    case "$ubus_wdm" in
        /*)
            if [ -c "$ubus_wdm" ]; then
                QMI_DEVICE=$ubus_wdm
            fi
            ;;
    esac
fi

# Follow the engine's idea of the data interface: wwan0 for a QMI modem,
# usb0 for a router-mode (HiLink) modem. Traffic counters are read from it.
if [ "$ubus_ok" = true ]; then
    ubus_net=$(uf '@.session.netifd.l3_device')
    if [ -n "$ubus_net" ] && [ -d "/sys/class/net/$ubus_net" ]; then
        NET_DEVICE=$ubus_net
    fi
fi

# Temperature is cheap through medvemodem (~40 ms on T99W175) and is cached.
# Read it before the larger QMI/MM query set can consume the request deadline.
read_temperature

# When the engine record is available it already contains every slow field
# (operator, registration, manufacturer, model, firmware, signal, bearer, sim,
# CA, cells). Skip the expensive mmcli/qmicli round-trips and use the record.
# The only direct QMI call we keep is --nas-get-signal-info: it gives live
# signal values for antenna aiming while the engine samples only every ~30 s.
if [ "$ubus_ok" != true ] && command -v mmcli >/dev/null 2>&1; then
    # Key-value output is stable and does not contain ANSI color sequences.
    mm_info=$(run_limited mmcli -m "$MODEM" -K)
    mm_signal=$(run_limited mmcli -m "$MODEM" --signal-get)

    sim_path=$(mm_field 'modem.generic.sim' 'primary sim path')
    sim_index=${sim_path##*/}
    case "$sim_index" in ''|*[!0-9]*) sim_index="" ;; esac
    [ -n "$sim_index" ] && mm_sim=$(run_limited mmcli -i "$sim_index" -K)

    bearer_paths=$(kv_values "$mm_info" 'modem.generic.bearers.value[')
    [ -n "$bearer_paths" ] || bearer_paths=$(mm_field 'modem.generic.bearers.value[1]' 'paths')
    bearer_count=0
    for bearer_path in $bearer_paths; do
        bearer_count=$((bearer_count + 1))
        [ "$bearer_count" -le "$MAX_BEARERS" ] || break
        candidate_index=${bearer_path##*/}
        case "$candidate_index" in ''|*[!0-9]*) continue ;; esac
        candidate_bearer=$(run_limited mmcli -b "$candidate_index" -K)
        candidate_sample_ts=$(date +%s)
        [ -n "$mm_bearer" ] || {
            mm_bearer=$candidate_bearer
            bearer_index=$candidate_index
            bearer_sample_ts=$candidate_sample_ts
        }
        case "$(kv_field "$candidate_bearer" 'bearer.status.connected')" in
            yes|true)
                mm_bearer=$candidate_bearer
                bearer_index=$candidate_index
                bearer_sample_ts=$candidate_sample_ts
                break
                ;;
        esac
    done
fi

if command -v qmicli >/dev/null 2>&1 && [ -c "$QMI_DEVICE" ]; then
    # Live signal (antenna aiming). The engine samples every ~30 s, which is
    # too slow for aiming, so we keep one cheap QMI NAS call per tick.
    qmi_signal=$(run_limited qmicli -p -d "$QMI_DEVICE" --nas-get-signal-info)

    # Neighbour cells for the "Cells" screen. The engine owns the modem, but
    # --nas-get-cell-location-info is a read-only NAS query and the collector
    # already issues one QMI call per tick (--nas-get-signal-info). Cached:
    # snapshot_ttl is 3 s, neighbour data changes slowly. QMI_DETAIL=0 keeps
    # this off for a pure-ubus collector.
    if [ "$ubus_ok" = true ] && [ "$QMI_DETAIL" = 1 ]; then
        qmi_cell=$(qmi_cached cell 30 \
            qmicli -p -d "$QMI_DEVICE" --nas-get-cell-location-info)
    fi

    if [ "$ubus_ok" != true ]; then
        # Fallback: the engine is not available, read everything directly.
        if [ "$QMI_DETAIL" = 1 ]; then
            qmi_cell=$(run_limited qmicli -p -d "$QMI_DEVICE" --nas-get-cell-location-info)
        fi
        qmi_rf=$(qmi_cached rf "$QMI_CACHE_TTL" \
            qmicli -p -d "$QMI_DEVICE" --nas-get-rf-band-info)
        qmi_ca=$(qmi_cached ca "$QMI_CA_CACHE_TTL" \
            qmicli -p -d "$QMI_DEVICE" --nas-get-lte-cphy-ca-info)
        qmi_slots=$(qmi_cached slots "$QMI_SLOT_CACHE_TTL" \
            qmicli -p -d "$QMI_DEVICE" --uim-get-slot-status)
        if [ "$QMI_EXTRA" = 1 ]; then
            qmi_serving=$(qmi_cached serving "$QMI_CACHE_TTL" \
                qmicli -p -d "$QMI_DEVICE" --nas-get-serving-system)
            qmi_system=$(qmi_cached system "$QMI_CACHE_TTL" \
                qmicli -p -d "$QMI_DEVICE" --nas-get-system-info)
        fi
    fi
fi

rsrp=$(signal_field rsrp)
rsrq=$(signal_field rsrq)
sinr=$(signal_field sinr)
rssi=$(signal_field rssi)
# Extended ModemManager signal polling is optional. QMI NAS remains a read-only
# fallback when mmcli --signal-get has no recent LTE sample.
[ -n "$rsrp" ] || rsrp=$(qmi_signal_field 'RSRP')
[ -n "$rsrq" ] || rsrq=$(qmi_signal_field 'RSRQ')
[ -n "$sinr" ] || sinr=$(qmi_signal_field 'SNR')
[ -n "$rssi" ] || rssi=$(qmi_signal_field 'RSSI')
operator=$(mm_field 'modem.3gpp.operator-name' 'operator name')
network=$(kv_values "$mm_info" 'modem.generic.access-technologies.value[' |
    awk 'NF { if (seen++) printf ", "; printf "%s", $0 }')
[ -n "$network" ] || network=$(mm_field 'modem.generic.access-technologies.value[1]' 'access tech')

case "$(printf '%s' "$network" | tr '[:upper:]' '[:lower:]')" in
    *5gnr*lte*|*lte*5gnr*) network='5G NSA' ;;
    *5gnr*|*nr5g*|*5g*) network='5G NR' ;;
    *lte*) network='LTE' ;;
esac

[ -z "$operator" ] && operator=$(quoted_field "$qmi_serving" 'Description')

mcc=$(quoted_field "$qmi_serving" 'MCC')
mnc=$(quoted_field "$qmi_serving" 'MNC')
# ModemManager preserves the original 2/3-digit MNC in operator-code. Prefer it
# over concatenating QMI fields, where leading zeroes may be lost.
plmn=$(mm_field 'modem.3gpp.operator-code' 'operator id')
if [ -n "$plmn" ]; then
    mcc=$(printf '%s' "$plmn" | cut -c1-3)
    mnc=$(printf '%s' "$plmn" | cut -c4-)
elif [ -n "$mcc" ] && [ -n "$mnc" ]; then
    plmn="${mcc}${mnc}"
fi

registration=$(quoted_field "$qmi_serving" 'Registration state')
registration_type=$(mm_field 'modem.3gpp.registration-state' 'registration')
cs_state=$(quoted_field "$qmi_serving" 'CS')
ps_state=$(quoted_field "$qmi_serving" 'PS')
packet_service=$(mm_field 'modem.3gpp.packet-service-state' 'packet service state')
roaming_raw=$(quoted_field "$qmi_serving" 'Roaming status')
case "$roaming_raw" in on|yes|true) roaming=true ;; off|no|false) roaming=false ;; *) roaming=null ;; esac

lte_service=$(section_quoted_field "$qmi_system" 'LTE service:' 'Status')
nr_sa_service=$(section_quoted_field "$qmi_system" '5G SA service:' 'Status')
nr_nsa_raw=$(quoted_field "$qmi_system" '5G NSA Available')
ims_voice_raw=$(quoted_field "$qmi_system" 'IMS voice support')
dcnr_raw=$(quoted_field "$qmi_system" 'DCNR Restriction')
case "$nr_nsa_raw" in yes|true|available) nr_nsa=true ;; no|false|none) nr_nsa=false ;; *) nr_nsa=null ;; esac
case "$ims_voice_raw" in yes|true|available) ims_voice=true ;; no|false|none) ims_voice=false ;; *) ims_voice=null ;; esac
case "$dcnr_raw" in yes|true) dcnr_restricted=true ;; no|false) dcnr_restricted=false ;; *) dcnr_restricted=null ;; esac
cell_access=$(quoted_field "$qmi_system" 'Cell access')
# RNDIS-fallback: ModemManager не видит RNDIS-модем — сигнал/оператор/регистрация
# из движка medvemodem (AT-опрос, движок сам знает команды каждого модема).
if [ -z "$rsrp" ] && [ -z "$sinr" ]; then
    eng=$(run_limited ubus call medvemodem metrics "{"id":"$MEDVEMODEM_DEVICE","max_age_ms":60000}" 2>/dev/null)
    if [ -n "$eng" ]; then
        eng_val() { jsonfilter -s "$eng" -e "$1" 2>/dev/null; }
        v=$(eng_val '@.metrics.rsrp_dbm'); [ -n "$v" ] && rsrp=$v
        v=$(eng_val '@.metrics.rsrq_db'); [ -n "$v" ] && rsrq=$v
        v=$(eng_val '@.metrics.sinr_db'); [ -n "$v" ] && sinr=$v
        v=$(eng_val '@.metrics.rssi_dbm'); [ -n "$v" ] && rssi=$v
        v=$(eng_val '@.metrics.operator'); if [ -n "$v" ]; then
            plmn=$v
            case "$v" in
                *25020*) operator=Tele2 ;;
                *25002*) operator=МегаФон ;;
                *25001*) operator=Билайн ;;
                *25099*) operator=МТС ;;
                *) operator=$v ;;
            esac
        fi
        v=$(eng_val '@.metrics.registration.eps'); [ -n "$v" ] && registration=$v
        v=$(eng_val '@.metrics.access_technology'); [ -n "$v" ] && network=$v
    fi
fi
registration_restriction=$(quoted_field "$qmi_system" 'Registration restriction')

# RNDIS-модемы (ModemManager их не видит): сигнал/оператор/регистрация из движка
if [ -z "$rsrp" ] && [ -z "$sinr" ]; then
    eng=$(run_limited ubus call medvemodem metrics "{\"id\":\"$MEDVEMODEM_DEVICE\",\"max_age_ms\":60000}" 2>/dev/null)
    if [ -n "$eng" ]; then
        eng_val() { jsonfilter -s "$eng" -e "$1" 2>/dev/null; }
        v=$(eng_val '@.metrics.rsrp_dbm'); [ -n "$v" ] && rsrp=$v
        v=$(eng_val '@.metrics.rsrq_db'); [ -n "$v" ] && rsrq=$v
        v=$(eng_val '@.metrics.sinr_db'); [ -n "$v" ] && sinr=$v
        v=$(eng_val '@.metrics.rssi_dbm'); [ -n "$v" ] && rssi=$v
        v=$(eng_val '@.metrics.operator'); if [ -n "$v" ]; then
            plmn=$v
            case "$v" in
                *25020*) operator=Tele2 ;;
                *25002*) operator=МегаФон ;;
                *25001*) operator=Билайн ;;
                *25099*) operator=МТС ;;
                *) operator=$v ;;
            esac
        fi
        v=$(eng_val '@.metrics.registration.eps'); [ -n "$v" ] && registration=$v
        v=$(eng_val '@.metrics.access_technology'); [ -n "$v" ] && network=$v
    fi
fi

band_number=$(printf '%s\n' "$qmi_cell" | sed -n 's/.*(E-UTRA band \([0-9][0-9]*\):.*/\1/p' | head -n 1)
band=""
[ -n "$band_number" ] && band="B$band_number"
earfcn=$(printf '%s\n' "$qmi_cell" | sed -n "s/^[[:space:]]*EUTRA Absolute RF Channel Number: '\([0-9][0-9]*\)'.*/\1/p" | head -n 1)
cell_id=$(quoted_field "$qmi_cell" 'Global Cell ID')
[ -z "$cell_id" ] && cell_id=$(quoted_field "$qmi_serving" '3GPP cell ID')
pci=$(quoted_field "$qmi_cell" 'Serving Cell ID')
[ -z "$pci" ] && pci=$(ca_primary_field 'Physical Cell ID')
tac=$(quoted_field "$qmi_cell" 'Tracking Area Code')
[ -z "$tac" ] && tac=$(quoted_field "$qmi_serving" 'LTE tracking area code')

# --- serving cell from the engine record (ubus) ----------------------------
# modemmanager.ca.cells[] carries band/earfcn/pci/bandwidth per carrier; the
# primary one is the serving cell. This replaces the direct qmicli cell query
# when the engine is available (no modem transaction, no QMI round-trip).
if [ "$ubus_ok" = true ]; then
    ubus_value=$(uf '@.modemmanager.ca.cells[@.role="primary"].band')
    if [ -z "$band" ] && is_uint "$ubus_value"; then
        band_number=$ubus_value
        band="B$band_number"
    fi
    ubus_value=$(uf '@.modemmanager.ca.cells[@.role="primary"].earfcn')
    [ -z "$earfcn" ] && is_uint "$ubus_value" && earfcn=$ubus_value
    ubus_value=$(uf '@.modemmanager.ca.cells[@.role="primary"].pci')
    [ -z "$pci" ] && is_uint "$ubus_value" && pci=$ubus_value
    ubus_value=$(uf '@.modemmanager.ca.cells[@.role="primary"].bandwidth_mhz')
    [ -z "$bandwidth" ] && is_uint "$ubus_value" && bandwidth=$ubus_value
fi

# --- serving cell from the engine's AT read --------------------------------
# AT^DEBUG? through the engine reports ci/tac as hex strings; the schema keeps
# cell_id as decimal and tac as decimal text, so convert here.
if [ -z "$cell_id" ] && [ -n "$ubus_cells" ]; then
    cells_primary='@.cells[@.role="primary"]'
    cell_value=$(cf "$cells_primary.ci")
    case "$cell_value" in
        ''|*[!0-9A-Fa-f]*) ;;
        *) cell_id=$(printf '%d' "0x$cell_value" 2>/dev/null) ;;
    esac
    cell_value=$(cf "$cells_primary.tac")
    case "$cell_value" in
        ''|*[!0-9A-Fa-f]*) ;;
        *) tac=$(printf '%d' "0x$cell_value" 2>/dev/null) ;;
    esac
    cell_value=$(cf "$cells_primary.band")
    if is_uint "$cell_value"; then
        band_number=$cell_value
        band="B$band_number"
    fi
    cell_value=$(cf "$cells_primary.earfcn")
    is_uint "$cell_value" && earfcn=$cell_value
    cell_value=$(cf "$cells_primary.pci")
    is_uint "$cell_value" && pci=$cell_value
    cell_value=$(cf "$cells_primary.bandwidth")
    is_uint "$cell_value" && [ -z "$bandwidth" ] && bandwidth=$cell_value
fi

ue_idle=$(quoted_field "$qmi_cell" 'UE In Idle')
case "$ue_idle" in yes|true) ue_idle_json=true ;; no|false) ue_idle_json=false ;; *) ue_idle_json=null ;; esac
timing_advance=$(numeric_value "$(quoted_field "$qmi_cell" 'LTE Timing Advance')")
bandwidth=$(bandwidth_value "$(quoted_field "$qmi_rf" 'Bandwidth')")
# Bandwidth is often missing from the QMI path; the AT cell read has it.
if [ -z "$bandwidth" ] && [ -n "$ubus_cells" ]; then
    cell_value=$(cf '@.cells[@.role="primary"].bandwidth')
    is_uint "$cell_value" && bandwidth=$cell_value
fi
# Pure-ubus mode: take bandwidth straight from the engine CA record.
if [ -z "$bandwidth" ] && [ "$ubus_ok" = true ]; then
    ubus_value=$(uf '@.modemmanager.ca.cells[@.role="primary"].bandwidth_mhz')
    is_uint "$ubus_value" && bandwidth=$ubus_value
fi
frequency_mhz=$(lte_frequency_mhz "$band_number" "$earfcn")
case "$(printf '%s\n' "$qmi_cell" | sed -n 's/.*(E-UTRA band [0-9][0-9]*: \([^)]*\)).*/\1/p' | head -n 1)" in
    TD*) duplex='TDD' ;;
    '') duplex='' ;;
    *) duplex='FDD' ;;
esac
# No QMI cell text (pure-ubus mode): derive duplex from the band number.
if [ -z "$duplex" ] && [ -n "$band_number" ]; then
    case "$band_number" in
        33|34|35|36|37|38|39|40|41|42|43|44|45) duplex='TDD' ;;
        *) duplex='FDD' ;;
    esac
fi

enodeb=""
sector=""
case "$cell_id" in
    ''|*[!0-9]*) ;;
    *) enodeb=$((cell_id / 256)); sector=$((cell_id % 256)) ;;
esac

ca_active=null
if printf '%s\n' "$qmi_ca" | grep -q 'No Secondary Cells'; then
    ca_active=false
elif printf '%s\n' "$qmi_ca" | grep -q "State: '\(configured-\)\?activated'"; then
    ca_active=true
elif printf '%s\n' "$qmi_ca" | grep -q "State: '\(configured-\)\?deactivated'\|State: 'deconfigured'"; then
    ca_active=false
fi

ca_primary_pci=$(numeric_value "$(ca_primary_field 'Physical Cell ID')")
ca_primary_arfcn=$(numeric_value "$(ca_primary_field 'RX Channel')")
ca_primary_bw=$(bandwidth_value "$(ca_primary_field 'DL Bandwidth')")
ca_primary_band=$(ca_primary_field 'LTE Band')
ca_primary_band=$(printf '%s' "$ca_primary_band" | sed 's/^eutran-/B/')
secondary_carriers=$(secondary_carriers_json)
case "$secondary_carriers" in
    ''|'[]') secondary_carriers=$(secondary_carriers_from_ubus) ;;
esac
neighbors=$(neighbor_cells_json)

sim_slot=$(active_slot_number "$qmi_slots")
if ! is_uint "$sim_slot"; then
    mm_primary_sim_slot=$(uint_value "$(mm_field 'modem.generic.primary-sim-slot' 'primary sim slot')")
    if is_uint "$mm_primary_sim_slot" && [ "$mm_primary_sim_slot" -gt 0 ]; then
        sim_slot=$((mm_primary_sim_slot - 1))
    fi
fi
card_status=$(active_slot_field "$qmi_slots" 'Card status')
slot_status=$(active_slot_field "$qmi_slots" 'Slot status')
logical_slot=$(numeric_value "$(active_slot_field "$qmi_slots" 'Logical slot')")
sim_protocol=$(active_slot_field "$qmi_slots" 'Protocol')
esim_raw=$(active_slot_field "$qmi_slots" 'Is eUICC')
case "$esim_raw" in yes|true) is_esim=true ;; no|false) is_esim=false ;; *) is_esim=null ;; esac
iccid=$(active_slot_field "$qmi_slots" 'ICCID')
[ -z "$iccid" ] && iccid=$(kv_field "$mm_sim" 'sim.properties.iccid')
imsi=$(kv_field "$mm_sim" 'sim.properties.imsi')
sim_operator_code=$(kv_field "$mm_sim" 'sim.properties.operator-code')
sim_operator_name=$(kv_field "$mm_sim" 'sim.properties.operator-name')
iccid_masked=$(mask_id "$iccid")
imsi_masked=$(mask_id "$imsi")

manufacturer=$(mm_field 'modem.generic.manufacturer' 'manufacturer')
model=$(mm_field 'modem.generic.model' 'model')
firmware=$(mm_field 'modem.generic.revision' 'firmware revision')
hardware_revision=$(mm_field 'modem.generic.hardware-revision' 'h/w revision')
carrier_config=$(mm_field 'modem.generic.carrier-configuration' 'carrier config')
carrier_config_revision=$(mm_field 'modem.generic.carrier-configuration-revision' 'carrier config revision')
modem_state=$(mm_field 'modem.generic.state' 'state')
power_state=$(mm_field 'modem.generic.power-state' 'power state')
signal_quality=$(mm_field 'modem.generic.signal-quality.value' 'signal quality')
signal_quality=$(numeric_value "$signal_quality")

bearer_connected=$(kv_field "$mm_bearer" 'bearer.status.connected')
case "$bearer_connected" in yes|true) bearer_connected_json=true ;; no|false) bearer_connected_json=false ;; *) bearer_connected_json=null ;; esac
bearer_interface=$(kv_field "$mm_bearer" 'bearer.status.interface')
apn=$(kv_field "$mm_bearer" 'bearer.properties.apn')
ip_type=$(kv_field "$mm_bearer" 'bearer.properties.ip-type')
ipv4_address=$(kv_field "$mm_bearer" 'bearer.ipv4-config.address')
ipv4_prefix=$(kv_field "$mm_bearer" 'bearer.ipv4-config.prefix')
ipv4_mtu=$(kv_field "$mm_bearer" 'bearer.ipv4-config.mtu')
connect_time=$(uint_value "$(kv_field "$mm_bearer" 'bearer.stats.duration')")
total_dl=$(uint_value "$(kv_field "$mm_bearer" 'bearer.stats.bytes-rx')")
total_ul=$(uint_value "$(kv_field "$mm_bearer" 'bearer.stats.bytes-tx')")
traffic_source="bearer:${bearer_index:-unknown}"

# --- ubus overlay ----------------------------------------------------------
# Values below come from the engine record (no modem I/O). They take priority
# over ModemManager text parsing because the engine already did that work and
# keeps it fresh (signal ~30 s, the rest on change).
if [ "$ubus_ok" = true ]; then
    ubus_value=$(uf '@.modemmanager.operator')
    [ -n "$ubus_value" ] && operator=$ubus_value

    ubus_value=$(uf '@.modemmanager.operator_code')
    if [ -n "$ubus_value" ]; then
        plmn=$ubus_value
        mcc=$(printf '%s' "$plmn" | cut -c1-3)
        mnc=$(printf '%s' "$plmn" | cut -c4-)
    fi

    ubus_value=$(uf '@.modemmanager.registration')
    if [ -n "$ubus_value" ]; then
        registration_type=$ubus_value
        case "$ubus_value" in
            home|roaming) registration="registered" ;;
            *) registration=$ubus_value ;;
        esac
        case "$ubus_value" in
            roaming) roaming=true ;;
            home) roaming=false ;;
        esac
    fi

    ubus_tech=$(printf '%s' "$ubus_record" |
        jsonfilter -e '@.modemmanager.access_tech[*]' 2>/dev/null |
        awk 'NF { if (seen++) printf ", "; printf "%s", $0 }')
    if [ -n "$ubus_tech" ]; then
        case "$(printf '%s' "$ubus_tech" | tr '[:upper:]' '[:lower:]')" in
            *5gnr*lte*|*lte*5gnr*) network='5G NSA' ;;
            *5gnr*|*nr5g*|*5g*) network='5G NR' ;;
            *lte*) network='LTE' ;;
        esac
    fi

    ubus_value=$(uf '@.modemmanager.manufacturer')
    [ -n "$ubus_value" ] && manufacturer=$ubus_value
    ubus_value=$(uf '@.modemmanager.model')
    [ -n "$ubus_value" ] && model=$ubus_value
    ubus_value=$(uf '@.modemmanager.firmware')
    [ -n "$ubus_value" ] && firmware=$ubus_value
    ubus_value=$(uf '@.modemmanager.state')
    [ -n "$ubus_value" ] && modem_state=$ubus_value
    ubus_value=$(uf '@.modemmanager.power_state')
    [ -n "$ubus_value" ] && power_state=$ubus_value
    ubus_value=$(uf '@.modemmanager.signal_quality')
    if is_uint "$ubus_value"; then
        signal_quality=$ubus_value
    fi

    # Signal: the engine samples it about every 30 s, which is too slow for
    # antenna aiming. QMI NAS values (live, every request) always win; the
    # engine record is only a fallback when qmicli is unavailable.
    if [ -z "$rsrp" ]; then
        ubus_value=$(uf '@.modemmanager.signal.lte.rsrp')
        [ -n "$ubus_value" ] && rsrp=$(numeric_value "$ubus_value")
    fi
    if [ -z "$rsrq" ]; then
        ubus_value=$(uf '@.modemmanager.signal.lte.rsrq')
        [ -n "$ubus_value" ] && rsrq=$(numeric_value "$ubus_value")
    fi
    if [ -z "$rssi" ]; then
        ubus_value=$(uf '@.modemmanager.signal.lte.rssi')
        [ -n "$ubus_value" ] && rssi=$(numeric_value "$ubus_value")
    fi
    if [ -z "$sinr" ]; then
        ubus_value=$(uf '@.modemmanager.signal.lte.snr')
        [ -n "$ubus_value" ] && sinr=$(numeric_value "$ubus_value")
    fi

    # NR signal from the engine (available when the modem is in 5G mode).
    # The engine exposes NR under "signal.5g" (see UBUS-API.md); older builds
    # used "signal.nr". A leading digit is not a valid jsonfilter label, so the
    # key must be quoted: @.modemmanager.signal["5g"].rsrp.
    nr_rsrp=""; nr_rsrq=""; nr_sinr=""
    ubus_value=$(uf '@.modemmanager.signal["5g"].rsrp')
    [ -n "$ubus_value" ] || ubus_value=$(uf '@.modemmanager.signal["nr"].rsrp')
    [ -n "$ubus_value" ] && nr_rsrp=$(numeric_value "$ubus_value")
    ubus_value=$(uf '@.modemmanager.signal["5g"].rsrq')
    [ -n "$ubus_value" ] || ubus_value=$(uf '@.modemmanager.signal["nr"].rsrq')
    [ -n "$ubus_value" ] && nr_rsrq=$(numeric_value "$ubus_value")
    ubus_value=$(uf '@.modemmanager.signal["5g"].snr')
    [ -n "$ubus_value" ] || ubus_value=$(uf '@.modemmanager.signal["nr"].snr')
    [ -n "$ubus_value" ] && nr_sinr=$(numeric_value "$ubus_value")

    # Antenna chains (RSRP per chain) from the engine. Prefer the LTE set; on a
    # modem that only reports NR chains, fall back to antennas.nr.chains.
    antennas_json=""
    ant_band="lte"
    antenna_count=$(jsonfilter -i "$UBUS_RECORD_FILE" -e '@.modemmanager.antennas.lte.chains[*].chain' 2>/dev/null | wc -l)
    if [ "$antenna_count" -eq 0 ]; then
        ant_band="nr"
        antenna_count=$(jsonfilter -i "$UBUS_RECORD_FILE" -e '@.modemmanager.antennas.nr.chains[*].chain' 2>/dev/null | wc -l)
    fi
    if [ "$antenna_count" -gt 0 ]; then
        aidx=0
        aout=""
        while [ "$aidx" -lt "$antenna_count" ]; do
            a_chain=$(jsonfilter -i "$UBUS_RECORD_FILE" -e "@.modemmanager.antennas.$ant_band.chains[$aidx].chain" 2>/dev/null)
            a_rsrp=$(jsonfilter -i "$UBUS_RECORD_FILE" -e "@.modemmanager.antennas.$ant_band.chains[$aidx].rsrp" 2>/dev/null)
            a_power=$(jsonfilter -i "$UBUS_RECORD_FILE" -e "@.modemmanager.antennas.$ant_band.chains[$aidx].power" 2>/dev/null)
            a_phase=$(jsonfilter -i "$UBUS_RECORD_FILE" -e "@.modemmanager.antennas.$ant_band.chains[$aidx].phase" 2>/dev/null)
            a_tuned=$(jsonfilter -i "$UBUS_RECORD_FILE" -e "@.modemmanager.antennas.$ant_band.chains[$aidx].tuned" 2>/dev/null)
            a_ecio=$(jsonfilter -i "$UBUS_RECORD_FILE" -e "@.modemmanager.antennas.$ant_band.chains[$aidx].ecio" 2>/dev/null)
            [ -n "$aout" ] && aout="$aout,"
            aout="$aout{\"chain\":$(number_or_null "$a_chain"),\"rsrp\":$(number_or_null "$a_rsrp"),\"power\":$(number_or_null "$a_power"),\"phase\":$(number_or_null "$a_phase"),\"tuned\":$(bool_or_null "$a_tuned"),\"ecio\":$(number_or_null "$a_ecio")}"
            aidx=$((aidx + 1))
        done
        antennas_json="[$aout]"
    fi

    # Signal history (5-minute ready-made series) from the engine.
    signal_history_json=""
    ubus_history=$(run_limited ubus call medvemodem signal_history \
        "{\"id\":\"$MEDVEMODEM_DEVICE\",\"range\":\"5m\"}" 2>/dev/null)
    if [ -n "$ubus_history" ]; then
        # Pass the engine JSON through unchanged — it is already well-formed.
        signal_history_json=$(printf '%s' "$ubus_history" | tr -d '\n\r' | sed 's/  */ /g')
    fi

    # RNDIS v3: SIM ICCID/IMSI (маскированные), APN, сессия, роуминг/пакетный сервис, NR NSA
    v=$(uf '@.sim.iccid'); if [ -n "$v" ]; then iccid=$v; iccid_masked=$(mask_id "$iccid"); fi
    v=$(uf '@.sim.imsi'); if [ -n "$v" ]; then imsi=$v; imsi_masked=$(mask_id "$imsi"); fi
    v=$(ip -4 addr show eth1 2>/dev/null | grep -oE 'inet [0-9./]+' | awk '{print $2}'); if [ -n "$v" ]; then ipv4_address=${v%%/*}; ipv4_prefix=${v#*/}; fi
    v=$(cat /sys/class/net/eth1/mtu 2>/dev/null); [ -n "$v" ] && ipv4_mtu=$v
    v=$(cat /sys/class/net/eth1/mtu 2>/dev/null); [ -n "$v" ] && ipv4_mtu=$v
    v=$(uf '@.devices[0].session.since'); if [ -n "$v" ]; then connect_time=$((temperature_now - v)); fi
    cgd=$(rndis_at_cmd 'AT+CGDCONT?' 2>/dev/null)
    v=$(printf '%s\n' "$cgd" | grep -oE '"[a-z0-9.\-]+"' | sed -n '3p' | tr -d '"')
    if [ -n "$v" ]; then apn=$v; else
        v=$(printf '%s\n' "$cgd" | grep -oE '"[a-z0-9.\-]+"' | sed -n '2p' | tr -d '"')
        [ -n "$v" ] && apn=$v
    fi
    case "$registration" in
        home) roaming=false; packet_service=есть ;;
        *) roaming=null; packet_service=null ;;
    esac
    case "$signal_history_json" in *nr-nsa*) nr_nsa=1 ;; esac
    ubus_value=$(uf '@.modemmanager.sim_card.operator_code')
    [ -n "$ubus_value" ] && sim_operator_code=$ubus_value
    ubus_value=$(uf '@.modemmanager.sim_card.operator_name')
    [ -n "$ubus_value" ] && sim_operator_name=$ubus_value
    ubus_value=$(uf '@.modemmanager.sim_card.iccid')
    [ -n "$ubus_value" ] && iccid=$ubus_value
    ubus_value=$(uf '@.modemmanager.sim_card.imsi')
    [ -n "$ubus_value" ] && imsi=$ubus_value
    iccid_masked=$(mask_id "$iccid")
    imsi_masked=$(mask_id "$imsi")

    ubus_value=$(uf '@.modemmanager.bearer.connected')
    case "$ubus_value" in
        true) bearer_connected_json=true ;;
        false) bearer_connected_json=false ;;
    esac
    ubus_value=$(uf '@.modemmanager.bearer.interface')
    [ -n "$ubus_value" ] && bearer_interface=$ubus_value
    ubus_value=$(uf '@.connection.apn')
    [ -n "$ubus_value" ] && apn=$ubus_value
    ubus_value=$(uf '@.modemmanager.bearer.ipv4.address')
    [ -n "$ubus_value" ] && ipv4_address=$ubus_value
    ubus_value=$(uf '@.modemmanager.bearer.ipv4.prefix')
    [ -n "$ubus_value" ] && ipv4_prefix=$ubus_value
    ubus_value=$(uf '@.modemmanager.bearer.ipv4.mtu')
    [ -n "$ubus_value" ] && ipv4_mtu=$ubus_value

    # Router-mode modems have no ModemManager bearer: take addressing and
    # counters from the session and the uplink block instead.
    if [ -z "$ipv4_address" ]; then
        ubus_value=$(uf '@.session.netifd.ipv4_addresses[0]')
        case "$ubus_value" in
            */*) ipv4_address=${ubus_value%%/*}; ipv4_prefix=${ubus_value#*/} ;;
            '') ;;
            *) ipv4_address=$ubus_value ;;
        esac
    fi
    if [ -z "$bearer_interface" ]; then
        ubus_value=$(uf '@.session.netifd.l3_device')
        [ -n "$ubus_value" ] && bearer_interface=$ubus_value
    fi
    if ! is_uint "$total_dl"; then
        ubus_value=$(uf '@.uplink.traffic.rx_bytes')
        is_uint "$ubus_value" && total_dl=$ubus_value
    fi
    if ! is_uint "$total_ul"; then
        ubus_value=$(uf '@.uplink.traffic.tx_bytes')
        is_uint "$ubus_value" && total_ul=$ubus_value
    fi

    ubus_value=$(uf '@.modemmanager.bearer.stats.duration')
    if is_uint "$ubus_value"; then
        connect_time=$ubus_value
    fi
    ubus_value=$(uf '@.modemmanager.bearer.stats.rx_bytes')
    if is_uint "$ubus_value"; then
        total_dl=$ubus_value
        sample_ts=$(date +%s)
    fi
    ubus_value=$(uf '@.modemmanager.bearer.stats.tx_bytes')
    if is_uint "$ubus_value"; then
        total_ul=$ubus_value
    fi

    # Extra blocks the schema did not have before (additive, old clients ignore).
    ca_from_ubus=$(printf '%s' "$ubus_record" |
        jsonfilter -e '@.modemmanager.ca.cells[*].pci' 2>/dev/null | head -n 1)
    if [ -n "$ca_from_ubus" ]; then
        ubus_ca_primary_pci=$(uf '@.modemmanager.ca.cells[@.role="primary"].pci')
        ubus_ca_primary_arfcn=$(uf '@.modemmanager.ca.cells[@.role="primary"].earfcn')
        ubus_ca_primary_band=$(uf '@.modemmanager.ca.cells[@.role="primary"].band')
        ubus_ca_primary_bw=$(uf '@.modemmanager.ca.cells[@.role="primary"].bandwidth_mhz')
        [ -z "$ca_primary_pci" ] && [ -n "$ubus_ca_primary_pci" ] &&
            ca_primary_pci=$(numeric_value "$ubus_ca_primary_pci")
        [ -z "$ca_primary_arfcn" ] && [ -n "$ubus_ca_primary_arfcn" ] &&
            ca_primary_arfcn=$(numeric_value "$ubus_ca_primary_arfcn")
        [ -z "$ca_primary_bw" ] && [ -n "$ubus_ca_primary_bw" ] &&
            ca_primary_bw=$(bandwidth_value "$ubus_ca_primary_bw")
        if [ -z "$ca_primary_band" ]; then
            case "$ubus_ca_primary_band" in
                ''|*[!0-9]*) ;;
                *) ca_primary_band="B$ubus_ca_primary_band" ;;
            esac
        fi
        # A configured-but-deactivated secondary must not read as active.
        # The direct QMI CA answer is live, the engine's copy lags: only use
        # the engine when the QMI one is unavailable.
        if [ -z "$qmi_ca" ]; then
            if [ -n "$(uf '@.modemmanager.ca.cells[@.role="secondary"][@.state="activated"].pci')" ]; then
                ca_active=true
            elif [ -n "$(uf '@.modemmanager.ca.cells[@.role="secondary"].pci')" ]; then
                ca_active=false
            fi
        fi
    fi

    ubus_diag_ok=$(uf '@.diagnosis.ok')
    ubus_diag_reason=$(uf '@.diagnosis.warning.reason')
    ubus_diag_detail=$(uf '@.diagnosis.warning.detail')
    ubus_quota_used=$(uf '@.quota.used_bytes')
    ubus_reach_ms=$(uf '@.uplink.reach.latency_ms')
    ubus_apn_used=$(uf '@.connection.apn')

    # The engine record already exposes manufacturer, model, firmware, state,
    # power_state, signal, bearer, sim_card, CA, diagnosis, location, and
    # access_tech. No additional mmcli call is needed when ubus is available.
fi


use_netdev_counters=false
if [ -r "/sys/class/net/$NET_DEVICE/statistics/rx_bytes" ] &&
   [ -r "/sys/class/net/$NET_DEVICE/statistics/tx_bytes" ]; then
    use_netdev_counters=true
    traffic_source="netdev:$NET_DEVICE"
    # Expose current totals even when another CGI request owns the rate-state
    # lock. The lock protects only delta calculation and state replacement.
    total_dl=$(cat "/sys/class/net/$NET_DEVICE/statistics/rx_bytes" 2>/dev/null)
    total_ul=$(cat "/sys/class/net/$NET_DEVICE/statistics/tx_bytes" 2>/dev/null)
fi
sample_ts=${bearer_sample_ts:-$(date +%s)}

# Calculate bit rates from monotonically increasing byte counters. The first
# sample, stale state, a source change, or a counter reset intentionally yields
# JSON null instead of a false traffic spike.
dl_rate=""
ul_rate=""
now=$(date +%s)
traffic_locked=false
if [ "$use_netdev_counters" = true ] || { is_uint "$total_dl" && is_uint "$total_ul"; }; then
    # mkdir is atomic on OpenWrt. If another CGI request is updating the state,
    # skip one rate sample instead of racing or delaying the response.
    if mkdir "$TRAFFIC_LOCK" 2>/dev/null; then
        printf '%s\n' "$now" > "$TRAFFIC_LOCK/ts" 2>/dev/null
        trap 'rm -f "$TRAFFIC_LOCK/ts" 2>/dev/null; rmdir "$TRAFFIC_LOCK" 2>/dev/null' EXIT
        trap 'rm -f "$TRAFFIC_LOCK/ts" 2>/dev/null; rmdir "$TRAFFIC_LOCK" 2>/dev/null; exit 1' HUP INT TERM
        traffic_locked=true

        if [ "$use_netdev_counters" = true ]; then
            total_dl=$(cat "/sys/class/net/$NET_DEVICE/statistics/rx_bytes" 2>/dev/null)
            total_ul=$(cat "/sys/class/net/$NET_DEVICE/statistics/tx_bytes" 2>/dev/null)
            sample_ts=$(date +%s)
        fi

        previous_ts=""
        previous_dl=""
        previous_ul=""
        previous_source=""
        [ -r "$TRAFFIC_STATE" ] &&
            IFS='|' read -r previous_ts previous_dl previous_ul previous_source < "$TRAFFIC_STATE"

        state_is_newer=true
        if is_uint "$previous_ts" && [ "$sample_ts" -le "$previous_ts" ]; then
            state_is_newer=false
        fi

        if [ "$state_is_newer" = true ] && is_uint "$total_dl" && is_uint "$total_ul" &&
           is_uint "$previous_ts" && is_uint "$previous_dl" && is_uint "$previous_ul" &&
           [ "$previous_source" = "$traffic_source" ]; then
            elapsed=$((sample_ts - previous_ts))
            if [ "$elapsed" -gt 0 ] && [ "$elapsed" -lt 120 ] &&
               [ "$total_dl" -ge "$previous_dl" ] && [ "$total_ul" -ge "$previous_ul" ]; then
                dl_rate=$(( (total_dl - previous_dl) * 8 / elapsed ))
                ul_rate=$(( (total_ul - previous_ul) * 8 / elapsed ))
            fi
        fi

        if [ "$state_is_newer" = true ] && is_uint "$total_dl" && is_uint "$total_ul"; then
            traffic_state_tmp="${TRAFFIC_STATE}.tmp.$$"
            if printf '%s|%s|%s|%s\n' "$sample_ts" "$total_dl" "$total_ul" "$traffic_source" > "$traffic_state_tmp" 2>/dev/null; then
                mv "$traffic_state_tmp" "$TRAFFIC_STATE" 2>/dev/null || rm -f "$traffic_state_tmp"
            fi
        fi

        rm -f "$TRAFFIC_LOCK/ts" 2>/dev/null
        rmdir "$TRAFFIC_LOCK" 2>/dev/null
        trap - EXIT HUP INT TERM
        traffic_locked=false
    fi
fi

# Some ModemManager versions omit bearer.stats.duration. OpenWrt keeps the
# connection uptime on the logical network interface, so use it as fallback.
if ! is_uint "$connect_time" && command -v ubus >/dev/null 2>&1; then
    interface_status=$(run_limited ubus call "network.interface.$MM_IFACE" status)
    if command -v jsonfilter >/dev/null 2>&1; then
        connect_time=$(printf '%s\n' "$interface_status" |
            jsonfilter -e '@.uptime' 2>/dev/null | head -n 1)
    fi
    if ! is_uint "$connect_time"; then
        connect_time=$(printf '%s\n' "$interface_status" |
            sed -n 's/.*"uptime"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' |
            head -n 1)
    fi
fi

if [ -n "$mm_info" ] || [ -n "$qmi_serving" ] || [ "$ubus_ok" = true ]; then
    online=true
    collector_error=null
else
    online=false
    collector_error='"modem_not_found"'
fi

# JSON fragment helpers. They assign J_<name> instead of printing, so the
# output heredoc needs no command substitution: every $(...) costs a fork, and
# the collector spends most of its time spawning processes, not querying.
jnum() {
    for jname in "$@"; do
        eval "jvalue=\$$jname"
        case "$jvalue" in
            *[0-9]*) ;;
            *) eval "J_$jname=null"; continue ;;
        esac
        case "$jvalue" in [+-]*) jvalue=${jvalue#?} ;; esac
        case "$jvalue" in
            ''|*[!0-9.]*|*.*.*) eval "J_$jname=null" ;;
            *) eval "J_$jname=\$$jname" ;;
        esac
    done
}

jstr() {
    for jname in "$@"; do
        eval "jvalue=\$$jname"
        if [ -z "$jvalue" ]; then
            eval "J_$jname=null"
            continue
        fi
        case "$jvalue" in
            *"$ESC"*|*"$TAB"*|*"$NL"*|*\\*|*\"*)
                jvalue=$(json_escape "$jvalue")
                ;;
        esac
        eval "J_$jname=\"\\\"\$jvalue\\\"\""
    done
}

juint() {
    for jname in "$@"; do
        eval "jvalue=\$$jname"
        if is_uint "$jvalue"; then
            eval "J_$jname=\$$jname"
        else
            eval "J_$jname=null"
        fi
    done
}

jbool() {
    for jname in "$@"; do
        eval "jvalue=\$$jname"
        case "$jvalue" in
            true|false) eval "J_$jname=$jvalue" ;;
            *) eval "J_$jname=null" ;;
        esac
    done
}


# RNDIS: движок (medvemodem) знает модем — model/firmware/SIM/bearer/трафик
if [ -z "$model" ] || [ -z "$firmware" ] || [ -z "$ipv4_address" ] || [ -z "$card_status" ]; then
    ENGD=$(jsonfilter -i "$UBUS_DEVICES_FILE" -e '@.devices[0].device_id' 2>/dev/null)
    [ -z "$ENGD" ] && ENGD="$MEDVEMODEM_DEVICE"
    if [ -n "$ENGD" ]; then
        eng=$(run_limited ubus call medvemodem metrics "{\"id\":\"$ENGD\",\"max_age_ms\":60000}" 2>/dev/null)
        if [ -n "$eng" ]; then
            ev() { jsonfilter -s "$eng" -e "$1" 2>/dev/null; }
            v=$(ev '@.metrics.model'); [ -n "$v" ] && model=$v
            v=$(ev '@.metrics.firmware'); [ -n "$v" ] && firmware=$v
            v=$(ev '@.metrics.manufacturer'); [ -n "$v" ] && manufacturer=$v
            v=$(ev '@.metrics.sim'); [ -n "$v" ] && card_status=$v
            v=$(ip -4 addr show eth1 2>/dev/null | grep -oE "inet [0-9./]+" | awk '{print $2}')
            if [ -n "$v" ]; then ipv4_address=${v%%/*}; ipv4_prefix=${v#*/}; fi
            bearer_interface=eth1
            bearer_connected_json=true
            [ -z "$total_dl" ] && v=$(cat /sys/class/net/eth1/statistics/rx_bytes 2>/dev/null) && total_dl=$v
            [ -z "$total_ul" ] && v=$(cat /sys/class/net/eth1/statistics/tx_bytes 2>/dev/null) && total_ul=$v
        fi
    fi
fi

# Precompute every JSON fragment.
if [ -n "$band$pci$cell_id$rsrp" ]; then J_lte_present=true; else J_lte_present=false; fi
case "$network" in *5G*) J_nr_active=true ;; *) J_nr_active=false ;; esac
# NR is considered present only when the engine reports a real RSRP value.
# The engine emits a partial "5g" object with rsrq/snr=0 placeholders in LTE-only
# mode; only rsrp indicates an actual 5G measurement.
if [ -n "$nr_rsrp" ]; then J_nr_present=true; else J_nr_present=false; fi
if [ "$ubus_ok" = true ]; then J_engine_source='"ubus"'; else J_engine_source='"mmcli"'; fi
jnum rsrp rsrq sinr rssi cell_id pci
jnum nr_rsrp nr_rsrq nr_sinr
jnum temp_c sim_slot earfcn frequency_mhz enodeb sector
jnum bandwidth timing_advance ca_primary_arfcn ca_primary_pci ca_primary_bw signal_quality
jnum temp_pa_c temp_skin_c temp_tsens_c logical_slot ipv4_prefix ipv4_mtu
jstr network band operator plmn registration registration_type
jstr packet_service cs_state ps_state mcc mnc lte_service
jstr nr_sa_service cell_access registration_restriction tac duplex ca_primary_band
jstr manufacturer model firmware hardware_revision carrier_config carrier_config_revision
jstr modem_state power_state temp_source temp_sensor temp_status card_status
jstr slot_status sim_protocol sim_operator_code sim_operator_name iccid_masked imsi_masked
jstr apn bearer_interface ip_type ipv4_address MEDVEMODEM_DEVICE ubus_diag_reason
jstr ubus_diag_detail
juint dl_rate ul_rate total_dl total_ul connect_time ubus_quota_used
juint ubus_reach_ms
jbool roaming nr_nsa ims_voice dcnr_restricted ue_idle_json bearer_connected_json
jbool is_esim ubus_diag_ok

cat <<EOF
{
  "schema_version": 2,
  "ts": $(date +%s),
  "online": $online,
  "error": $collector_error,
  "rsrp": ${J_rsrp},
  "rsrq": ${J_rsrq},
  "sinr": ${J_sinr},
  "rssi": ${J_rssi},
  "network": ${J_network},
  "band": ${J_band},
  "operator": ${J_operator},
  "cell_id": ${J_cell_id},
  "pci": ${J_pci},
  "ca_active": $ca_active,
  "temp_c": ${J_temp_c},
  "sim_slot": ${J_sim_slot},
  "rat": ${J_network},
  "plmn": ${J_plmn},
  "network_info": {
    "registration": ${J_registration},
    "registration_type": ${J_registration_type},
    "packet_service": ${J_packet_service},
    "cs": ${J_cs_state},
    "ps": ${J_ps_state},
    "roaming": ${J_roaming},
    "mcc": ${J_mcc},
    "mnc": ${J_mnc},
    "plmn": ${J_plmn},
    "lte_service": ${J_lte_service},
    "nr_nsa_available": ${J_nr_nsa},
    "nr_sa_service": ${J_nr_sa_service},
    "ims_voice": ${J_ims_voice},
    "dcnr_restricted": ${J_dcnr_restricted},
    "cell_access": ${J_cell_access},
    "registration_restriction": ${J_registration_restriction}
  },
  "lte": {
    "present": ${J_lte_present},
    "rsrp": ${J_rsrp},
    "rsrq": ${J_rsrq},
    "sinr": ${J_sinr},
    "rssi": ${J_rssi},
    "band": ${J_band},
    "earfcn": ${J_earfcn},
    "freq_mhz": ${J_frequency_mhz},
    "pci": ${J_pci},
    "cell_id": ${J_cell_id},
    "enodeb": ${J_enodeb},
    "sector": ${J_sector},
    "tac": ${J_tac},
    "bw_dl_mhz": ${J_bandwidth},
    "bw_ul_mhz": null,
    "duplex": ${J_duplex},
    "timing_advance_us": ${J_timing_advance},
    "ue_idle": ${J_ue_idle_json}
  },
  "nr": {
    "present": ${J_nr_present},
    "active": ${J_nr_active},
    "nsa_available": ${J_nr_nsa},
    "sa_service": ${J_nr_sa_service},
    "rsrp": ${J_nr_rsrp},
    "rsrq": ${J_nr_rsrq},
    "sinr": ${J_nr_sinr}
  },
  "ca": {
    "active": $ca_active,
    "primary": {
      "index": 0,
      "band": ${J_ca_primary_band},
      "arfcn": ${J_ca_primary_arfcn},
      "pci": ${J_ca_primary_pci},
      "bw_dl_mhz": ${J_ca_primary_bw},
      "state": "primary"
    },
    "secondaries": $secondary_carriers
  },
  "neighbors": $neighbors,
  "device": {
    "manufacturer": ${J_manufacturer},
    "model": ${J_model},
    "firmware": ${J_firmware},
    "hardware_revision": ${J_hardware_revision},
    "carrier_config": ${J_carrier_config},
    "carrier_config_revision": ${J_carrier_config_revision},
    "state": ${J_modem_state},
    "power_state": ${J_power_state},
    "signal_quality_percent": ${J_signal_quality},
    "temp_c": ${J_temp_c},
    "temp_source": ${J_temp_source},
    "temp_sensor": ${J_temp_sensor},
    "temp_status": ${J_temp_status},
    "temp_pa_c": ${J_temp_pa_c},
    "temp_skin_c": ${J_temp_skin_c},
    "temp_tsens_c": ${J_temp_tsens_c},
    "data_enabled": ${J_bearer_connected_json},
    "sim_slot": ${J_sim_slot}
  },
  "sim": {
    "slot": ${J_sim_slot},
    "card_status": ${J_card_status},
    "slot_status": ${J_slot_status},
    "logical_slot": ${J_logical_slot},
    "protocol": ${J_sim_protocol},
    "is_esim": ${J_is_esim},
    "operator_code": ${J_sim_operator_code},
    "operator_name": ${J_sim_operator_name},
    "iccid_masked": ${J_iccid_masked},
    "imsi_masked": ${J_imsi_masked}
  },
  "bearer": {
    "connected": ${J_bearer_connected_json},
    "apn": ${J_apn},
    "interface": ${J_bearer_interface},
    "ip_type": ${J_ip_type},
    "ipv4_address": ${J_ipv4_address},
    "ipv4_prefix": ${J_ipv4_prefix},
    "ipv4_mtu": ${J_ipv4_mtu}
  },
  "traffic": {
    "dl_rate": ${J_dl_rate},
    "ul_rate": ${J_ul_rate},
    "total_dl": ${J_total_dl},
    "total_ul": ${J_total_ul},
    "connect_time": ${J_connect_time}
  },
  "engine": {
    "source": ${J_engine_source},
    "device_id": ${J_MEDVEMODEM_DEVICE},
    "diagnosis_ok": ${J_ubus_diag_ok},
    "diagnosis_reason": ${J_ubus_diag_reason},
    "diagnosis_detail": ${J_ubus_diag_detail},
    "quota_used_bytes": ${J_ubus_quota_used},
    "reach_latency_ms": ${J_ubus_reach_ms}
  },
  "antennas": ${antennas_json:-null},
  "signal_history": ${signal_history_json:-null}
}
EOF
