package com.lynbrookrobotics.twenty.subsystems

import com.lynbrookrobotics.kapuchin.control.data.*
import com.lynbrookrobotics.kapuchin.hardware.*
import com.lynbrookrobotics.kapuchin.subsystems.*
import com.lynbrookrobotics.kapuchin.timing.*
import edu.wpi.first.wpilibj.RobotController
import info.kunalsheth.units.generated.*

class ElectricalSystemHardware : RobotHardware<ElectricalSystemHardware>() {
    override val name = "Electrical System"
    override val priority = Priority.Lowest

    val batteryVoltage = sensor { RobotController.getBatteryVoltage().Volt stampSince it }
}
