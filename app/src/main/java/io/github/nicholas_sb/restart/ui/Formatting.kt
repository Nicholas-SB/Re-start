package io.github.nicholas_sb.restart.ui

/** 225000 ms -> "3:45" */
/** e.g. "17 Oct 2026, 15:02", in the phone's language and time format. */
fun formatDateTime(epochMs: Long): String =
    java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(epochMs))

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
