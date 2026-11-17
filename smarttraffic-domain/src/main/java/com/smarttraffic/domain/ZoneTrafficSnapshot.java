package com.smarttraffic.domain;

public record ZoneTrafficSnapshot(String zoneId, double density, CongestionState state) {}
