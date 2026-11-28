package dev.inkhome.folio.core;

/** Hardware observations, independent of the selected rendering profile. */
public final class Capabilities {
    public final boolean epdControl, regal, pageKeys, frontlight, warmLight;
    public final boolean wifi, bluetooth, colour, mediaVolume, rotation, hardwareKeyboard;

    public Capabilities(boolean epdControl, boolean regal, boolean pageKeys, boolean frontlight,
                        boolean warmLight, boolean wifi, boolean bluetooth, boolean colour, boolean mediaVolume,
                        boolean rotation, boolean hardwareKeyboard) {
        this.epdControl = epdControl;
        this.regal = regal;
        this.pageKeys = pageKeys;
        this.frontlight = frontlight;
        this.warmLight = warmLight;
        this.wifi = wifi;
        this.bluetooth = bluetooth;
        this.colour = colour;
        this.mediaVolume = mediaVolume;
        this.rotation = rotation;
        this.hardwareKeyboard = hardwareKeyboard;
    }
}
