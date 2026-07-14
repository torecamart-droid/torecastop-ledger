package com.torecastop.ledger.ui.theme

import androidx.compose.ui.graphics.Color

// TorecaStop brand palette
val Rose = Color(0xFFF5CAC3)
val Teal = Color(0xFF84A59D)
val Coral = Color(0xFFF28482)
val Bronze = Color(0xFFF6BD60)

val Ink = Color(0xFF2B2B2B)
val Cloud = Color(0xFFFDFBFA)
val Mist = Color(0xFFF3EDEA)

// Dark-theme surfaces, derived from Ink so dark mode keeps the brand's warmth
// instead of falling back to Material's generic greys. (v1.3)
val InkBackground = Color(0xFF1A1917)
val InkSurface = Color(0xFF242220)
val InkSurfaceVariant = Color(0xFF38332F)
val CloudDim = Color(0xFFEAE4DF)
val CloudMuted = Color(0xFFC7BFB8)

// Explicit error red — a true red, deliberately distinct from Coral (the warm
// salmon used for sale totals) so "trade lost value" never reads as a total.
val ErrorRed = Color(0xFFB3261E)
val ErrorRedLight = Color(0xFFFFB4AB)
