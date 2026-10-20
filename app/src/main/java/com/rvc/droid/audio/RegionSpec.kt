package com.rvc.droid.audio

/**
 * Specifica di una regione audio da esportare: titolo + range in millisecondi.
 * Usato da [WavEditor.exportRegions] e da RegionExportDialog.
 */
data class RegionSpec(
    val title: String,
    val startMs: Long,
    val endMs: Long
)
