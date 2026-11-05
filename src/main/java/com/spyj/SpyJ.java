package com.spyj;

import bluej.extensions2.BlueJ;
import bluej.extensions2.Extension;

public class SpyJ extends Extension {

    public void startup (BlueJ bluej) {

    }

    public void terminate () {
        
    }

    public boolean isCompatible () {
        return (getExtensionsAPIVersionMajor() >= 3);
    }

    public String getVersion () {
        return "1.0.0";
    }

    public String getName () {
        return "SpyJ";
    }

    public String getDescription () {
        return "A data analytics plugin for collecting, formatting and analysing telemetry data";
    }
}
