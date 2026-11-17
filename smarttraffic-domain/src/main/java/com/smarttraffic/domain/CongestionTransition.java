package com.smarttraffic.domain;

public interface CongestionTransition {
    CongestionState resolve(double density);
}
