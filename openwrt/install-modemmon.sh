#!/bin/sh
# ModemMon: установка пакетов medvemodem на OpenWrt (24.10 / 25.12)
# Запускать на самом роутере (SSH): sh install-modemmon.sh
# Пакеты: https://github.com/Medvedolog/medvemodem
set -e

TAG="v0.16.0-alpha1-r1"
VER_PKG="0.16.0-alpha1-r1"
BASE="https://github.com/Medvedolog/medvemodem/releases/download/$TAG"

. /etc/openwrt_release 2>/dev/null || { echo "Это не OpenWrt?"; exit 1; }
ARCH="$DISTRIB_ARCH"
REL="$DISTRIB_RELEASE"

# Версия OpenWrt -> набор пакетов из релиза
case "$REL" in
    24.10*)  VER="24.10.8" ;;
    25.12*)  VER="25.12.6" ;;
    *) echo "Версия OpenWrt '$REL' не покрыта релизом (24.10/25.12)."
       echo "Скачай tar.gz вручную: $BASE"; exit 1 ;;
esac

# Архитектура должна совпадать с собранными пакетами
case "$ARCH" in
    aarch64_cortex-a53|aarch64_generic|x86_64) ;;
    *) echo "Архитектура '$ARCH' не покрыта релизом."
       echo "Скачай tar.gz вручную: $BASE"; exit 1 ;;
esac

TARBALL="medvemodem-$VER_PKG-$ARCH-$VER.tar.gz"
URL="$BASE/$TARBALL"

echo "Архитектура: $ARCH, OpenWrt: $VER"
echo "Скачиваю: $URL"
cd /tmp
if ! (curl -sL "$URL" -o medve.tar.gz 2>/dev/null || uclient-fetch "$URL" -O medve.tar.gz 2>/dev/null || wget -O medve.tar.gz "$URL"); then
    echo "Ошибка скачивания. Проверь связь с github.com"; exit 1
fi

mkdir -p medve
tar -xzf medve.tar.gz -C medve
echo "Устанавливаю пакеты:"
ls medve/*.apk
apk add --allow-untrusted medve/*.apk

rm -rf medve medve.tar.gz
echo ""
echo "Готово. Дальше: LuCI -> Сервисы -> medvemodem."
echo "ModemMon (приложение) подключай к этому роутеру по ubus."
