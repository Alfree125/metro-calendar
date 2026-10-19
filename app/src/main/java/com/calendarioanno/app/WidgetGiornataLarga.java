package com.calendarioanno.app;

/** Widget 4x2 con tutta la giornata, una riga per evento. */
public class WidgetGiornataLarga extends WidgetTabellone {
    @Override
    AggiornaWidget.Tipo tipo() {
        return AggiornaWidget.Tipo.GIORNATA_LARGA;
    }
}
