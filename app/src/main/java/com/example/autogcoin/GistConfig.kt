package com.example.autogcoin

data class GistConfig(
    val game_package: String = "",
    val flow: List<FlowStep> = emptyList()
)

data class FlowStep(
    val action: String,
    val text: String? = null,
    val texts: List<String>? = null,
    val delay_ms: Long = 0,
    val max_wait_ms: Long = 45000 // Thêm biến này
)
