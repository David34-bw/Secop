package co.secopradar.controlador;

import co.secopradar.modelo.Avance;
import co.secopradar.modelo.Etapa;
import co.secopradar.modelo.Modalidades;
import co.secopradar.modelo.Postulacion;
import co.secopradar.modelo.Preparacion;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.TipoDocumento;
import co.secopradar.modelo.servicio.DocumentosService;
import co.secopradar.modelo.servicio.PostulacionesService;
import co.secopradar.modelo.servicio.Servicios;
import co.secopradar.modelo.util.Formato;
import co.secopradar.modelo.util.TextoOracion;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static javafx.scene.layout.Region.USE_PREF_SIZE;

/**
 * Controlador de postulacion.fxml: un proceso y sus 7 etapas. Solo se
 * puede trabajar la etapa actual o volver a una ya completa; las demás
 * aparecen bloqueadas hasta completar las anteriores.
 */
public class PostulacionControlador {

    /** Página de SECOP II donde se registran los proveedores. */
    private static final String URL_SECOP = "https://community.secop.gov.co/";

    @FXML private ScrollPane desplazable;
    @FXML private Label entidad;
    @FXML private Label objeto;
    @FXML private Label valor;
    @FXML private Label modalidad;
    @FXML private Label cierre;
    @FXML private Hyperlink enlace;
    @FXML private Label mensaje;
    @FXML private Label estadoFinal;
    @FXML private ProgressBar barra;
    @FXML private Label resumen;
    @FXML private VBox etapas;
    @FXML private Button botonDesistir;

    private final Servicios s = Servicios.get();
    private final int usuarioId = Sesion.usuarioId();
    private int id;
    private PostulacionesService.Detalle detalle;
    /** Error de la última acción y la etapa donde mostrarlo. */
    private Etapa etapaError;
    private String textoError;

    /** Muestra la postulación en el centro de la ventana. */
    public static void abrir(int id) {
        PostulacionControlador c = Navegador.mostrar(Pantalla.POSTULACION);
        c.cargar(id);
    }

    private void cargar(int id) {
        this.id = id;
        detalle = s.postulaciones.abrir(id, usuarioId);
        pintar();
    }

    @FXML
    private void initialize() {
        Vistas.ocultar(mensaje);
    }

    /** Corre una acción del modelo; si falla, el error se muestra en la etapa. */
    private void accion(Etapa etapa, String exito, Supplier<PostulacionesService.Detalle> a) {
        try {
            detalle = a.get();
            etapaError = null;
            if (exito != null) Vistas.mostrarError(mensaje, exito);
            else Vistas.ocultar(mensaje);
        } catch (PostulacionesService.ErrorPostulacion | DocumentosService.ErrorArchivo e) {
            etapaError = etapa;
            textoError = e.getMessage();
            Vistas.ocultar(mensaje);
        }
        double y = desplazable.getVvalue();
        pintar();
        Platform.runLater(() -> desplazable.setVvalue(y));
    }

    private void pintar() {
        Postulacion p = detalle.postulacion();
        Avance a = detalle.avance();
        entidad.setText(TextoOracion.aTitulo(p.entidad()));
        objeto.setText(TextoOracion.aOracion(p.objeto()));
        valor.setText(Formato.pesos(p.valor()));
        modalidad.setText(Modalidades.label(p.modalidad()));
        Integer dias = Formato.diasRestantes(p.fechaCierre());
        cierre.setText(dias == null ? "Sin fecha de cierre" : dias < 0 ? "Cerró el " + Formato.fechaHora(p.fechaCierre())
                : "Cierra el " + Formato.fechaHora(p.fechaCierre()) + " (" + Formato.textoCierre(dias).toLowerCase() + ")");
        Vistas.clase(cierre, "urgente-texto", dias != null && dias <= 3 && p.presentadaAt() == null);
        Vistas.mostrar(enlace, p.url() != null);

        barra.setProgress(a.progreso());
        resumen.setText(a.resumen());
        estadoFinal.getStyleClass().removeAll("aviso-exito", "aviso-error", "aviso-alerta", "aviso-info");
        String fin = null;
        if (p.desistida()) {
            fin = "Desististe de este proceso. Puedes reanudarlo con el botón de abajo.";
            estadoFinal.getStyleClass().add("aviso-info");
        } else if (p.resultado() == Postulacion.Resultado.GANADA) {
            fin = "¡Felicitaciones! Te adjudicaron este contrato. Revisa en SECOP II los pasos para firmarlo.";
            estadoFinal.getStyleClass().add("aviso-exito");
        } else if (p.resultado() == Postulacion.Resultado.NO_GANADA) {
            fin = "Esta vez no ganaste. Revisa el informe de evaluación para mejorar en el próximo proceso.";
            estadoFinal.getStyleClass().add("aviso-info");
        } else if (p.resultado() == Postulacion.Resultado.SUBSANAR) {
            fin = "Te pidieron subsanar documentos: tienes hasta el " + Formato.fechaLarga(
                    p.plazoSubsanar().atStartOfDay(Formato.BOGOTA).toOffsetDateTime()) + ".";
            estadoFinal.getStyleClass().add("aviso-alerta");
        } else if (p.cerrado() && p.presentadaAt() == null) {
            fin = "Este proceso ya cerró y no marcaste la oferta como presentada.";
            estadoFinal.getStyleClass().add("aviso-alerta");
        }
        Vistas.mostrarError(estadoFinal, fin);
        botonDesistir.setText(p.desistida() ? "Reanudar postulación" : "Desistir de este proceso");

        etapas.getChildren().clear();
        for (Etapa e : Etapa.values()) etapas.getChildren().add(etapa(e, a));
    }

    // ---- Cada etapa ----

    private Node etapa(Etapa e, Avance a) {
        Avance.Estado estado = a.estado(e);
        boolean desistida = detalle.postulacion().desistida();

        Label numero = new Label(estado == Avance.Estado.COMPLETA ? "✓" : String.valueOf(e.numero()));
        numero.getStyleClass().addAll("etapa-numero", "etapa-" + estado.name().toLowerCase());
        numero.setMinSize(30, 30);
        numero.setAlignment(Pos.CENTER);
        Label titulo = new Label(e.titulo);
        titulo.getStyleClass().add("subtitulo");
        Label descripcion = new Label(e.descripcion);
        descripcion.getStyleClass().add("texto-chico");
        descripcion.setWrapText(true);
        VBox textos = new VBox(2, titulo, descripcion);
        HBox.setHgrow(textos, Priority.ALWAYS);
        Label pildora = new Label(switch (estado) {
            case COMPLETA -> "Completa";
            case ACTUAL -> "En curso";
            case BLOQUEADA -> "Bloqueada";
        });
        pildora.getStyleClass().add(switch (estado) {
            case COMPLETA -> "pildora-listo";
            case ACTUAL -> "pildora-por-vencer";
            case BLOQUEADA -> "pildora-falta";
        });
        pildora.setMinWidth(USE_PREF_SIZE);
        HBox cabecera = new HBox(12, numero, textos, pildora);
        cabecera.setAlignment(Pos.TOP_LEFT);

        VBox caja = new VBox(10, cabecera);
        caja.getStyleClass().addAll("caja", "etapa", "etapa-caja-" + estado.name().toLowerCase());
        caja.setPadding(new Insets(16, 18, 16, 18));

        if (estado == Avance.Estado.BLOQUEADA) {
            Etapa falta = a.actual();
            Label l = new Label("Se desbloquea cuando completes la etapa " + falta.numero() + ".");
            l.getStyleClass().add("texto-chico");
            l.setPadding(new Insets(0, 0, 0, 42));
            caja.getChildren().add(l);
            return caja;
        }
        VBox cuerpo = new VBox(8);
        cuerpo.setPadding(new Insets(0, 0, 0, 42));
        cuerpo.setDisable(desistida);
        switch (e) {
            case REGISTRO -> cuerpoRegistro(cuerpo, a);
            case REVISAR -> cuerpoRevisar(cuerpo, a);
            case PLIEGO -> cuerpoPliego(cuerpo, a);
            case DOCUMENTOS -> cuerpoDocumentos(cuerpo, a);
            case PROPUESTA -> cuerpoPropuesta(cuerpo, a);
            case PRESENTAR -> cuerpoPresentar(cuerpo, a);
            case EVALUACION -> cuerpoEvaluacion(cuerpo, a);
        }
        if (e == etapaError) {
            Label err = new Label(textoError);
            err.getStyleClass().add("aviso-error");
            err.setWrapText(true);
            err.setMaxWidth(Double.MAX_VALUE);
            cuerpo.getChildren().add(err);
        }
        caja.getChildren().add(cuerpo);
        return caja;
    }

    private void cuerpoRegistro(VBox c, Avance a) {
        if (a.completa(Etapa.REGISTRO)) {
            c.getChildren().add(texto("Ya tienes cuenta de proveedor en SECOP II."));
            return;
        }
        Hyperlink ir = new Hyperlink("Ir a SECOP II para registrarme");
        ir.setOnAction(ev -> Navegador.abrirEnlace(URL_SECOP));
        Button listo = boton("Ya me registré", "boton-azul");
        listo.setOnAction(ev -> accion(Etapa.REGISTRO, "Etapa 1 completa: ya tienes cuenta en SECOP II.",
                () -> s.postulaciones.marcarRegistro(id, usuarioId)));
        c.getChildren().addAll(texto("Regístrate con tiempo: la oferta solo se puede presentar por la plataforma."), ir, listo);
    }

    private void cuerpoRevisar(VBox c, Avance a) {
        Postulacion p = a.postulacion();
        var grupo = Preparacion.Grupo.de(p.modalidad());
        c.getChildren().add(texto("Modalidad: " + Modalidades.label(p.modalidad())));
        grupo.ifPresent(g -> c.getChildren().add(texto(g.descripcion + (g.pideRup ? " Pide RUP." : " No pide RUP."))));
        c.getChildren().add(texto("Valor: " + Formato.pesos(p.valor()) + " · " + cierre.getText()));
        CheckBox ok = new CheckBox("Revisé la modalidad, el valor y el cierre: me sirve y alcanzo a presentarme");
        ok.setWrapText(true);
        ok.setSelected(p.revisado());
        ok.setOnAction(ev -> accion(Etapa.REVISAR, ok.isSelected() ? "Etapa 2 completa." : null,
                () -> s.postulaciones.marcarRevisado(id, usuarioId, ok.isSelected())));
        c.getChildren().add(ok);
    }

    private void cuerpoPliego(VBox c, Avance a) {
        Postulacion p = a.postulacion();
        Hyperlink ver = new Hyperlink("Abrir el proceso en SECOP II para leer el pliego");
        ver.setOnAction(ev -> Navegador.abrirEnlace(p.url()));
        Vistas.mostrar(ver, p.url() != null);
        TextArea notas = new TextArea(p.notas() == null ? "" : p.notas());
        notas.setPromptText("Tus notas: fechas del cronograma, adendas, lo que piden de experiencia...");
        notas.setWrapText(true);
        notas.setPrefRowCount(3);
        Button guardar = boton("Guardar notas", "boton-borde");
        guardar.setOnAction(ev -> accion(Etapa.PLIEGO, "Notas guardadas.",
                () -> s.postulaciones.guardarNotas(id, usuarioId, notas.getText())));
        CheckBox leido = new CheckBox("Leí completos la invitación o el pliego, el cronograma y las adendas");
        leido.setWrapText(true);
        leido.setSelected(p.pliegoLeido());
        leido.setOnAction(ev -> accion(Etapa.PLIEGO, leido.isSelected() ? "Etapa 3 completa. Anota en la etapa 4 lo que pida este pliego." : null,
                () -> s.postulaciones.marcarPliego(id, usuarioId, leido.isSelected(), notas.getText())));
        c.getChildren().addAll(ver, notas, guardar, leido);
    }

    private void cuerpoDocumentos(VBox c, Avance a) {
        Postulacion p = a.postulacion();
        Preparacion prep = a.preparacion();
        List<TipoDocumento> requeridos = Preparacion.Grupo.de(p.modalidad()).map(Preparacion.Grupo::requeridos)
                .orElse(List.of(TipoDocumento.CEDULA, TipoDocumento.INHABILIDADES, TipoDocumento.SEGURIDAD_SOCIAL));
        Label t1 = new Label("Lo que pide esta modalidad (desde Mis documentos):");
        t1.getStyleClass().add("resultado-frase");
        c.getChildren().add(t1);
        for (TipoDocumento t : requeridos) {
            Label l = new Label((prep.tiene(t) ? "✓  " : "✗  ") + t.nombre);
            l.getStyleClass().add(prep.tiene(t) ? "regla-ok" : "urgente-texto");
            c.getChildren().add(l);
        }
        Hyperlink ir = new Hyperlink("Subir documentos en Mis documentos");
        ir.setOnAction(ev -> Navegador.mostrar(Pantalla.DOCUMENTOS));
        c.getChildren().add(ir);

        Label t2 = new Label("Lo que pide este pliego en particular:");
        t2.getStyleClass().add("resultado-frase");
        c.getChildren().add(t2);
        List<Postulacion.Requisito> extra = p.requisitosExtra();
        if (extra.isEmpty()) c.getChildren().add(texto("Nada anotado. Agrega aquí lo que leíste en el pliego (garantía de seriedad, curso de alturas, experiencia específica...)."));
        for (int i = 0; i < extra.size(); i++) {
            final int indice = i;
            CheckBox cb = new CheckBox(extra.get(i).texto());
            cb.setSelected(extra.get(i).cumplido());
            cb.setWrapText(true);
            cb.setOnAction(ev -> accion(Etapa.DOCUMENTOS, null, () -> s.postulaciones.alternarRequisito(id, usuarioId, indice)));
            Button quitar = boton("Quitar", "boton-enlace");
            quitar.setOnAction(ev -> accion(Etapa.DOCUMENTOS, null, () -> s.postulaciones.quitarRequisito(id, usuarioId, indice)));
            HBox fila = new HBox(8, cb, quitar);
            fila.setAlignment(Pos.CENTER_LEFT);
            c.getChildren().add(fila);
        }
        TextField nuevo = new TextField();
        nuevo.setPromptText("Ej: Garantía de seriedad de la oferta");
        HBox.setHgrow(nuevo, Priority.ALWAYS);
        Button agregar = boton("Agregar", "boton-borde");
        Runnable add = () -> accion(Etapa.DOCUMENTOS, null, () -> s.postulaciones.agregarRequisito(id, usuarioId, nuevo.getText()));
        agregar.setOnAction(ev -> add.run());
        nuevo.setOnAction(ev -> add.run());
        HBox fila = new HBox(8, nuevo, agregar);
        fila.setMaxWidth(560);
        c.getChildren().add(fila);

        List<String> faltan = a.faltantesDocumentos();
        Label estado = new Label(faltan.isEmpty() ? "✓ Tienes todo lo necesario. Sigue con la propuesta económica."
                : "Para completar esta etapa te falta: " + String.join(", ", faltan) + ".");
        estado.getStyleClass().add(faltan.isEmpty() ? "aviso-exito" : "aviso-alerta");
        estado.setWrapText(true);
        estado.setMaxWidth(Double.MAX_VALUE);
        c.getChildren().add(estado);
    }

    private void cuerpoPropuesta(VBox c, Avance a) {
        Postulacion p = a.postulacion();
        if (p.valor() != null && p.valor() > 0) {
            c.getChildren().add(texto("Presupuesto oficial: " + Formato.pesos(p.valor()) + ". Tu oferta no puede pasar de ese valor."));
        }
        TextField campo = new TextField(p.valorOfertado() == null ? "" : Formato.numero(p.valorOfertado()));
        campo.setPromptText("Valor de tu oferta en pesos");
        campo.setMaxWidth(260);
        Label porcentaje = new Label();
        porcentaje.getStyleClass().add("texto-chico");
        Runnable calcular = () -> {
            Long v = pesos(campo.getText());
            porcentaje.setText(v == null || p.valor() == null || p.valor() <= 0 ? ""
                    : String.format("= %.1f %% del presupuesto", v * 100.0 / p.valor()));
        };
        campo.textProperty().addListener((o, x, y) -> calcular.run());
        calcular.run();
        Button guardar = boton(p.valorOfertado() == null ? "Guardar valor" : "Cambiar valor", "boton-azul");
        guardar.setOnAction(ev -> {
            Long v = pesos(campo.getText());
            if (v == null) {
                etapaError = Etapa.PROPUESTA;
                textoError = "Escribe el valor de tu oferta en pesos, solo números.";
                pintar();
                return;
            }
            accion(Etapa.PROPUESTA, "Propuesta guardada: " + Formato.pesos(v) + ".", () -> s.postulaciones.guardarPropuesta(id, usuarioId, v));
        });
        HBox fila = new HBox(10, campo, guardar, porcentaje);
        fila.setAlignment(Pos.CENTER_LEFT);
        c.getChildren().addAll(texto("Calcula con los costos reales: materiales, tu tiempo, seguridad social e impuestos."), fila);
        if (p.valorOfertado() != null) {
            Button quitar = boton("Quitar el valor (volver a esta etapa)", "boton-enlace");
            quitar.setOnAction(ev -> accion(Etapa.PROPUESTA, null, () -> s.postulaciones.guardarPropuesta(id, usuarioId, null)));
            c.getChildren().add(quitar);
        }

        Button subir = boton(p.propuestaArchivo() == null ? "Subir archivo de la propuesta (opcional)" : "Reemplazar archivo", "boton-borde");
        subir.setOnAction(ev -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Archivo de la propuesta económica");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF o imagen (máx. 5 MB)", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
            File f = fc.showOpenDialog(Navegador.ventana());
            if (f != null) accion(Etapa.PROPUESTA, "Archivo guardado: " + f.getName() + ".",
                    () -> s.postulaciones.subirArchivoPropuesta(id, usuarioId, f.toPath()));
        });
        HBox archivo = new HBox(10, subir);
        archivo.setAlignment(Pos.CENTER_LEFT);
        if (p.propuestaArchivo() != null) {
            Button abrir = boton("📄 " + p.propuestaArchivo(), "boton-enlace");
            abrir.setOnAction(ev -> abrirPropuesta(p.propuestaArchivo()));
            archivo.getChildren().add(0, abrir);
        }
        c.getChildren().add(archivo);
    }

    private void cuerpoPresentar(VBox c, Avance a) {
        Postulacion p = a.postulacion();
        if (p.presentadaAt() != null) {
            c.getChildren().add(texto("Presentaste la oferta el " + Formato.fechaHora(p.presentadaAt()) + "."));
            if (p.resultado() == null) {
                Button deshacer = boton("No la he presentado (desmarcar)", "boton-enlace");
                deshacer.setOnAction(ev -> accion(Etapa.PRESENTAR, null, () -> s.postulaciones.marcarPresentada(id, usuarioId, false)));
                c.getChildren().add(deshacer);
            }
            return;
        }
        Hyperlink ir = new Hyperlink("Abrir el proceso en SECOP II para presentar la oferta");
        ir.setOnAction(ev -> Navegador.abrirEnlace(p.url()));
        Vistas.mostrar(ir, p.url() != null);
        Button listo = boton("Ya presenté mi oferta", "boton-azul");
        listo.setOnAction(ev -> {
            if (Vistas.confirmar("Presentar oferta", "¿Ya enviaste la oferta en SECOP II? Revisa que la plataforma te haya dado el comprobante.")) {
                accion(Etapa.PRESENTAR, "Etapa 6 completa: oferta presentada. Ahora espera el informe de evaluación.",
                        () -> s.postulaciones.marcarPresentada(id, usuarioId, true));
            }
        });
        c.getChildren().addAll(texto("Hazlo con tiempo: SECOP II no recibe ofertas después de la hora de cierre (" + cierre.getText() + ")."), ir, listo);
    }

    private void cuerpoEvaluacion(VBox c, Avance a) {
        Postulacion p = a.postulacion();
        c.getChildren().add(texto(p.resultado() == null ? "Cuando la entidad publique el informe de evaluación, marca el resultado."
                : "Resultado: " + p.resultado().texto + "."));
        Button ganada = boton("Gané", "boton-azul");
        ganada.setOnAction(ev -> accion(Etapa.EVALUACION, "¡Felicitaciones!", () -> s.postulaciones.guardarResultado(id, usuarioId, Postulacion.Resultado.GANADA, null)));
        Button perdida = boton("No gané", "boton-borde");
        perdida.setOnAction(ev -> accion(Etapa.EVALUACION, null, () -> s.postulaciones.guardarResultado(id, usuarioId, Postulacion.Resultado.NO_GANADA, null)));
        Button subsanar = boton("Piden subsanar", "boton-borde");
        subsanar.setOnAction(ev -> pedirPlazo(p.plazoSubsanar()).ifPresent(plazo -> accion(Etapa.EVALUACION,
                "Anotado. Sube lo que te pidan antes del plazo.",
                () -> s.postulaciones.guardarResultado(id, usuarioId, Postulacion.Resultado.SUBSANAR, plazo))));
        HBox botones = new HBox(10, ganada, perdida, subsanar);
        c.getChildren().add(botones);
        if (p.resultado() != null) {
            Button quitar = boton("Quitar resultado", "boton-enlace");
            quitar.setOnAction(ev -> accion(Etapa.EVALUACION, null, () -> s.postulaciones.guardarResultado(id, usuarioId, null, null)));
            c.getChildren().add(quitar);
        }
    }

    // ---- Ayudas ----

    private static Label texto(String t) {
        Label l = new Label(t);
        l.setWrapText(true);
        return l;
    }

    private static Button boton(String t, String clase) {
        Button b = new Button(t);
        b.getStyleClass().add(clase);
        b.setMinWidth(USE_PREF_SIZE);
        return b;
    }

    /** "1.500.000", "$ 1500000" -> 1500000; null si no es un número. */
    static Long pesos(String texto) {
        String d = texto == null ? "" : texto.replaceAll("[$\\s.]", "").replace(",00", "");
        if (!d.matches("\\d{1,15}")) return null;
        return Long.parseLong(d);
    }

    private java.util.Optional<LocalDate> pedirPlazo(LocalDate actual) {
        Dialog<LocalDate> d = new Dialog<>();
        d.initOwner(Navegador.ventana());
        d.setTitle("Subsanar");
        d.setHeaderText("¿Hasta cuándo tienes para subsanar?");
        DatePicker fecha = new DatePicker(actual == null ? LocalDate.now().plusDays(3) : actual);
        d.getDialogPane().setContent(fecha);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.setResultConverter(b -> b == ButtonType.OK ? fecha.getValue() : null);
        return d.showAndWait();
    }

    private void abrirPropuesta(String nombre) {
        try {
            byte[] datos = s.postulaciones.archivoPropuesta(id, usuarioId).orElseThrow();
            Path carpeta = Path.of(System.getProperty("java.io.tmpdir"), "secop-radar");
            Files.createDirectories(carpeta);
            Path f = carpeta.resolve("propuesta-" + id + "-" + nombre.replaceAll("[\\\\/:*?\"<>|]", "_"));
            Files.write(f, datos);
            Navegador.abrirEnlace(f.toUri().toString());
        } catch (Exception e) {
            Vistas.informar("No se pudo abrir", e.getMessage());
        }
    }

    @FXML
    private void abrirEnSecop() {
        Navegador.abrirEnlace(detalle.postulacion().url());
    }

    @FXML
    private void volver() {
        Navegador.mostrar(Pantalla.POSTULACIONES);
    }

    @FXML
    private void desistir() {
        boolean desistir = !detalle.postulacion().desistida();
        if (desistir && !Vistas.confirmar("Desistir", "¿Dejar de seguir este proceso? Podrás reanudarlo después.")) return;
        accion(null, desistir ? "Desististe de este proceso." : "Postulación reanudada.",
                () -> s.postulaciones.desistir(id, usuarioId, desistir));
    }

    @FXML
    private void eliminar() {
        if (!Vistas.confirmar("Eliminar postulación", "¿Eliminar esta postulación con sus notas y archivo de propuesta? No se puede deshacer.")) return;
        s.postulaciones.eliminar(id, usuarioId);
        Navegador.mostrar(Pantalla.POSTULACIONES);
    }
}
