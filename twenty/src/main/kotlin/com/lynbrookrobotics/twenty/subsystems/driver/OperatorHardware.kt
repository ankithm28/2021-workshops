package com.lynbrookrobotics.twenty.subsystems.driver

import com.lynbrookrobotics.kapuchin.control.conversion.deadband.*
import com.lynbrookrobotics.kapuchin.control.data.*
import com.lynbrookrobotics.kapuchin.control.math.*
import com.lynbrookrobotics.kapuchin.hardware.*
import com.lynbrookrobotics.kapuchin.logging.*
import com.lynbrookrobotics.kapuchin.preferences.*
import com.lynbrookrobotics.kapuchin.subsystems.*
import com.lynbrookrobotics.kapuchin.timing.*
import com.lynbrookrobotics.kapuchin.timing.clock.*
import edu.wpi.first.wpilibj.GenericHID.Hand.kLeft
import edu.wpi.first.wpilibj.GenericHID.Hand.kRight
import edu.wpi.first.wpilibj.XboxController
import info.kunalsheth.units.generated.*
import info.kunalsheth.units.math.*
import kotlin.math.PI

class OperatorHardware : RobotHardware<OperatorHardware>() {
    override val priority = Priority.High
    override val name = "Operator"

    private val flywheelMappingNamed = Named("Flywheel Mapping", this)
    private val deadband by flywheelMappingNamed.pref(15, Percent)
    private val minRpm by flywheelMappingNamed.pref(20, Percent)
    private val maxRpm by flywheelMappingNamed.pref(100, Percent)
    private val presetRange by flywheelMappingNamed.pref(45, Degree)

    private val withDeadband = horizontalDeadband(deadband, 100.Percent)
    private fun flywheelMapping(x: Dimensionless, y: Dimensionless): Angle? {
        if (withDeadband(x).isZero && withDeadband(y).isZero) return null
        return (atan2(y * Metre, x * Metre) + PI.Radian)
    }

    private val turretMapping by pref {
        val exponent by pref(1)
        val deadband by pref(10, Percent)
        val sensitivity by pref(100, Percent)

        ({
            val db = horizontalDeadband(deadband, 100.Percent)
            fun(x: Dimensionless) = db(x).abs.pow(exponent.Each).withSign(x) * sensitivity
        })
    }

    val xbox by hardw { XboxController(1) }.verify("the operator controller is connected") {
        it.name == "Controller (Xbox One For Windows)"
    }.verify("xbox controller and rumblr are not swapped") {
        it.getTriggerAxis(kLeft) < 0.1 && it.getTriggerAxis(kRight) < 0.1
    }

    private fun <Input> s(f: XboxController.() -> Input) = sensor { f(xbox) stampWith it }

    private val triggerPressure by pref(30, Percent)
    private val lt get() = xbox.getTriggerAxis(kLeft) > triggerPressure.Each
    private val rt get() = xbox.getTriggerAxis(kRight) > triggerPressure.Each
    private val lb get() = xbox.getBumper(kLeft)
    private val rb get() = xbox.getBumper(kRight)
    private val start get() = xbox.startButton
    private val back get() = xbox.backButton

    val aim = s { lt }
    val aimPreset = s {
        flywheelMapping(getX(kLeft).Each, getY(kLeft).Each)?.takeIf {
            it in 270.Degree `±` (presetRange)
        } != null
    }.with(graph("Flywheel Preset", Each)) { if (it) 1.Each else 0.Each }

    private var lastRt = false
    val shoot = s {
        (!lastRt && rt).also { lastRt = it }
    }

    val hoodUp = s { lb }

    val flywheelManual = s {
        flywheelMapping(getX(kLeft).Each, getY(kLeft).Each)?.takeIf {
            it in 0.Degree..180.Degree
        }?.let {
            val normalized = it / 180.Degree
            val slope = (maxRpm - minRpm) / (100.Percent - 0.Percent)
            normalized * slope + minRpm
        }
    }.with(graph("Flywheel Manual", Percent)) { it ?: Double.NaN.Each }

    val turretManual = s {
        turretMapping(getX(kRight).Each)
    }.with(graph("Turret Manual", Percent))

    val unjamCarousel = s { pov == 0 }
    val rezeroTurret = s { pov == 90 }
    val centerTurret = s { pov == 180 }
    val reindexCarousel = s { pov == 270 }

    val extendClimber = s { back && rb }
    val retractClimber = s { start && rb }
    val extendControlPanel = s { pov == 0 }
    val controlPanelStage2 = s { pov == 0 && aButton }
    val controlPanelStage3 = s { pov == 0 && bButton }

    init {
        EventLoop.runOnTick { time ->
            setOf(flywheelManual, aimPreset, turretManual).forEach {
                it.optimizedRead(time, 0.1.Second)
            }
        }
    }
}