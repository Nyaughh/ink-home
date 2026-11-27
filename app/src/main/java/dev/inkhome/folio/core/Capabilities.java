package dev.inkhome.folio.core;

/** Hardware observations, independent of the selected rendering profile. */
public final class Capabilities {
    public final boolean epdControl, regal, pageKeys, frontlight, warmLight;
    public final boolean bluetooth, colour, speaker, rotation, hardwareKeyboard;

    public Capabilities(boolean epdControl, boolean regal, boolean pageKeys, boolean frontlight,
                        boolean warmLight, boolean bluetooth, boolean colour, boolean speaker,
                        boolean rotation, boolean hardwareKeyboard) {
        this.epdControl = epdControl;
        this.regal = regal;
        this.pageKeys = pageKeys;
        this.frontlight = frontlight;
        this.warmLight = warmLight;
        this.bluetooth = bluetooth;
        this.colour = colour;
        this.speaker = speaker;
        this.rotation = rotation;
        this.hardwareKeyboard = hardwareKeyboard;
    }
}
