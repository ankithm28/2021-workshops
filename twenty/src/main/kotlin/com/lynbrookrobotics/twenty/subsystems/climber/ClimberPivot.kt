package com.lynbrookrobotics.twenty.subsystems.climber

import com.lynbrookrobotics.kapuchin.hardware.*
import com.lynbrookrobotics.kapuchin.subsystems.*
import com.lynbrookrobotics.kapuchin.timing.*
import com.lynbrookrobotics.twenty.Subsystems.Companion.pneumaticTicker
import com.lynbrookrobotics.twenty.Subsystems.Companion.sharedTickerTiming
import com.lynbrookrobotics.twenty.subsystems.climber.ClimberPivotState.*
import edu.wpi.first.wpilibj.Solenoid
import info.kunalsheth.units.generated.*
import info.kunalsheth.units.math.*

enum class ClimberPivotState(val output: Boolean) { Down(false), Up(true) }

class ClimberPivotComponent(hardware: ClimberPivotHardware) :
    Component<ClimberPivotComponent, ClimberPivotHardware, ClimberPivotState>(hardware, pneumaticTicker) {

    override val fallbackController: ClimberPivotComponent.(Time) -> ClimberPivotState = { Down }

    override fun ClimberPivotHardware.output(value: ClimberPivotState) {
        pivotSolenoid.set(value.output)
    }
}

class ClimberPivotHardware : SubsystemHardware<ClimberPivotHardware, ClimberPivotComponent>() {
    override val period by sharedTickerTiming
    override val syncThreshold = 100.milli(Second)
    override val priority = Priority.Low
    override val name = "Climber Pivot"

    private val pivotSolenoidChannel = 1

    val pivotSolenoid by hardw { Solenoid(pivotSolenoidChannel) }
}