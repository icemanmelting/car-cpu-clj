package javaclasses;

import eu.hansolo.enzo.gauge.Gauge;
import eu.hansolo.enzo.lcd.Lcd;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * Created by iceman on 18/07/16.
 */
public abstract class Dashboard extends Screen {
    protected Gauge speedGauge;
    protected AbsolutePositioning speedGaugeAbsPos;
    protected double speed;

    protected Image gearShift;
    protected ImageView gearShiftView;
    protected AbsolutePositioning gearShiftAbsPos;
    protected double gear;

    protected Gauge tempGauge;
    protected AbsolutePositioning tempGaugeAbsPos;
    protected double temp;

    protected Image tempImage;
    protected ImageView tempImageView;
    protected AbsolutePositioning tempImageAbsPos;

    protected Gauge rpmGauge;
    protected AbsolutePositioning rpmGaugeAbsPos;
    protected double rpm;

    protected Gauge dieselGauge;
    protected AbsolutePositioning dieselGaugeAbsPos;
    protected double diesel;

    protected Image dieselImage;
    protected ImageView dieselImageView;
    protected AbsolutePositioning dieselImageAbsPos;

    protected Lcd distanceLcd;
    protected AbsolutePositioning distanceLcdAbsPos;
    protected double distance;

    protected Lcd totalDistanceLcd;
    protected AbsolutePositioning totalDistanceLcdAbsPos;
    protected double totalDistance;

    protected AnimationTimer animationTimer;

    protected boolean brakesOil;
    protected Image brakesOilImage;
    protected ImageView brakesOilImageView;
    protected AbsolutePositioning brakesOilImageAbsPos;

    protected boolean battery;
    protected Image batteryImage;
    protected ImageView batteryImageView;
    protected AbsolutePositioning batteryImageAbsPos;

    protected boolean abs;
    protected Image absImage;
    protected ImageView absImageView;
    protected AbsolutePositioning absImageAbsPos;

    protected boolean parking;
    protected Image parkingImage;
    protected ImageView parkingImageView;
    protected AbsolutePositioning parkingImageAbsPos;

    protected boolean highBeams;
    protected Image highBeamsImage;
    protected ImageView highBeamsImageView;
    protected AbsolutePositioning highBeamsImageAbsPos;

    protected boolean oilPressure;
    protected Image oilPressureImage;
    protected ImageView oilPressureImageView;
    protected AbsolutePositioning oilPressureImageAbsPos;

    protected boolean sparkPlug;
    protected Image sparkPlugImage;
    protected ImageView sparkPlugImageView;
    protected AbsolutePositioning sparkPlugImageAbsPos;

    protected boolean turnSigns;
    protected Image turningSignsImage;
    protected ImageView turningSignsImageView;
    protected AbsolutePositioning turningSignsImageAbsPos;

    protected Image backgroundImage;
    protected ImageView backgroundImageView;
    protected AbsolutePositioning backgroundImageAbsPos;

    protected boolean ice;
    protected Image iceImage;
    protected ImageView iceImageView;
    protected AbsolutePositioning iceImageAbsPos;

    public Dashboard() {
        super();
        configureInstruments();
    }

    public synchronized Double getSpeed() {
        return speed;
    }

    public synchronized void setSpeed(double speed) {
        this.speed = speed;
    }

    public Double getRpm() {
        return rpm;
    }

    public void setRpm(double rpm) {
        this.rpm = rpm;
    }

    public double getGear() {
        return gear;
    }

    public void setGear(double gear) {
        this.gear = gear;
    }

    public synchronized double getDistance() {
        return distance;
    }

    public synchronized double getTotalDistance() {
        return totalDistance;
    }

    public synchronized void setDistance(double distance) {
        this.distance = getDistance() + distance;
    }

    public synchronized void resetDistance() {
        this.distance = 0;
    }

    public synchronized void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public double getTemp() {
        return temp;
    }

    public void setTemp(double temp) {
        this.temp = temp;
    }

    public double getDiesel() {
        return diesel;
    }

    public void setDiesel(double diesel) {
        this.diesel = diesel;
    }

    public boolean isBrakesOil() {
        return brakesOil;
    }

    public void setBrakesOil(boolean brakesOil) {
        this.brakesOil = brakesOil;
        Platform.runLater(() -> brakesOilImageView.setVisible(brakesOil));
    }

    public boolean isBattery() {
        return battery;
    }

    public void setBattery(boolean battery) {
        this.battery = battery;
        Platform.runLater(() -> batteryImageView.setVisible(battery));
    }

    public boolean isAbs() {
        return abs;
    }

    public void setAbs(boolean abs) {
        this.abs = abs;
        Platform.runLater(() -> absImageView.setVisible(abs));
    }

    public boolean isParking() {
        return parking;
    }

    public void setParking(boolean parking) {
        this.parking = parking;
        Platform.runLater(() -> parkingImageView.setVisible(parking));
    }

    public boolean isHighBeams() {
        return highBeams;
    }

    public void setHighBeams(boolean highBeams) {
        this.highBeams = highBeams;
        Platform.runLater(() -> highBeamsImageView.setVisible(highBeams));
    }

    public boolean isOilPressure() {
        return oilPressure;
    }

    public void setOilPressure(boolean oilPressure) {
        this.oilPressure = oilPressure;
        Platform.runLater(() -> oilPressureImageView.setVisible(oilPressure));
    }

    public boolean isSparkPlug() {
        return sparkPlug;
    }

    public void setSparkPlug(boolean sparkPlug) {
        this.sparkPlug = sparkPlug;
        Platform.runLater(() -> sparkPlugImageView.setVisible(sparkPlug));
    }

    public boolean isTurnSigns() {
        return turnSigns;
    }

    public void setTurnSigns(boolean turnSigns) {
        this.turnSigns = turnSigns;
        Platform.runLater(() -> turningSignsImageView.setVisible(turnSigns));
    }

    public void setIce(boolean ice) {
        this.ice = ice;
        Platform.runLater(() -> iceImageView.setVisible(ice));
    }

    public void configureInstruments() {
    }
}
