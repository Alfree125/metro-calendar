package com.calendarioanno.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * L'anno intero in una schermata: 12 mini-mesi in griglia (3 colonne sul telefono,
 * 4 o 6 su schermi larghi). Sotto ogni giorno con eventi c'e' una barretta
 * con i colori delle linee; i giorni passati sono attenuati.
 */
public class VistaAnno extends View {

    public interface Ascoltatore {
        void onGiornoToccato(LocalDate giorno);

        void onGiornoTenuto(LocalDate giorno);

        void onCambiaAnno(int delta);
    }

    private static final int MAX_COLORI = 3;
    private static final float ALFA_PASSATO = 0.45f;

    private int anno = LocalDate.now().getYear();
    private LocalDate oggi = LocalDate.now();
    private LocalDate selezionato;
    /** Per ogni giorno dell'anno (indice dayOfYear - 1) i colori distinti dei suoi eventi. */
    private int[][] colori = new int[0][];
    private Ascoltatore ascoltatore;

    private final float dp;
    private final Paint pNumero = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pIniziali = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pMese = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pPieno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pAnello = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rettangolo = new RectF();

    private final int cTesto, cTestoVariante, cPrimario, cSuPrimario, cSuperficieMese;

    private final String[] nomiMesi = new String[12];
    private final String[] iniziali = new String[7];
    private final String[] numeri = new String[32];
    private final DayOfWeek primoGiornoSettimana;
    private final GestureDetector gesti;

    // Geometria calcolata in onMeasure
    private int colonne = 3;
    private float larghezzaMese, altezzaMese, cella, riga, altezzaTitolo, altezzaIniziali;
    private float spazioX, spazioY;

    public VistaAnno(Context context) {
        this(context, null);
    }

    public VistaAnno(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        Locale locale = Preferenze.locale(context);
        primoGiornoSettimana = WeekFields.of(locale).getFirstDayOfWeek();
        for (int m = 0; m < 12; m++) {
            String nome = Month.of(m + 1).getDisplayName(TextStyle.FULL_STANDALONE, locale);
            nomiMesi[m] = nome.substring(0, 1).toUpperCase(locale) + nome.substring(1);
        }
        for (int i = 0; i < 7; i++) {
            iniziali[i] = primoGiornoSettimana.plus(i).getDisplayName(TextStyle.NARROW_STANDALONE, locale);
        }
        for (int i = 1; i < numeri.length; i++) numeri[i] = Integer.toString(i);

        // Colori fissi da cartello: testo bianco su nero, oggi in giallo
        cTesto = ContextCompat.getColor(context, R.color.bianco);
        cTestoVariante = ContextCompat.getColor(context, R.color.grigio);
        cPrimario = ContextCompat.getColor(context, R.color.giallo);
        cSuPrimario = ContextCompat.getColor(context, R.color.inchiostro);
        cSuperficieMese = ContextCompat.getColor(context, R.color.nero_mese);

        pNumero.setTextAlign(Paint.Align.CENTER);
        pIniziali.setTextAlign(Paint.Align.CENTER);
        pIniziali.setColor(cTestoVariante);
        pMese.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        pAnello.setStyle(Paint.Style.STROKE);
        pAnello.setColor(cPrimario);

        gesti = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                LocalDate g = giornoIn(e.getX(), e.getY());
                if (g != null && ascoltatore != null) {
                    playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    ascoltatore.onGiornoToccato(g);
                }
                return g != null;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                LocalDate g = giornoIn(e.getX(), e.getY());
                if (g != null && ascoltatore != null) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    ascoltatore.onGiornoTenuto(g);
                }
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (ascoltatore == null || Math.abs(vx) < 600 * dp || Math.abs(vx) < Math.abs(vy) * 1.5f) {
                    return false;
                }
                ascoltatore.onCambiaAnno(vx < 0 ? 1 : -1);
                return true;
            }
        });
    }

    public void setAscoltatore(Ascoltatore a) {
        ascoltatore = a;
    }

    public int getAnno() {
        return anno;
    }

    /** Cambia anno; i colori arrivano dopo con {@link #setColori}. */
    public void setAnno(int anno) {
        if (this.anno == anno) return;
        this.anno = anno;
        colori = new int[0][];
        selezionato = null;
        aggiornaDescrizione();
        invalidate();
    }

    public void setColori(int anno, int[][] colori) {
        if (anno != this.anno) return;
        this.colori = colori;
        invalidate();
    }

    public void setSelezionato(LocalDate giorno) {
        selezionato = giorno;
        invalidate();
    }

    /** Da chiamare quando si torna nell'app: il giorno potrebbe essere cambiato. */
    public void aggiornaOggi() {
        LocalDate adesso = LocalDate.now();
        if (!adesso.equals(oggi)) {
            oggi = adesso;
            invalidate();
        }
    }

    private void aggiornaDescrizione() {
        setContentDescription(getContext().getString(R.string.descrizione_anno, anno));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        aggiornaDescrizione();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int larghezza = MeasureSpec.getSize(widthMeasureSpec);
        float larghezzaDp = larghezza / dp;
        colonne = larghezzaDp >= 840 ? 6 : larghezzaDp >= 560 ? 4 : 3;
        int righe = 12 / colonne;

        spazioX = 8 * dp;
        spazioY = 8 * dp;
        float utile = larghezza - getPaddingLeft() - getPaddingRight() - spazioX * (colonne - 1);
        larghezzaMese = utile / colonne;
        cella = larghezzaMese / 7f;
        riga = cella * 1.05f;
        altezzaTitolo = cella * 1.5f;
        altezzaIniziali = cella * 0.9f;
        altezzaMese = altezzaTitolo + altezzaIniziali + 6 * riga + cella * 0.15f;

        pNumero.setTextSize(cella * 0.56f);
        pIniziali.setTextSize(cella * 0.46f);
        pMese.setTextSize(cella * 0.74f);
        pAnello.setStrokeWidth(Math.max(1.5f * dp, cella * 0.08f));

        int altezza = (int) Math.ceil(getPaddingTop() + getPaddingBottom()
                + righe * altezzaMese + (righe - 1) * spazioY);
        setMeasuredDimension(larghezza, altezza);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean annoCorrente = anno == oggi.getYear();
        for (int m = 0; m < 12; m++) {
            float x0 = getPaddingLeft() + (m % colonne) * (larghezzaMese + spazioX);
            float y0 = getPaddingTop() + (m / colonne) * (altezzaMese + spazioY);
            boolean meseCorrente = annoCorrente && oggi.getMonthValue() == m + 1;

            // Il mese corrente ha uno sfondo leggero, per trovarlo subito
            if (meseCorrente) {
                pPieno.setColor(cSuperficieMese);
                rettangolo.set(x0 - 2 * dp, y0, x0 + larghezzaMese + 2 * dp, y0 + altezzaMese);
                canvas.drawRoundRect(rettangolo, 8 * dp, 8 * dp, pPieno);
            }

            pMese.setColor(meseCorrente ? cPrimario : cTesto);
            canvas.drawText(nomiMesi[m], x0 + cella * 0.2f, y0 + altezzaTitolo * 0.72f, pMese);

            float yIniziali = y0 + altezzaTitolo + altezzaIniziali * 0.6f;
            for (int i = 0; i < 7; i++) {
                canvas.drawText(iniziali[i], x0 + cella * (i + 0.5f), yIniziali, pIniziali);
            }

            LocalDate primo = LocalDate.of(anno, m + 1, 1);
            int sfasamento = (primo.getDayOfWeek().getValue() - primoGiornoSettimana.getValue() + 7) % 7;
            int giorni = primo.lengthOfMonth();
            int giornoAnno0 = primo.getDayOfYear() - 1;
            float yGriglia = y0 + altezzaTitolo + altezzaIniziali;

            for (int d = 1; d <= giorni; d++) {
                int indice = sfasamento + d - 1;
                float cx = x0 + cella * (indice % 7 + 0.5f);
                float yRiga = yGriglia + riga * (indice / 7);
                float cy = yRiga + riga * 0.42f;
                disegnaGiorno(canvas, m + 1, d, giornoAnno0 + d - 1, cx, cy, yRiga);
            }
        }
    }

    private void disegnaGiorno(Canvas canvas, int mese, int giorno, int giornoAnno0,
                               float cx, float cy, float yRiga) {
        boolean eOggi = anno == oggi.getYear() && mese == oggi.getMonthValue() && giorno == oggi.getDayOfMonth();
        boolean passato = !eOggi && (anno < oggi.getYear() || (anno == oggi.getYear()
                && (mese < oggi.getMonthValue() || (mese == oggi.getMonthValue() && giorno < oggi.getDayOfMonth()))));
        boolean eSelezionato = selezionato != null && selezionato.getYear() == anno
                && selezionato.getMonthValue() == mese && selezionato.getDayOfMonth() == giorno;
        float raggio = cella * 0.42f;

        if (eOggi) {
            pPieno.setColor(cPrimario);
            canvas.drawCircle(cx, cy, raggio, pPieno);
        }
        if (eSelezionato && !eOggi) {
            canvas.drawCircle(cx, cy, raggio - pAnello.getStrokeWidth() / 2, pAnello);
        }

        int colore = eOggi ? cSuPrimario : cTesto;
        if (passato) colore = conAlfa(cTestoVariante, ALFA_PASSATO);
        pNumero.setColor(colore);
        pNumero.setFakeBoldText(eOggi);
        float baseline = cy - (pNumero.descent() + pNumero.ascent()) / 2f;
        canvas.drawText(numeri[giorno], cx, baseline, pNumero);

        int[] c = giornoAnno0 < colori.length ? colori[giornoAnno0] : null;
        if (c == null || c.length == 0) return;
        float larghezzaBarra = cella * 0.62f;
        float altezzaBarra = Math.max(2f * dp, cella * 0.13f);
        float yBarra = yRiga + riga * 0.88f;
        float pezzo = larghezzaBarra / c.length;
        float x = cx - larghezzaBarra / 2f;
        for (int k = 0; k < c.length; k++) {
            pPieno.setColor(passato ? conAlfa(c[k], ALFA_PASSATO) : c[k]);
            rettangolo.set(x + pezzo * k, yBarra - altezzaBarra / 2f,
                    x + pezzo * (k + 1), yBarra + altezzaBarra / 2f);
            canvas.drawRoundRect(rettangolo, altezzaBarra / 2f, altezzaBarra / 2f, pPieno);
        }
    }

    private static int conAlfa(int colore, float alfa) {
        return Color.argb(Math.round(Color.alpha(colore) * alfa),
                Color.red(colore), Color.green(colore), Color.blue(colore));
    }

    /** Il giorno sotto il punto toccato, o null se fuori dalle celle. */
    private LocalDate giornoIn(float x, float y) {
        float lx = x - getPaddingLeft();
        float ly = y - getPaddingTop();
        if (lx < 0 || ly < 0) return null;
        int colonna = (int) (lx / (larghezzaMese + spazioX));
        int rigaMese = (int) (ly / (altezzaMese + spazioY));
        if (colonna >= colonne || rigaMese >= 12 / colonne) return null;
        float dentroX = lx - colonna * (larghezzaMese + spazioX);
        float dentroY = ly - rigaMese * (altezzaMese + spazioY) - altezzaTitolo - altezzaIniziali;
        if (dentroX > larghezzaMese || dentroY < 0 || dentroY >= 6 * riga) return null;

        int mese = rigaMese * colonne + colonna + 1;
        LocalDate primo = LocalDate.of(anno, mese, 1);
        int sfasamento = (primo.getDayOfWeek().getValue() - primoGiornoSettimana.getValue() + 7) % 7;
        int indice = (int) (dentroY / riga) * 7 + Math.min(6, (int) (dentroX / cella));
        int giorno = indice - sfasamento + 1;
        if (giorno < 1 || giorno > primo.lengthOfMonth()) return null;
        return primo.withDayOfMonth(giorno);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gesti.onTouchEvent(event) || super.onTouchEvent(event);
    }
}
