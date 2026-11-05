package co.secopradar.modelo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** Un archivo que el usuario subió (sin el contenido, que se pide aparte al abrirlo). */
public record Documento(int id, int usuarioId, TipoDocumento tipo, String nombreArchivo, long tamano,
                        LocalDate vence, OffsetDateTime subidoAt) {

    public enum Vigencia { VIGENTE, POR_VENCER, VENCIDO }

    /** Días antes del vencimiento en que se avisa. */
    public static final int DIAS_AVISO = 15;

    public Vigencia vigencia() {
        return vigencia(LocalDate.now());
    }

    public Vigencia vigencia(LocalDate hoy) {
        if (vence == null) return Vigencia.VIGENTE;
        if (vence.isBefore(hoy)) return Vigencia.VENCIDO;
        return ChronoUnit.DAYS.between(hoy, vence) <= DIAS_AVISO ? Vigencia.POR_VENCER : Vigencia.VIGENTE;
    }

    public String tamanoTexto() {
        if (tamano < 1024) return tamano + " B";
        if (tamano < 1024 * 1024) return Math.round(tamano / 1024.0) + " KB";
        return String.format("%.1f MB", tamano / (1024.0 * 1024.0));
    }
}
