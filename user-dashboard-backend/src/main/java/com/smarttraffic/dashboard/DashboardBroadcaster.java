package com.smarttraffic.dashboard;

/** Puerto de salida (DIP): difundir una vista a los navegadores conectados. */
public interface DashboardBroadcaster {
    void broadcast(ZoneStatusView view);
}
