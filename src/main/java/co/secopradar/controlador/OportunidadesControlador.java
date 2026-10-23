package co.secopradar.controlador;

import co.secopradar.modelo.Modalidades;
import co.secopradar.modelo.Oportunidad;
import co.secopradar.modelo.Plan;
import co.secopradar.modelo.RangoValor;
import co.secopradar.modelo.Region;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.Filtros;
import co.secopradar.modelo.servicio.OportunidadesService;
import co.secopradar.modelo.servicio.Servicios;
import co.secopradar.modelo.util.Formato;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Controlador de oportunidades.fxml: las coincidencias de los perfiles del
 * usuario, con filtros, orden y paginación (port del panel /dashboard).
 */
public class OportunidadesControlador {

    @FXML private Label saludo;
    @FXML private Label resumen;
    @FXML private HBox cajaNuevas;
    @FXML private Label textoNuevas;
    @FXML private HBox cajaPrueba;
    @FXML private Label textoPrueba;
    @FXML private HBox cajaOcultas;
    @FXML private Label textoOcultas;
    @FXML private VBox cajaCifras;
    @FXML private Label cifraTotal;
    @FXML private Label cifraSemana;
    @FXML private Label cifraValor;
    @FXML private VBox cajaSinPerfiles;
    @FXML private VBox cajaFiltros;
    @FXML private Label textoFiltros;
    @FXML private ComboBox<String[]> orden;
    @FXML private TextField q;
    @FXML private ComboBox<String> entidad;
    @FXML private ComboBox<String> region;
    @FXML private ComboBox<String> departamento;
    @FXML private ComboBox<String> modalidad;
    @FXML private ToggleGroup grupoCierre;
    @FXML private ToggleButton cierreCualquiera;
    @FXML private FlowPane rangosValor;
    @FXML private Hyperlink quitarFiltros;
    @FXML private Label fraseResultados;
    @FXML private VBox vacio;
    @FXML private Label textoVacio;
    @FXML private Label ayudaVacio;
    @FXML private VBox lista;
    @FXML private Button cargarMas;

    private final Servicios s = Servicios.get();
    private final PauseTransition espera = new PauseTransition(Duration.millis(400));
    private final List<ToggleButton> botonesValor = new ArrayList<>();
    private Usuario usuario;
    private Plan plan;
    private OportunidadesService.Pendientes nuevas;
    private int limite = Filtros.LIMITE_INICIAL;
    // Mientras se rellenan los selectores no se vuelve a consultar.
    private boolean pintando;

    @FXML
    private void initialize() {
        usuario = s.usuarios.porId(Sesion.usuarioId()).orElseThrow();
        plan = Plan.efectivo(usuario);
        saludo.setText("Hola" + (usuario.nombre() != null ? ", " + usuario.nombre() : ""));

        orden.getItems().setAll(Filtros.OPCIONES_ORDEN);
        orden.setConverter(convertidor(o -> o == null ? "" : o[1]));
        orden.getSelectionModel().selectFirst();

        region.setConverter(convertidor(id -> Region.porId(id == null ? "" : id).map(Region::nombre).orElse("Todas")));
        departamento.setConverter(convertidor(d -> d == null || d.isEmpty() ? "Todos" : Formato.labelDepartamento(d)));
        modalidad.setConverter(convertidor(m -> m == null || m.isEmpty() ? "Todas" : Modalidades.label(m)));

        for (RangoValor r : RangoValor.TODOS) {
            ToggleButton b = new ToggleButton(Formato.labelRango(r));
            b.setUserData(r.id());
            b.getStyleClass().add("chip");
            b.selectedProperty().addListener((o, a, n) -> refrescarDesdeFiltro());
            botonesValor.add(b);
        }
        rangosValor.getChildren().setAll(botonesValor);

        // Cambiar un filtro vuelve a la primera página.
        orden.valueProperty().addListener((o, a, n) -> refrescarDesdeFiltro());
        region.valueProperty().addListener((o, a, n) -> refrescarDesdeFiltro());
        departamento.valueProperty().addListener((o, a, n) -> refrescarDesdeFiltro());
        modalidad.valueProperty().addListener((o, a, n) -> refrescarDesdeFiltro());
        grupoCierre.selectedToggleProperty().addListener((o, a, n) -> {
            if (n == null) cierreCualquiera.setSelected(true); // siempre hay una opción marcada
            else refrescarDesdeFiltro();
        });
        espera.setOnFinished(e -> refrescarDesdeFiltro());
        q.textProperty().addListener((o, a, n) -> espera.playFromStart());
        entidad.getEditor().textProperty().addListener((o, a, n) -> espera.playFromStart());

        mostrarNuevas();
        refrescar();
    }

    private static <T> StringConverter<T> convertidor(Function<T, String> texto) {
        return new StringConverter<>() {
            @Override
            public String toString(T t) {
                return texto.apply(t);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        };
    }

    /** Avisos dentro de la app: coincidencias nuevas desde la última vez que las vio. */
    private void mostrarNuevas() {
        nuevas = plan.alertas() && usuario.alertasActivas() ? s.oportunidades.alertasPendientes(usuario.id()) : null;
        int n = nuevas == null ? 0 : nuevas.oportunidades().size();
        Vistas.mostrar(cajaNuevas, n > 0);
        if (n > 0) {
            String perfiles = String.join(", ", nuevas.porPerfil().keySet());
            textoNuevas.setText("🔔 Tienes " + n + (n == 1 ? " oportunidad nueva" : " oportunidades nuevas")
                    + " desde tu última visita (" + perfiles + ").");
        }
    }

    @FXML
    private void marcarNuevasComoVistas() {
        if (nuevas != null) s.coincidencias.marcarNotificadas(nuevas.idsCoincidencias());
        Vistas.mostrar(cajaNuevas, false);
    }

    private Filtros filtros() {
        Map<String, String> m = new HashMap<>();
        m.put("q", q.getText());
        m.put("entidad", entidad.getEditor().getText());
        m.put("region", Objects.requireNonNullElse(region.getValue(), ""));
        m.put("departamento", Objects.requireNonNullElse(departamento.getValue(), ""));
        m.put("modalidad", Objects.requireNonNullElse(modalidad.getValue(), ""));
        m.put("cierra", grupoCierre.getSelectedToggle() == null ? "" : (String) grupoCierre.getSelectedToggle().getUserData());
        m.put("valor", String.join(",", botonesValor.stream().filter(ToggleButton::isSelected).map(b -> (String) b.getUserData()).toList()));
        m.put("orden", orden.getValue() == null ? "relevancia" : orden.getValue()[0]);
        m.put("limite", String.valueOf(limite));
        return Filtros.leer(m);
    }

    private void refrescarDesdeFiltro() {
        if (pintando) return;
        limite = Filtros.LIMITE_INICIAL;
        refrescar();
    }

    private void refrescar() {
        Filtros f = filtros();
        OportunidadesService.Panel panel = s.oportunidades.panel(usuario.id(), plan, f);
        boolean tienePerfiles = !s.perfiles.deUsuario(usuario.id()).isEmpty();
        int total = panel.total();

        resumen.setText(total == 0 ? "Aquí verás las oportunidades que coincidan con tus perfiles."
                : total + (total == 1 ? " oportunidad vigente" : " oportunidades vigentes") + " en tus perfiles.");

        Vistas.mostrar(cajaPrueba, "prueba".equals(plan.origen()));
        if ("prueba".equals(plan.origen())) {
            textoPrueba.setText("Te quedan " + plan.diasRestantes() + (plan.diasRestantes() == 1 ? " día" : " días")
                    + " de prueba del plan " + plan.nombre() + ".");
        }
        Vistas.mostrar(cajaOcultas, panel.ocultasPorPlan() > 0);
        textoOcultas.setText("Con el plan " + plan.nombre() + " ves tus " + plan.maxCoincidencias()
                + " mejores oportunidades. Tienes " + panel.ocultasPorPlan() + " más que coinciden con tus perfiles.");

        Vistas.mostrar(cajaCifras, total > 0);
        cifraTotal.setText(String.valueOf(total));
        cifraSemana.setText(String.valueOf(panel.cierranSemana()));
        cifraValor.setText(panel.valorTotalTexto());

        Vistas.mostrar(cajaSinPerfiles, !tienePerfiles);
        Vistas.mostrar(cajaFiltros, tienePerfiles && total > 0);
        rellenarSelectores(panel);
        textoFiltros.setText(f.contar() > 0 ? "Filtros (" + f.contar() + " activos)" : "Filtros");
        Vistas.mostrar(quitarFiltros, f.contar() > 0);

        int t = panel.totalFiltrado();
        String lugar = String.join(" y ", java.util.stream.Stream.of(
                f.departamento().isEmpty() ? null : "en " + Formato.labelDepartamento(f.departamento()),
                Region.porId(f.region()).map(r -> "en la región " + r.nombre()).orElse(null)).filter(Objects::nonNull).toList());
        String plural = t == 1 ? "" : "s";
        fraseResultados.setText(total == 0 ? "" : t == 0 ? "Ningún resultado con estos filtros."
                : panel.hayMas() ? "Mostrando " + panel.oportunidades().size() + " de " + t + " resultado" + plural + (lugar.isEmpty() ? "" : " " + lugar) + "."
                : t + " resultado" + plural + (lugar.isEmpty() ? "" : " " + lugar) + ".");
        Vistas.mostrar(fraseResultados, total > 0);

        boolean sinResultados = tienePerfiles && panel.oportunidades().isEmpty();
        Vistas.mostrar(vacio, sinResultados);
        if (sinResultados && total > 0) {
            textoVacio.setText("No hay coincidencias vigentes con estos filtros.");
            ayudaVacio.setText("Prueba quitando algunos filtros.");
        } else if (sinResultados) {
            textoVacio.setText("Aún no hay coincidencias vigentes.");
            ayudaVacio.setText("Prueba ampliando las palabras clave o quitando filtros de departamento. "
                    + "Si la base aún se está llenando, vuelve en unos minutos.");
        }

        lista.getChildren().clear();
        for (Oportunidad o : panel.oportunidades()) lista.getChildren().add(TarjetaOportunidadControlador.crear(o, true));
        Vistas.mostrar(cargarMas, panel.hayMas());
    }

    /** Opciones de los selectores: solo valores que existen en las coincidencias del usuario. */
    private void rellenarSelectores(OportunidadesService.Panel panel) {
        pintando = true;
        try {
            reemplazar(region, Region.TODAS.stream().map(Region::id).toList());

            reemplazar(departamento, panel.departamentos());
            reemplazar(modalidad, panel.modalidades());

            String textoEntidad = entidad.getEditor().getText();
            entidad.getItems().setAll(panel.entidades());
            entidad.getEditor().setText(textoEntidad);
        } finally {
            pintando = false;
        }
    }

    private static void reemplazar(ComboBox<String> combo, List<String> valores) {
        String actual = combo.getValue();
        List<String> items = new ArrayList<>();
        items.add("");
        items.addAll(valores);
        if (actual != null && !actual.isEmpty() && !items.contains(actual)) items.add(actual);
        combo.getItems().setAll(items);
        combo.setValue(actual == null ? "" : actual);
    }

    @FXML
    private void quitarFiltros() {
        pintando = true;
        q.clear();
        entidad.getEditor().clear();
        region.setValue("");
        departamento.setValue("");
        modalidad.setValue("");
        cierreCualquiera.setSelected(true);
        botonesValor.forEach(b -> b.setSelected(false));
        espera.stop();
        pintando = false;
        refrescarDesdeFiltro();
    }

    @FXML
    private void cargarMas() {
        limite = Math.min(limite + Filtros.PASO_CARGAR_MAS, Filtros.LIMITE_MAXIMO);
        refrescar();
    }

    @FXML
    private void verPlanes() {
        Navegador.mostrar(Pantalla.PLANES);
    }

    @FXML
    private void crearPerfil() {
        Navegador.mostrar(Pantalla.NUEVO_PERFIL);
    }
}
