package com.rate.aegis.surfaces.prospectus

/**
 * Hand the prospectus HTML to the host platform — JVM saves to disk, WASM opens
 * the file in a new browser tab via a blob URL. The Aegis surface stays
 * commonMain by deferring this single host-specific step.
 */
expect fun openOrSaveProspectus(planId: String, html: String)
