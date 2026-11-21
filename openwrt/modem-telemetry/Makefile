include $(TOPDIR)/rules.mk

PKG_NAME:=modem-telemetry
PKG_VERSION:=2.7.0
PKG_RELEASE:=1
PKG_MAINTAINER:=franhzyz
PKG_LICENSE:=MIT
PKGARCH:=all

include $(INCLUDE_DIR)/package.mk

define Package/modem-telemetry
  SECTION:=net
  CATEGORY:=Network
  TITLE:=Modem telemetry collector for ModemMon (RNDIS support)
  DEPENDS:=+medvemodem
endef

define Package/modem-telemetry/description
  Collects modem telemetry (signal, operator, registration, SIM, model,
  firmware, temperature) through the MedveModem engine. Works with RNDIS
  modems (Fibocom FM350-GL and similar) invisible to ModemManager.
endef

define Build/Prepare
	mkdir -p $(PKG_BUILD_DIR)
endef

define Build/Compile
endef

define Package/modem-telemetry/install
	$(INSTALL_DIR) $(1)/usr/bin
	$(INSTALL_BIN) ./files/usr/bin/modem-telemetry.sh $(1)/usr/bin/
	$(INSTALL_DIR) $(1)/etc/init.d
	$(INSTALL_DATA) ./files/etc/init.d/modem-telemetry $(1)/etc/init.d/
	$(INSTALL_DIR) $(1)/etc/config
	$(INSTALL_CONF) ./files/etc/config/modem-telemetry $(1)/etc/config/
endef

$(eval $(call BuildPackage,modem-telemetry))
