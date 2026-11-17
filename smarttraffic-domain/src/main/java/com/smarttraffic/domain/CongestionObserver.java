package com.smarttraffic.domain;

@FunctionalInterface
public interface CongestionObserver {
    void onCriticalChange(String zoneId, CongestionState previous, CongestionState current, double density);
}
