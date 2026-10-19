package com.calendarioanno.app;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** "Edit Line" / "New Line" del video: anteprima, nome categoria, codice linea, colore. */
public class LineaActivity extends AppCompatActivity {

    static final String EXTRA_ID_LINEA = "id_linea";

    private static final int[] COLORI = {
            R.color.linea_blu, R.color.linea_arancio, R.color.linea_gialla, R.color.linea_rossa,
            R.color.linea_verde, R.color.linea_lime, R.color.linea_viola, R.color.linea_turchese,
            R.color.linea_marrone, R.color.linea_grigia,
    };

    private final ExecutorService esecutore = Executors.newSingleThreadExecutor();
    private final Handler principale = new Handler(Looper.getMainLooper());

    private ImageView anteprimaPallino;
    private TextView anteprimaNome, anteprimaCodice, errore;
    private EditText inNome, inCodice;
    private LinearLayout tavolozza;
    private final List<DrawablePallino> pallini = new ArrayList<>();
    private final List<Integer> coloriTavolozza = new ArrayList<>();

    private Linee.Linea linea; // null per una linea nuova
    private int colore;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Preferenze.conLingua(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_linea);

        View root = findViewById(R.id.root);
        View scorrimento = findViewById(R.id.scorrimento);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets barre = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(barre.left, barre.top, barre.right, 0);
            scorrimento.setPadding(0, 0, 0, Math.max(barre.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });

        ((TextView) findViewById(R.id.striscia_nome)).setText(Preferenze.nomeApp(this));
        findViewById(R.id.btn_chiudi).setOnClickListener(v -> finish());
        anteprimaPallino = findViewById(R.id.anteprima_pallino);
        anteprimaNome = findViewById(R.id.anteprima_nome);
        anteprimaCodice = findViewById(R.id.anteprima_codice);
        errore = findViewById(R.id.errore);
        inNome = findViewById(R.id.in_nome);
        inCodice = findViewById(R.id.in_codice);
        tavolozza = findViewById(R.id.tavolozza);

        TextWatcher aggiorna = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                errore.setVisibility(View.GONE);
                aggiornaAnteprima();
            }
        };
        inNome.addTextChangedListener(aggiorna);
        inCodice.addTextChangedListener(aggiorna);
        findViewById(R.id.btn_salva).setOnClickListener(v -> salva());

        long id = getIntent().getLongExtra(EXTRA_ID_LINEA, -1);
        final Context contesto = this;
        esecutore.execute(() -> {
            List<Linee.Linea> tutte;
            try {
                tutte = Linee.da(contesto, Calendario.calendari(getContentResolver(), false));
            } catch (SecurityException e) {
                tutte = new ArrayList<>();
            }
            List<Linee.Linea> trovate = tutte;
            principale.post(() -> {
                if (!isDestroyed()) inizializza(trovate, id, savedInstanceState == null);
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        esecutore.shutdown();
    }

    private void inizializza(List<Linee.Linea> tutte, long id, boolean primaVolta) {
        for (Linee.Linea l : tutte) if (l.id == id) linea = l;
        TextView titolo = findViewById(R.id.titolo_foglio);
        if (linea != null) {
            titolo.setText(R.string.titolo_modifica_linea);
            colore = linea.colore;
            if (primaVolta) {
                inNome.setText(linea.nome);
                inCodice.setText(linea.codice);
            }
            if (!linea.creataDaApp) {
                TextView nota = findViewById(R.id.nota_esterna);
                nota.setText(getString(R.string.linea_esterna, linea.account));
                nota.setVisibility(View.VISIBLE);
            } else {
                View elimina = findViewById(R.id.btn_elimina);
                elimina.setVisibility(View.VISIBLE);
                elimina.setOnClickListener(v -> MainActivity.confermaEliminaLinea(this, esecutore, linea, () -> {
                    AggiornaWidget.richiedi(this);
                    finish();
                }));
            }
        } else {
            titolo.setText(R.string.titolo_nuova_linea);
            // Primo colore non ancora usato da un'altra linea
            colore = ContextCompat.getColor(this, COLORI[0]);
            for (int res : COLORI) {
                int c = ContextCompat.getColor(this, res);
                boolean usato = false;
                for (Linee.Linea l : tutte) usato |= l.colore == c;
                if (!usato) {
                    colore = c;
                    break;
                }
            }
            if (primaVolta) {
                inCodice.setText(Linee.codiceLibero(tutte));
                inNome.requestFocus();
            }
        }
        preparaTavolozza();
        aggiornaAnteprima();
    }

    private void preparaTavolozza() {
        for (int res : COLORI) coloriTavolozza.add(ContextCompat.getColor(this, res));
        if (!coloriTavolozza.contains(colore)) coloriTavolozza.add(0, colore);
        int lato = Math.round(44 * getResources().getDisplayMetrics().density);
        int margine = Math.round(4 * getResources().getDisplayMetrics().density);
        for (int c : coloriTavolozza) {
            DrawablePallino d = new DrawablePallino(c, "", -1);
            d.setSelezionato(c == colore);
            pallini.add(d);
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(d);
            iv.setPadding(margine, margine, margine, margine);
            iv.setOnClickListener(v -> {
                colore = c;
                for (DrawablePallino p : pallini) p.setSelezionato(p == d);
                aggiornaAnteprima();
            });
            tavolozza.addView(iv, new LinearLayout.LayoutParams(lato, lato));
        }
    }

    private String codice() {
        String c = inCodice.getText().toString().trim().toUpperCase(Locale.ROOT);
        return c.isEmpty() ? DrawablePallino.iniziale(inNome.getText().toString()) : c;
    }

    private void aggiornaAnteprima() {
        String nome = inNome.getText().toString().trim();
        anteprimaPallino.setImageDrawable(new DrawablePallino(colore, codice(), -1));
        anteprimaNome.setText(nome.isEmpty() ? "—" : nome);
        anteprimaCodice.setText(getString(R.string.linea_codice, codice()));
    }

    private void salva() {
        String nome = inNome.getText().toString().trim();
        String codice = codice();
        if (TextUtils.isEmpty(nome) || codice.isEmpty()) {
            errore.setText(R.string.errore_nome);
            errore.setVisibility(View.VISIBLE);
            return;
        }
        int c = colore;
        final Context contesto = getApplicationContext();
        esecutore.execute(() -> {
            long id;
            try {
                if (linea == null) {
                    id = Calendario.creaCalendarioLocale(getContentResolver(), nome, c);
                } else {
                    id = linea.id;
                    if (linea.creataDaApp) Calendario.aggiornaCalendario(getContentResolver(), id, nome, c);
                }
            } catch (RuntimeException e) {
                id = -1;
            }
            long salvata = id;
            if (salvata >= 0) Linee.salva(contesto, salvata, nome, codice, c);
            principale.post(() -> {
                if (isDestroyed()) return;
                if (salvata < 0) {
                    errore.setText(R.string.errore_linea);
                    errore.setVisibility(View.VISIBLE);
                    return;
                }
                AggiornaWidget.richiedi(this);
                finish();
            });
        });
    }
}
