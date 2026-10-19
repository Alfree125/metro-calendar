package com.calendarioanno.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.core.graphics.ColorUtils;

import java.util.Locale;

/**
 * Pallino di linea in stile metro di New York: cerchio pieno col colore della linea e
 * il suo codice, bianco o nero a seconda di cosa si legge meglio. Sulla riga gialla
 * un pallino giallo diventa un anello nero, come nel video.
 */
final class DrawablePallino extends Drawable {

    private static final int INCHIOSTRO = 0xFF111111;

    private final Paint cerchio = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint testo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint anello = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String lettera;
    private final int dimensione;
    private boolean selezionato;
    private boolean contorno;

    /** @param dimensione lato intrinseco in pixel, -1 per adattarsi alla vista */
    DrawablePallino(int colore, String lettera, int dimensione) {
        this.lettera = lettera == null ? "" : lettera;
        this.dimensione = dimensione;
        cerchio.setColor(colore);
        testo.setColor(chiaro(colore) ? INCHIOSTRO : 0xFFFFFFFF);
        testo.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        testo.setTextAlign(Paint.Align.CENTER);
        anello.setStyle(Paint.Style.STROKE);
        anello.setColor(0xFFFFFFFF);
    }

    static boolean chiaro(int colore) {
        return ColorUtils.calculateLuminance(colore) > 0.45;
    }

    /** Iniziale da mostrare per un nome (prima lettera o cifra). */
    static String iniziale(String nome) {
        if (nome != null) {
            for (int i = 0; i < nome.length(); i++) {
                char c = nome.charAt(i);
                if (Character.isLetterOrDigit(c)) return String.valueOf(c).toUpperCase(Locale.ROOT);
            }
        }
        return "";
    }

    /** Il pallino disegnato su una bitmap, per i widget. */
    static Bitmap bitmap(int colore, String lettera, int lato) {
        Bitmap b = Bitmap.createBitmap(lato, lato, Bitmap.Config.ARGB_8888);
        DrawablePallino d = new DrawablePallino(colore, lettera, lato);
        d.setBounds(0, 0, lato, lato);
        d.draw(new Canvas(b));
        return b;
    }

    /** Anello bianco attorno, per il colore scelto nel selettore. */
    void setSelezionato(boolean s) {
        selezionato = s;
        invalidateSelf();
    }

    /** Anello e lettera nera senza riempimento (pallino chiaro su riga gialla). */
    void setContorno(boolean c) {
        contorno = c;
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float r = Math.min(b.width(), b.height()) / 2f;
        if (selezionato) {
            anello.setColor(INCHIOSTRO);
            anello.setStrokeWidth(r * 0.12f);
            canvas.drawCircle(cx, cy, r - anello.getStrokeWidth() / 2f, anello);
            r *= 0.72f;
        }
        if (contorno) {
            anello.setColor(INCHIOSTRO);
            anello.setStrokeWidth(r * 0.1f);
            canvas.drawCircle(cx, cy, r - anello.getStrokeWidth() / 2f, anello);
            testo.setColor(INCHIOSTRO);
        } else {
            canvas.drawCircle(cx, cy, r, cerchio);
        }
        if (lettera.isEmpty()) return;
        testo.setTextSize(r * (lettera.length() > 1 ? 0.85f : 1.1f));
        canvas.drawText(lettera, cx, cy - (testo.descent() + testo.ascent()) / 2f, testo);
    }

    @Override
    public int getIntrinsicWidth() {
        return dimensione;
    }

    @Override
    public int getIntrinsicHeight() {
        return dimensione;
    }

    @Override
    public void setAlpha(int alpha) {
        cerchio.setAlpha(alpha);
        testo.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        cerchio.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
