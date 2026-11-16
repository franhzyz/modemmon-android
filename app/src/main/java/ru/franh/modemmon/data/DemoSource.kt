package ru.franh.modemmon.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Тестовый режим: имитирует поворот антенны к вышке — качество растёт,
 * проходит через пик и падает. Позволяет проверить интерфейс без роутера.
 */
object DemoSource {

    private val start = System.currentTimeMillis()
    private var lastDl = 0L
    private var lastUl = 0L
    private var totalDl = 12_480_000_000L
    private var totalUl = 1_940_000_000L

    fun next(): ModemSnapshot {
        val t = (System.currentTimeMillis() - start) / 1000.0
        // Основной цикл «наведения» — 45 секунд, плюс медленный дрейф.
        val phase = (t % 45.0) / 45.0
        val aim = 0.5 - 0.5 * cos(2 * PI * phase)          // 0 → 1 → 0
        val drift = 0.06 * sin(t / 7.3)

        fun jitter(amp: Double) = Random.nextDouble(-amp, amp)

        val q = (aim + drift).coerceIn(0.0, 1.0)

        val sinr = -2.0 + 24.0 * q + jitter(0.9)
        val rsrp = -118.0 + 38.0 * q + jitter(1.2)
        val rsrq = -18.0 + 9.5 * q + jitter(0.5)
        val rssi = -96.0 + 40.0 * q + jitter(1.5)

        val nrSinr = sinr - 1.5 + jitter(0.6)
        val nrRsrp = rsrp + 6.0 + jitter(1.0)

        val dl = (2_000_000 + 118_000_000 * q * q).toLong() + Random.nextLong(-500_000, 500_000)
        val ul = (300_000 + 18_000_000 * q * q).toLong() + Random.nextLong(-120_000, 120_000)
        totalDl += (dl / 8).coerceAtLeast(0)
        totalUl += (ul / 8).coerceAtLeast(0)
        lastDl = dl.coerceAtLeast(0)
        lastUl = ul.coerceAtLeast(0)

        return ModemSnapshot(
            ts = System.currentTimeMillis(),
            online = true,
            rat = "5G NSA",
            operator = "MTS",
            plmn = "25001",
            caActive = true,
            networkInfo = NetworkInfo(
                registration = "registered",
                registrationType = "home",
                packetService = "attached",
                cs = "attached",
                ps = "attached",
                roaming = false,
                mcc = "250",
                mnc = "01",
                lteService = "available",
                nrNsaAvailable = true,
                nrSaService = "none",
                imsVoice = true,
                dcnrRestricted = false,
                cellAccess = "all-calls",
                registrationRestriction = "unrestricted"
            ),
            lte = Link(
                present = true,
                rsrp = round1(rsrp),
                rsrq = round1(rsrq),
                sinr = round1(sinr),
                rssi = round1(rssi),
                band = "3",
                arfcn = 1650,
                freqMhz = 1845.0,
                pci = 217,
                cellId = 128_451_073L,
                enodeb = 501_762,
                sector = 3,
                tac = "6A1F",
                bwDl = "20",
                bwUl = "20",
                duplex = "FDD",
                timingAdvanceUs = 2.0,
                ueIdle = false
            ),
            nr = Link(
                present = true,
                rsrp = round1(nrRsrp),
                rsrq = round1(rsrq + 1.2),
                sinr = round1(nrSinr),
                band = "n78",
                arfcn = 636_666,
                freqMhz = 3550.0,
                pci = 41,
                cellId = 8_812_345_678_901L,
                bwDl = "100"
            ),
            caPrimary = Carrier(
                index = 0,
                primary = true,
                band = "B3",
                arfcn = 1650,
                freqMhz = 1845.0,
                pci = 217,
                bwDlMhz = 20.0,
                state = "primary"
            ),
            caSecondaries = listOf(
                Carrier(index = 1, band = "B1", arfcn = 100, pci = 332, bwDlMhz = 10.0, state = "activated"),
                Carrier(index = 2, band = "B7", arfcn = 3400, pci = 37, bwDlMhz = 10.0, state = "deactivated")
            ),
            neighbors = listOf(
                NeighborCell(band = "B40", arfcn = 38750, pci = 391, rsrp = -84.0, rsrq = -11.7, rssi = -52.8, relation = "inter"),
                NeighborCell(band = "B7", arfcn = 3400, pci = 37, rsrp = -75.4, rsrq = -9.3, rssi = -57.4, relation = "inter")
            ),
            traffic = Traffic(
                dlRate = lastDl,
                ulRate = lastUl,
                totalDl = totalDl,
                totalUl = totalUl,
                connectTime = t.toLong() + 4_820
            ),
            device = Device(
                manufacturer = "Foxconn",
                model = "Foxconn T99W175",
                firmware = "T99W175.F0.1.0.8.9",
                hardwareRevision = "V065",
                carrierConfig = "GCF",
                carrierConfigRevision = "0A000804",
                state = "connected",
                powerState = "on",
                signalQualityPercent = 100,
                temp = (38 + 9 * q).toInt().toString() + " °C",
                tempSource = "at_temp",
                tempSensor = "TSENS",
                tempStatus = "available",
                tempPa = (39 + 8 * q).toInt().toString() + " °C",
                tempSkin = (37 + 7 * q).toInt().toString() + " °C",
                tempTsens = (38 + 9 * q).toInt().toString() + " °C",
                uptime = t.toLong() + 93_400,
                dataEnabled = true,
                simState = "слот 1",
                simSlot = 0
            ),
            sim = SimInfo(
                slot = 0,
                cardStatus = "present",
                slotStatus = "active",
                logicalSlot = 1,
                protocol = "uicc",
                isEsim = false,
                operatorCode = "25001",
                operatorName = "MTS",
                iccidMasked = "****1234",
                imsiMasked = "****5678"
            ),
            bearer = BearerInfo(
                connected = true,
                apn = "internet.mts.ru",
                interfaceName = "wwan0",
                ipType = "ipv4v6",
                ipv4Address = "10.0.0.2",
                ipv4Prefix = 30,
                ipv4Mtu = 1500
            )
        )
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
