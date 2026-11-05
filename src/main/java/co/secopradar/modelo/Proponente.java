package co.secopradar.modelo;

/**
 * Datos del usuario como proponente en SECOP II: solo para tenerlos a la
 * mano al llenar una oferta. Todo es opcional; null = sin llenar.
 */
public record Proponente(
        String tipoPersona,
        String tipoDocumento,
        String numeroDocumento,
        String telefono,
        String departamento,
        String ciudad,
        String profesion,
        String titulo,
        String universidad,
        String matriculaProfesional,
        Integer aniosExperiencia,
        String resumen,
        boolean registradoSecop,
        boolean tieneRup,
        String numeroRup,
        boolean sigepActualizada) {

    public static final java.util.List<String> TIPOS_PERSONA = java.util.List.of("Persona natural", "Persona jurídica");
    public static final java.util.List<String> TIPOS_DOCUMENTO = java.util.List.of(
            "Cédula de ciudadanía", "Cédula de extranjería", "NIT", "Permiso por Protección Temporal (PPT)", "Pasaporte");

    public static Proponente vacio() {
        return new Proponente("Persona natural", "Cédula de ciudadanía", null, null, null, null, null, null, null,
                null, null, null, false, false, null, false);
    }

    /** Cuántos de los datos principales están llenos, para la barra de avance. */
    public int llenos() {
        int n = 0;
        for (Object v : new Object[]{numeroDocumento, telefono, departamento, ciudad, profesion, titulo,
                aniosExperiencia, resumen}) {
            if (v != null && !v.toString().isBlank()) n++;
        }
        return n + (registradoSecop ? 1 : 0);
    }

    public static final int TOTAL_DATOS = 9;
}
