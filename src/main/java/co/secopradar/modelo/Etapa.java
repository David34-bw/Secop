package co.secopradar.modelo;

/** Las 7 etapas para presentarse a un proceso, en el orden de la guía de SECOP II. */
public enum Etapa {
    REGISTRO("Registrarte en SECOP II", "Tener cuenta de proveedor: la oferta solo se presenta por la plataforma."),
    REVISAR("Revisar el proceso", "Modalidad, valor y fecha de cierre: ¿te sirve y alcanzas a presentarte?"),
    PLIEGO("Leer la invitación o el pliego", "Completo, con el cronograma y las adendas. Anota lo que pide."),
    DOCUMENTOS("Reunir los documentos", "Lo que pide la modalidad y lo que pida este pliego en particular."),
    PROPUESTA("Preparar la propuesta económica", "Con los costos reales, incluida la seguridad social y los impuestos."),
    PRESENTAR("Presentar la oferta", "En SECOP II, antes de la fecha y hora de cierre."),
    EVALUACION("Revisar la evaluación", "Mira el informe de evaluación; si piden subsanar, hazlo dentro del plazo.");

    public final String titulo;
    public final String descripcion;

    Etapa(String titulo, String descripcion) {
        this.titulo = titulo;
        this.descripcion = descripcion;
    }

    public int numero() {
        return ordinal() + 1;
    }
}
