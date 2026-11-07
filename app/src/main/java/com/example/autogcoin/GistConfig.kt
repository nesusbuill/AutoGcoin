package com.example.autogcoin // 🔴 Nhớ đổi theo package của sếp

data class GistConfig(
    val game_package: String = "",
    val watch_duration_ms: Long = 25000,
    val flow: List<FlowStep> = emptyList()
)

data class FlowStep(
    val action: String,
    val text: String? = null,
    val texts: List<String>? = null,
    val delay_ms: Long = 0
)
