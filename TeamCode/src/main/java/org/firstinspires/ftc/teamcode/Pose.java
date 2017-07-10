package org.firstinspires.ftc.teamcode;

import android.graphics.Bitmap;
import android.util.Log;

import com.qualcomm.ftccommon.SoundPlayer;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.I2cDevice;
import com.qualcomm.robotcore.hardware.I2cDeviceSynch;
import com.qualcomm.robotcore.hardware.OpticalDistanceSensor;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.hardware.bosch.BNO055IMU;
import com.vuforia.Image;
import com.vuforia.PIXEL_FORMAT;

import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackableDefaultListener;

import org.firstinspires.ftc.robotcore.external.matrices.VectorF;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;

import static org.firstinspires.ftc.teamcode.util.VisionUtils.bitmapToMat;
import static org.firstinspires.ftc.teamcode.util.VisionUtils.getImageFromFrame;


/**
 * The Pose class stores the current real world position/orientation: <b>position</b>, <b>heading</b>,
 * and <b>speed</b> of the robot.
 *
 * This class should be a point of reference for any navigation classes that want to know current
 * orientation and location of the robot.  The update method must be called regularly, it
 * monitors and integrates data from the orientation (IMU) and odometry (motor encoder) sensors.
 * @author Max Virani, Tycho Virani
 * @version 1.2
 * @since 2016-12-10
 */

public class Pose
{

    HardwareMap hwMap;

    //motors

    PIDController drivePID = new PIDController(0, 0, 0);

    public  double KpDrive = 0.05; //proportional constant multiplier
    private double KiDrive = 0.000; //integral constant multiplier
    private double KdDrive = 0.03; //derivative constant multiplier
    private double driveIMUBasePower = .5;
    private double motorPower = 0;

    DcMotor motorFront = null;

    DcMotor motorBack = null;


    DcMotor motorNeck = null; //
    DcMotor headLamp        = null; //front white LED string
    //DcMotor redLamps        = null; //side red highlight LED strings
    Servo servoPan = null; //gate for the particle launcher
    Servo servoTilt = null;
    Servo servoSteerFront = null;
    Servo servoSteerBack = null;

    BNO055IMU imu; //Inertial Measurement Unit: Accelerometer and Gyroscope combination sensor
    Orientation angles; //feedback from the IMU

    OpticalDistanceSensor beaconPresentRear;
    OpticalDistanceSensor beaconPresent;


    byte[] beaconColorCache = new byte[100]; //
    //byte[] ballColorCache = new byte[100];

    I2cDevice beaconColorSensor;
    I2cDevice ballColorSensor;
    I2cDeviceSynch beaconColorReader; //senses the color of the
    //I2cDeviceSynch ballColorReader;
    long beaconColor; //numerical feedback from the beacon color sensor (2-3 = blue, 9-12 = red)
    //long ballColor;
    double beaconDistAft; //holds most recent linearized distance reading from ODS sensor
    double beaconDistFore;

    private double powerFront = 0;
    private double powerFrontRight = 0;
    private double powerBack = 0;
    private double powerBackRight  = 0;
    private double powerConveyor   = 0;
    static  int ticksPerRot        = 1680;



    private long flingTimer = 0;
    private int flingSpeed  = 5000; //ticks per second
    private int TPM_Forward = 2439; //measurement was for the original 42 tooth driven sprocket, since replaced by a 32 tooth sprocket
    private int TPM_Strafe  = 3145 ; //measurement was for the original 42 tooth driven sprocket, since replaced by a 32 tooth sprocket

    private double poseX;
    private double poseY;
    private double poseHeading; //current heading in degrees. Might be rotated by 90 degrees from imu's heading when strafing
    private double poseHeadingRad; //current heading converted to radians
    private double poseSpeed;
    private double posePitch;
    private double poseRoll;
    private long timeStamp; //timestamp of last update
    private boolean initialized = false;
    public  double offsetHeading;
    private double offsetPitch;
    private double offsetRoll;
    private double displacement;
    private double displacementPrev;
    private double odometer;
    static double scanSpeed = .25;
    private long presserTimer = 0;
    private long presserSavedTime = 0;
    private double zeroHeading = 0;
    private long turnTimer = 0;
    private boolean turnTimerInit = false;
    private double minTurnError = 1.0;
    public boolean maintainHeadingInit = false;;
    private double poseSavedHeading = 0.0;
    private double[] headPosition = new double[2];
    private double headZeroPan = .5;//1500;
    private double headZeroTilt = .5;//1500;

    SoundPlayer deadShotSays = SoundPlayer.getInstance(); //plays audio feedback from the robot controller phone

    private long ticksLeftPrev;
    private long ticksRightPrev;
    private long ticksLeftOffset; //provide a way to offset (effectively reset) the motor encoder readings
    private long ticksRightOffset;
    private double wheelbase; //the width between the wheels

    private VectorF vuTrans; //vector that calculates the position of the vuforia target relative to the phone (mm)
    private double vuPanAngle; //angle of the vuforia target from the center of the phone camera (degrees)
    private double vuTiltAngle;
    private double vuDepth = 0; //calculated distance from the vuforia target on the z axis (mm)
    private double vuXOffset = 0; //calculated distance from the vuforia target on the x axis (mm)

    public enum MoveMode{
        forward,
        backward,
        left,
        right,
        rotate,
        still;
    }

    protected MoveMode moveMode;



    Orientation imuAngles; //pitch, roll and yaw from the IMU
    protected boolean targetAngleInitialized = false;
    private int beaconState = 0; //switch variable that controls progress through the beacon pressing sequence


    /**
     * Create a Pose instance that stores all real world position/orientation elements:
     * <var>x</var>, <var>y</var>, <var>heading</var>, and <var>speed</var>.
     *
     * @param x     The position relative to the x axis of the field
     * @param y     The position relative to the y axis of the field
     * @param heading The heading of the robot
     * @param speed The speed of the robot
     */
    public Pose(double x, double y, double heading, double speed)
    {

        poseX     = x;
        poseY     = y;
        poseHeading = heading;
        poseSpeed = speed;
        posePitch = 0;
        poseRoll = 0;
        headPosition[0] = headZeroPan;
        headPosition[1] = headZeroTilt;

    }

    /**
     * Creates a Pose instance with _0 speed, to prevent muscle fatigue
     * by excess typing demand on the software team members.
     *
     * @param x     The position relative to the x axis of the field
     * @param y     The position relative to the y axis of the field
     * @param angle The vuPanAngle of the robot
     */
    public Pose(double x, double y, double angle)
    {

        poseX     = x;
        poseY     = y;
        poseHeading = angle;
        poseSpeed = 0;
        headPosition[0] = headZeroPan;
        headPosition[1] = headZeroTilt;

    }

    /**
     * Creates a base Pose instance at the origin, (_0,_0), with _0 speed and _0 vuPanAngle.
     * Useful for determining the Pose of the robot relative to the origin.
     */
    public Pose()
    {

        poseX     = 0;
        poseY     = 0;
        poseHeading = 0;
        poseSpeed = 0;
        posePitch=0;
        poseRoll=0;
        headPosition[0] = headZeroPan;
        headPosition[1] = headZeroTilt;

    }

    public void ResetTPM(){
        TPM_Forward = 2493;
        TPM_Strafe = 3145;
    }

    /**
     * Initializes motors, servos, lights and sensors from a given hardware map
     *
     * @param ahwMap   Given hardware map
     * @param isBlue   Tells the robot which alliance to initialize for
     */
    public void init(HardwareMap ahwMap, boolean isBlue) {
        // save reference to HW Map
        hwMap = ahwMap;
               /* eg: Initialize the hardware variables. Note that the strings used here as parameters
         * to 'get' must correspond to the names assigned during the robot configuration
         * step (using the FTC Robot Controller app on the phone).
         */
        this.motorFront = this.hwMap.dcMotor.get("motorFront");

        this.motorBack = this.hwMap.dcMotor.get("motorBack");

        this.motorNeck = this.hwMap.dcMotor.get("motorNeck");
        this.headLamp        = this.hwMap.dcMotor.get("headLamp");
        //this.redLamps        = this.hwMap.dcMotor.get("redLamps");
        this.servoPan = this.hwMap.servo.get("servoPan");
        this.servoTilt = this.hwMap.servo.get("servoTilt");
        this.servoSteerFront = this.hwMap.servo.get("servoSteerFront");
        this.servoSteerBack = this.hwMap.servo.get("servoSteerBack");

/*
        // get a reference to our distance sensors
        beaconPresentRear = hwMap.opticalDistanceSensor.get("beaconPresentRear");
        beaconPresent = hwMap.opticalDistanceSensor.get("beaconPresentFore");

        // color sensors init
        beaconColorSensor = hwMap.i2cDevice.get("beaconColorSensor");
        ballColorSensor = hwMap.i2cDevice.get("ballColorSensor");

        beaconColorReader = new I2cDeviceSynchImpl(beaconColorSensor, I2cAddr.create8bit(0x60), false);
        //ballColorReader = new I2cDeviceSynchImpl(ballColorSensor, I2cAddr.create8bit(0x64), false);

        beaconColorReader.engage();
        //ballColorReader.engage();
*/

        //motor configurations

        this.motorFront.setDirection(DcMotorSimple.Direction.REVERSE);
        this.motorBack.setDirection(DcMotorSimple.Direction.REVERSE);
        this.motorNeck.setDirection(DcMotorSimple.Direction.FORWARD);
        this.servoSteerFront.setDirection(Servo.Direction.REVERSE);
        this.headLamp.setDirection(DcMotorSimple.Direction.REVERSE);

        motorFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        motorBack.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        headLamp.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.FLOAT);

        moveMode = MoveMode.still;



        BNO055IMU.Parameters parametersIMU = new BNO055IMU.Parameters();
        parametersIMU.angleUnit            = BNO055IMU.AngleUnit.DEGREES;
        parametersIMU.accelUnit            = BNO055IMU.AccelUnit.METERS_PERSEC_PERSEC;
        parametersIMU.loggingEnabled       = true;
        parametersIMU.loggingTag           = "IMU";

        //imu = hardwareMap.get(BNO055IMU.class, "imu");
        //imu = (BNO055IMU)hwMap.get("imu");
        imu = hwMap.get(BNO055IMU.class, "imu");
        imu.initialize(parametersIMU);

        HeadLampOn();
//        RedLampOn();
        //Set the MR color sensors to passive mode - NEVER DO THIS IN A LOOP - LIMITED NUMBER OF MODE WRITES TO DEVICE
//        beaconColorReader.write8(3, 1);    //Set the mode of the color sensor using LEDState
//        ballColorReader.write8(3, 1);    //Set the mode of the color sensor using LEDState

    }

    public void HeadLampOn(){
        headLamp.setPower(1);
    }
    public void HeadLampOff(){
        headLamp.setPower(0);
    }
/*    public void RedLampOn(){
        redLamps.setPower(1);
    }
    public void RedLampOff(){
        redLamps.setPower(0);
    }*/

    /**
     * Moves the mecanum platform under PID control applied to the rotation of the robot. This version can either drive forwards/backwards or strafe.
     *
     * @param Kp   proportional constant multiplier
     * @param Ki   integral constant multiplier
     * @param Kd   derivative constant multiplier
     * @param pwr  base motor power before correction is applied
     * @param currentAngle   current angle of the robot in the coordinate system of the sensor that provides it- should be updated every cycle
     * @param targetAngle   the target angle of the robot in the coordinate system of the sensor that provides the current angle

     */
    public void MovePID(double Kp, double Ki, double Kd, double pwr, double currentAngle, double targetAngle) {
        //if (pwr>0) PID.setOutputRange(pwr-(1-pwr),1-pwr);
        //else PID.setOutputRange(pwr - (-1 - pwr),-1-pwr);
        drivePID.setOutputRange(-.5,.5);
        drivePID.setPID(Kp, Ki, Kd);
        drivePID.setSetpoint(targetAngle);
        drivePID.enable();

        drivePID.setInputRange(0, 360);
        drivePID.setContinuous();
        drivePID.setInput(currentAngle);
        double correction = drivePID.performPID();
        /*ArrayList toUpdate = new ArrayList();
        toUpdate.add((PID.m_deltaTime));
        toUpdate.add(Double.valueOf(PID.m_error));
        toUpdate.add(new Double(PID.m_totalError));
        toUpdate.add(new Double(PID.pwrP));
        toUpdate.add(new Double(PID.pwrI));
        toUpdate.add(new Double(PID.pwrD));*/
/*
        logger.UpdateLog(Long.toString(System.nanoTime()) + ","
                + Double.toString(PID.m_deltaTime) + ","
                + Double.toString(pose.getOdometer()) + ","
                + Double.toString(PID.m_error) + ","
                + Double.toString(PID.m_totalError) + ","
                + Double.toString(PID.pwrP) + ","
                + Double.toString(PID.pwrI) + ","
                + Double.toString(PID.pwrD) + ","
                + Double.toString(correction));
        motorLeft.setPower(pwr + correction);
        motorRight.setPower(pwr - correction);

*/

        driveMixer(pwr, correction);
    }

    public double DistancePID(double Kp, double Ki, double Kd, double pwr, double currentDistance, double targetDistance) {
        //if (pwr>0) PID.setOutputRange(pwr-(1-pwr),1-pwr);
        //else PID.setOutputRange(pwr - (-1 - pwr),-1-pwr);
        drivePID.setOutputRange(-.1,.1);
        drivePID.setPID(Kp, Ki, Kd);
        drivePID.setSetpoint(targetDistance);
        drivePID.enable();

        //drivePID.setInputRange(0, 360);
        //drivePID.setContinuous();
        drivePID.setInput(currentDistance);
        double correction = drivePID.performPID();

        /*ArrayList toUpdate = new ArrayList();
        toUpdate.add((PID.m_deltaTime));
        toUpdate.add(Double.valueOf(PID.m_error));
        toUpdate.add(new Double(PID.m_totalError));
        toUpdate.add(new Double(PID.pwrP));
        toUpdate.add(new Double(PID.pwrI));
        toUpdate.add(new Double(PID.pwrD));*/
/*
        logger.UpdateLog(Long.toString(System.nanoTime()) + ","
                + Double.toString(PID.m_deltaTime) + ","
                + Double.toString(pose.getOdometer()) + ","
                + Double.toString(PID.m_error) + ","
                + Double.toString(PID.m_totalError) + ","
                + Double.toString(PID.pwrP) + ","
                + Double.toString(PID.pwrI) + ","
                + Double.toString(PID.pwrD) + ","
                + Double.toString(correction));
        motorLeft.setPower(pwr + correction);
        motorRight.setPower(pwr - correction);

*/

        return -correction;
    }

    /**
     * Moves the platform under PID control applied to the rotation of the robot. This version can drive forwards/backwards and uses ackerman steering
     *
     * @param Kp   proportional constant multiplier
     * @param Ki   integral constant multiplier
     * @param Kd   derivative constant multiplier
     * @param pwr  base forwards/backwards motor power before correction is applied
     * @param targetAngle   the target angle of the robot in the coordinate system of the sensor that provides the current angle
     */


    public void DriveIMU(double Kp, double Ki, double Kd, double pwr, double targetAngle){
        MovePID(Kp, Ki, Kd, pwr, poseHeading, targetAngle);
    }


    public boolean DriveIMUDistance(double Kp, double pwr, double targetAngle,boolean forwardOrLeft, double targetMeters, boolean strafe){
        if(!forwardOrLeft){
            moveMode = moveMode.backward;
            targetMeters = -targetMeters;
            pwr = -pwr;
        }
        else moveMode = moveMode.forward;
        long targetPos;
        if(strafe) targetPos = (long) targetMeters * TPM_Strafe;
        else targetPos = (long)(targetMeters * TPM_Forward);
        if(Math.abs(targetPos) > Math.abs(getAverageAbsTicks())){//we've not arrived yet
            DriveIMU(KdDrive, KiDrive, KdDrive, pwr, targetAngle);
            return false;
        }
        else { //destination achieved
            driveMixer(0, 0);
            return true;
        }
    }

    public boolean RotateIMU(double targetAngle, double maxTime){ //uses default pose PID constants and has end conditions
        if(!turnTimerInit){
            turnTimer = System.nanoTime() + (long)(maxTime * (long) 1e9);
            turnTimerInit = true;
        }
        DriveIMU(KpDrive, KiDrive, KdDrive, 0, targetAngle); //if the robot turns within a threshold of the target
        if(Math.abs(poseHeading - targetAngle) < minTurnError) {
            turnTimerInit = false;
            driveMixer(0,0);
            return true;
        }
        if(turnTimer < System.nanoTime()){ //if the robot takes too long to turn within a threshold of the target (it gets stuck)
            turnTimerInit = false;
            driveMixer(0,0);
            return true;
        }
        return false;
    }

    public void MaintainHeading(boolean buttonState){
        if(buttonState) {
            if (!maintainHeadingInit) {
                poseSavedHeading = poseHeading;
                maintainHeadingInit = true;
            }
            DriveIMU(KpDrive, KiDrive, KdDrive, 0, poseSavedHeading);
        }
        if(!buttonState){
            maintainHeadingInit = false;
        }
    }

    public void setZeroHeading(){
        setHeading(0);
    }
    public void setHeading(double angle){
        poseHeading = angle;
        initialized = false; //triggers recalc of heading offset at next IMU update cycle
    }

    public void setHeadPan(double pan){
        headPosition[0] = pan;
    }

    public void setHeadTilt(double tilt){
        headPosition[0] = tilt;
    }

    public void setHeadPos(double pan, double tilt){
        headPosition[0] = pan;
        headPosition[1] = tilt;
    }

    public void driveMixer(double forward,double steerAngle){

        double wheelAngle;

        //the wheels on argos can't turn more than 45 degrees, so crop the excess
        wheelAngle = clampDouble(-45,45,steerAngle); //
        wheelAngle   = (wheelAngle / -45 /2)+.5; //convert to range needed for servo control

        powerFront = forward;
        powerBack = forward;

        motorFront.setPower(clampMotor(powerFront));
        motorBack.setPower(clampMotor(powerBack));

        servoSteerBack.setPosition(wheelAngle);
        servoSteerFront.setPosition(wheelAngle);

    }

    public void resetMotors(boolean enableEncoders){
        motorFront.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);
        motorBack.setMode(DcMotor.RunMode.STOP_AND_RESET_ENCODER);

        if (enableEncoders) {
            motorFront.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
            motorBack.setMode(DcMotor.RunMode.RUN_USING_ENCODER);

        }
        else {
            motorFront.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            motorBack.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        }

    }

    public long getAverageTicks(){
        long averageTicks = (motorFront.getCurrentPosition() + motorBack.getCurrentPosition() )/2;
        return averageTicks;
    }
    public long getAverageAbsTicks(){
        long averageTicks = (Math.abs(motorFront.getCurrentPosition()) + Math.abs(motorBack.getCurrentPosition()) )/2;
        return averageTicks;
    }

    public double clampMotor(double power) { return clampDouble(-1, 1, power); }

    public double clampDouble(double min, double max, double value)
    {
        double result = value;
        if(value > max)
            result = max;
        if(value < min)
            result = min;
        return result;
    }



    //    public void moveTicks(double forward, double strafe, double rotate, long ticks){
//        ticks += motorFront.getCurrentPosition();
//        while(motorFront.getCurrentPosition() < ticks && opModeIsActive()){
//            telemetry.addData("Status", "Front Left Ticks: " + Long.toString(motorFront.getCurrentPosition()));
//            telemetry.update();
//            driveMixer(forward, strafe, rotate);
//        }
//    }
    public boolean driveForward(boolean forward, double targetMeters, double power){
        if(!forward){
            moveMode = moveMode.backward;
            targetMeters = -targetMeters;
            power = -power;
        }
        else moveMode = moveMode.forward;

        long targetPos = (long)(targetMeters * TPM_Forward);
        if(Math.abs(targetPos) > Math.abs(getAverageTicks())){//we've not arrived yet
            driveMixer(power, 0);
            return false;
        }
        else { //destination achieved
            driveMixer(0, 0);
            return true;
        }
    }
//    boolean rotateRelative(boolean clockwise, double targetAngle, double power){
//        moveMode = moveMode.rotate;
//        if(!clockwise){
//            targetAngle = -targetAngle;
//            power = -power;
//        }
//        if(!targetAngleInitialized) { targetAngle = targetAngle + angles.firstAngle; targetAngleInitialized = true; }
//        if(Math.abs(targetAngle) > Math.abs(angles.firstAngle)){
//            driveMixer(0, 0, power);
//            return false;
//        }
//        else {
//            driveMixer(0, 0, 0);
//            return true;
//        }
//    }



    /**
     * Set the current position of the robot in the X direction on the field
     * @param poseX
     */
    public void setPoseX(double poseX) {
        this.poseX = poseX;
    }

    /**
     * Set the current position of the robot in the Y direction on the field
     * @param poseY
     */
    public void setPoseY(double poseY) {
        this.poseY = poseY;
    }

    /**
     * Set the absolute heading (yaw) of the robot _0-360 degrees
     * @param poseHeading
     */
    public void setPoseHeading(double poseHeading) {
        this.poseHeading = poseHeading;
        initialized = false; //trigger recalc of offset on next Update
    }

    /**
     * Set the absolute pitch of the robot _0-360 degrees
     * @param posePitch
     */
    public void setPosePitch(double posePitch) {
        this.posePitch = posePitch;
        initialized = false; //trigger recalc of offset on next Update
    }

    /**
     * Set the absolute roll of the robot _0-360 degrees
     * @param poseRoll
     */
    public void setPoseRoll(double poseRoll) {
        this.poseRoll = poseRoll;
        initialized = false; //trigger recalc of offset on next Update
    }
    /**
     * Returns the x position of the robot
     *
     * @return The current x position of the robot
     */
    public double getX()
    {
        return poseX;
    }

    /**
     * Returns the y position of the robot
     *
     * @return The current y position of the robot
     */
    public double getY()
    {
        return poseY;
    }

    /**
     * Returns the angle of the robot
     *
     * @return The current angle of the robot
     */
    public double getHeading()
    {
        return poseHeading;
    }

    /**
     * Returns the speed of the robot
     *
     * @return The current speed of the robot
     */
    public double getSpeed()
    {
        return poseSpeed;
    }
    public double getPitch() {
        return posePitch;
    }

    public double getRoll() {
        return poseRoll;
    }

    public long getTPM_Forward() {
        return TPM_Forward;
    }

    public void setTPM_Forward(long TPM_Forward) { this.TPM_Forward = (int)TPM_Forward; }



    public void updateSensors(){
        // read color sensors
        //beaconColorCache = beaconColorReader.read(0x04, 1);
        //ballColorCache = ballColorReader.read(0x04, 1);
        //ballColor = (ballColorCache[0] & 0xFF);
        //beaconColor = (beaconColorCache[0] & 0xFF);

        //odsReadingLinear = Math.pow(odsReadingRaw, 0.5);
        //beaconDistAft  = Math.pow(beaconPresentRear.getLightDetected(), 0.5); //calculate linear value
        //beaconDistFore = Math.pow(beaconPresent.getLightDetected(), 0.5); //calculate linear value
        Update(imu, 0, 0);
    }

    /**
     * Update the current location of the robot. This implementation gets heading and orientation
     * from the Bosch BNO055 IMU and assumes a simple differential steer robot with left and right motor
     * encoders.
     *
     *
     * The current naive implementation assumes an unobstructed robot - it cannot account
     * for running into objects and assumes no slippage in the wheel encoders.  Debris
     * on the field and the mountain ramps will cause problems for this implementation. Further
     * work could be done to compare odometry against IMU integrated displacement calculations to
     * detect stalls and slips
     *
     * This method should be called regularly - about every 20 - 30 milliseconds or so.
     *
     * @param imu
     * @param ticksLeft
     * @param ticksRight
     */

    public void Update(BNO055IMU imu, long ticksLeft, long ticksRight){
        long currentTime = System.nanoTime();
        imuAngles= imu.getAngularOrientation().toAxesReference(AxesReference.INTRINSIC).toAxesOrder(AxesOrder.ZYX);
        if (!initialized){
            //first time in - we assume that the robot has not started moving and that orientation values are set to the current absolute orientation
            //so first set of imu readings are effectively offsets


            offsetHeading = wrapAngleMinus(poseHeading, imuAngles.firstAngle);
            offsetRoll = wrapAngleMinus(imuAngles.secondAngle, poseRoll);
            offsetPitch = wrapAngleMinus(imuAngles.thirdAngle, posePitch);

            initialized = true;
        }


        servoPan.setPosition((headPosition[0]));
        servoTilt.setPosition((headPosition[1]));

        poseHeading = wrapAngle(imuAngles.firstAngle, offsetHeading);
        posePitch = wrapAngle(imuAngles.thirdAngle, offsetPitch);
        poseRoll = wrapAngle(imuAngles.secondAngle, offsetRoll);

        //double displacement = (((double)(ticksRight - ticksRightPrev)/ticksPerMeterRight) + ((double)(ticksLeft - ticksLeftPrev)/ticksPerMeterLeft))/2.0;

        // we haven't worked out the trig of calculating displacement from any driveMixer combination, so
        // for now we are just restricting ourselves to cardinal relative directions of pure forward, backward, left and right
        // so no diagonals or rotations - if we do those then our absolute positioning fails

        switch (moveMode) {
            case forward:
            case backward:
                displacement = (getAverageTicks() - displacementPrev) * TPM_Forward;
                odometer += Math.abs(displacement);
                poseHeadingRad = Math.toRadians(poseHeading);
                break;
            case left:
                displacement = (getAverageAbsTicks() - displacementPrev) * TPM_Strafe; //todo: there might be a problem when switching between driving forward and strafing - displacementPrev may need to be updated the first time in to use the correct avgTicks calculation
                poseHeadingRad = Math.toRadians(poseHeading)- Math.PI/2; //actual heading is rotated 90 degrees counterclockwise

                break;
            case right:
                displacement = (getAverageAbsTicks() - displacementPrev) * TPM_Strafe; //todo: there might be a problem when switching between driving forward and strafing - displacementPrev may need to be updated the first time in to use the correct avgTicks calculation
                poseHeadingRad = Math.toRadians(poseHeading) + Math.PI/2; //actual heading is rotated 90 degrees clockwise

                break;
            default:
                displacement=0; //when rotating or in an undefined moveMode, ignore/reset displacement
                displacementPrev = 0;
                break;
        }

        odometer += Math.abs(displacement);
        poseSpeed = displacement / (double)(currentTime - this.timeStamp)*1000000; //meters per second when ticks per meter is calibrated

        timeStamp = currentTime;
        displacementPrev = displacement;

        poseX += displacement * Math.cos(poseHeadingRad);
        poseY += displacement * Math.sin(poseHeadingRad);

    }

    public long getTicksLeftPrev()
    {
        return ticksLeftPrev;
    }
    public long getTicksRightPrev()
    {
        return ticksRightPrev;
    }

    /**
     *
     * gets the odometer. The odometer tracks the robot's total amount of travel since the last odometer reset
     * The value is in meters and is always increasing (absolute), even when the robot is moving backwards
     * @returns odometer value
     */
    public double getOdometer() {

        return  odometer;

    }

    /**
     * resets the odometer. The odometer tracks the robot's total amount of travel since the last odometer reset
     * The value is in meters and is always increasing (absolute), even when the robot is moving backwards
     * @param distance
     */
    public void setOdometer(double distance){
        odometer = 0;
    }

    /**
     * returns the minimum difference (in absolute terms) between two angles,
     * preserves the sign of the difference
     *
     * @param angle1
     * @param angle2
     * @return
     */
    public double diffAngle(double angle1, double angle2){
        return Math.abs(angle1 - angle2) < Math.abs(angle2-angle1) ? Math.abs(angle1 - angle2) : Math.abs(angle2-angle1);
    }

    public double diffAngle2(double angle1, double angle2){

        double diff = angle1 - angle2;

        //allow wrap around

        if (Math.abs(diff) > 180)
        {
            if (diff > 0) {
                diff -= 360;
            } else {
                diff += 360;
            }
        }
        return diff;
    }


    /**
     * Apply and angular adjustment to a base angle with result wrapping around at 360 degress
     *
     * @param angle1
     * @param angle2
     * @return
     */
    public double wrapAngle(double angle1, double angle2){
        return (angle1 + angle2) % 360;
    }
    public double wrapAngleMinus(double angle1, double angle2){
        return 360-((angle1 + angle2) % 360);
    }

    double getBearingTo(double x, double y){
        double diffx = x-poseX;
        double diffy = y-poseY;
        return ( Math.toDegrees(Math.atan2( diffy, diffx)) - 90  + 360 ) % 360;
    }

    double getBearingOpposite(double x, double y){
        double diffx = x-poseX;
        double diffy = y-poseY;
        return ( Math.toDegrees(Math.atan2( diffy, diffx)) + 90 + 360 ) % 360;
    }

    double getDistanceTo(double x, double y){

        double dx = x - poseX;
        double dy = y - poseY;
        return Math.sqrt(dx*dx + dy*dy);

    }


    public static double ServoNormalize(int pulse){
        double normalized = (double)pulse;
        return (normalized - 750.0) / 1500.0; //convert mr servo controller pulse width to double on _0 - 1 scale
    }

    public boolean nearBeacon(boolean isBlue) { //did the optical distance sensor see something close enough to
        if(isBlue){                              //be one of the beacons
            return beaconDistFore > 0.08;
        }
        return beaconDistFore > .08;
    }


    public void drivePID(boolean forward, double power){

    }


    public boolean onOpposingColor(boolean isBlue){ //is the robot looking at its team's aliance color
        if(isBlue){
            return (beaconColor > 9 && beaconColor < 12);
        }
        return beaconColor == 3 || beaconColor == 2;
    }


    // Don't need this since we now have a PID version of RotateIMU
//    public boolean turnIMU(double targetAngle, double power, boolean turnRight){
//            if(turnRight)
//                driveMixer(0, 0, power);
//            else
//                driveMixer(0, 0, -power);
//            if(turnRight && targetAngle <= poseHeading)
//                return true;
//            else if(!turnRight && targetAngle >= poseHeading)
//                return true;
//            else return false;
//
//    }

    public boolean trackVuTarget(VuforiaTrackableDefaultListener beacon, double maxSpeed, int bufferDistance){
        double pwr = 0;
        maxSpeed = maxSpeed/10; //debug
        if(beacon.getPose() != null) {

            vuTrans = beacon.getRawPose().getTranslation();
            vuPanAngle = Math.toDegrees(Math.atan2(vuTrans.get(0), vuTrans.get(2)));
            vuTiltAngle = Math.toDegrees(Math.atan2(vuTrans.get(1), vuTrans.get(2)));
            vuDepth = vuTrans.get(2);
            setHeadPos(clampDouble(0.0, 1.0, headPosition[0] + vuPanAngle / 1500), clampDouble(0.0, 0.8, headPosition[1] + vuTiltAngle / 1000));
            servoSteerBack.setPosition(headPosition[0]);
            servoSteerFront.setPosition(headPosition[0]);
            //pwr = clampDouble(-maxSpeed, maxSpeed, ((vuDepth - bufferDistance)/2000.0));
            pwr=DistancePID(KpDrive, KiDrive, KdDrive, pwr, vuDepth/1000,1); //pre-converted distances to meters to get into a similar range as the output
            motorFront.setPower(pwr);
            motorBack.setPower(pwr);
            return true;

        }
        else{
//            motorFront.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
//            motorBack.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
            pwr = 0;
            return false;
        }


    }

    public void setKpDrive(double Kp){
        KpDrive = Kp;
    }

    public void setKdDrive(double Kd){
        KdDrive = Kd;
    }

    public double getKpDrive(){
        return KpDrive;
    }

    public double getKdDrive(){
        return KdDrive;
    }

    public double driveToBeacon(VuforiaTrackableDefaultListener beacon, boolean isBlue, int beaconConfig, double bufferDistance, double maxSpeed, boolean turnOnly, boolean offset) {

        //double vuDepth = 0;
        double pwr = 0;

        if (beacon.getPose() != null) {
            vuTrans = beacon.getRawPose().getTranslation();

            //todo - add a new transform that will shift our target left or right depending on beacon analysis

            if(offset){
                vuPanAngle = Math.toDegrees(Math.atan2(vuTrans.get(0) , vuTrans.get(2)));}
            else vuPanAngle = Math.toDegrees(Math.atan2(vuTrans.get(0), vuTrans.get(2)));
            vuDepth = vuTrans.get(2);

            if (turnOnly)
                pwr = 0; //(vuDepth - bufferDistance/1200.0);
            else
                // this is a very simple proportional on the distance to target - todo - convert to PID control
                pwr = clampDouble(-maxSpeed, maxSpeed, ((vuDepth - bufferDistance)/1200.0));//but this should be equivalent
            Log.i("Beacon Angle", String.valueOf(vuPanAngle));
            MovePID(KpDrive, KiDrive, KdDrive, pwr, -vuPanAngle, 0);

        } else { //disable motors if given target not visible
            vuDepth = 0;
            driveMixer(0,0);
        }//else

    return vuDepth; // 0 indicates there was no good vuforia pose - target likely not visible
    }//driveToBeacon

    public double driveToParticle( VuforiaLocalizer locale, boolean isBlue, double bufferDistance, double maxSpeed, boolean turnOnly) {

        //double vuDepth = 0;
        double pwr = 0;
        int blobx; //current x value of the centroid (center of mass) of the largest tracked blob contour
        int bloby; //current y value of the centroid of the largest tracked blob
        org.opencv.core.Rect blobBox;
        int blobHeight;
        int blobWidth;
        double maxContour = 0;
        double minContour = 1000; //smallest contour area that we will pay attention to
        double targetContour = -1; //what is the size of the maximum contour just after it is selected by touch? - serves as the target size (distance to maintain from the object)
        boolean               mIsColorSelected = false;
        Mat                   mRgba;
        Scalar mBlobColorRgba;
        Scalar mBlobColorHsv;
        ColorBlobDetector     mDetector;
        Image img;
        mDetector = new ColorBlobDetector();

        try {
            img = getImageFromFrame(locale.getFrameQueue().take(), PIXEL_FORMAT.RGB565);
        } catch (InterruptedException e) {
            img = null;
            e.printStackTrace();
        }

        //copy image into intermediate stage android bitmap which can then be used to create an opencv native Mat
        Bitmap bm = Bitmap.createBitmap(img.getWidth(), img.getHeight(), Bitmap.Config.RGB_565);
        bm.copyPixelsFromBuffer(img.getPixels());

        mRgba = bitmapToMat(bm, CvType.CV_8UC3);

        Scalar targetHue = RC.a().getTargetBlobColor();


        if (mIsColorSelected) {
            mDetector.setHsvColor(targetHue);
            mDetector.process(mRgba);
            List<MatOfPoint> contours = mDetector.getContours();
            Log.e("OpenCV", "Contours count: " + contours.size());
            //Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR, 3);

            //get the centroid (center of mass) and area for each contour

            List<Moments> mu = new ArrayList<Moments>(contours.size());
            maxContour=0;
            blobWidth = 0;
            blobHeight = 0;
            blobBox = null;

            for (int i = 0; i < contours.size(); i++) {
                mu.add(i, Imgproc.moments(contours.get(i), false));
                Moments p = mu.get(i);
                int x = (int) (p.get_m10() / p.get_m00()); //centroid in x
                int y = (int) (p.get_m01() / p.get_m00());

                //Core.circle(mRgba, new Point(x, y), 5, CONTOUR_COLOR, -1);
                double area = Imgproc.contourArea(contours.get(i));
                if (area > minContour && area > maxContour) //we have a new largest contour in the set
                {
                    maxContour=area;
                    blobx=x;
                    bloby=y;
                    blobBox=Imgproc.boundingRect(contours.get(i));
                    blobWidth=blobBox.width;
                    blobHeight = blobBox.height;
                }
            }

            if (targetContour == -1 && maxContour > 0 )
            {
                targetContour = maxContour; //new target size, thus distance to object
            }

        }
        /*
        if (beacon.getPose() != null) {
            vuTrans = beacon.getRawPose().getTranslation();

            //todo - add a new transform that will shift our target left or right depending on beacon analysis

            if(offset){vuPanAngle = Math.toDegrees(Math.atan2(vuTrans.get(0) + getBeaconOffset(isBlue, beaconConfig), vuTrans.get(2)));}
            else vuPanAngle = Math.toDegrees(Math.atan2(vuTrans.get(0), vuTrans.get(2)));
            vuDepth = vuTrans.get(2);

            if (turnOnly)
                pwr = 0; //(vuDepth - bufferDistance/1200.0);
            else
                // this is a very simple proportional on the distance to target - todo - convert to PID control
                pwr = clampDouble(-maxSpeed, maxSpeed, ((vuDepth - bufferDistance)/1200.0));//but this should be equivalent
            Log.i("Particle Angle", String.valueOf(vuPanAngle));
            MovePID(KpDrive, KiDrive, KdDrive, pwr, -vuPanAngle, 0, false);

        } else { //disable motors if given target not visible
            vuDepth = 0;
            driveMixer(0,0,0);
        }//else

*/

        return vuDepth; // 0 indicates there was no good vuforia pose - target likely not visible
    }




    public double getVuPanAngle(){
        return vuPanAngle;
    }
    public double getVuDepth(){
        return vuDepth;
    }
    public double getVuXOffset(){
        return vuXOffset;
    }





    public void resetBeaconPresserState(){
        beaconState = 0;
    }

    public double getBatteryVoltage(){
        return RC.h.voltageSensor.get("Motor Controller 1").getVoltage();
    }


}

