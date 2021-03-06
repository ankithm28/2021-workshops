package com.lynbrookrobotics.twenty.subsystems.drivetrain

import com.lynbrookrobotics.kapuchin.control.data.*
import com.lynbrookrobotics.kapuchin.control.math.drivetrain.*
import com.lynbrookrobotics.kapuchin.hardware.offloaded.*
import com.lynbrookrobotics.kapuchin.logging.*
import com.lynbrookrobotics.kapuchin.preferences.*
import com.lynbrookrobotics.kapuchin.subsystems.*
import com.lynbrookrobotics.kapuchin.timing.clock.*
import com.lynbrookrobotics.kapuchin.timing.monitoring.RealtimeChecker.Companion.realtimeChecker
import info.kunalsheth.units.generated.*

class DrivetrainComponent(hardware: DrivetrainHardware) :
    Component<DrivetrainComponent, DrivetrainHardware, TwoSided<OffloadedOutput>>(hardware),
    GenericDrivetrainComponent {

    private val maxLeftSpeed by pref(11.9, FootPerSecond)
    private val maxRightSpeed by pref(12.5, FootPerSecond)

    override val maxSpeed get() = maxLeftSpeed min maxRightSpeed
    val maxOmega get() = maxSpeed / hardware.conversions.trackLength / 2 * Radian

    val velocityGains by pref {
        val kP by pref(5, Volt, 2, FootPerSecond)
        val kF by pref(110, Percent)
        ({
            val left = OffloadedEscGains(
                kP = hardware.conversions.encoder.left.native(kP),
                kF = hardware.conversions.encoder.left.native(
                    Gain(hardware.escConfig.voltageCompSaturation, maxLeftSpeed)
                ) * kF.Each
            )
            val right = OffloadedEscGains(
                kP = hardware.conversions.encoder.right.native(kP),
                kF = hardware.conversions.encoder.right.native(
                    Gain(hardware.escConfig.voltageCompSaturation, maxRightSpeed)
                ) * kF.Each
            )
            TwoSided(left, right)
        })
    }

    private val bearingGainsNamed = Named("bearingGains", this)
    override val bearingKp by bearingGainsNamed.pref(5, FootPerSecond, 45, Degree)
    override val bearingKd by bearingGainsNamed.pref(3, FootPerSecond, 360, DegreePerSecond)

    val shootTolerance by pref(5, Percent) // max drivetrain output when shooting

    override val fallbackController: DrivetrainComponent.(Time) -> TwoSided<OffloadedOutput> = {
        TwoSided(PercentOutput(hardware.escConfig, 0.Percent))
    }

    override fun DrivetrainHardware.output(value: TwoSided<OffloadedOutput>) {
        value.left.writeTo(leftMasterEsc)
        value.right.writeTo(rightMasterEsc)
    }

    init {
        if (clock is Ticker) clock.realtimeChecker(hardware.jitterPulsePin::set) { hardware.jitterReadPin.period.Second }
    }
}