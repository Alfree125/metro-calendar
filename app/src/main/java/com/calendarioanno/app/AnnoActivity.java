package com.calendarioanno.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Scelta del giorno dal tabellone: l'anno intero a colpo d'occhio, con i colori delle linee. */
public class AnnoActivity extends AppCompatActivity implements VistaAnno.Ascoltatore {

    static final String EXTRA_GIORNO = "giorno";
    private static final String STATO_ANNO = "anno";

    private final ExecutorService esecutore = Executors.newSingleThreadExecutor();
    private final Handler principale = new Handler(Looper.getMainLooper());
    private VistaAnno vistaAnno;
    private TextView titoloAnno, progressoAnno;
    private View btnAnnoOggi;
    private int anno;
    private int generazione;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Preferenze.conLingua(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_anno);

        View root = findViewById(R.id.root);
        View contenuto = findViewById(R.id.contenuto);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(barre.left, barre.top, barre.right, 0);
            contenuto.setPadding(0, contenuto.getPaddingTop(), 0, barre.bottom + dp(16));
            return WindowInsetsCompat.CONSUMED;
        });

        ((TextView) findViewById(R.id.striscia_nome)).setText(R.string.scegli_giorno);
        View chiudi = findViewById(R.id.striscia_chiudi);
        chiudi.setVisibility(View.VISIBLE);
        chiudi.setOnClickListener(v -> finish());

        vistaAnno = findViewById(R.id.vista_anno);
        titoloAnno = findViewById(R.id.titolo_anno);
        progressoAnno = findViewById(R.id.progresso_anno);
        btnAnnoOggi = findViewById(R.id.btn_anno_oggi);

        LocalDate selezionato = LocalDate.ofEpochDay(getIntent().getLongExtra(EXTRA_GIORNO, LocalDate.now().toEpochDay()));
        anno = savedInstanceState != null ? savedInstanceState.getInt(STATO_ANNO, selezionato.getYear()) : selezionato.getYear();
        vistaAnno.setAnno(anno);
        if (selezionato.getYear() == anno) vistaAnno.setSelezionato(selezionato);
        vistaAnno.setAscoltatore(this);
        findViewById(R.id.btn_anno_prec).setOnClickListener(v -> onCambiaAnno(-1));
        findViewById(R.id.btn_anno_succ).setOnClickListener(v -> onCambiaAnno(1));
        btnAnnoOggi.setOnClickListener(v -> vaiAllAnno(LocalDate.now().getYear()));
        mostraIntestazione();
        carica();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        esecutore.shutdownNow();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATO_ANNO, anno);
    }

    private void carica() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return;
        final int a = anno;
        final int gen = ++generazione;
        final Context contesto = this;
        esecutore.execute(() -> {
            int[][] colori;
            try {
                Map<Long, Linee.Linea> linee = Linee.mappa(Linee.da(contesto, Calendario.calendari(getContentResolver(), false)));
                List<Calendario.Istanza> istanze = Calendario.istanze(getContentResolver(),
                        LocalDate.of(a, 1, 1), LocalDate.of(a + 1, 1, 1), Linee.nascoste(contesto));
                colori = coloriPerGiorno(a, istanze, linee);
            } catch (SecurityException e) {
                return;
            }
            principale.post(() -> {
                if (!isDestroyed() && gen == generazione) vistaAnno.setColori(a, colori);
            });
        });
    }

    /** Fino a tre colori di linea distinti per ogni giorno dell'anno. */
    private static int[][] coloriPerGiorno(int anno, List<Calendario.Istanza> istanze, Map<Long, Linee.Linea> linee) {
        LocalDate inizioAnno = LocalDate.of(anno, 1, 1);
        LocalDate fineAnno = LocalDate.of(anno, 12, 31);
        int giorni = inizioAnno.lengthOfYear();
        int[][] colori = new int[giorni][];
        int[] conteggio = new int[giorni];
        int[][] temp = new int[giorni][3];
        for (Calendario.Istanza i : istanze) {
            Linee.Linea l = linee.get(i.idCalendario);
            int colore = l != null ? l.colore : i.colore;
            LocalDate da = i.primoGiorno.isBefore(inizioAnno) ? inizioAnno : i.primoGiorno;
            LocalDate a = i.ultimoGiorno.isAfter(fineAnno) ? fineAnno : i.ultimoGiorno;
            for (LocalDate g = da; !g.isAfter(a); g = g.plusDays(1)) {
                int k = g.getDayOfYear() - 1;
                if (conteggio[k] >= 3) continue;
                boolean presente = false;
                for (int j = 0; j < conteggio[k]; j++) presente |= temp[k][j] == colore;
                if (!presente) temp[k][conteggio[k]++] = colore;
            }
        }
        for (int k = 0; k < giorni; k++) colori[k] = conteggio[k] == 0 ? null : Arrays.copyOf(temp[k], conteggio[k]);
        return colori;
    }

    @Override
    public void onGiornoToccato(LocalDate giorno) {
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_GIORNO, giorno.toEpochDay()));
        finish();
    }

    @Override
    public void onGiornoTenuto(LocalDate giorno) {
        startActivity(new Intent(this, EventoActivity.class).putExtra(EventoActivity.EXTRA_GIORNO, giorno.toEpochDay()));
    }

    @Override
    public void onCambiaAnno(int delta) {
        vaiAllAnno(anno + delta);
    }

    private void vaiAllAnno(int nuovo) {
        if (nuovo == anno) return;
        int verso = nuovo > anno ? 1 : -1;
        anno = nuovo;
        vistaAnno.setAnno(anno);
        mostraIntestazione();
        carica();
        vistaAnno.animate().cancel();
        vistaAnno.setTranslationX(verso * 48 * getResources().getDisplayMetrics().density);
        vistaAnno.setAlpha(0f);
        vistaAnno.animate().translationX(0).alpha(1f).setDuration(220).start();
    }

    private void mostraIntestazione() {
        titoloAnno.setText(String.valueOf(anno));
        LocalDate oggi = LocalDate.now();
        boolean corrente = anno == oggi.getYear();
        progressoAnno.setVisibility(corrente ? View.VISIBLE : View.GONE);
        progressoAnno.setText(getString(R.string.progresso_anno, oggi.getDayOfYear(),
                oggi.lengthOfYear() - oggi.getDayOfYear()));
        btnAnnoOggi.setVisibility(corrente ? View.GONE : View.VISIBLE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
