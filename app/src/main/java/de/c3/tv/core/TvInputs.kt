package de.c3.tv.core

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

/** Read-only view on the TV Input Framework (public API, no special permission). */
class TvInputs(private val context: Context) {

    private val manager: TvInputManager? = context.getSystemService(TvInputManager::class.java)

    data class Input(
        val id: String,
        val label: String,
        val type: Int,
        val state: Int,
        val parentId: String?,
        val isPassthrough: Boolean,
    ) {
        val typeName: String
            get() = when (type) {
                TvInputInfo.TYPE_HDMI -> "HDMI"
                TvInputInfo.TYPE_TUNER -> "Tuner"
                TvInputInfo.TYPE_COMPOSITE -> "Composite"
                TvInputInfo.TYPE_OTHER -> "Other"
                else -> "Typ $type"
            }

        val stateName: String
            get() = when (state) {
                TvInputManager.INPUT_STATE_CONNECTED -> "verbunden"
                TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> "Standby"
                TvInputManager.INPUT_STATE_DISCONNECTED -> "getrennt"
                else -> "unbekannt ($state)"
            }
    }

    fun list(): List<Input> {
        val tim = manager ?: return emptyList()
        return try {
            tim.tvInputList.map { info ->
                Input(
                    id = info.id,
                    label = info.loadLabel(context)?.toString().orEmpty(),
                    type = info.type,
                    state = tim.getInputState(info.id),
                    parentId = info.parentId,
                    isPassthrough = info.isPassthroughInput,
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    companion object {
        /** HDMI 2 on the L75MB-FPEU (PS5 port), used if the CEC device is not announced. */
        const val PS5_FALLBACK_INPUT = "com.mediatek.tis/.HdmiInputService/HW3"

        /** The PlayStation as announced via HDMI-CEC (e.g. label "PlayStation 5"). */
        fun findPlayStation(inputs: List<Input>): Input? =
            inputs.firstOrNull { it.label.contains("PlayStation", ignoreCase = true) }

        /** Physical HDMI ports (no CEC child devices), sorted by label. */
        fun hdmiPorts(inputs: List<Input>): List<Input> =
            inputs.filter { it.type == TvInputInfo.TYPE_HDMI && it.parentId == null && it.isPassthrough }
                .sortedBy { it.label }
    }
}
