package co.secopradar.controlador;

import co.secopradar.modelo.Catalogo;
import co.secopradar.modelo.Oportunidad;
import co.secopradar.modelo.servicio.BusquedaService;
import co.secopradar.modelo.servicio.Servicios;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.function.Supplier;

/** Controlador de buscar.fxml: búsqueda libre o por profesión (no guarda nada). */
public class BuscarControlador {

    @FXML private TextField texto;
    @FXML private ComboBox<Catalogo.Profesion> profesion;
    @FXML private Label aviso;
    @FXML private ProgressIndicator cargando;
    @FXML private Label resultado;
    @FXML private VBox lista;

    private final Servicios s = Servicios.get();
    private final Catalogo catalogo = Catalogo.get();

    @FXML
    private void initialize() {
        profesion.getItems().setAll(catalogo.profesiones());
        profesion.setConverter(new StringConverter<>() {
            @Override
            public String toString(Catalogo.Profesion p) {
                return p == null ? "" : p.nombre();
            }

            @Override
            public Catalogo.Profesion fromString(String s) {
                return null;
            }
        });
        profesion.valueProperty().addListener((o, a, p) -> {
            if (p != null) buscarPorProfesion(p);
        });
        Vistas.mostrar(aviso, false);
        Vistas.mostrar(cargando, false);
        Vistas.mostrar(resultado, false);
    }

    @FXML
    private void buscarTexto() {
        String t = texto.getText().trim();
        if (t.isEmpty()) return;
        if (t.length() > 200) {
            mostrarResultado("La búsqueda es demasiado larga.");
            return;
        }
        Vistas.mostrar(aviso, false);
        ejecutar(() -> s.busqueda.porTexto(t));
    }

    private void buscarPorProfesion(Catalogo.Profesion p) {
        aviso.setText(catalogo.avisoContratacionDirecta());
        Vistas.mostrar(aviso, p.contratacionDirecta());
        ejecutar(() -> s.busqueda.porProfesion(p));
    }

    /** Busca en segundo plano para no congelar la ventana. */
    private void ejecutar(Supplier<BusquedaService.Resultado> busqueda) {
        Vistas.mostrar(cargando, true);
        mostrarResultado("Buscando...");
        lista.getChildren().clear();
        Task<BusquedaService.Resultado> tarea = new Task<>() {
            @Override
            protected BusquedaService.Resultado call() {
                return busqueda.get();
            }
        };
        tarea.setOnSucceeded(e -> pintar(tarea.getValue()));
        tarea.setOnFailed(e -> {
            Vistas.mostrar(cargando, false);
            mostrarResultado("No se pudo buscar: " + tarea.getException().getMessage());
        });
        Thread hilo = new Thread(tarea, "busqueda");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void pintar(BusquedaService.Resultado r) {
        Vistas.mostrar(cargando, false);
        int n = r.total();
        mostrarResultado(n == 0 ? "No encontramos procesos vigentes con esa búsqueda. Prueba con otras palabras."
                : n + (n == 1 ? " proceso vigente" : " procesos vigentes") + ". Estos son los "
                + r.oportunidades().size() + " más relevantes:");
        for (Oportunidad o : r.oportunidades()) lista.getChildren().add(TarjetaOportunidadControlador.crear(o, false));
    }

    private void mostrarResultado(String mensaje) {
        resultado.setText(mensaje);
        Vistas.mostrar(resultado, true);
    }
}
