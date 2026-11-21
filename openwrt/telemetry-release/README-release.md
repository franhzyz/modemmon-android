# ModemMon telemetry — release assets

`modem-telemetry-2.6.1-r0.apk` is **our** OpenWrt package. It provides  
`/cgi-bin/modem-telemetry` (schema v2) for the ModemMon Android/iOS apps.

The APK metadata is `arch=noarch`: the same package works on  
`aarch64_cortex-a53`, `aarch64_generic` and `x86_64`, on OpenWrt 24.10 and 25.12.  
The archives named for a specific architecture are compatibility aliases: each  
contains the same `noarch` APK, so users can pick the archive that matches their  
router without guessing.

These files are **not** `medvemodem`. The `medvemodem-*.tar.gz` archives in the  
existing GitHub releases contain Medvedolog's engine packages  
(`medvemodem`, `luci-app-medvemodem`, `medvemodem-bot`, lpac/uqmi) and must keep  
that name.

Install directly:

```sh
apk add --allow-untrusted ./modem-telemetry-2.6.1-r0.apk
```

Verify:

```sh
sha256sum -c SHA256SUMS.txt
```

