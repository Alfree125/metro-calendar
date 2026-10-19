"""Genera i layout dei widget del progetto CalendarioAnno.

- widget_tabellone.xml: layout vero, unico per i tre formati (8 righe, data, piede).
  Cosa si vede, colori e dimensioni li decide AggiornaWidget in base alle opzioni del widget.
- widget_<formato>_anteprima.xml: anteprime statiche per il selettore dei widget.
- xml/widget_<formato>_info.xml: descrizione dei widget.
- widget_giornata.xml: il widget 2x4 con tutta la giornata, righe su due livelli.
- widget_giornata_larga.xml: la giornata in 4x2, una riga per evento.

Uso: python -I genera_widget.py <cartella res>
"""
import os
import sys

RES = sys.argv[1]
RIGHE = 8

FORMATI = {
    # nome: (righe in anteprima, testo sp, data, piede, minWidth, minHeight, celle L, celle A)
    'piccolo': (4, 12, False, True, '110dp', '110dp', 2, 2),
    'medio': (4, 13, False, False, '250dp', '110dp', 4, 2),
    'grande': (8, 14, True, False, '250dp', '250dp', 4, 4),
}

ESEMPI = [
    ('#FFFCCC0A', 'COFFEE WITH TEAM', 'LIVE'),
    ('#FF0039A6', 'CROSS-FUNCTIONAL LAUNCH READINESS', 'LIVE'),
    ('#FF0039A6', 'MORNING STANDUP', '18'),
    ('#FFFF6319', 'DEEP WORK', '52'),
    ('#FFB933AD', 'LUNCH WITH MARA', '118'),
    ('#FFEE352E', 'SEND FINAL CUT', '195'),
    ('#FF00933C', 'DENTIST APPOINTMENT', '288'),
    ('#FF00ADD0', 'DESIGN SPRINT KICKOFF', '315'),
]

LED = '''android:fontFamily="monospace"
                android:textStyle="bold"'''


def intestazione(sp, prefisso_id):
    def cella(id_, testo, larghezza, peso=''):
        return f'''
            <TextView
                {id_}
                android:layout_width="{larghezza}"
                android:layout_height="wrap_content"{peso}
                android:letterSpacing="0.05"
                android:text="@string/{testo}"
                android:textAllCaps="true"
                android:textColor="@color/led_giallo"
                android:textSize="{sp - 3}sp"
                {LED} />'''
    ln = cella(f'android:id="@+id/{prefisso_id}h_ln"' if prefisso_id else '', 'widget_ln', '24dp')
    ev = cella(f'android:id="@+id/{prefisso_id}h_ev"' if prefisso_id else '', 'widget_evento', '0dp',
               '\n                android:layout_weight="1"')
    mi = cella(f'android:id="@+id/{prefisso_id}h_min"' if prefisso_id else '', 'widget_min', 'wrap_content')
    return f'''
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal">
{ln}
{ev}
{mi}
        </LinearLayout>
'''


def riga(k, sp, esempio):
    if esempio:
        colore, titolo, minuti = ESEMPI[k]
        id_riga = id_p = id_t = id_m = id_c = ''
        pallino = f'android:src="@drawable/anteprima_pallino"\n                    android:tint="{colore}"'
        t_titolo = f'android:text="{titolo}"'
        t_min = f'android:text="{minuti}"'
        colore_min = '@color/led_verde' if minuti == 'LIVE' else '@color/led_giallo'
        vis = ''
    else:
        id_riga = f'android:id="@+id/w_r{k}"'
        id_p = f'android:id="@+id/w_r{k}_p"'
        id_c = f'android:id="@+id/w_r{k}_c"'
        id_t = f'android:id="@+id/w_r{k}_t"'
        id_m = f'android:id="@+id/w_r{k}_m"'
        pallino = t_titolo = t_min = ''
        colore_min = '@color/led_giallo'
        vis = '\n            android:visibility="gone"'
    # Nel layout vero il pallino prende la misura della bitmap (cresce col testo)
    lato = f'{sp + 3}dp' if esempio else 'wrap_content'
    return f'''
        <LinearLayout
            {id_riga}
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:gravity="center_vertical"
            android:orientation="horizontal"{vis}>

            <FrameLayout
                {id_c}
                android:layout_width="24dp"
                android:layout_height="wrap_content">

                <ImageView
                    {id_p}
                    android:layout_width="{lato}"
                    android:layout_height="{lato}"
                    {pallino} />
            </FrameLayout>

            <TextView
                {id_t}
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="@color/led_giallo"
                android:textSize="{sp}sp"
                {t_titolo}
                {LED} />

            <TextView
                {id_m}
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:textColor="{colore_min}"
                android:textSize="{sp}sp"
                {t_min}
                {LED} />
        </LinearLayout>
'''


def data(esempio):
    return f'''
        <TextView
            {'' if esempio else 'android:id="@+id/w_data"'}
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="10dp"
            android:textColor="@color/bianco"
            android:textSize="20sp"
            android:textStyle="bold"
            {'android:text="WED SEP 30"' if esempio else 'android:visibility="gone"'} />
'''


def piede(esempio):
    # Spaziatore: nei widget una semplice <View> non e' ammessa
    return f'''
        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1" />

        <LinearLayout
            {'' if esempio else 'android:id="@+id/w_piede"'}
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center_vertical"
            android:orientation="horizontal"{'' if esempio else chr(10) + '            android:visibility="gone"'}>

            <TextView
                {'' if esempio else 'android:id="@+id/w_altri"'}
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:textColor="@color/bianco"
                android:textSize="11sp"
                android:textStyle="bold"
                {'android:text="+4"' if esempio else ''} />

            <TextView
                {'' if esempio else 'android:id="@+id/w_nome"'}
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="@color/led_grigio"
                android:textSize="11sp"
                {'android:text="@string/app_name"' if esempio else ''} />
        </LinearLayout>
'''


def vuoto():
    return '''
        <TextView
            android:id="@+id/w_vuoto"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:fontFamily="monospace"
            android:textAllCaps="true"
            android:textColor="@color/led_grigio"
            android:textSize="12sp"
            android:textStyle="bold"
            android:visibility="gone" />
'''


def documento(titolo, corpo, esempio):
    strumenti = ' xmlns:tools="http://schemas.android.com/tools"' if esempio else ''
    ignora = '\n    tools:ignore="UseAppTint"' if esempio else ''
    # Lo sfondo e' un'immagine a parte: colore (setColorFilter) e trasparenza (setImageAlpha)
    # si cambiano senza perdere gli angoli arrotondati
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- {titolo}. Generato da strumenti/genera_widget.py -->
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"{strumenti}
    android:id="@+id/w_radice"
    android:layout_width="match_parent"
    android:layout_height="match_parent"{ignora}>

    <ImageView
        android:id="@+id/w_sfondo"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="fitXY"
        android:src="@drawable/bg_widget"
        android:importantForAccessibility="no" />

    <LinearLayout
        android:id="@+id/w_contenuto"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:padding="14dp">
{corpo}
    </LinearLayout>
</FrameLayout>
'''


def scrivi(percorso, testo):
    with open(os.path.join(RES, percorso), 'w', encoding='utf-8') as f:
        f.write(testo)


# Layout vero, unico
corpo = data(False) + intestazione(13, 'w_') + ''.join(riga(k, 13, False) for k in range(RIGHE)) + vuoto() + piede(False)
scrivi('layout/widget_tabellone.xml', documento('Widget a tabellone LED, come nel video', corpo, False))

for nome, (righe, sp, con_data, con_piede, larg, alt, cl, ca) in FORMATI.items():
    corpo = (data(True) if con_data else '') + intestazione(sp, '') + \
        ''.join(riga(k, sp, True) for k in range(righe)) + (piede(True) if con_piede else '')
    scrivi(f'layout/widget_{nome}_anteprima.xml', documento(f'Anteprima del widget "{nome}"', corpo, True))
    scrivi(f'xml/widget_{nome}_info.xml', f'''<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:configure="com.calendarioanno.app.ConfiguraWidgetActivity"
    android:description="@string/widget_descrizione_{nome}"
    android:initialLayout="@layout/widget_tabellone"
    android:minWidth="{larg}"
    android:minHeight="{alt}"
    android:previewImage="@mipmap/ic_launcher"
    android:previewLayout="@layout/widget_{nome}_anteprima"
    android:resizeMode="horizontal|vertical"
    android:targetCellWidth="{cl}"
    android:targetCellHeight="{ca}"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen"
    android:widgetFeatures="reconfigurable|configuration_optional" />
''')

# ---- Widget "giornata" 2x4: tutta la giornata in una colonna stretta e alta ----
RIGHE_GIORNATA = 10
ESEMPI_GIORNATA = [
    ('#FF0039A6', '8:30 AM', 'DEP', 'EARLY SYNC', True),
    ('#FFFCCC0A', '10:03 AM', 'LIVE', 'COFFEE WITH TEAM', False),
    ('#FF0039A6', '10:31 AM', '18 MIN', 'MORNING STANDUP', False),
    ('#FFFF6319', '11:05 AM', '52 MIN', 'DEEP WORK', False),
    ('#FFB933AD', '12:11 PM', '1:58', 'LUNCH WITH MARA', False),
    ('#FFEE352E', '3:15 PM', '5:02', 'SEND FINAL CUT', False),
    ('#FF00933C', '5:00 PM', '6:47', 'DENTIST', False),
]


def riga_giornata(k, esempio):
    if esempio:
        colore, ora, stato, titolo, passato = ESEMPI_GIORNATA[k]
        ids = {n: '' for n in ('r', 'c', 'p', 'o', 's', 't')}
        pallino = f'android:src="@drawable/anteprima_pallino"\n                    android:tint="{colore}"'
        lato = '14dp'
        colore_testo = '@color/led_grigio' if passato else '@color/led_giallo'
        colore_stato = '@color/led_verde' if stato == 'LIVE' else colore_testo
        testi = {'o': f'android:text="{ora}"', 's': f'android:text="{stato}"', 't': f'android:text="{titolo}"'}
        vis = ''
    else:
        ids = {n: f'android:id="@+id/g_r{k}{"" if n == "r" else "_" + n}"' for n in ('r', 'c', 'p', 'o', 's', 't')}
        pallino = ''
        lato = 'wrap_content'
        colore_testo = colore_stato = '@color/led_giallo'
        testi = {'o': '', 's': '', 't': ''}
        vis = '\n            android:visibility="gone"'
    return f"""
        <LinearLayout
            {ids['r']}
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="7dp"
            android:orientation="vertical"{vis}>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <FrameLayout
                    {ids['c']}
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="6dp">

                    <ImageView
                        {ids['p']}
                        android:layout_width="{lato}"
                        android:layout_height="{lato}"
                        {pallino} />
                </FrameLayout>

                <TextView
                    {ids['o']}
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:maxLines="1"
                    android:textColor="{colore_testo}"
                    android:textSize="11sp"
                    {testi['o']}
                    {LED} />

                <TextView
                    {ids['s']}
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="6dp"
                    android:maxLines="1"
                    android:textColor="{colore_stato}"
                    android:textSize="11sp"
                    {testi['s']}
                    {LED} />
            </LinearLayout>

            <TextView
                {ids['t']}
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="{colore_testo}"
                android:textSize="13sp"
                {testi['t']}
                {LED} />
        </LinearLayout>
"""


def data_giornata(esempio):
    return f"""
        <TextView
            {'' if esempio else 'android:id="@+id/w_data"'}
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="4dp"
            android:lineSpacingMultiplier="0.9"
            android:textColor="@color/bianco"
            android:textSize="20sp"
            android:textStyle="bold"
            {'android:text="WED&#10;SEP 30"' if esempio else ''} />

        <TextView
            {'' if esempio else 'android:id="@+id/g_titolo"'}
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:letterSpacing="0.05"
            android:text="@string/widget_giornata_titolo"
            android:textAllCaps="true"
            android:textColor="@color/led_giallo"
            android:textSize="10sp"
            {LED} />
"""


corpo = data_giornata(False) + ''.join(riga_giornata(k, False) for k in range(RIGHE_GIORNATA)) + vuoto() + piede(False)
scrivi('layout/widget_giornata.xml', documento('Widget "giornata" 2x4: tutta la giornata', corpo, False))
corpo = data_giornata(True) + ''.join(riga_giornata(k, True) for k in range(len(ESEMPI_GIORNATA)))
scrivi('layout/widget_giornata_anteprima.xml', documento('Anteprima del widget "giornata"', corpo, True))
scrivi('xml/widget_giornata_info.xml', """<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:configure="com.calendarioanno.app.ConfiguraWidgetActivity"
    android:description="@string/widget_descrizione_giornata"
    android:initialLayout="@layout/widget_giornata"
    android:minWidth="110dp"
    android:minHeight="250dp"
    android:previewImage="@mipmap/ic_launcher"
    android:previewLayout="@layout/widget_giornata_anteprima"
    android:resizeMode="horizontal|vertical"
    android:targetCellWidth="2"
    android:targetCellHeight="4"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen"
    android:widgetFeatures="reconfigurable|configuration_optional" />
""")

# ---- Widget "giornata larga" 4x2: tutta la giornata, una riga per evento ----
RIGHE_LARGA = 6


def riga_larga(k, esempio):
    if esempio:
        colore, ora, stato, titolo, passato = ESEMPI_GIORNATA[k]
        ids = {n: '' for n in ('r', 'c', 'p', 'o', 's', 't')}
        pallino = f'android:src="@drawable/anteprima_pallino"\n                    android:tint="{colore}"'
        lato = '14dp'
        colore_testo = '@color/led_grigio' if passato else '@color/led_giallo'
        colore_stato = '@color/led_verde' if stato == 'LIVE' else colore_testo
        testi = {'o': f'android:text="{ora}"', 's': f'android:text="{stato}"', 't': f'android:text="{titolo}"'}
        vis = ''
    else:
        ids = {n: f'android:id="@+id/g_r{k}{"" if n == "r" else "_" + n}"' for n in ('r', 'c', 'p', 'o', 's', 't')}
        pallino = ''
        lato = 'wrap_content'
        colore_testo = colore_stato = '@color/led_giallo'
        testi = {'o': '', 's': '', 't': ''}
        vis = '\n            android:visibility="gone"'
    return f"""
        <LinearLayout
            {ids['r']}
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:gravity="center_vertical"
            android:orientation="horizontal"{vis}>

            <TextView
                {ids['o']}
                android:layout_width="72dp"
                android:layout_height="wrap_content"
                android:maxLines="1"
                android:textColor="{colore_testo}"
                android:textSize="12sp"
                {testi['o']}
                {LED} />

            <FrameLayout
                {ids['c']}
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginEnd="6dp">

                <ImageView
                    {ids['p']}
                    android:layout_width="{lato}"
                    android:layout_height="{lato}"
                    {pallino} />
            </FrameLayout>

            <TextView
                {ids['t']}
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:ellipsize="end"
                android:maxLines="1"
                android:textColor="{colore_testo}"
                android:textSize="13sp"
                {testi['t']}
                {LED} />

            <TextView
                {ids['s']}
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:maxLines="1"
                android:textColor="{colore_stato}"
                android:textSize="12sp"
                {testi['s']}
                {LED} />
        </LinearLayout>
"""


def testa_larga(esempio):
    return f"""
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginBottom="4dp"
            android:gravity="bottom"
            android:orientation="horizontal">

            <TextView
                {'' if esempio else 'android:id="@+id/w_data"'}
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:maxLines="1"
                android:textColor="@color/bianco"
                android:textSize="17sp"
                android:textStyle="bold"
                {'android:text="WED SEP 30"' if esempio else ''} />

            <TextView
                {'' if esempio else 'android:id="@+id/g_titolo"'}
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:letterSpacing="0.05"
                android:text="@string/widget_giornata_titolo"
                android:textAllCaps="true"
                android:textColor="@color/led_giallo"
                android:textSize="10sp"
                {LED} />
        </LinearLayout>
"""


corpo = testa_larga(False) + ''.join(riga_larga(k, False) for k in range(RIGHE_LARGA)) + vuoto() + piede(False)
scrivi('layout/widget_giornata_larga.xml', documento('Widget "giornata larga" 4x2: tutta la giornata', corpo, False))
corpo = testa_larga(True) + ''.join(riga_larga(k, True) for k in range(4))
scrivi('layout/widget_giornata_larga_anteprima.xml', documento('Anteprima del widget "giornata larga"', corpo, True))
scrivi('xml/widget_giornata_larga_info.xml', """<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:configure="com.calendarioanno.app.ConfiguraWidgetActivity"
    android:description="@string/widget_descrizione_giornata_larga"
    android:initialLayout="@layout/widget_giornata_larga"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:previewImage="@mipmap/ic_launcher"
    android:previewLayout="@layout/widget_giornata_larga_anteprima"
    android:resizeMode="horizontal|vertical"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen"
    android:widgetFeatures="reconfigurable|configuration_optional" />
""")

for vecchio in ('piccolo', 'medio', 'grande'):
    p = os.path.join(RES, 'layout', f'widget_{vecchio}.xml')
    if os.path.exists(p):
        os.remove(p)

scrivi('drawable/anteprima_pallino.xml', '''<?xml version="1.0" encoding="utf-8"?>
<!-- Pallino senza lettera per le anteprime dei widget (il colore lo da' android:tint) -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="oval">
    <solid android:color="#FFFFFFFF" />
</shape>
''')
print('ok')
