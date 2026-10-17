package io.github.nicholas_sb.restart.ui

/** 225000 ms -> "3:45" */
fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
