package dev.linkfuse.app.model

/** Physical transport a link rides on. A second phone's hotspot arrives here as WI-FI
 *  (over the air) or ETHERNET (USB tether) — the system already classifies it for us. */
enum class LinkKind(val label: String, val weight: Int) {
    WIFI("Wi-Fi", 3),
    CELLULAR("Mobile data", 2),
    ETHERNET("Ethernet / USB tether", 4);
}

/** Overall engine state, mirrored verbatim into the notification and the tile. */
enum class MergeStatus(val caption: String) {
    MERGING("Merging"),
    PAUSED("Paused"),
    NO_CONNECTIONS("No connections"),
    ERROR("Error"),
    STOPPED("Stopped");
}

/** One physical connection as seen by the UI. */
data class LinkUi(
    val id: String,
    val kind: LinkKind,
    val detail: String,          // e.g. "HomeNet" or "LTE · carrier"
    val metered: Boolean,
    val downRate: Long,          // bytes/sec, smoothed
    val upRate: Long,
    val share: Float,            // 0..1 portion of new flows routed here
)

/** What the main screen, notification and tile all render from. */
data class UiState(
    val status: MergeStatus = MergeStatus.STOPPED,
    val links: List<LinkUi> = emptyList(),
    val totalDown: Long = 0,
    val totalUp: Long = 0,
    val message: String? = null, // human-readable error / hint
)
