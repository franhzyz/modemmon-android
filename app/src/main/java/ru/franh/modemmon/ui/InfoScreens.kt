package ru.franh.modemmon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.franh.modemmon.data.Carrier
import ru.franh.modemmon.data.Link
import ru.franh.modemmon.data.ModemSnapshot
import ru.franh.modemmon.data.NeighborCell
import ru.franh.modemmon.ui.theme.Accent
import ru.franh.modemmon.ui.theme.Bad
import ru.franh.modemmon.ui.theme.Good
import ru.franh.modemmon.ui.theme.Muted
import ru.franh.modemmon.ui.theme.Outline
import ru.franh.modemmon.ui.theme.Warn
import ru.franh.modemmon.util.Fmt
import ru.franh.modemmon.vm.UiState
import java.util.Locale

@Composable
fun CellScreen(state: UiState) {
    val snap = state.snap
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        if (snap == null) {
            EmptyHint("Данных ещё нет — приложение опрашивает источник.")
            return@Column
        }

        NetworkCard(snap)
        Spacer(Modifier.height(12.dp))

        if (snap.lte.present) {
            LinkCard("Обслуживающая LTE-сота", snap.lte)
            Spacer(Modifier.height(12.dp))
        }

        if (snap.nr.present) {
            LinkCard("5G NR-сота", snap.nr)
            Spacer(Modifier.height(12.dp))
        } else if (snap.hasNr || snap.networkInfo.nrNsaAvailable != null || snap.networkInfo.nrSaService.isNotBlank()) {
            CardBox {
                SectionLabel("5G NR")
                InfoRow("Режим", snap.rat.ifBlank { "—" }, Good)
                InfoRow("NSA доступна", yesNo(snap.networkInfo.nrNsaAvailable))
                InfoRow("SA-сервис", prettyState(snap.networkInfo.nrSaService))
                Text(
                    "QMI сообщает доступность 5G, но не отдаёт параметры NR-соты и NR-сигнала.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (!snap.lte.present && !snap.nr.present) {
            EmptyHint("Модем не отдал параметры обслуживающей соты. Проверьте QMI NAS и ModemManager на роутере.")
            Spacer(Modifier.height(12.dp))
        }

        CarrierAggregationCard(snap)
        Spacer(Modifier.height(12.dp))

        NeighborCellsCard(snap.neighbors)
        Spacer(Modifier.height(12.dp))

        SimCard(snap)
        Spacer(Modifier.height(12.dp))

        BearerCard(snap)
        Spacer(Modifier.height(12.dp))

        DeviceCard(snap)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NetworkCard(snap: ModemSnapshot) {
    val info = snap.networkInfo
    CardBox {
        SectionLabel("Сеть и регистрация")
        InfoRow("Оператор", snap.operator.ifBlank { info.mccMncName(snap.plmn) }, Accent)
        InfoRow("PLMN", snap.plmn.ifBlank { "—" })
        if (info.mcc.isNotBlank() || info.mnc.isNotBlank()) {
            InfoRow("MCC / MNC", "${info.mcc.ifBlank { "—" }} / ${info.mnc.ifBlank { "—" }}")
        }
        InfoRow("Технология", snap.rat.ifBlank { "—" }, Good)
        InfoRow("Регистрация", prettyState(info.registrationType.ifBlank { info.registration }))
        InfoRow("Роуминг", yesNo(info.roaming, yes = "да", no = "нет"))
        InfoRow("Пакетный сервис", prettyState(info.packetService.ifBlank { info.ps }))
        if (info.cs.isNotBlank()) InfoRow("CS", prettyState(info.cs))
        if (info.lteService.isNotBlank()) InfoRow("LTE-сервис", prettyState(info.lteService))
        InfoRow("5G NSA доступна", yesNo(info.nrNsaAvailable))
        if (info.nrSaService.isNotBlank()) InfoRow("5G SA-сервис", prettyState(info.nrSaService))
        InfoRow("IMS Voice", yesNo(info.imsVoice))
        InfoRow("DCNR ограничение", yesNo(info.dcnrRestricted, yes = "есть", no = "нет"))
        if (info.cellAccess.isNotBlank()) InfoRow("Доступ к соте", prettyState(info.cellAccess))
        if (info.registrationRestriction.isNotBlank()) {
            InfoRow("Ограничение регистрации", prettyState(info.registrationRestriction))
        }
        InfoRow(
            "Агрегация частот",
            when (snap.caActive) {
                true -> "активна"
                false -> if (snap.caSecondaries.isNotEmpty()) "настроена, неактивна" else "неактивна"
                null -> "—"
            },
            if (snap.caActive == true) Good else Color.Unspecified
        )
    }
}

@Composable
private fun LinkCard(title: String, link: Link) {
    CardBox {
        SectionLabel(title)
        InfoRow("Бэнд", Fmt.band(link.band), Accent)
        InfoRow("Частота DL", Fmt.freq(link.freqMhz ?: Fmt.lteFreq(link.arfcn) ?: Fmt.nrFreq(link.arfcn)))
        InfoRow("EARFCN / ARFCN", link.arfcn?.toString() ?: "—")
        InfoRow("PCI", link.pci?.toString() ?: "—")
        InfoRow("Cell ID", link.cellId?.toString() ?: "—")
        if (link.enodeb != null || link.sector != null) {
            InfoRow("eNodeB / сектор", "${link.enodeb ?: "—"} / ${link.sector ?: "—"}")
        }
        InfoRow("TAC", link.tac.ifBlank { "—" })
        if (link.duplex.isNotBlank()) InfoRow("Дуплекс", link.duplex.uppercase(Locale.US))
        if (link.bwDl.isNotBlank() || link.bwUl.isNotBlank()) {
            InfoRow(
                "Полоса DL / UL",
                "${Fmt.mhzText(link.bwDl)} / ${Fmt.mhzText(link.bwUl)}"
            )
        }
        if (link.timingAdvanceUs != null) {
            InfoRow("Timing Advance", Fmt.microseconds(link.timingAdvanceUs))
        }
        if (link.ueIdle != null) InfoRow("Режим UE", if (link.ueIdle) "idle" else "connected")

        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Outline)
        InfoRow("RSRP", Fmt.withUnit(Fmt.dbm(link.rsrp), "дБм"))
        InfoRow("RSRQ", Fmt.withUnit(Fmt.db(link.rsrq), "дБ"))
        InfoRow("SINR", Fmt.withUnit(Fmt.db(link.sinr), "дБ"))
        InfoRow("RSSI", Fmt.withUnit(Fmt.dbm(link.rssi), "дБм"))

        Spacer(Modifier.height(10.dp))
        link.rsrp?.let { v ->
            val f = ((v + 140.0) / 96.0).coerceIn(0.0, 1.0).toFloat()
            ProgressRow(
                label = "Уровень сигнала",
                value = Fmt.withUnit(Fmt.dbm(v), "дБм"),
                fraction = f,
                color = qualityColor(f)
            )
        }
        link.sinr?.let { v ->
            val f = ((v + 10.0) / 40.0).coerceIn(0.0, 1.0).toFloat()
            ProgressRow(
                label = "Качество (SINR)",
                value = Fmt.withUnit(Fmt.db(v), "дБ"),
                fraction = f,
                color = qualityColor(f)
            )
        }
    }
}

/** Цвет по доле качества: плохо / средне / хорошо. */
private fun qualityColor(fraction: Float): Color = when {
    fraction < 0.3f -> Bad
    fraction < 0.6f -> Warn
    else -> Good
}

@Composable
private fun CarrierAggregationCard(snap: ModemSnapshot) {
    CardBox {
        SectionLabel("Carrier Aggregation")
        if (snap.caPrimary == null && snap.caSecondaries.isEmpty()) {
            Text("Список несущих не получен.", color = Muted, fontSize = 12.sp)
            return@CardBox
        }

        snap.caPrimary?.let {
            CarrierBlock("PCC · основная", it, Accent)
        }
        snap.caSecondaries.forEachIndexed { index, carrier ->
            if (snap.caPrimary != null || index > 0) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Outline)
            }
            val state = prettyState(carrier.state)
            val title = "SCC ${carrier.index ?: index + 1} · $state"
            CarrierBlock(title, carrier, if (carrier.state.equals("activated", true)) Good else Muted)
        }
    }
}

@Composable
private fun CarrierBlock(title: String, carrier: Carrier, color: Color) {
    Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(3.dp))
    InfoRow("Бэнд", Fmt.band(carrier.band))
    InfoRow("EARFCN", carrier.arfcn?.toString() ?: "—")
    InfoRow("Частота DL", Fmt.freq(carrier.freqMhz ?: Fmt.lteFreq(carrier.arfcn)))
    InfoRow("PCI", carrier.pci?.toString() ?: "—")
    InfoRow("Полоса DL", Fmt.mhz(carrier.bwDlMhz))
}

@Composable
private fun NeighborCellsCard(neighbors: List<NeighborCell>) {
    CardBox {
        SectionLabel("Соседние LTE-соты · ${neighbors.size}")
        if (neighbors.isEmpty()) {
            Text("Модем не отдал список соседних сот.", color = Muted, fontSize = 12.sp)
            return@CardBox
        }

        neighbors.forEachIndexed { index, cell ->
            if (index > 0) HorizontalDivider(Modifier.padding(vertical = 7.dp), color = Outline)
            NeighborBlock(index + 1, cell)
        }
    }
}

@Composable
private fun NeighborBlock(number: Int, cell: NeighborCell) {
    val relation = when (cell.relation.lowercase(Locale.US)) {
        "intra" -> "та же частота"
        "inter" -> "другая частота"
        else -> cell.relation.ifBlank { "соседняя" }
    }
    Text(
        "#$number · ${Fmt.band(cell.band)} · PCI ${cell.pci ?: "—"}",
        color = Accent,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
    InfoRow("Тип", relation)
    InfoRow("EARFCN", cell.arfcn?.toString() ?: "—")
    InfoRow("Частота DL", Fmt.freq(Fmt.lteFreq(cell.arfcn)))
    InfoRow("RSRP / RSRQ", "${Fmt.withUnit(Fmt.dbm(cell.rsrp), "дБм")} / ${Fmt.withUnit(Fmt.db(cell.rsrq), "дБ")}")
    if (cell.rssi != null) InfoRow("RSSI", Fmt.withUnit(Fmt.dbm(cell.rssi), "дБм"))
}

@Composable
private fun SimCard(snap: ModemSnapshot) {
    val sim = snap.sim
    CardBox {
        SectionLabel("SIM")
        InfoRow("Физический слот", sim.slot?.let { (it + 1).toString() } ?: "—")
        if (sim.logicalSlot != null) InfoRow("Логический слот", sim.logicalSlot.toString())
        InfoRow("Карта", prettyState(sim.cardStatus.ifBlank { sim.slotStatus }))
        if (sim.protocol.isNotBlank()) InfoRow("Протокол", sim.protocol.uppercase(Locale.US))
        InfoRow("eSIM / eUICC", yesNo(sim.isEsim))
        if (sim.operatorCode.isNotBlank()) InfoRow("Оператор SIM", sim.operatorCode)
        if (sim.operatorName.isNotBlank()) InfoRow("Имя оператора", sim.operatorName)
        if (sim.iccidMasked.isNotBlank()) InfoRow("ICCID", sim.iccidMasked)
        if (sim.imsiMasked.isNotBlank()) InfoRow("IMSI", sim.imsiMasked)
    }
}

@Composable
private fun BearerCard(snap: ModemSnapshot) {
    val bearer = snap.bearer
    CardBox {
        SectionLabel("Соединение данных")
        InfoRow("Подключено", yesNo(bearer.connected))
        InfoRow("APN", bearer.apn.ifBlank { "—" })
        InfoRow("Интерфейс", bearer.interfaceName.ifBlank { "—" })
        if (bearer.ipType.isNotBlank()) InfoRow("Тип IP", bearer.ipType.uppercase(Locale.US))
        if (bearer.ipv4Address.isNotBlank()) {
            val address = buildString {
                append(bearer.ipv4Address)
                bearer.ipv4Prefix?.let { append("/").append(it) }
            }
            InfoRow("IPv4", address)
        }
        if (bearer.ipv4Mtu != null) InfoRow("MTU", bearer.ipv4Mtu.toString())
        InfoRow("Время соединения", Fmt.duration(snap.traffic.connectTime))
        InfoRow("Принято", Fmt.bytes(snap.traffic.totalDl))
        InfoRow("Передано", Fmt.bytes(snap.traffic.totalUl))
    }
}

@Composable
private fun DeviceCard(snap: ModemSnapshot) {
    val device = snap.device
    CardBox {
        SectionLabel("Модем")
        if (device.manufacturer.isNotBlank()) InfoRow("Производитель", device.manufacturer)
        InfoRow("Модель", device.model.ifBlank { "—" })
        InfoRow("Прошивка", device.firmware.ifBlank { "—" })
        if (device.hardwareRevision.isNotBlank()) InfoRow("Ревизия", device.hardwareRevision)
        if (device.carrierConfig.isNotBlank()) {
            InfoRow(
                "Carrier config",
                listOf(device.carrierConfig, device.carrierConfigRevision)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
            )
        }
        if (device.state.isNotBlank()) InfoRow("Состояние", prettyState(device.state))
        if (device.powerState.isNotBlank()) InfoRow("Питание", prettyState(device.powerState))
        if (device.signalQualityPercent != null) InfoRow("Качество MM", "${device.signalQualityPercent}%")
        InfoRow("Температура", temperatureText(device), if (device.temp.isNotBlank()) Good else Muted)
        if (device.tempSource.isNotBlank()) {
            InfoRow("Источник температуры", temperatureSource(device.tempSource))
        }
        if (device.tempSensor.isNotBlank()) InfoRow("Основной датчик", device.tempSensor)
        if (device.tempPa.isNotBlank()) InfoRow("PA", device.tempPa)
        if (device.tempSkin.isNotBlank()) InfoRow("Skin Sensor", device.tempSkin)
        if (device.tempTsens.isNotBlank()) InfoRow("TSENS", device.tempTsens)
        if (device.temp.isBlank() && device.tempStatus.isNotBlank()) {
            Text(
                temperatureStatusHint(device.tempStatus),
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        InfoRow("Версия API", if (snap.schemaVersion > 0) "schema v${snap.schemaVersion}" else "legacy")
    }
}

@Composable
fun TrafficScreen(state: UiState) {
    val snap: ModemSnapshot? = state.snap
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeedTile("↓ Загрузка", Fmt.rate(snap?.traffic?.dlRate), Good, Modifier.weight(1f))
            SpeedTile("↑ Отдача", Fmt.rate(snap?.traffic?.ulRate), Accent, Modifier.weight(1f))
        }

        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionLabel("Трафик за сессию")
            InfoRow("Скачано", Fmt.bytes(snap?.traffic?.totalDl))
            InfoRow("Отправлено", Fmt.bytes(snap?.traffic?.totalUl))
            InfoRow("Время соединения", Fmt.duration(snap?.traffic?.connectTime))
        }

        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionLabel("Модем")
            InfoRow("Модель", snap?.device?.model?.ifBlank { "—" } ?: "—")
            InfoRow("Температура", snap?.device?.temp?.ifBlank { "—" } ?: "—")
            InfoRow("Uptime", Fmt.duration(snap?.device?.uptime))
            InfoRow(
                "Передача данных",
                when (snap?.device?.dataEnabled) {
                    true -> "включена"
                    false -> "выключена"
                    null -> "—"
                }
            )
        }

        Spacer(Modifier.height(12.dp))

        CardBox {
            SectionLabel("Состояние радиозвена")
            InfoRow("LTE RSRP", Fmt.withUnit(Fmt.dbm(snap?.lte?.rsrp), "дБм"))
            InfoRow("LTE SINR", Fmt.withUnit(Fmt.db(snap?.lte?.sinr), "дБ"))
            InfoRow("NR RSRP", Fmt.withUnit(Fmt.dbm(snap?.nr?.rsrp), "дБм"))
            InfoRow("NR SINR", Fmt.withUnit(Fmt.db(snap?.nr?.sinr), "дБ"))
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SpeedTile(label: String, value: String, color: Color, modifier: Modifier) {
    CardBox(modifier = modifier) {
        Text(label, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            color = color,
            fontSize = 20.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun yesNo(value: Boolean?, yes: String = "да", no: String = "нет"): String = when (value) {
    true -> yes
    false -> no
    null -> "—"
}

private fun prettyState(raw: String): String {
    if (raw.isBlank()) return "—"
    return when (raw.lowercase(Locale.US)) {
        "registered" -> "зарегистрирован"
        "home" -> "домашняя сеть"
        "attached" -> "подключён"
        "available" -> "доступен"
        "connected" -> "подключён"
        "present" -> "установлена"
        "active", "activated" -> "активна"
        "deactivated" -> "неактивна"
        "none" -> "нет"
        "on" -> "включено"
        "off" -> "выключено"
        "all-calls" -> "все вызовы"
        "unrestricted" -> "нет"
        else -> raw.replace('-', ' ')
    }
}

private fun ru.franh.modemmon.data.NetworkInfo.mccMncName(plmn: String): String =
    if (plmn.isNotBlank()) plmn else "—"

private fun temperatureText(device: ru.franh.modemmon.data.Device): String = when {
    device.temp.isNotBlank() -> device.temp
    device.tempStatus == "modeminfo_not_installed" -> "— (нет modeminfo)"
    else -> "—"
}

private fun temperatureSource(source: String): String = when (source.lowercase(Locale.US)) {
    "at_temp" -> "AT^TEMP?"
    "modeminfo" -> "modeminfo · chiptemp"
    "mqtt" -> "MQTT"
    else -> source
}

private fun temperatureStatusHint(status: String): String = when (status.lowercase(Locale.US)) {
    "at_command_failed" -> "AT^TEMP? временно не ответила через medvemodem."
    "at_not_reported" -> "AT^TEMP? ответила без распознаваемого значения температуры."
    "medvemodem_not_installed" -> "medvemodem недоступен: AT^TEMP? выполнить нельзя."
    "modeminfo_not_installed" -> "Пакет modeminfo не установлен: источник температуры отсутствует."
    "not_reported" -> "Источник температуры не сообщил валидное значение."
    "unavailable" -> "Подтверждённый датчик температуры модема пока недоступен. Температура Orange Pi здесь намеренно не показывается."
    else -> "Температура модема недоступна: $status."
}
