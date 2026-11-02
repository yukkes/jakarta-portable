package com.example.app.core;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** The Jakarta REST application. Every endpoint lives under {@code /api}. */
@ApplicationPath("/api")
public class OrderApplication extends Application {

    /** Creates the application. The runtime finds it through {@code @ApplicationPath}. */
    public OrderApplication() {}
}
