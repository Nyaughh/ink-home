package dev.inkhome.folio.core;

import java.util.Locale;

public enum DisplayClass {
    MONO_EINK, COLOR_EINK, EMISSIVE;

    private static String compact(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Mixed-product makers need model matches, since they also ship LCD phones and tablets. */
    public static DisplayClass detect(String manufacturer, String model, boolean epdControl) {
        String maker = compact(manufacturer);
        String id = compact(model);
        boolean hisenseInk = maker.contains("hisense") && (id.matches("(hisense)?a(5|7|9)(pro)?(c|cc)?")
                || id.equals("hitv205n") || id.equals("hlte556n") || id.startsWith("hireader") || id.startsWith("touchlite")
                || id.equals("hisensetouch") || id.equals("touch"));
        boolean nookInk = (maker.contains("barnes") || maker.contains("nook") || id.startsWith("nook"))
                && (id.contains("glowlight") || id.contains("simpletouch")
                || id.matches("bnrv(300|350|500|510|520|700|1000|1100|1300)"));
        boolean moaan = maker.contains("moaan") || id.startsWith("moaan")
                || (maker.contains("xiaomi") && (id.contains("inkpalm") || id.contains("duokan")));
        boolean otherReader = (maker.equals("hyread") && (id.equals("r08p") || id.equals("k08cc")))
                || (maker.equals("energysistem") && id.startsWith("ereader"))
                || (maker.equals("fidibo") && id.equals("fidibook"));
        boolean ink = otherReader || epdControl || hisenseInk || nookInk || moaan || maker.contains("onyx")
                || maker.equals("boox") || maker.contains("bigme") || maker.contains("boyue")
                || maker.contains("meebook") || maker.contains("tolino") || maker.contains("pocketbook")
                || maker.contains("supernote") || maker.contains("ratta") || id.startsWith("supernote");
        if (!ink) return EMISSIVE;
        boolean color = id.contains("color") || id.contains("colour") || id.contains("kaleido")
                || id.contains("gallery") || id.contains("galy") || id.contains("tabultrac")
                || id.contains("tabminic") || id.contains("noteminic") || id.matches(".*noteair[0-9]+c.*")
                || id.equals("k08cc") || id.endsWith("palma2pro") || id.matches("b(751|752|1051)c.*")
                || (hisenseInk && id.endsWith("c"));
        return color ? COLOR_EINK : MONO_EINK;
    }

    public boolean isEink() { return this != EMISSIVE; }

    public float defaultInterfaceScale() {
        return (isEink() ? Tokens.InterfaceSize.DEFAULT_EINK : Tokens.InterfaceSize.DEFAULT_EMISSIVE) / 100f;
    }
}
