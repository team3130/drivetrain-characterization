/**
* This is a very simple robot program that can be used to send telemetry to
* the data_logger script to characterize your drivetrain. If you wish to use
* your actual robot code, you only need to implement the simple logic in the
* autonomousPeriodic function and change the NetworkTables update rate
*/

package dc;

import java.util.function.Supplier;

import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;
import com.ctre.phoenix.motorcontrol.can.WPI_VictorSPX;

// WPI_Talon* imports are needed in case a user has a Pigeon on a Talon
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;
import com.ctre.phoenix.sensors.PigeonIMU;
import com.kauailabs.navx.frc.AHRS;
import edu.wpi.first.wpilibj.ADXRS450_Gyro;
import edu.wpi.first.wpilibj.AnalogGyro;
import edu.wpi.first.wpilibj.interfaces.Gyro;
import edu.wpi.first.wpilibj.SerialPort;
import edu.wpi.first.wpilibj.I2C;
import edu.wpi.first.wpilibj.SPI;

import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.PWMTalonSRX;
import edu.wpi.first.wpilibj.PWMVictorSPX;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.Spark;
import edu.wpi.first.wpilibj.SpeedController;
import edu.wpi.first.wpilibj.SpeedControllerGroup;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.Victor;
import edu.wpi.first.wpilibj.VictorSP;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class Robot extends TimedRobot {

  static private double WHEEL_DIAMETER = 6.0;
  static private double ENCODER_EDGES_PER_REV = 2048.0; // falcon 500 has 2048 CPR, SRX mags have 4096 ticks per rev

  Joystick stick;
  DifferentialDrive drive;

  Supplier<Double> leftEncoderPosition;
  Supplier<Double> leftEncoderRate;
  Supplier<Double> rightEncoderPosition;
  Supplier<Double> rightEncoderRate;
  Supplier<Double> gyroAngleRadians;

  NetworkTableEntry autoSpeedEntry = NetworkTableInstance.getDefault().getEntry("/robot/autospeed");
  NetworkTableEntry telemetryEntry = NetworkTableInstance.getDefault().getEntry("/robot/telemetry");
  NetworkTableEntry rotateEntry = NetworkTableInstance.getDefault().getEntry("/robot/rotate");

  double priorAutospeed = 0;
  Number[] numberArray = new Number[10];

  @Override
  public void robotInit() {
    if (!isReal()) SmartDashboard.putData(new SimEnabler());

    stick = new Joystick(0);

    WPI_TalonFX leftMotor1 = new WPI_TalonFX(4);
    WPI_TalonFX leftMotor2 = new WPI_TalonFX(5);
    leftMotor1.configFactoryDefault();
    leftMotor2.configFactoryDefault();

    WPI_TalonFX rightMotor1 = new WPI_TalonFX(2);
    WPI_TalonFX rightMotor2 = new WPI_TalonFX(3);
    rightMotor1.configFactoryDefault();
    rightMotor2.configFactoryDefault();
    rightMotor1.setInverted(true);
    rightMotor2.setInverted(true);

    SpeedController[] leftMotors = new SpeedController[1];
    leftMotors[0] = leftMotor2;

    SpeedController[] rightMotors = new SpeedController[1];
    rightMotors[0] = rightMotor2;

    //
    // Configure gyro
    //

    // Note that the angle from the NavX and all implementors of wpilib Gyro
    // must be negated because getAngle returns a clockwise positive angle
    AHRS navx = new AHRS(SerialPort.Port.kMXP);
    gyroAngleRadians = () -> -1 * Math.toRadians(navx.getAngle());

    //
    // Configure drivetrain movement
    //

    SpeedControllerGroup leftGroup = new SpeedControllerGroup(leftMotor1, leftMotors);
    SpeedControllerGroup rightGroup = new SpeedControllerGroup(rightMotor1, rightMotors);

    drive = new DifferentialDrive(leftGroup, rightGroup);
    drive.setRightSideInverted(false); // Drivetrain inversion is already handled in motor configs
    drive.setDeadband(0);


    //
    // Configure encoder related functions -- getDistance and getrate should return
    // units and units/s
    //

    double encoderConstant = (1 / ENCODER_EDGES_PER_REV) * WHEEL_DIAMETER * Math.PI;


    leftMotor1.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Relative, 0 ,0); //TODO: check
    leftMotor1.setSensorPhase(true);
    leftEncoderPosition = () -> leftMotor1.getSelectedSensorPosition() * encoderConstant;
    leftEncoderRate = () -> leftMotor1.getSelectedSensorVelocity() * 10.0 * encoderConstant;


    rightMotor1.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Relative, 0 ,0);
    rightMotor1.setSensorPhase(true);
    rightEncoderPosition = () -> rightMotor1.getSelectedSensorPosition() * encoderConstant;
    rightEncoderRate = () -> rightMotor1.getSelectedSensorVelocity() * 10.0 * encoderConstant;

    // Set the update rate instead of using flush because of a ntcore bug
    // -> probably don't want to do this on a robot in competition
    NetworkTableInstance.getDefault().setUpdateRate(0.010);
  }

  @Override
  public void disabledInit() {
    System.out.println("Robot disabled");
    drive.tankDrive(0, 0);
  }

  @Override
    public void disabledPeriodic() {
  }

  @Override
  public void robotPeriodic() {
    // feedback for users, but not used by the control program
    SmartDashboard.putNumber("l_encoder_pos", leftEncoderPosition.get());
    SmartDashboard.putNumber("l_encoder_rate", leftEncoderRate.get());
    SmartDashboard.putNumber("r_encoder_pos", rightEncoderPosition.get());
    SmartDashboard.putNumber("r_encoder_rate", rightEncoderRate.get());
    SmartDashboard.putNumber("gyro_angle", gyroAngleRadians.get() * 360.0 / (2 * Math.PI));
  }

  @Override
  public void teleopInit() {
    System.out.println("Robot in operator control mode");
  }

  @Override
  public void teleopPeriodic() {
    drive.arcadeDrive(-stick.getY(), stick.getX());
  }

  @Override
  public void autonomousInit() {
    System.out.println("Robot in autonomous mode");
  }

  /**
  * If you wish to just use your own robot program to use with the data logging
  * program, you only need to copy/paste the logic below into your code and
  * ensure it gets called periodically in autonomous mode
  * 
  * Additionally, you need to set NetworkTables update rate to 10ms using the
  * setUpdateRate call.
  */
  @Override
  public void autonomousPeriodic() {

    // Retrieve values to send back before telling the motors to do something
    double now = Timer.getFPGATimestamp();

    double leftPosition = leftEncoderPosition.get();
    double leftRate = leftEncoderRate.get();

    double rightPosition = rightEncoderPosition.get();
    double rightRate = rightEncoderRate.get();

    double battery = RobotController.getBatteryVoltage();
    double motorVolts = battery * Math.abs(priorAutospeed);

    double leftMotorVolts = motorVolts;
    double rightMotorVolts = motorVolts;

    // Retrieve the commanded speed from NetworkTables
    double autospeed = autoSpeedEntry.getDouble(0);
    priorAutospeed = autospeed;

    // command motors to do things
    drive.tankDrive(
      (rotateEntry.getBoolean(false) ? -1 : 1) * autospeed, autospeed,
      false
    );

    // send telemetry data array back to NT
    numberArray[0] = now;
    numberArray[1] = battery;
    numberArray[2] = autospeed;
    numberArray[3] = leftMotorVolts;
    numberArray[4] = rightMotorVolts;
    numberArray[5] = leftPosition;
    numberArray[6] = rightPosition;
    numberArray[7] = leftRate;
    numberArray[8] = rightRate;
    numberArray[9] = gyroAngleRadians.get();

    telemetryEntry.setNumberArray(numberArray);
  }
}