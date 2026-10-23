package co.secopradar.controlador;

import co.secopradar.modelo.BorradorPerfil;
import co.secopradar.modelo.GrupoOpciones;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Controlador de grupoOpciones.fxml: un grupo obligatorio del formulario
 * de perfiles. "Todos" excluye a las demás opciones; "Otro" abre un campo
 * para escribir (o el mínimo y máximo, en el valor del proceso).
 */
public class GrupoOpcionesControlador {

    @FXML private VBox grupo;
    @FXML private Label titulo;
    @FXML private Label ayuda;
    @FXML private FlowPane opciones;
    @FXML private TextField textoOtro;
    @FXML private HBox cajaRango;
    @FXML private TextField rangoMin;
    @FXML private TextField rangoMax;
    @FXML private Label error;

    private GrupoOpciones definicion;
    private ToggleButton todos;
    private ToggleButton otro;
    private final List<ToggleButton> marcables = new ArrayList<>();
    private Runnable alCambiar = () -> { };

    public void configurar(GrupoOpciones g, Runnable alCambiar) {
        this.definicion = g;
        this.alCambiar = alCambiar;
        titulo.setText(g.titulo() + " *");
        ayuda.setText(g.ayuda() == null ? "" : g.ayuda());
        Vistas.mostrar(ayuda, g.ayuda() != null);
        textoOtro.setPromptText(g.placeholderOtro());

        todos = chip(g.etiquetaTodos(), "especial");
        opciones.getChildren().add(todos);
        for (GrupoOpciones.Opcion o : g.opciones()) {
            ToggleButton b = chip(o.etiqueta(), null);
            b.setUserData(o.valor());
            if (o.descripcion() != null) b.setTooltip(new Tooltip(o.descripcion()));
            marcables.add(b);
            opciones.getChildren().add(b);
        }
        otro = chip(g.etiquetaOtro(), "especial");
        opciones.getChildren().add(otro);

        // "Todos" excluye a las demás opciones (y al revés).
        todos.setOnAction(e -> {
            if (todos.isSelected()) {
                marcables.forEach(b -> b.setSelected(false));
                otro.setSelected(false);
            }
            cambio();
        });
        marcables.forEach(b -> b.setOnAction(e -> {
            if (b.isSelected()) todos.setSelected(false);
            cambio();
        }));
        otro.setOnAction(e -> {
            if (otro.isSelected()) todos.setSelected(false);
            cambio();
            if (otro.isSelected()) (g.esRangoValor() ? rangoMin : textoOtro).requestFocus();
        });
        textoOtro.textProperty().addListener((o, a, n) -> cambio());
        rangoMin.textProperty().addListener((o, a, n) -> cambio());
        rangoMax.textProperty().addListener((o, a, n) -> cambio());

        mostrarError(null);
        actualizarCampoOtro();
    }

    private static ToggleButton chip(String texto, String claseExtra) {
        ToggleButton b = new ToggleButton(texto);
        b.getStyleClass().add("chip");
        if (claseExtra != null) b.getStyleClass().add(claseExtra);
        return b;
    }

    private void cambio() {
        actualizarCampoOtro();
        mostrarError(null);
        alCambiar.run();
    }

    private void actualizarCampoOtro() {
        boolean abierto = otro != null && otro.isSelected();
        Vistas.mostrar(textoOtro, abierto && !definicion.esRangoValor());
        Vistas.mostrar(cajaRango, abierto && definicion.esRangoValor());
    }

    /** Copia lo elegido en la pantalla al borrador del modelo. */
    public void volcar(BorradorPerfil b) {
        BorradorPerfil.Eleccion e = b.grupo(definicion.campo());
        e.marcadas.clear();
        marcables.stream().filter(ToggleButton::isSelected).forEach(t -> e.marcadas.add((String) t.getUserData()));
        e.todos = todos.isSelected();
        e.otro = otro.isSelected();
        e.textoOtro = textoOtro.getText();
        if (definicion.esRangoValor()) {
            b.rangoOtroMin = otro.isSelected() ? rangoMin.getText() : "";
            b.rangoOtroMax = otro.isSelected() ? rangoMax.getText() : "";
        }
    }

    public void mostrarError(String mensaje) {
        Vistas.mostrarError(error, mensaje);
        Vistas.clase(grupo, "grupo-error", mensaje != null);
    }

    public String campo() {
        return definicion.campo();
    }

    public VBox nodo() {
        return grupo;
    }
}
