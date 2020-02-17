package com.example.foregroundserviceproject;

public class BeaconAddedData {
    private boolean available = false; // false: 사용 불가, true: 사용 가능
    private boolean trafficLightOn = false; // false:빨간불, true:초록불
    private int crosswalkRotate = 0; // -180~180도
    private int remainingTime = 255; // 0~255초

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isTrafficLightOn() {
        return trafficLightOn;
    }

    public void setTrafficLight(boolean trafficLight) {
        this.trafficLightOn = trafficLight;
    }

    public int getCrosswalkRotate() {
        return crosswalkRotate;
    }

    public void setCrosswalkRotate(int crosswalkRotate) {
        this.crosswalkRotate = crosswalkRotate;
    }

    public int getRemainingTime() {
        return remainingTime;
    }

    public void setRemainingTime(int remainingTime) {
        this.remainingTime = remainingTime;
    }
}
