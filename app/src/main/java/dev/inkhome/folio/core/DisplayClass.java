package dev.inkhome.folio.core;

public enum DisplayClass {
    MONO_EINK, COLOR_EINK, EMISSIVE;

    public boolean isEink() { return this != EMISSIVE; }

    public float defaultInterfaceScale() {
        return (isEink() ? Tokens.InterfaceSize.DEFAULT_EINK : Tokens.InterfaceSize.DEFAULT_EMISSIVE) / 100f;
    }
}
