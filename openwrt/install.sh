#!/bin/sh
# Установка коллектора на OpenWrt.
# Запуск: sh install.sh   (из каталога openwrt/)

set -e

echo "== ModemMon: установка коллектора =="

# 1. ModemManager
if ! command -v mmcli >/dev/null 2>&1; then
    echo "-- mmcli не найден, ставлю ModemManager"
    opkg update
    opkg install modemmanager umbim
    /etc/init.d/modemmanager enable
    /etc/init.d/modemmanager start
else
    echo "-- mmcli уже есть"
fi

# 2. CGI на uhttpd
if ! uci get uhttpd.main.cgi_prefix >/dev/null 2>&1; then
    echo "-- включаю CGI в uhttpd"
    uci set uhttpd.main.cgi_prefix='/cgi-bin'
    uci set uhttpd.main.interpreter='.cgi=/bin/sh'
    uci commit uhttpd
    /etc/init.d/uhttpd restart
fi

# 3. Файлы
mkdir -p /www/cgi-bin
cp -f modem-collector.sh /usr/bin/modem-collector.sh
cp -f cgi-bin/modem /www/cgi-bin/modem
chmod 755 /usr/bin/modem-collector.sh /www/cgi-bin/modem

# 4. Конфиг (правим под себя: IFACE, MODEM, AT_PORT)
if [ ! -f /etc/modemmon.conf ]; then
    cat > /etc/modemmon.conf <<'EOF'
# ModemMon collector settings
MODEM=any
IFACE=wwan0
# AT_PORT=/dev/wwan0at0
EOF
    chmod 644 /etc/modemmon.conf
fi

# 5. Частый опрос сигнала — чтобы график в приложении был живым
mmcli -m any --signal-setup=5 >/dev/null 2>&1 || true

# 6. Проверка
echo "-- проверка вывода:"
/usr/bin/modem-collector.sh | head -20

echo
echo "Готово. В приложении укажите:"
echo "  адрес: $(uci get network.lan.ipaddr 2>/dev/null || echo 192.168.1.1)"
echo "  путь:  /cgi-bin/modem"
echo
echo "Проверка из браузера: http://<ip-роутера>/cgi-bin/modem"
