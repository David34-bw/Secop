package co.secopradar.modelo;

import java.util.Arrays;
import java.util.List;

/**
 * Documentos que pide SECOP II para presentarse a un proceso (guía para
 * persona natural). Cada uno dice a qué grupo pertenece, si caduca y si se
 * pueden subir varios archivos (por ejemplo, certificados de experiencia).
 */
public enum TipoDocumento {

    CEDULA(Grupo.BASICO, "Cédula de ciudadanía", "Copia por ambas caras.", false, false),
    INHABILIDADES(Grupo.BASICO, "Declaración de inhabilidades",
            "Formato donde declaras que no tienes impedimentos para contratar con el Estado.", false, false),
    SEGURIDAD_SOCIAL(Grupo.BASICO, "Seguridad social al día",
            "Planilla de pago de salud, pensión y ARL (Ley 1150 de 2007, art. 23). Se renueva cada mes.", true, false),
    ANTECEDENTES(Grupo.BASICO, "Certificados de antecedentes",
            "Procuraduría, Contraloría, Policía y Registro de Deudores Alimentarios Morosos. La entidad también los revisa.", true, true),
    RUT(Grupo.COMUN, "RUT", "Registro Único Tributario. Muchas entidades lo piden.", false, false),
    CERTIFICACION_BANCARIA(Grupo.COMUN, "Certificación bancaria", "Para que la entidad te pague.", true, false),
    RUP(Grupo.MODALIDAD, "Certificado del RUP",
            "Registro Único de Proponentes de la cámara de comercio, con tu experiencia e indicadores financieros. "
                    + "Lo piden en selección abreviada, licitación y concurso de méritos.", true, false),
    HOJA_VIDA_SIGEP(Grupo.MODALIDAD, "Hoja de vida SIGEP II",
            "La piden en la prestación de servicios (contratación directa).", false, false),
    TITULO(Grupo.PROFESION, "Título profesional", "Diploma o acta de grado.", false, true),
    TARJETA_PROFESIONAL(Grupo.PROFESION, "Matrícula o tarjeta profesional",
            "Del COPNIA en ingeniería; la piden cuando el contrato exige un ingeniero.", false, false),
    EXPERIENCIA(Grupo.PROFESION, "Certificados de experiencia",
            "De contratos parecidos (desarrollo de software, soporte, redes, ciberseguridad...).", false, true),
    CERTIFICACION_TECNICA(Grupo.PROFESION, "Certificaciones técnicas",
            "De redes, seguridad o de un fabricante, si el pliego las pide.", true, true),
    OTRO(Grupo.OTROS, "Otros documentos", "Lo que pida la invitación o el pliego de un proceso.", false, true);

    /** Secciones de la pantalla, en el orden de la guía. */
    public enum Grupo {
        BASICO("Lo que piden casi siempre"),
        COMUN("Lo que piden muchas entidades"),
        MODALIDAD("Según la modalidad"),
        PROFESION("Lo propio de tu profesión"),
        OTROS("Otros");

        public final String titulo;

        Grupo(String titulo) {
            this.titulo = titulo;
        }
    }

    public final Grupo grupo;
    public final String nombre;
    public final String descripcion;
    /** Tiene fecha de vencimiento (la planilla es mensual, el RUP se renueva cada año...). */
    public final boolean vence;
    /** Se pueden guardar varios archivos de este tipo. */
    public final boolean varios;

    TipoDocumento(Grupo grupo, String nombre, String descripcion, boolean vence, boolean varios) {
        this.grupo = grupo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.vence = vence;
        this.varios = varios;
    }

    public static List<TipoDocumento> de(Grupo g) {
        return Arrays.stream(values()).filter(t -> t.grupo == g).toList();
    }
}
