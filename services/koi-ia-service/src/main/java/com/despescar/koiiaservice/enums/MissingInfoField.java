package com.despescar.koiiaservice.enums;

/** Datos que KOI puede pedir, en el orden en que se preguntan. */
public enum MissingInfoField {
    BUDGET,
    TRAVELERS,
    ORIGIN,
    DESTINATION,
    DEPARTURE_DATE,
    /** El usuario dio solo el mes: falta el día. */
    DEPARTURE_DAY,
    /** Falta la fecha de vuelta o la cantidad de noches. */
    RETURN_OR_NIGHTS
}
