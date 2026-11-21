package ru.franh.modemmon.data

import org.json.JSONArray
import org.json.JSONObject

/** Одно радиозвено: LTE-якорь или 5G NR. */
data class Link(
    val present: Boolean = false,
    val rsrp: Double? = null,
    val rsrq: Double? = null,
    val sinr: Double? = null,
    val rssi: Double? = null,
    val band: String = "",
    val arfcn: Int? = null,
    val freqMhz: Double? = null,
    val pci: Int? = null,
    val cellId: Long? = null,
    val enodeb: Int? = null,
    val sector: Int? = null,
    val tac: String = "",
    val bwDl: String = "",
    val bwUl: String = "",
    val duplex: String = "",
    val timingAdvanceUs: Double? = null,
    val ueIdle: Boolean? = null
)

data class NetworkInfo(
    val registration: String = "",
    val registrationType: String = "",
    val packetService: String = "",
    val cs: String = "",
    val ps: String = "",
    val roaming: Boolean? = null,
    val mcc: String = "",
    val mnc: String = "",
    val lteService: String = "",
    val nrNsaAvailable: Boolean? = null,
    val nrSaService: String = "",
    val imsVoice: Boolean? = null,
    val dcnrRestricted: Boolean? = null,
    val cellAccess: String = "",
    val registrationRestriction: String = ""
)

data class Carrier(
    val index: Int? = null,
    val primary: Boolean = false,
    val band: String = "",
    val arfcn: Int? = null,
    val freqMhz: Double? = null,
    val pci: Int? = null,
    val bwDlMhz: Double? = null,
    val state: String = ""
) {
    val present: Boolean
        get() = band.isNotBlank() || arfcn != null || pci != null || bwDlMhz != null
}

data class NeighborCell(
    val rat: String = "LTE",
    val relation: String = "",
    val band: String = "",
    val arfcn: Int? = null,
    val pci: Int? = null,
    val rsrp: Double? = null,
    val rsrq: Double? = null,
    val rssi: Double? = null
)

data class Traffic(
    val dlRate: Long? = null,
    val ulRate: Long? = null,
    val totalDl: Long? = null,
    val totalUl: Long? = null,
    val connectTime: Long? = null
)

data class Device(
    val manufacturer: String = "",
    val model: String = "",
    val firmware: String = "",
    val hardwareRevision: String = "",
    val carrierConfig: String = "",
    val carrierConfigRevision: String = "",
    val state: String = "",
    val powerState: String = "",
    val signalQualityPercent: Int? = null,
    val temp: String = "",
    val tempSource: String = "",
    val tempSensor: String = "",
    val tempStatus: String = "",
    val tempPa: String = "",
    val tempSkin: String = "",
    val tempTsens: String = "",
    val uptime: Long? = null,
    val dataEnabled: Boolean? = null,
    val simState: String = "",
    val simSlot: Int? = null
)

data class SimInfo(
    val slot: Int? = null,
    val cardStatus: String = "",
    val slotStatus: String = "",
    val logicalSlot: Int? = null,
    val protocol: String = "",
    val isEsim: Boolean? = null,
    val operatorCode: String = "",
    val operatorName: String = "",
    val iccidMasked: String = "",
    val imsiMasked: String = ""
)

data class BearerInfo(
    val connected: Boolean? = null,
    val apn: String = "",
    val interfaceName: String = "",
    val ipType: String = "",
    val ipv4Address: String = "",
    val ipv4Prefix: Int? = null,
    val ipv4Mtu: Int? = null
)

/** Одна антенная цепь (MIMO): RSRP/power по каждому входу модема. */
data class AntennaChain(
    val chain: Int? = null,
    val rsrp: Double? = null,
    val power: Double? = null,
    val phase: Double? = null,
    val tuned: Boolean? = null,
    val ecio: Double? = null
)

/** Точка истории сигнала, посчитанная движком MedveModem. */
data class HistoryPoint(
    val t: Long = 0L,
    val lteRsrp: Double? = null,
    val lteRsrq: Double? = null,
    val lteSinr: Double? = null,
    val nrRsrp: Double? = null,
    val nrRsrq: Double? = null,
    val nrSinr: Double? = null,
    val quality: Int? = null,
    val tech: String = ""
)

/** Диагностика движка MedveModem: ok/reason/detail/предупреждение. */
data class EngineInfo(
    val source: String = "",
    val deviceId: String = "",
    val diagnosisOk: Boolean? = null,
    val diagnosisReason: String = "",
    val diagnosisDetail: String = "",
    val warningReason: String = "",
    val warningDetail: String = "",
    val quotaUsedBytes: Long? = null,
    val reachLatencyMs: Long? = null
)

/**
 * Снимок состояния модема. Парсер понимает старую плоскую schema v1,
 * расширенную schema v2 и прежнюю вложенную схему /cgi-bin/modem.
 */
data class ModemSnapshot(
    val schemaVersion: Int = 0,
    val ts: Long = 0L,
    val online: Boolean = false,
    val error: String? = null,
    val rat: String = "",
    val operator: String = "",
    val plmn: String = "",
    val caActive: Boolean? = null,
    val networkInfo: NetworkInfo = NetworkInfo(),
    val lte: Link = Link(),
    val nr: Link = Link(),
    val caPrimary: Carrier? = null,
    val caSecondaries: List<Carrier> = emptyList(),
    val neighbors: List<NeighborCell> = emptyList(),
    val traffic: Traffic = Traffic(),
    val device: Device = Device(),
    val sim: SimInfo = SimInfo(),
    val bearer: BearerInfo = BearerInfo(),
    val engine: EngineInfo = EngineInfo(),
    val antennas: List<AntennaChain> = emptyList(),
    val signalHistory: List<HistoryPoint> = emptyList()
) {
    val hasNr: Boolean get() = nr.present || rat.contains("5G", ignoreCase = true)

    companion object {

        fun offline(error: String?): ModemSnapshot =
            ModemSnapshot(ts = System.currentTimeMillis(), online = false, error = error)

        fun parse(text: String): ModemSnapshot {
            val root = JSONObject(text)
            return when (root.optInt("schema_version", 0)) {
                1 -> parseSchemaV1(root)
                else -> parseNested(root)
            }
        }

        private fun parseNested(root: JSONObject): ModemSnapshot {
            val schemaVersion = root.optInt("schema_version", 0)
            val lteObj = root.optJSONObject("lte")
            val nrObj = root.optJSONObject("nr")
            val networkObj = root.optJSONObject("network_info")
            val caObj = root.optJSONObject("ca")
            val trObj = root.optJSONObject("traffic")
            val devObj = root.optJSONObject("device")
            val simObj = root.optJSONObject("sim")
            val bearerObj = root.optJSONObject("bearer")
            val hist = parseHistory(root.optJSONObject("signal_history"))

            val lteRaw = parseLink(lteObj, root, "lte_")
            val histLast = hist.lastOrNull()
            val lte = if (lteRaw?.rsrp == null && histLast?.lteRsrp != null) {
                lteRaw.copy(present = true, rsrp = histLast.lteRsrp, rsrq = histLast.lteRsrq, sinr = histLast.lteSinr)
            } else lteRaw
            val nr = parseLink(nrObj, root, "nr_")
            val tempC = devObj?.num("temp_c") ?: root.num("temp_c")
            val explicitTemp = devObj?.str("temp").orEmpty().ifBlank { root.str("temp") }
            val temperature = explicitTemp.ifBlank { formatTemperature(tempC) }
            val simSlot = simObj?.num("slot")?.toInt()
                ?: devObj?.num("sim_slot")?.toInt()
                ?: root.num("sim_slot")?.toInt()

            val primary = caObj?.optJSONObject("primary")?.let { parseCarrier(it, primary = true) }
                ?.takeIf { it.present }
            val secondaries = parseCarriers(caObj?.optJSONArray("secondaries"))
            val neighbors = parseNeighbors(root.optJSONArray("neighbors"))

            val onlineFlag = root.bool("online") ?: true
            val network = root.str("rat")
                .ifBlank { root.str("network") }
                .ifBlank { detectRat(lte, nr) }
            val plmn = root.str("plmn").ifBlank { networkObj?.str("plmn").orEmpty() }

            return ModemSnapshot(
                schemaVersion = schemaVersion,
                ts = normalizeTimestamp(root.long("ts")),
                online = onlineFlag,
                error = root.str("error").ifBlank { null },
                rat = if (network.isNullOrBlank()) histLast?.tech ?: "" else network,
                operator = root.str("operator").ifBlank { root.str("network_operator") },
                plmn = plmn,
                caActive = caObj?.bool("active") ?: root.bool("ca_active"),
                networkInfo = NetworkInfo(
                    registration = networkObj?.str("registration").orEmpty(),
                    registrationType = networkObj?.str("registration_type").orEmpty(),
                    packetService = networkObj?.str("packet_service").orEmpty(),
                    cs = networkObj?.str("cs").orEmpty(),
                    ps = networkObj?.str("ps").orEmpty(),
                    roaming = networkObj?.bool("roaming"),
                    mcc = networkObj?.str("mcc").orEmpty(),
                    mnc = networkObj?.str("mnc").orEmpty(),
                    lteService = networkObj?.str("lte_service").orEmpty(),
                    nrNsaAvailable = networkObj?.bool("nr_nsa_available"),
                    nrSaService = networkObj?.str("nr_sa_service").orEmpty(),
                    imsVoice = networkObj?.bool("ims_voice"),
                    dcnrRestricted = networkObj?.bool("dcnr_restricted"),
                    cellAccess = networkObj?.str("cell_access").orEmpty(),
                    registrationRestriction = networkObj?.str("registration_restriction").orEmpty()
                ),
                lte = lte,
                nr = nr,
                caPrimary = primary,
                caSecondaries = secondaries,
                neighbors = neighbors,
                traffic = Traffic(
                    dlRate = trObj?.long("dl_rate") ?: root.long("dl_rate"),
                    ulRate = trObj?.long("ul_rate") ?: root.long("ul_rate"),
                    totalDl = trObj?.long("total_dl") ?: root.long("total_dl"),
                    totalUl = trObj?.long("total_ul") ?: root.long("total_ul"),
                    connectTime = trObj?.long("connect_time") ?: root.long("connect_time")
                ),
                device = Device(
                    manufacturer = devObj?.str("manufacturer").orEmpty(),
                    model = devObj?.str("model").orEmpty().ifBlank { root.str("model") },
                    firmware = devObj?.str("firmware").orEmpty().ifBlank { root.str("firmware") },
                    hardwareRevision = devObj?.str("hardware_revision").orEmpty(),
                    carrierConfig = devObj?.str("carrier_config").orEmpty(),
                    carrierConfigRevision = devObj?.str("carrier_config_revision").orEmpty(),
                    state = devObj?.str("state").orEmpty(),
                    powerState = devObj?.str("power_state").orEmpty(),
                    signalQualityPercent = devObj?.num("signal_quality_percent")?.toInt(),
                    temp = temperature,
                    tempSource = devObj?.str("temp_source").orEmpty(),
                    tempSensor = devObj?.str("temp_sensor").orEmpty(),
                    tempStatus = devObj?.str("temp_status").orEmpty(),
                    tempPa = formatTemperature(devObj?.num("temp_pa_c")),
                    tempSkin = formatTemperature(devObj?.num("temp_skin_c")),
                    tempTsens = formatTemperature(devObj?.num("temp_tsens_c")),
                    uptime = devObj?.long("uptime") ?: root.long("uptime"),
                    dataEnabled = devObj?.bool("data_enabled") ?: root.bool("data_enabled"),
                    simState = devObj?.str("sim_state").orEmpty().ifBlank {
                        simSlot?.let { "слот ${it + 1}" }.orEmpty()
                    },
                    simSlot = simSlot
                ),
                sim = SimInfo(
                    slot = simSlot,
                    cardStatus = simObj?.str("card_status").orEmpty(),
                    slotStatus = simObj?.str("slot_status").orEmpty(),
                    logicalSlot = simObj?.num("logical_slot")?.toInt(),
                    protocol = simObj?.str("protocol").orEmpty(),
                    isEsim = simObj?.bool("is_esim"),
                    operatorCode = simObj?.str("operator_code").orEmpty(),
                    operatorName = simObj?.str("operator_name").orEmpty(),
                    iccidMasked = simObj?.str("iccid_masked").orEmpty(),
                    imsiMasked = simObj?.str("imsi_masked").orEmpty()
                ),
                bearer = BearerInfo(
                    connected = bearerObj?.bool("connected"),
                    apn = bearerObj?.str("apn").orEmpty(),
                    interfaceName = bearerObj?.str("interface").orEmpty(),
                    ipType = bearerObj?.str("ip_type").orEmpty(),
                    ipv4Address = bearerObj?.str("ipv4_address").orEmpty(),
                    ipv4Prefix = bearerObj?.num("ipv4_prefix")?.toInt(),
                    ipv4Mtu = bearerObj?.num("ipv4_mtu")?.toInt()
                ),
                engine = parseEngine(root.optJSONObject("engine")),
                antennas = parseAntennas(root.optJSONArray("antennas")),
                signalHistory = hist
            )
        }

        private fun parseSchemaV1(root: JSONObject): ModemSnapshot {
            val network = root.str("network")
            val link = Link(
                present = listOf("rsrp", "rsrq", "sinr", "rssi", "band", "pci", "cell_id")
                    .any { root.has(it) && !root.isNull(it) },
                rsrp = root.num("rsrp"),
                rsrq = root.num("rsrq"),
                sinr = root.num("sinr"),
                rssi = root.num("rssi"),
                band = root.str("band"),
                pci = root.num("pci")?.toInt(),
                cellId = root.long("cell_id")
            )
            val isNrOnly = network.contains("5G", ignoreCase = true) &&
                !network.contains("NSA", ignoreCase = true)
            val temperature = formatTemperature(root.num("temp_c"))
            val simSlot = root.num("sim_slot")?.toInt()

            return ModemSnapshot(
                schemaVersion = 1,
                ts = normalizeTimestamp(root.long("ts")),
                online = root.bool("online") ?: true,
                error = root.str("error").ifBlank { null },
                rat = network,
                operator = root.str("operator"),
                caActive = root.bool("ca_active"),
                lte = if (isNrOnly) Link() else link,
                nr = if (isNrOnly) link else Link(),
                device = Device(
                    temp = temperature,
                    simState = simSlot?.let { "слот ${it + 1}" }.orEmpty(),
                    simSlot = simSlot
                ),
                sim = SimInfo(slot = simSlot)
            )
        }

        private fun parseLink(obj: JSONObject?, root: JSONObject, prefix: String): Link {
            val src = obj ?: root
            val hasOwn = obj != null
            fun d(key: String): Double? = src.num(key) ?: if (!hasOwn) root.num(prefix + key) else null
            fun i(key: String): Int? = d(key)?.toInt()
            fun l(key: String): Long? = d(key)?.toLong()
            fun s(key: String): String = src.str(key).ifBlank {
                if (!hasOwn) root.str(prefix + key) else ""
            }

            val link = Link(
                rsrp = d("rsrp"),
                rsrq = d("rsrq"),
                sinr = d("sinr"),
                rssi = d("rssi"),
                band = s("band"),
                arfcn = i("arfcn") ?: i("earfcn"),
                freqMhz = d("freq_mhz"),
                pci = i("pci"),
                cellId = l("cell_id"),
                enodeb = i("enodeb"),
                sector = i("sector"),
                tac = s("tac"),
                bwDl = s("bw_dl_mhz").ifBlank { s("bw_dl") },
                bwUl = s("bw_ul_mhz").ifBlank { s("bw_ul") },
                duplex = s("duplex"),
                timingAdvanceUs = d("timing_advance_us"),
                ueIdle = src.bool("ue_idle")
            )
            val explicitPresent = src.bool("present")
            val detected = link.rsrp != null || link.sinr != null || link.rsrq != null ||
                link.rssi != null || link.band.isNotBlank() || link.pci != null ||
                link.cellId != null || link.arfcn != null
            return link.copy(present = explicitPresent ?: detected)
        }

        private fun parseCarrier(obj: JSONObject, primary: Boolean): Carrier = Carrier(
            index = obj.num("index")?.toInt(),
            primary = primary,
            band = obj.str("band"),
            arfcn = obj.num("arfcn")?.toInt(),
            freqMhz = obj.num("freq_mhz"),
            pci = obj.num("pci")?.toInt(),
            bwDlMhz = obj.num("bw_dl_mhz"),
            state = obj.str("state")
        )

        private fun parseCarriers(array: JSONArray?): List<Carrier> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val carrier = parseCarrier(obj, primary = false)
                    if (carrier.present) add(carrier)
                }
            }
        }

        private fun parseNeighbors(array: JSONArray?): List<NeighborCell> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        NeighborCell(
                            rat = obj.str("rat").ifBlank { "LTE" },
                            relation = obj.str("relation"),
                            band = obj.str("band"),
                            arfcn = obj.num("arfcn")?.toInt(),
                            pci = obj.num("pci")?.toInt(),
                            rsrp = obj.num("rsrp"),
                            rsrq = obj.num("rsrq"),
                            rssi = obj.num("rssi")
                        )
                    )
                }
            }
        }

        private fun detectRat(lte: Link, nr: Link): String = when {
            nr.present && lte.present -> "5G NSA"
            nr.present -> "5G SA"
            lte.present -> if (lte.band.contains("+")) "LTE+ CA" else "LTE"
            else -> ""
        }

        private fun parseEngine(obj: JSONObject?): EngineInfo {
            if (obj == null) return EngineInfo()
            return EngineInfo(
                source = obj.str("source"),
                deviceId = obj.str("device_id"),
                diagnosisOk = obj.bool("diagnosis_ok"),
                diagnosisReason = obj.str("diagnosis_reason"),
                diagnosisDetail = obj.str("diagnosis_detail"),
                warningReason = obj.str("warning_reason"),
                warningDetail = obj.str("warning_detail"),
                quotaUsedBytes = obj.long("quota_used_bytes"),
                reachLatencyMs = obj.long("reach_latency_ms")
            )
        }

        private fun parseAntennas(array: JSONArray?): List<AntennaChain> {
            if (array == null) return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        AntennaChain(
                            chain = obj.num("chain")?.toInt(),
                            rsrp = obj.num("rsrp"),
                            power = obj.num("power"),
                            phase = obj.num("phase"),
                            tuned = obj.bool("tuned"),
                            ecio = obj.num("ecio")
                        )
                    )
                }
            }
        }

        private fun parseHistory(obj: JSONObject?): List<HistoryPoint> {
            val points = obj?.optJSONArray("points") ?: return emptyList()
            return buildList {
                for (i in 0 until points.length()) {
                    val p = points.optJSONObject(i) ?: continue
                    add(
                        HistoryPoint(
                            t = normalizeTimestamp(p.long("t")),
                            lteRsrp = p.num("lte_rsrp"),
                            lteRsrq = p.num("lte_rsrq"),
                            lteSinr = p.num("lte_sinr"),
                            nrRsrp = p.num("nr_rsrp"),
                            nrRsrq = p.num("nr_rsrq"),
                            nrSinr = p.num("nr_sinr"),
                            quality = p.num("quality")?.toInt(),
                            tech = p.str("tech")
                        )
                    )
                }
            }
        }

        private fun formatTemperature(value: Double?): String = value?.let {
            val shown = if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            "$shown °C"
        }.orEmpty()

        private fun normalizeTimestamp(raw: Long?): Long {
            val value = raw ?: return System.currentTimeMillis()
            return if (value < 10_000_000_000L) value * 1000 else value
        }

        private fun JSONObject.num(key: String): Double? {
            if (!has(key) || isNull(key)) return null
            return when (val value = opt(key)) {
                is Number -> value.toDouble()
                is String -> value.trim().replace(',', '.').toDoubleOrNull()
                else -> null
            }
        }

        private fun JSONObject.str(key: String): String {
            if (!has(key) || isNull(key)) return ""
            return optString(key, "").trim().takeUnless { it == "--" } ?: ""
        }

        private fun JSONObject.long(key: String): Long? = num(key)?.toLong()

        private fun JSONObject.bool(key: String): Boolean? {
            if (!has(key) || isNull(key)) return null
            return when (val value = opt(key)) {
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> when (value.trim().lowercase()) {
                    "true", "1", "yes", "on", "active", "connected", "available" -> true
                    "false", "0", "no", "off", "inactive", "disconnected", "none" -> false
                    else -> null
                }
                else -> null
            }
        }
    }
}
