package com.rvc.droid.ui.rvc

/**
 * Feature flag del motore RVC locale (ONNX).
 *
 * La pipeline ONNX su telefono è sperimentale e storicamente fragile: richiede
 * modelli convertiti (.onnx + .fbin) e non è mai stata validata su un dispositivo
 * reale con un set completo. Il flusso supportato e testato è **RVC remoto**:
 * il PC usa direttamente i .pth (PyTorch/CUDA) e restituisce il WAV.
 *
 * Tenere false per nascondere la modalità locale dall'interfaccia. Il codice nativo
 * resta compilato per non rischiare regressioni, ma non è raggiungibile dall'utente.
 */
object RvcFeatures {
    const val LOCAL_ONNX_ENABLED = false
}
