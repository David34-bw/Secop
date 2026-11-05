package co.secopradar.controlador;

import co.secopradar.modelo.Catalogo;
import co.secopradar.modelo.Documento;
import co.secopradar.modelo.Preparacion;
import co.secopradar.modelo.Proponente;
import co.secopradar.modelo.Region;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.TipoDocumento;
import co.secopradar.modelo.servicio.DocumentosService;
import co.secopradar.modelo.servicio.Servicios;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import static javafx.scene.layout.Region.USE_PREF_SIZE;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador de documentos.fxml: datos del proponente, archivos que pide
 * SECOP II y resumen de para qué modalidades está listo el usuario.
 */
public class DocumentosControlador {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "CO"));

    private static final List<String> PASOS = List.of(
            "Registrarte como proveedor en SECOP II.",
            "Buscar el proceso y revisar la modalidad, el valor y la fecha de cierre.",
            "Leer completo la invitación o el pliego, el cronograma y las adendas.",
            "Reunir los documentos y verificar que cumples todos los requisitos.",
            "Preparar la propuesta económica con los costos reales (incluida la seguridad social e impuestos).",
            "Presentar la oferta en SECOP II antes de la fecha y hora de cierre.",
            "Revisar el informe de evaluación y, si piden subsanar algún documento, hacerlo dentro del plazo.");

    @FXML private ScrollPane desplazable;
    @FXML private Label mensaje;
    @FXML private Label error;
    @FXML private ProgressBar barraBasicos;
    @FXML private Label textoBasicos;
    @FXML private ProgressBar barraDatos;
    @FXML private Label textoDatos;
    @FXML private VBox modalidades;

    @FXML private ComboBox<String> tipoPersona;
    @FXML private ComboBox<String> tipoDocumento;
    @FXML private TextField numeroDocumento;
    @FXML private TextField telefono;
    @FXML private ComboBox<String> departamento;
    @FXML private TextField ciudad;
    @FXML private ComboBox<String> profesion;
    @FXML private TextField aniosExperiencia;
    @FXML private TextField titulo;
    @FXML private TextField universidad;
    @FXML private TextField matriculaProfesional;
    @FXML private CheckBox tieneRup;
    @FXML private TextField numeroRup;
    @FXML private CheckBox registradoSecop;
    @FXML private CheckBox sigepActualizada;
    @FXML private TextArea resumen;

    @FXML private Label errorNumeroDocumento;
    @FXML private Label errorTelefono;
    @FXML private Label errorCiudad;
    @FXML private Label errorProfesion;
    @FXML private Label errorAniosExperiencia;
    @FXML private Label errorTitulo;
    @FXML private Label errorUniversidad;
    @FXML private Label errorMatriculaProfesional;
    @FXML private Label errorNumeroRup;
    @FXML private Label errorResumen;

    @FXML private VBox secciones;
    @FXML private VBox pasos;

    private final Servicios s = Servicios.get();
    private final int usuarioId = Sesion.usuarioId();
    /** campo del modelo -> (control, etiqueta de error). */
    private Map<String, Map.Entry<Node, Label>> campos;
    private boolean ocupado;

    @FXML
    private void initialize() {
        Vistas.ocultar(mensaje);
        Vistas.ocultar(error);
        campos = Map.of(
                "numeroDocumento", Map.entry(numeroDocumento, errorNumeroDocumento),
                "telefono", Map.entry(telefono, errorTelefono),
                "ciudad", Map.entry(ciudad, errorCiudad),
                "profesion", Map.entry(profesion, errorProfesion),
                "aniosExperiencia", Map.entry(aniosExperiencia, errorAniosExperiencia),
                "titulo", Map.entry(titulo, errorTitulo),
                "universidad", Map.entry(universidad, errorUniversidad),
                "matriculaProfesional", Map.entry(matriculaProfesional, errorMatriculaProfesional),
                "numeroRup", Map.entry(numeroRup, errorNumeroRup),
                "resumen", Map.entry(resumen, errorResumen));
        campos.forEach((k, v) -> {
            Vistas.ocultar(v.getValue());
            Node control = v.getKey();
            if (control instanceof TextInputControl t) t.textProperty().addListener((o, a, b) -> limpiarError(k));
            if (control instanceof ComboBox<?> c) c.getEditor().textProperty().addListener((o, a, b) -> limpiarError(k));
        });

        tipoPersona.getItems().setAll(Proponente.TIPOS_PERSONA);
        tipoDocumento.getItems().setAll(Proponente.TIPOS_DOCUMENTO);
        departamento.getItems().setAll(Region.TODAS.stream().flatMap(r -> r.departamentos().stream()).sorted().toList());
        profesion.getItems().setAll(Catalogo.get().profesiones().stream().map(Catalogo.Profesion::nombre).toList());
        numeroRup.disableProperty().bind(tieneRup.selectedProperty().not());
        aniosExperiencia.textProperty().addListener((o, a, b) -> {
            if (!b.matches("\\d{0,2}")) aniosExperiencia.setText(a);
        });

        for (int i = 0; i < PASOS.size(); i++) {
            Label l = new Label((i + 1) + ".  " + PASOS.get(i));
            l.setWrapText(true);
            pasos.getChildren().add(l);
        }
        llenarDatos(s.documentos.datos(usuarioId));
        pintarDocumentos();
    }

    private void limpiarError(String campo) {
        var v = campos.get(campo);
        Vistas.ocultar(v.getValue());
        Vistas.clase(v.getKey(), "campo-error", false);
    }

    private void exito(String texto) {
        Vistas.ocultar(error);
        Vistas.mostrarError(mensaje, texto);
    }

    private void fallo(String texto) {
        Vistas.ocultar(mensaje);
        Vistas.mostrarError(error, texto);
        desplazable.setVvalue(0);
    }

    // ---- Datos del proponente ----

    private void llenarDatos(Proponente p) {
        tipoPersona.setValue(p.tipoPersona() == null ? Proponente.TIPOS_PERSONA.get(0) : p.tipoPersona());
        tipoDocumento.setValue(p.tipoDocumento() == null ? Proponente.TIPOS_DOCUMENTO.get(0) : p.tipoDocumento());
        numeroDocumento.setText(texto(p.numeroDocumento()));
        telefono.setText(texto(p.telefono()));
        departamento.setValue(p.departamento());
        ciudad.setText(texto(p.ciudad()));
        profesion.setValue(p.profesion());
        aniosExperiencia.setText(p.aniosExperiencia() == null ? "" : p.aniosExperiencia().toString());
        titulo.setText(texto(p.titulo()));
        universidad.setText(texto(p.universidad()));
        matriculaProfesional.setText(texto(p.matriculaProfesional()));
        tieneRup.setSelected(p.tieneRup());
        numeroRup.setText(texto(p.numeroRup()));
        registradoSecop.setSelected(p.registradoSecop());
        sigepActualizada.setSelected(p.sigepActualizada());
        resumen.setText(texto(p.resumen()));
    }

    private static String texto(String v) {
        return v == null ? "" : v;
    }

    @FXML
    private void guardarDatos() {
        campos.keySet().forEach(this::limpiarError);
        String anios = aniosExperiencia.getText().trim();
        Proponente p = new Proponente(tipoPersona.getValue(), tipoDocumento.getValue(), numeroDocumento.getText(),
                telefono.getText(), departamento.getValue(), ciudad.getText(), profesion.getEditor().getText(),
                titulo.getText(), universidad.getText(), matriculaProfesional.getText(),
                anios.isEmpty() ? null : Integer.valueOf(anios), resumen.getText(), registradoSecop.isSelected(),
                tieneRup.isSelected(), numeroRup.getText(), sigepActualizada.isSelected());
        try {
            llenarDatos(s.documentos.guardarDatos(usuarioId, p));
            exito("Datos guardados.");
            desplazable.setVvalue(0);
            pintarResumen(s.documentos.preparacion(usuarioId));
        } catch (DocumentosService.ErrorDatos e) {
            e.errores.forEach((campo, msg) -> {
                var v = campos.get(campo);
                Vistas.mostrarError(v.getValue(), msg);
                Vistas.clase(v.getKey(), "campo-error", true);
            });
            fallo("Revisa los campos marcados en rojo.");
        }
    }

    // ---- Resumen ----

    private void pintarResumen(Preparacion prep) {
        int listos = prep.basicosListos();
        int total = Preparacion.totalBasicos();
        barraBasicos.setProgress((double) listos / total);
        textoBasicos.setText(listos + " de " + total + " documentos básicos al día");
        int datos = s.documentos.datos(usuarioId).llenos();
        barraDatos.setProgress((double) datos / Proponente.TOTAL_DATOS);
        textoDatos.setText(datos + " de " + Proponente.TOTAL_DATOS + " datos principales llenos");

        modalidades.getChildren().clear();
        for (Preparacion.Estado e : prep.estados()) {
            Label icono = new Label(e.listo() ? "✓" : "✗");
            icono.getStyleClass().add(e.listo() ? "modalidad-lista" : "modalidad-falta");
            icono.setMinWidth(18);
            Label nombre = new Label(e.grupo().nombre + (e.grupo().pideRup ? "  (pide RUP)" : ""));
            nombre.getStyleClass().add("resultado-frase");
            Label detalle = new Label(e.listo() ? "Tienes lo básico. " + e.grupo().descripcion
                    : "Te falta: " + String.join(", ", e.faltantes()) + ".");
            detalle.getStyleClass().add("texto-chico");
            detalle.setWrapText(true);
            VBox textos = new VBox(1, nombre, detalle);
            HBox.setHgrow(textos, Priority.ALWAYS);
            HBox fila = new HBox(8, icono, textos);
            modalidades.getChildren().add(fila);
        }
    }

    // ---- Documentos ----

    private void pintarDocumentos() {
        List<Documento> docs = s.documentos.documentos(usuarioId);
        pintarResumen(new Preparacion(docs, s.documentos.datos(usuarioId)));
        secciones.getChildren().clear();
        for (TipoDocumento.Grupo g : TipoDocumento.Grupo.values()) {
            VBox caja = new VBox(4);
            caja.getStyleClass().add("caja");
            caja.setPadding(new Insets(18));
            Label titulo = new Label(g.titulo);
            titulo.getStyleClass().add("subtitulo");
            caja.getChildren().add(titulo);
            for (TipoDocumento t : TipoDocumento.de(g)) {
                caja.getChildren().add(filaTipo(t, docs.stream().filter(d -> d.tipo() == t).toList()));
            }
            secciones.getChildren().add(caja);
        }
    }

    private Node filaTipo(TipoDocumento t, List<Documento> docs) {
        Label nombre = new Label(t.nombre);
        nombre.getStyleClass().add("resultado-frase");
        Label descripcion = new Label(t.descripcion);
        descripcion.getStyleClass().add("texto-chico");
        descripcion.setWrapText(true);
        VBox textos = new VBox(2, nombre, descripcion);
        HBox.setHgrow(textos, Priority.ALWAYS);

        Label estado = new Label();
        estado.setMinWidth(USE_PREF_SIZE);
        if (docs.isEmpty()) {
            estado.setText("Falta");
            estado.getStyleClass().add("pildora-falta");
        } else if (docs.stream().allMatch(d -> d.vigencia() == Documento.Vigencia.VENCIDO)) {
            estado.setText("Vencido");
            estado.getStyleClass().add("pildora-vencido");
        } else if (docs.stream().anyMatch(d -> d.vigencia() == Documento.Vigencia.POR_VENCER)) {
            estado.setText("Por vencer");
            estado.getStyleClass().add("pildora-por-vencer");
        } else {
            estado.setText(docs.size() > 1 ? docs.size() + " archivos" : "Listo");
            estado.getStyleClass().add("pildora-listo");
        }

        Button subir = new Button(docs.isEmpty() ? "Subir" : t.varios ? "Agregar otro" : "Reemplazar");
        subir.getStyleClass().add(docs.isEmpty() ? "boton-azul" : "boton-borde");
        subir.setMinWidth(USE_PREF_SIZE);
        subir.setOnAction(e -> subir(t));

        HBox cabecera = new HBox(12, textos, estado, subir);
        cabecera.setAlignment(Pos.CENTER_LEFT);
        VBox fila = new VBox(6, cabecera);
        fila.getStyleClass().add("fila-documento");
        fila.setPadding(new Insets(10, 0, 10, 0));
        for (Documento d : docs) fila.getChildren().add(filaArchivo(d));
        return fila;
    }

    private Node filaArchivo(Documento d) {
        String vence = d.vence() == null ? "" : switch (d.vigencia()) {
            case VENCIDO -> " · venció el " + FECHA.format(d.vence());
            case POR_VENCER -> " · vence el " + FECHA.format(d.vence()) + " (pronto)";
            case VIGENTE -> " · vence el " + FECHA.format(d.vence());
        };
        Label archivo = new Label("📄 " + d.nombreArchivo() + "  (" + d.tamanoTexto() + ") · subido el "
                + FECHA.format(d.subidoAt().atZoneSameInstant(co.secopradar.modelo.util.Formato.BOGOTA)) + vence);
        archivo.getStyleClass().add("texto-chico");
        if (d.vigencia() != Documento.Vigencia.VIGENTE) archivo.getStyleClass().add("urgente-texto");
        archivo.setWrapText(true);
        HBox.setHgrow(archivo, Priority.ALWAYS);
        archivo.setMaxWidth(Double.MAX_VALUE);

        Button abrir = enlace("Abrir", () -> abrir(d));
        Button guardar = enlace("Guardar copia", () -> guardarCopia(d));
        Button quitar = enlace("Quitar", () -> quitar(d));
        quitar.getStyleClass().add("urgente-texto");
        HBox fila = new HBox(6, archivo, abrir, guardar, quitar);
        if (d.tipo().vence) fila.getChildren().add(3, enlace("Cambiar vencimiento", () -> cambiarVence(d)));
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setPadding(new Insets(0, 0, 0, 14));
        return fila;
    }

    private static Button enlace(String texto, Runnable accion) {
        Button b = new Button(texto);
        b.getStyleClass().add("boton-enlace");
        b.setMinWidth(USE_PREF_SIZE);
        b.setOnAction(e -> accion.run());
        return b;
    }

    private void subir(TipoDocumento t) {
        if (ocupado) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Subir " + t.nombre);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF o imagen (máx. 5 MB)", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
        File f = fc.showOpenDialog(Navegador.ventana());
        if (f == null) return;
        LocalDate vence = null;
        if (t.vence) {
            LocalDate sugerida = t == TipoDocumento.SEGURIDAD_SOCIAL ? LocalDate.now().plusMonths(1) : null;
            Optional<Optional<LocalDate>> r = pedirVence(t, sugerida);
            if (r.isEmpty()) return;
            vence = r.get().orElse(null);
        }
        final LocalDate fecha = vence;
        ejecutar("Subiendo " + f.getName() + "...", () -> s.documentos.subir(usuarioId, t, f.toPath(), fecha),
                d -> t.nombre + ": se guardó " + d.nombreArchivo() + ".");
    }

    /**
     * Pregunta la fecha de vencimiento. Vacío = canceló; Optional.empty()
     * adentro = sin fecha.
     */
    private Optional<Optional<LocalDate>> pedirVence(TipoDocumento t, LocalDate sugerida) {
        Dialog<Optional<LocalDate>> dialogo = new Dialog<>();
        dialogo.initOwner(Navegador.ventana());
        dialogo.setTitle("Fecha de vencimiento");
        dialogo.setHeaderText(t.nombre);
        DatePicker fecha = new DatePicker(sugerida);
        fecha.setPromptText("Sin fecha");
        Label ayuda = new Label(t == TipoDocumento.SEGURIDAD_SOCIAL
                ? "La planilla se paga cada mes: por defecto vence en un mes."
                : "¿Hasta cuándo es válido? Déjalo vacío si no sabes. Te avisamos " + Documento.DIAS_AVISO + " días antes.");
        ayuda.setWrapText(true);
        ayuda.setMaxWidth(340);
        dialogo.getDialogPane().setContent(new VBox(8, ayuda, fecha));
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialogo.setResultConverter(b -> b == ButtonType.OK ? Optional.ofNullable(fecha.getValue()) : null);
        return dialogo.showAndWait();
    }

    private void cambiarVence(Documento d) {
        pedirVence(d.tipo(), d.vence()).ifPresent(f -> {
            s.documentos.cambiarVence(d.id(), usuarioId, f.orElse(null));
            exito("Vencimiento actualizado.");
            pintarDocumentos();
        });
    }

    private void quitar(Documento d) {
        if (!Vistas.confirmar("Quitar documento", "¿Quitar " + d.nombreArchivo() + " de " + d.tipo().nombre + "?")) return;
        s.documentos.eliminar(d.id(), usuarioId);
        exito("Se quitó " + d.nombreArchivo() + ".");
        pintarDocumentos();
    }

    /** Abre el archivo con el programa del sistema (lo copia a una carpeta temporal). */
    private void abrir(Documento d) {
        ejecutar(null, () -> {
            byte[] datos = s.documentos.contenido(d.id(), usuarioId).orElseThrow(() -> new IOException("El archivo ya no existe"));
            Path carpeta = Path.of(System.getProperty("java.io.tmpdir"), "secop-radar");
            Files.createDirectories(carpeta);
            Path p = carpeta.resolve(d.id() + "-" + d.nombreArchivo().replaceAll("[\\\\/:*?\"<>|]", "_"));
            Files.write(p, datos);
            return p;
        }, p -> {
            Navegador.abrirEnlace(p.toUri().toString());
            return null;
        });
    }

    private void guardarCopia(Documento d) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Guardar copia");
        fc.setInitialFileName(d.nombreArchivo());
        File destino = fc.showSaveDialog(Navegador.ventana());
        if (destino == null) return;
        ejecutar("Descargando...", () -> {
            byte[] datos = s.documentos.contenido(d.id(), usuarioId).orElseThrow(() -> new IOException("El archivo ya no existe"));
            Files.write(destino.toPath(), datos);
            return destino;
        }, f -> "Copia guardada en " + f.getAbsolutePath() + ".");
    }

    @FunctionalInterface
    private interface Trabajo<T> {
        T hacer() throws Exception;
    }

    /** Corre el trabajo fuera del hilo de la interfaz (subir 5 MB a Supabase toma unos segundos). */
    private <T> void ejecutar(String aviso, Trabajo<T> trabajo, java.util.function.Function<T, String> alTerminar) {
        ocupado = true;
        if (aviso != null) exito(aviso);
        Task<T> tarea = new Task<>() {
            @Override
            protected T call() throws Exception {
                return trabajo.hacer();
            }
        };
        tarea.setOnSucceeded(e -> {
            ocupado = false;
            String texto = alTerminar.apply(tarea.getValue());
            if (texto != null) exito(texto);
            else if (aviso != null) Vistas.ocultar(mensaje);
            if (aviso != null) pintarDocumentos();
        });
        tarea.setOnFailed(e -> {
            ocupado = false;
            Throwable t = tarea.getException();
            fallo(t instanceof DocumentosService.ErrorArchivo ? t.getMessage() : "No se pudo completar: " + t.getMessage());
        });
        Thread hilo = new Thread(tarea, "documentos");
        hilo.setDaemon(true);
        hilo.start();
    }
}
