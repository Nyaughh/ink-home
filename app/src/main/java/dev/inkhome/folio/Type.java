package dev.inkhome.folio;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.widget.TextView;

import dev.inkhome.folio.core.*;
import java.util.HashMap;
import java.util.Map;

public final class Type {
    private static final Map<String, Typeface> faces = new HashMap<>();

    private Type() {}

    private static synchronized Typeface asset(Context context, String filename, String fallback,
                                               int weight, boolean italic) {
        String key = filename + weight;
        Typeface face = faces.get(key);
        if (face == null) {
            try {
                face = new Typeface.Builder(context.getAssets(), "fonts/" + filename)
                        .setFontVariationSettings("'wght' " + weight)
                        .setWeight(weight).setItalic(italic).setFallback(fallback).build();
            } catch (RuntimeException ignored) { /* Some firmware rejects variable fonts. */ }
            if (face == null) face = Typeface.create(Typeface.create(fallback, Typeface.NORMAL), weight, italic);
            faces.put(key, face);
        }
        return face;
    }

    public static Typeface sans(Context context, int weight) {
        return asset(context, "FolioSans.ttf", "sans-serif", weight, false);
    }

    public static Typeface reading(Context context, boolean italic) {
        return asset(context, italic ? "Literata-Italic.ttf" : "Literata.ttf", "serif", 400, italic);
    }

    public static TextView text(Folio folio, CharSequence value, TypeScale requested, int color) {
        TypeScale role = requested.forWindow(folio.windowClass());
        TextView view = new TextView(folio.context);
        float scale = folio.uiScale * folio.textScale;
        view.setIncludeFontPadding(false);
        view.setTextColor(color);
        view.setTypeface(role == TypeScale.READING ? reading(folio.context, false)
                : sans(folio.context, role.weight(folio.displayClass)));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, role.size * scale);
        float line = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, role.line * scale,
                folio.context.getResources().getDisplayMetrics());
        view.setLineHeight(Math.round(line));
        view.setFontFeatureSettings("tnum, lnum");
        if (role.size >= Tokens.Type.TITLE_SIZE) view.setLetterSpacing(Tokens.Type.HEADING_TRACKING_EM);
        view.setText(value);
        return view;
    }
}
