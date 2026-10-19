package com.calendarioanno.app;

/** Widget 2x4 con tutta la giornata. */
public class WidgetGiornata extends WidgetTabellone {
    @Override
    AggiornaWidget.Tipo tipo() {
        return AggiornaWidget.Tipo.GIORNATA;
    }
}
