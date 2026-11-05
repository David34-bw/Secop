package co.secopradar.modelo.servicio;

import co.secopradar.modelo.Documento;
import co.secopradar.modelo.Preparacion;
import co.secopradar.modelo.Proponente;
import co.secopradar.modelo.TipoDocumento;
import co.secopradar.modelo.dao.DocumentoDAO;
import co.secopradar.modelo.dao.ProponenteDAO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * "Mis documentos": los datos del proponente y los archivos que pide
 * SECOP II (cédula, seguridad social, RUP...). Valida lo que se guarda y
 * calcula para qué modalidades está listo el usuario.
 */
public class DocumentosService {

    public static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    public static final List<String> EXTENSIONES = List.of("pdf", "jpg", "jpeg", "png");

    /** Errores de validación por campo (campo -> mensaje). */
    public static class ErrorDatos extends RuntimeException {
        public final Map<String, String> errores;

        public ErrorDatos(Map<String, String> errores) {
            super(errores.values().iterator().next());
            this.errores = errores;
        }
    }

    public static class ErrorArchivo extends RuntimeException {
        public ErrorArchivo(String mensaje) {
            super(mensaje);
        }
    }

    private final ProponenteDAO proponentes;
    private final DocumentoDAO documentos;

    public DocumentosService(ProponenteDAO proponentes, DocumentoDAO documentos) {
        this.proponentes = proponentes;
        this.documentos = documentos;
    }

    public Proponente datos(int usuarioId) {
        return proponentes.deUsuario(usuarioId).orElse(Proponente.vacio());
    }

    public List<Documento> documentos(int usuarioId) {
        return documentos.deUsuario(usuarioId);
    }

    public Preparacion preparacion(int usuarioId) {
        return new Preparacion(documentos(usuarioId), datos(usuarioId));
    }

    // ---- Datos del proponente ----

    /** Valida, limpia y guarda. Devuelve lo guardado. Lanza ErrorDatos con los campos malos. */
    public Proponente guardarDatos(int usuarioId, Proponente p) {
        Map<String, String> errores = new LinkedHashMap<>();
        String documento = limpiar(p.numeroDocumento());
        if (documento != null) {
            documento = documento.replace(".", "").replace(" ", "");
            boolean esNit = "NIT".equals(p.tipoDocumento());
            boolean pasaporte = p.tipoDocumento() != null && p.tipoDocumento().startsWith("Pasaporte");
            if (pasaporte ? !documento.matches("[A-Za-z0-9]{5,15}")
                    : esNit ? !documento.matches("\\d{6,12}(-\\d)?") : !documento.matches("\\d{5,12}")) {
                errores.put("numeroDocumento", esNit ? "El NIT debe tener entre 6 y 12 números (puede llevar el dígito de verificación: 900123456-7)."
                        : pasaporte ? "El pasaporte solo lleva letras y números (5 a 15)."
                        : "El número de documento solo lleva números (entre 5 y 12), sin puntos.");
            }
        }
        String telefono = limpiar(p.telefono());
        if (telefono != null) {
            telefono = telefono.replaceAll("[\\s()-]", "").replaceFirst("^\\+?57", "");
            if (!telefono.matches("3\\d{9}|60\\d{8}|\\d{7}")) {
                errores.put("telefono", "Escribe un celular de 10 dígitos (empieza por 3) o un fijo (60 + 8 dígitos).");
            }
        }
        String ciudad = limpiar(p.ciudad());
        if (ciudad != null && !ciudad.matches("[\\p{L} .'-]{2,60}")) {
            errores.put("ciudad", "La ciudad solo lleva letras.");
        }
        Integer anios = p.aniosExperiencia();
        if (anios != null && (anios < 0 || anios > 60)) {
            errores.put("aniosExperiencia", "Los años de experiencia van de 0 a 60.");
        }
        String matricula = limpiar(p.matriculaProfesional());
        if (matricula != null && !matricula.matches("[A-Za-z0-9 -]{4,30}")) {
            errores.put("matriculaProfesional", "La matrícula solo lleva letras, números y guiones (ej: 25202-123456).");
        }
        String rup = p.tieneRup() ? limpiar(p.numeroRup()) : null;
        if (rup != null && !rup.matches("[A-Za-z0-9 -]{3,30}")) {
            errores.put("numeroRup", "El número de inscripción del RUP solo lleva letras, números y guiones.");
        }
        String resumen = limpiar(p.resumen());
        if (resumen != null && resumen.length() > 2000) {
            errores.put("resumen", "El perfil profesional puede tener hasta 2.000 caracteres.");
        }
        String[][] textos = {{"profesion", p.profesion()}, {"titulo", p.titulo()}, {"universidad", p.universidad()}};
        for (String[] campo : textos) {
            String v = limpiar(campo[1]);
            if (v != null && (v.length() > 150 || !v.matches(".*\\p{L}.*"))) {
                errores.put(campo[0], "Escribe un texto válido (hasta 150 caracteres).");
            }
        }
        if (!errores.isEmpty()) throw new ErrorDatos(errores);

        Proponente limpio = new Proponente(p.tipoPersona(), p.tipoDocumento(), documento, telefono, limpiar(p.departamento()),
                ciudad, limpiar(p.profesion()), limpiar(p.titulo()), limpiar(p.universidad()), matricula, anios, resumen,
                p.registradoSecop(), p.tieneRup(), rup, p.sigepActualizada());
        proponentes.guardar(usuarioId, limpio);
        return limpio;
    }

    private static String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    // ---- Archivos ----

    /**
     * Sube un archivo. Si el tipo admite uno solo, reemplaza al anterior.
     * vence puede ser null; para la planilla de seguridad social se toma un
     * mes desde hoy si no se indica.
     */
    public Documento subir(int usuarioId, TipoDocumento tipo, Path archivo, LocalDate vence) {
        String nombre = archivo.getFileName().toString();
        String ext = nombre.contains(".") ? nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!EXTENSIONES.contains(ext)) throw new ErrorArchivo("Solo se aceptan archivos PDF, JPG o PNG.");
        byte[] datos;
        try {
            if (Files.size(archivo) > TAMANO_MAXIMO) throw new ErrorArchivo("El archivo pesa más de 5 MB. Comprímelo o escanéalo con menos resolución.");
            datos = Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new ErrorArchivo("No se pudo leer el archivo: " + e.getMessage());
        }
        if (datos.length == 0) throw new ErrorArchivo("El archivo está vacío.");
        if (!coincideFormato(ext, datos)) throw new ErrorArchivo("El archivo no es un " + ext.toUpperCase() + " válido.");
        if (vence == null && tipo == TipoDocumento.SEGURIDAD_SOCIAL) vence = LocalDate.now().plusMonths(1);
        if (vence != null && !tipo.vence) vence = null;
        if (vence != null && vence.isBefore(LocalDate.now().minusYears(1))) {
            throw new ErrorArchivo("La fecha de vencimiento no es válida.");
        }
        if (!tipo.varios) documentos.eliminarTipo(usuarioId, tipo);
        if (nombre.length() > 200) nombre = nombre.substring(0, 190) + "." + ext;
        String mime = ext.equals("pdf") ? "application/pdf" : ext.equals("png") ? "image/png" : "image/jpeg";
        int id = documentos.crear(usuarioId, tipo, nombre, mime, datos, vence);
        return documentos.porId(id, usuarioId).orElseThrow();
    }

    /** Revisa los primeros bytes para que un .pdf sea de verdad un PDF (y no un archivo renombrado). */
    static boolean coincideFormato(String ext, byte[] d) {
        return switch (ext) {
            case "pdf" -> d.length > 4 && d[0] == '%' && d[1] == 'P' && d[2] == 'D' && d[3] == 'F';
            case "png" -> d.length > 8 && (d[0] & 0xff) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            default -> d.length > 3 && (d[0] & 0xff) == 0xFF && (d[1] & 0xff) == 0xD8;
        };
    }

    public Optional<byte[]> contenido(int documentoId, int usuarioId) {
        return documentos.contenido(documentoId, usuarioId);
    }

    public Optional<Documento> documento(int documentoId, int usuarioId) {
        return documentos.porId(documentoId, usuarioId);
    }

    public void eliminar(int documentoId, int usuarioId) {
        documentos.eliminar(documentoId, usuarioId);
    }

    public void cambiarVence(int documentoId, int usuarioId, LocalDate vence) {
        documentos.cambiarVence(documentoId, usuarioId, vence);
    }
}
