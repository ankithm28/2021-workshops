package com.lynbrookrobotics.twenty.subsystems.limelight

import com.lynbrookrobotics.kapuchin.control.data.*
import info.kunalsheth.units.generated.*

data class LimelightReading(
    val tx: Angle, val ty: Angle,
    val tx0: Dimensionless, val ty0: Dimensionless,
    val thor: Pixel, val tvert: Pixel,
    val ta: Dimensionless, val pipeline: Pipeline?,
    val ts: Angle,
)

enum class Pipeline(val number: Int) {
    ZoomOut(0), ZoomInPanHigh(1), ZoomInPanMid(2), ZoomInPanLow(3), DriverStream(4)
}

data class DetectedTarget(val inner: Position?, val outer: Position)