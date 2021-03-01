package com.lynbrookrobotics.twenty.choreos

import com.lynbrookrobotics.kapuchin.*
import com.lynbrookrobotics.kapuchin.control.data.*
import com.lynbrookrobotics.kapuchin.control.math.*
import com.lynbrookrobotics.kapuchin.logging.*
import com.lynbrookrobotics.kapuchin.logging.Level.*
import com.lynbrookrobotics.kapuchin.routines.*
import com.lynbrookrobotics.kapuchin.subsystems.*
import com.lynbrookrobotics.kapuchin.timing.*
import com.lynbrookrobotics.twenty.*
import com.lynbrookrobotics.twenty.routines.*
import com.lynbrookrobotics.twenty.subsystems.*
import com.lynbrookrobotics.twenty.subsystems.carousel.*
import com.lynbrookrobotics.twenty.subsystems.intake.*
import com.lynbrookrobotics.twenty.subsystems.shooter.*
import com.lynbrookrobotics.twenty.subsystems.shooter.FlashlightState.*
import com.lynbrookrobotics.twenty.subsystems.shooter.ShooterHoodState.*
import info.kunalsheth.units.generated.*
import info.kunalsheth.units.math.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sign

suspend fun Subsystems.digestionTeleop() = startChoreo("Digestion Teleop") {

    val intakeBalls by driver.intakeBalls.readEagerly().withoutStamps
    val unjamBalls by driver.unjamBalls.readEagerly().withoutStamps

    val aim by operator.aim.readEagerly().withoutStamps
    val aimPreset by operator.aimPreset.readEagerly().withoutStamps
    val shoot by operator.shoot.readEagerly().withoutStamps
    val hoodUp by operator.hoodUp.readEagerly().withoutStamps

    val flywheelManual by operator.flywheelManual.readEagerly().withoutStamps
    val turretManual by operator.turretManual.readEagerly().withoutStamps

    val unjamCarousel by operator.unjamCarousel.readEagerly().withoutStamps
    val rezeroTurret by operator.rezeroTurret.readEagerly().withoutStamps
    val reindexCarousel by operator.reindexCarousel.readEagerly().withoutStamps
    val centerTurret by operator.centerTurret.readEagerly().withoutStamps

    choreography {
        if (turret != null && !turret.hardware.isZeroed) launch {
            turret.rezero(electrical)
        }

        withTimeout(15.Second) {
            carousel.rezero()
            carousel.whereAreMyBalls()
        }

        launch {
            launchWhenever(
//                { turret?.routine == null } to choreography { turret?.fieldOrientedPosition(drivetrain) },
                    { shoot } to choreography { fire() }
            )
        }
        runWhenever(
            { intakeBalls } to choreography { eat() },
            { unjamBalls } to choreography { intakeRollers?.set(intakeRollers.pukeSpeed) ?: freeze() },

            { aim } to choreography { vision() },
//            { shoot } to choreography { fireAll() },
            { aimPreset } to choreography {
                flywheel?.let { spinUpShooter(flywheel.preset, Down) }
            },
            { hoodUp } to choreography { shooterHood?.set(Up) ?: freeze() },

            { flywheelManual != null } to choreography {
                scope.launch { withTimeout(2.Second) { flashlight?.set(On) } }
                flywheel?.let {
                    spinUpShooter(
                        flywheel.manualSpeed,
                        if (hoodUp) Up else Down
                    )
                } ?: freeze()

            },
            { !turretManual.isZero } to choreography {
                scope.launch { withTimeout(5.Second) { flashlight?.set(On) } }
                turret?.manualOverride(operator) ?: freeze()
            },

            { unjamCarousel } to choreography { unjam() },
            { rezeroTurret } to choreography { turret?.rezero(electrical) ?: freeze() },
            { reindexCarousel } to choreography {
                carousel.whereAreMyBalls()
                rumble.set(TwoSided(0.Percent, 100.Percent))
            },
            { centerTurret } to choreography { turret?.set(0.Degree) },
        )
    }
}

suspend fun Subsystems.eat() = startChoreo("Collect Balls") {
    choreography {

        while (isActive) {
            val angle = carousel.state.intakeAngle()
            if (angle == null) {
                log(Warning) { "I'm full. No open slots in carousel magazine." }

                launch { intakeSlider?.set(IntakeSliderState.In) }
                launch { intakeRollers?.set(0.Percent) }
                rumble.set(TwoSided(100.Percent, 0.Percent))
            } else {
                launch { feederRoller?.set(0.Rpm) }
                carousel.set(angle)
                launch { carousel.set(angle, 0.Degree) }

                launch { intakeSlider?.set(IntakeSliderState.Out) }
                launch { intakeRollers?.optimalEat(drivetrain, electrical) }

                log(Debug) { "Waiting for a yummy mouthful of balls." }

                carousel.delayUntilBall()
                carousel.state.push()
            }
        }
    }
}

suspend fun Subsystems.vision() {
    if (turret == null) {
        log(Error) { "Need turret for vision" }
        freeze()
    } else startChoreo("Vision Turret")
    {
        val reading1 by limelight.hardware.readings.readEagerly().withoutStamps
        val turretPos by turret.hardware.position.readEagerly().withoutStamps
        val robotPosition by drivetrain.hardware.position.readEagerly().withoutStamps
        val pitch by drivetrain.hardware.pitch.readEagerly().withoutStamps

        choreography {
            launch { turret.fieldOrientedPosition(drivetrain) }

            val reading = reading1
            if (reading?.pipeline == null) {
                log(Error) { "Limelight reading1 == $reading" }
                return@choreography
            }

            launch { limelight.set(reading.pipeline) }
            if (reading == null) {
                launch { turret.fieldOrientedPosition(drivetrain) }
            } else {
                launch {
                    turret.set(turretPos - reading.tx)
                }
                limelight.hardware.conversions.goalPositions(reading, robotPosition.bearing, pitch)
//                turret.fieldOrientedPosition(drivetrain, turretPos - reading.tx)}}
                withTimeout(1.Second) { limelight.autoZoom() }
            }
        }

    }

}

suspend fun Subsystems.visionAim() {
    if (flywheel == null || feederRoller == null || turret == null) {
        log(Error) { "Need flywheel and feederRoller for vision aiming" }
        freeze()
    } else startChoreo("Vision Aim") {

        val reading by limelight.hardware.readings.readEagerly().withoutStamps
        val robotPosition by drivetrain.hardware.position.readEagerly().withoutStamps
        val turretPosition by turret.hardware.position.readEagerly().withoutStamps
        val pitch by drivetrain.hardware.pitch.readEagerly().withoutStamps

        choreography {
            scope.launch { withTimeout(7.Second) { flashlight?.set(On) } }
            launch { turret.fieldOrientedPosition(drivetrain) }

            val reading1 = reading
            if (reading1?.pipeline == null) {
                log(Error) { "Limelight reading1 == $reading1" }
                return@choreography
            }

            launch { limelight.set(reading1.pipeline) }

            val snapshot1 = bestShot(
                limelight.hardware.conversions.goalPositions(reading1, robotPosition.bearing, pitch)
            )
            if (snapshot1 == null) {
                log(Warning) { "Couldn't find snapshot1 or no shots possible" }
                coroutineContext[Job]!!.cancelChildren()
                return@choreography
            }

            launch {
                turret.fieldOrientedPosition(
                    drivetrain,
                    turretPosition - reading1.tx + limelight.hardware.conversions.mountingBearing
                )
            }

            withTimeout(1.Second) { limelight.autoZoom() }

            val reading2 = reading
            if (reading2?.pipeline == null) {
                log(Error) { "Limelight reading2 == $reading2" }
                return@choreography
            }

            launch { limelight.set(reading2.pipeline) }

            val snapshot2 = bestShot(
                limelight.hardware.conversions.goalPositions(reading2, robotPosition.bearing, pitch)
            )
            if (snapshot2 == null) {
                log(Error) { "Couldn't find snapshot2 or no shots possible" }
                coroutineContext[Job]!!.cancelChildren()
                return@choreography
            }

            launch {
                turret.fieldOrientedPosition(
                    drivetrain,
                    turretPosition - reading2.tx + limelight.hardware.conversions.mountingBearing
                )
            }
            spinUpShooter(snapshot2.flywheel, snapshot2.hood)
        }
    }
}

suspend fun Subsystems.fire() = startChoreo("Fire") {
    choreography {
        val angle = carousel.state.shootAngle()
        if (angle == null) {
            log(Warning) { "I feel empty. I want to eat some balls." }
            withTimeout(2.Second) { rumble.set(TwoSided(100.Percent, 0.Percent)) }
        } else {
            launch {
                carousel.set(angle, 0.CarouselSlot)
            }

            log(Debug) { "Waiting for ball to launch." }
            withTimeout(1.5.Second) {
                flywheel?.delayUntilBall()
            } ?: log(Error) { "Did not detect ball launch. Assuming slot was actually empty." }
            carousel.state.pop()

            coroutineContext[Job]!!.cancelChildren()
            delay(100.milli(Second)) // Prevent accidentally shooting twice
        }
    }
}

suspend fun Subsystems.fireAll() = startChoreo("Fire") {
    choreography {
        carousel.set(carousel.fireAllDutycycle)
    }
}

suspend fun Subsystems.spinUpShooter(flywheelTarget: AngularVelocity, hoodTarget: ShooterHoodState) {
    if (flywheel == null || feederRoller == null) {
        log(Error) { "Need flywheel and feeder to spin up shooter" }
        freeze()
    } else startChoreo("Spin Up Shooter") {


        val flywheelSpeed by flywheel.hardware.speed.readEagerly().withoutStamps
        val feederSpeed by feederRoller.hardware.speed.readEagerly().withoutStamps

        choreography {
            launch { feederRoller.set(0.Rpm) }

            carousel.state.shootInitialAngle()?.let { angle ->
                carousel.set(angle)

                launch { flywheel.set(flywheelTarget) }
                launch { feederRoller.set(feederRoller.feedSpeed) }

                fun feederCheck() = feederSpeed in feederRoller.feedSpeed `±` feederRoller.tolerance
                fun flywheelCheck() = flywheelSpeed in flywheelTarget `±` flywheel.tolerance

                log(Debug) { "Waiting for feeder roller to get up to speed" }
                withTimeout(5.Second) {
                    delayUntil(predicate = ::feederCheck)
                } ?: log(Error) {
                    "Feeder roller never got up to speed (target = ${
                        feederRoller.feedSpeed.Rpm withDecimals 0
                    } RPM, current = ${
                        feederSpeed.Rpm withDecimals 0
                    })"
                }

                log(Debug) { "Waiting for flywheel to get up to speed" }
                withTimeout(5.Second) {
                    delayUntil(predicate = ::flywheelCheck)
                } ?: log(Error) {
                    "Flywheel never got up to speed (target = ${
                        flywheelTarget.Rpm withDecimals 0
                    } RPM, current = ${
                        flywheelSpeed.Rpm withDecimals 0
                    })"
                }

                log(Debug) { "Feeder roller and flywheel set" }
                launch { shooterHood?.set(hoodTarget) }

                runWhenever({
                    feederCheck() && flywheelCheck()
                } to choreography {
                    scope.launch {
                        withTimeout(.5.Second) {
                            rumble.set(TwoSided(0.Percent, 100.Percent))
                        }
                    }
                    freeze()
                })
            }
        }
    }
}

suspend fun Subsystems.unjam() = startChoreo("Unjam") {

    val carouselPosition by carousel.hardware.position.readEagerly().withoutStamps

    choreography {
        val period = 1.Second
        val delay = 1.Second

        val initPosition = carouselPosition
        val initDirection = carousel.hardware.esc.appliedOutput.sign

        runWhile({
            carouselPosition in initPosition `±` 1.CarouselSlot
        }, {
            withTimeout(period) { carousel.set(100.Percent * -initDirection) }
            delay(delay)
            withTimeout(period) { carousel.set(50.Percent * initDirection) }
            delay(delay)
        })

        rumble.set(TwoSided(0.Percent, 100.Percent))
    }
}