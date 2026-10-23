package co.secopradar.controlador;

import co.secopradar.modelo.Catalogo;
import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.Servicios;
import co.secopradar.modelo.util.Formato;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Controlador de admin.fxml: estadísticas, actualización manual y gestión de usuarios. */
public class AdminControlador {

    /** Fila de la tabla de usuarios. */
    public record FilaUsuario(Usuario usuario, Plan plan) {
    }

    @FXML private Label mensaje;
    @FXML private Label totalUsuarios;
    @FXML private Label totalPerfiles;
    @FXML private Label totalProcesos;
    @FXML private Label totalCoincidencias;
    @FXML private Label ultimoIngreso;
    @FXML private Label estadoActualizacion;
    @FXML private Spinner<Integer> dias;
    @FXML private Button botonActualizar;
    @FXML private TableView<Map<String, Object>> tablaIngesta;
    @FXML private TableView<Map<String, Object>> tablaCruce;
    @FXML private TableView<FilaUsuario> tablaUsuarios;
    @FXML private TableColumn<FilaUsuario, String> colCorreo;
    @FXML private TableColumn<FilaUsuario, String> colRol;
    @FXML private TableColumn<FilaUsuario, String> colPlanEfectivo;
    @FXML private TableColumn<FilaUsuario, String> colCreada;
    @FXML private TableColumn<FilaUsuario, String> colPlan;
    @FXML private TableColumn<FilaUsuario, String> colAcciones;
    @FXML private VBox cajaRanking;
    @FXML private VBox ranking;

    private final Servicios s = Servicios.get();

    @FXML
    private void initialize() {
        Vistas.ocultar(mensaje);
        dias.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, s.entero("ingesta.dias-atras", 5)));

        columnasLog(tablaIngesta, List.of("Fecha", "Leídos", "Nuevos", "Actualizados", "Estado"),
                List.of("corrida", "leidos", "nuevos", "actualizados", "estado"));
        columnasLog(tablaCruce, List.of("Fecha", "Perfiles", "Procesos", "Nuevas", "Quitadas", "Estado"),
                List.of("corrida", "perfiles", "procesos", "nuevas", "quitadas", "estado"));

        colCorreo.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().usuario().email()));
        colPlanEfectivo.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().plan().nombre() + " (" + c.getValue().plan().origen() + ")"));
        colCreada.setCellValueFactory(c -> new ReadOnlyStringWrapper(Formato.fechaLarga(c.getValue().usuario().createdAt())));
        colRol.setCellFactory(c -> celdaLista(List.of("usuario", "admin"), f -> f.usuario().rol(), this::cambiarRol));
        colPlan.setCellFactory(c -> celdaLista(List.copyOf(Plan.PLANES.keySet()), f -> f.usuario().plan(), this::asignarPlan));
        colAcciones.setCellFactory(c -> new TableCell<>() {
            private final Button boton = new Button("Reiniciar prueba");

            {
                boton.getStyleClass().add("boton-enlace");
                boton.setOnAction(e -> reiniciarPrueba(getTableRow().getItem()));
            }

            @Override
            protected void updateItem(String item, boolean vacia) {
                super.updateItem(item, vacia);
                setGraphic(vacia || getTableRow() == null || getTableRow().getItem() == null ? null : boton);
            }
        });

        // Mientras corre la actualización, esta pantalla también muestra el avance.
        s.actualizacion.alCambiarEstado("admin", m -> Platform.runLater(this::pintarActualizacion));
        recargar();
    }

    private static void columnasLog(TableView<Map<String, Object>> tabla, List<String> titulos, List<String> claves) {
        for (int i = 0; i < titulos.size(); i++) {
            String clave = claves.get(i);
            TableColumn<Map<String, Object>, String> col = new TableColumn<>(titulos.get(i));
            col.setCellValueFactory(c -> new ReadOnlyStringWrapper(Objects.toString(c.getValue().get(clave), "")));
            tabla.getColumns().add(col);
        }
    }

    /** Celda con una lista desplegable que guarda al cambiar. */
    private TableCell<FilaUsuario, String> celdaLista(List<String> opciones, java.util.function.Function<FilaUsuario, String> actual,
                                                      BiConsumer<FilaUsuario, String> alCambiar) {
        return new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>();

            {
                combo.getItems().setAll(opciones);
                combo.setOnAction(e -> {
                    FilaUsuario f = getTableRow() == null ? null : getTableRow().getItem();
                    if (f != null && combo.getValue() != null && !combo.getValue().equals(actual.apply(f))) {
                        alCambiar.accept(f, combo.getValue());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean vacia) {
                super.updateItem(item, vacia);
                FilaUsuario f = vacia || getTableRow() == null ? null : getTableRow().getItem();
                if (f == null) {
                    setGraphic(null);
                } else {
                    combo.setValue(actual.apply(f));
                    setGraphic(combo);
                }
            }
        };
    }

    @FXML
    private void recargar() {
        totalUsuarios.setText(String.valueOf(s.usuarios.contar()));
        totalPerfiles.setText(String.valueOf(s.perfiles.contar()));
        totalProcesos.setText(Formato.numero(s.procesos.contar()));
        totalCoincidencias.setText(Formato.numero(s.coincidencias.contar()));
        ultimoIngreso.setText("Último proceso nuevo: " + Formato.fechaHora(s.procesos.ultimoIngreso())
                + " · Actualización automática todos los días a las " + s.entero("actualizacion.hora", 21)
                + ":00 mientras la aplicación esté abierta.");
        pintarActualizacion();

        tablaIngesta.getItems().setAll(formatear(s.logs.ultimas("ingesta_log", 8)));
        tablaCruce.getItems().setAll(formatear(s.logs.ultimas("coincidencias_log", 8)));
        tablaUsuarios.getItems().setAll(s.usuarios.todos().stream().map(u -> new FilaUsuario(u, Plan.efectivo(u))).toList());

        ranking.getChildren().clear();
        List<Map<String, Object>> filas = s.perfiles.rankingProfesiones();
        Vistas.mostrar(cajaRanking, !filas.isEmpty());
        for (Map<String, Object> f : filas) {
            String id = (String) f.get("profesion_id");
            String nombre = Catalogo.get().profesion(id).map(Catalogo.Profesion::nombre).orElse(id);
            ranking.getChildren().add(new Label(nombre + " — " + f.get("n")));
        }
    }

    private void pintarActualizacion() {
        boolean corriendo = s.actualizacion.estaCorriendo();
        estadoActualizacion.setText((corriendo ? "En curso: " : "Último resultado: ") + s.actualizacion.ultimoResultado());
        botonActualizar.setDisable(corriendo);
        if (!corriendo) {
            totalProcesos.setText(Formato.numero(s.procesos.contar()));
            totalCoincidencias.setText(Formato.numero(s.coincidencias.contar()));
        }
    }

    private static List<Map<String, Object>> formatear(List<Map<String, Object>> filas) {
        for (Map<String, Object> f : filas) {
            Object c = f.get("corrida_at");
            f.put("corrida", c instanceof OffsetDateTime o ? Formato.fechaHora(o) : Objects.toString(c, ""));
            f.put("estado", Boolean.TRUE.equals(f.get("exito")) ? "OK" : "Error: " + f.get("error"));
        }
        return filas;
    }

    @FXML
    private void actualizar() {
        int d = dias.getValue() == null ? 5 : dias.getValue();
        boolean ok = s.actualizacion.correrEnSegundoPlano(d);
        Vistas.mostrarError(mensaje, ok
                ? "Actualización iniciada (" + d + " días). Puede tardar unos minutos; el avance se ve aquí y en la barra de abajo."
                : "Ya hay una actualización en curso.");
        pintarActualizacion();
    }

    private void asignarPlan(FilaUsuario f, String plan) {
        s.usuarios.asignarPlan(f.usuario().id(), plan);
        s.usuarios.porId(f.usuario().id()).ifPresent(u -> s.planes.sincronizarPerfiles(u.id(), Plan.efectivo(u)));
        Vistas.mostrarError(mensaje, "Plan de " + f.usuario().email() + " cambiado a " + plan + ".");
        recargar();
    }

    private void cambiarRol(FilaUsuario f, String rol) {
        if (f.usuario().id().equals(Sesion.usuarioId())) {
            Vistas.informar("No puedes cambiar tu propio rol", "Pídele a otro administrador que lo haga.");
            recargar();
            return;
        }
        s.usuarios.asignarRol(f.usuario().id(), rol);
        Vistas.mostrarError(mensaje, "Rol de " + f.usuario().email() + " cambiado a " + rol + ".");
        recargar();
    }

    private void reiniciarPrueba(FilaUsuario f) {
        if (f == null) return;
        s.usuarios.reiniciarPrueba(f.usuario().id(), OffsetDateTime.now().plusDays(Plan.DIAS_PRUEBA));
        s.usuarios.porId(f.usuario().id()).ifPresent(u -> s.planes.sincronizarPerfiles(u.id(), Plan.efectivo(u)));
        Vistas.mostrarError(mensaje, "Prueba de " + Plan.DIAS_PRUEBA + " días reiniciada para " + f.usuario().email() + ".");
        recargar();
    }
}
