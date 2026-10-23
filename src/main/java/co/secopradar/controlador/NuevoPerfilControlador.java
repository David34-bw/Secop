package co.secopradar.controlador;

import co.secopradar.modelo.BorradorPerfil;
import co.secopradar.modelo.Catalogo;
import co.secopradar.modelo.GrupoOpciones;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.PerfilesService;
import co.secopradar.modelo.servicio.Servicios;
import co.secopradar.modelo.util.Texto;
import javafx.animation.PauseTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * Controlador de nuevoPerfil.fxml. Copia lo que hay en pantalla a un
 * BorradorPerfil (modelo) y deja que PerfilesService valide y guarde.
 */
public class NuevoPerfilControlador {

    private static final String OTRA = "__otra__";

    @FXML private ScrollPane desplazable;
    @FXML private VBox grupoProfesion;
    @FXML private ComboBox<String> profesion;
    @FXML private TextField profesionOtra;
    @FXML private Label errorProfesion;
    @FXML private Label avisoDirecta;
    @FXML private VBox grupoNombre;
    @FXML private TextField nombre;
    @FXML private Label errorNombre;
    @FXML private FlowPane areasElegidas;
    @FXML private TextField buscarArea;
    @FXML private TreeView<String> arbolAreas;
    @FXML private TextField entradaClave;
    @FXML private FlowPane chipsClave;
    @FXML private TextField entradaExcluir;
    @FXML private FlowPane chipsExcluir;
    @FXML private VBox contenedorDonde;
    @FXML private TextField entradaCiudad;
    @FXML private FlowPane chipsCiudad;
    @FXML private VBox contenedorAjustes;
    @FXML private TextField dias;
    @FXML private Label vistaPrevia;
    @FXML private Label errorGeneral;
    @FXML private Button guardar;

    private final Servicios s = Servicios.get();
    private final Catalogo catalogo = Catalogo.get();
    private final List<GrupoOpcionesControlador> grupos = new ArrayList<>();
    private final PauseTransition espera = new PauseTransition(Duration.millis(500));
    private List<String> areas = new ArrayList<>();
    private ListaChips claves;
    private ListaChips excluir;
    private ListaChips ciudades;
    private Usuario usuario;

    @FXML
    private void initialize() {
        usuario = s.usuarios.porId(Sesion.usuarioId()).orElseThrow();

        // Profesión: catálogo + "Otra" para escribirla.
        profesion.getItems().addAll(catalogo.profesiones().stream().map(Catalogo.Profesion::id).toList());
        profesion.getItems().add(OTRA);
        profesion.setConverter(new StringConverter<>() {
            @Override
            public String toString(String id) {
                if (id == null) return "";
                if (OTRA.equals(id)) return "Otra (escribir la mía)";
                return catalogo.profesion(id).map(Catalogo.Profesion::nombre).orElse(id);
            }

            @Override
            public String fromString(String s) {
                return null;
            }
        });
        profesion.valueProperty().addListener((o, a, id) -> elegirProfesion(id));
        Vistas.mostrar(profesionOtra, false);
        Vistas.mostrar(avisoDirecta, false);
        profesionOtra.textProperty().addListener((o, a, n) -> limpiarError(grupoProfesion, errorProfesion));
        nombre.textProperty().addListener((o, a, n) -> limpiarError(grupoNombre, errorNombre));
        Vistas.ocultar(errorProfesion);
        Vistas.ocultar(errorNombre);
        Vistas.ocultar(errorGeneral);

        claves = new ListaChips(entradaClave, chipsClave, this::programarVistaPrevia);
        excluir = new ListaChips(entradaExcluir, chipsExcluir, this::programarVistaPrevia);
        ciudades = new ListaChips(entradaCiudad, chipsCiudad, this::programarVistaPrevia);

        // Grupos obligatorios: Departamentos en "Dónde", el resto en "Ajustes".
        for (GrupoOpciones g : GrupoOpciones.TODOS) {
            Navegador.Vista<GrupoOpcionesControlador> v = Navegador.cargar("grupoOpciones.fxml");
            v.controlador().configurar(g, this::programarVistaPrevia);
            grupos.add(v.controlador());
            (GrupoOpciones.DEPARTAMENTOS.equals(g.campo()) ? contenedorDonde : contenedorAjustes).getChildren().add(v.raiz());
        }

        // Árbol de áreas UNSPSC: segmentos > familias > clases.
        arbolAreas.setCellFactory(t -> new TreeCell<>() {
            @Override
            protected void updateItem(String codigo, boolean vacia) {
                super.updateItem(codigo, vacia);
                if (vacia || codigo == null) {
                    setText(null);
                    getStyleClass().remove("area-activa");
                    return;
                }
                boolean activa = Catalogo.areaActiva(areas, codigo);
                setText((activa ? "✓ " : "") + nombreArea(codigo) + "  (" + codigo + ")");
                getStyleClass().remove("area-activa");
                if (activa) getStyleClass().add("area-activa");
            }
        });
        arbolAreas.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) alternarAreaSeleccionada();
        });
        buscarArea.textProperty().addListener((o, a, n) -> construirArbol());
        construirArbol();
        pintarAreas();

        espera.setOnFinished(e -> calcularVistaPrevia());
        dias.textProperty().addListener((o, a, n) -> programarVistaPrevia());
    }

    private String nombreArea(String codigo) {
        String n = catalogo.nombreUnspsc(codigo);
        return n == null ? codigo : n;
    }

    // ---- Profesión ----

    private void elegirProfesion(String id) {
        limpiarError(grupoProfesion, errorProfesion);
        boolean otra = OTRA.equals(id);
        Vistas.mostrar(profesionOtra, otra);
        if (otra) {
            Vistas.mostrar(avisoDirecta, false);
            profesionOtra.requestFocus();
            programarVistaPrevia();
            return;
        }
        catalogo.profesion(id).ifPresent(p -> {
            // Prellena el perfil con lo que suele servir para esa profesión.
            areas = new ArrayList<>(Catalogo.quitarAncestros(p.categorias()));
            claves.reemplazar(p.palabrasClave());
            excluir.reemplazar(p.palabrasExcluir());
            if (nombre.getText().isBlank()) nombre.setText(p.nombre());
            avisoDirecta.setText(catalogo.avisoContratacionDirecta());
            Vistas.mostrar(avisoDirecta, p.contratacionDirecta());
            pintarAreas();
            arbolAreas.refresh();
            programarVistaPrevia();
        });
    }

    // ---- Áreas ----

    private void construirArbol() {
        String filtro = Texto.normalizar(buscarArea.getText());
        TreeItem<String> raiz = new TreeItem<>("raiz");
        for (Catalogo.Segmento seg : catalogo.segmentos()) {
            boolean segCoincide = filtro.isEmpty()
                    || Texto.normalizar(seg.label() + " " + seg.ejemplos()).contains(filtro);
            TreeItem<String> nodoSeg = new TreeItem<>(seg.code());
            for (Catalogo.AreaUnspsc fam : catalogo.familias(seg.code())) {
                TreeItem<String> nodoFam = new TreeItem<>(fam.codigo());
                boolean algunaClase = false;
                for (Catalogo.AreaUnspsc cl : catalogo.clases(fam.codigo())) {
                    if (filtro.isEmpty() || segCoincide || Texto.normalizar(cl.nombre()).contains(filtro)
                            || Texto.normalizar(fam.nombre()).contains(filtro)) {
                        nodoFam.getChildren().add(new TreeItem<>(cl.codigo()));
                        algunaClase = true;
                    }
                }
                if (filtro.isEmpty() || segCoincide || algunaClase || Texto.normalizar(fam.nombre()).contains(filtro)) {
                    nodoSeg.getChildren().add(nodoFam);
                }
            }
            if (segCoincide || !nodoSeg.getChildren().isEmpty()) {
                nodoSeg.setExpanded(!filtro.isEmpty() || Catalogo.areaActiva(areas, seg.code()));
                raiz.getChildren().add(nodoSeg);
            }
        }
        arbolAreas.setRoot(raiz);
    }

    @FXML
    private void alternarAreaSeleccionada() {
        TreeItem<String> item = arbolAreas.getSelectionModel().getSelectedItem();
        if (item == null) return;
        areas = new ArrayList<>(Catalogo.alternarArea(areas, item.getValue()));
        pintarAreas();
        arbolAreas.refresh();
        programarVistaPrevia();
    }

    private void pintarAreas() {
        areasElegidas.getChildren().clear();
        if (areas.isEmpty()) {
            Label l = new Label("Ninguna área elegida (opcional si usas palabras clave).");
            l.getStyleClass().add("texto-chico");
            areasElegidas.getChildren().add(l);
            return;
        }
        for (String c : areas) {
            Button quitar = new Button("×");
            quitar.getStyleClass().add("chip-quitar");
            quitar.setOnAction(e -> {
                areas.remove(c);
                pintarAreas();
                arbolAreas.refresh();
                programarVistaPrevia();
            });
            HBox chip = new HBox(4, new Label(nombreArea(c) + " (" + c + ")"), quitar);
            chip.getStyleClass().addAll("chip", "chip-activo");
            areasElegidas.getChildren().add(chip);
        }
    }

    // ---- Borrador, vista previa y guardado ----

    private BorradorPerfil borrador() {
        BorradorPerfil b = new BorradorPerfil();
        b.nombre = nombre.getText();
        String id = profesion.getValue();
        b.profesionOtra = OTRA.equals(id);
        b.profesionId = b.profesionOtra ? null : id;
        b.textoProfesionOtra = profesionOtra.getText();
        b.categorias.addAll(areas);
        b.palabrasClave.addAll(claves.valores());
        b.palabrasExcluir.addAll(excluir.valores());
        b.ciudades.addAll(ciudades.valores());
        try {
            b.diasMinRestantes = dias.getText().isBlank() ? null : Integer.parseInt(dias.getText().trim());
        } catch (NumberFormatException e) {
            b.diasMinRestantes = null;
        }
        grupos.forEach(g -> g.volcar(b));
        return b;
    }

    private void programarVistaPrevia() {
        espera.playFromStart();
    }

    /** Cuántos procesos vigentes coincidirían, calculado en segundo plano. */
    private void calcularVistaPrevia() {
        BorradorPerfil b = borrador();
        vistaPrevia.setText("Calculando...");
        Task<Integer> tarea = new Task<>() {
            @Override
            protected Integer call() {
                return s.perfilesService.vistaPrevia(b, usuario.id());
            }
        };
        tarea.setOnSucceeded(e -> {
            int n = tarea.getValue();
            vistaPrevia.setText(n == 0
                    ? "Ningún proceso vigente coincide todavía. Prueba con más palabras clave o menos filtros."
                    : n + (n == 1 ? " proceso vigente coincide" : " procesos vigentes coinciden") + " con este perfil.");
        });
        tarea.setOnFailed(e -> vistaPrevia.setText(""));
        Thread t = new Thread(tarea, "vista-previa");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void guardar() {
        Vistas.ocultar(errorGeneral);
        BorradorPerfil b = borrador();
        List<PerfilesService.ErrorCampo> errores = s.perfilesService.validar(b);
        mostrarErrores(errores);
        if (!errores.isEmpty()) {
            Vistas.mostrarError(errorGeneral, "Revisa los campos marcados en rojo.");
            return;
        }
        try {
            guardar.setDisable(true);
            PerfilesService.Creado creado = s.perfilesService.crear(b, usuario);
            Navegador.<PerfilesControlador>mostrar(Pantalla.PERFILES).creado(creado.coincidencias());
        } catch (PerfilesService.ErrorPerfil e) {
            guardar.setDisable(false);
            mostrarErrores(List.of(e.error));
            Vistas.mostrarError(errorGeneral, e.getMessage());
        }
    }

    private void mostrarErrores(List<PerfilesService.ErrorCampo> errores) {
        Node primero = null;
        limpiarError(grupoProfesion, errorProfesion);
        limpiarError(grupoNombre, errorNombre);
        grupos.forEach(g -> g.mostrarError(null));
        for (PerfilesService.ErrorCampo e : errores) {
            Node nodo = switch (e.campo()) {
                case "profesion" -> marcar(grupoProfesion, errorProfesion, e.mensaje());
                case "nombre" -> marcar(grupoNombre, errorNombre, e.mensaje());
                default -> grupos.stream().filter(g -> g.campo().equals(e.campo())).findFirst().map(g -> {
                    g.mostrarError(e.mensaje());
                    return (Node) g.nodo();
                }).orElse(null);
            };
            if (primero == null) primero = nodo;
        }
        if (primero != null) desplazarHasta(primero);
    }

    private Node marcar(VBox grupo, Label etiqueta, String mensaje) {
        Vistas.mostrarError(etiqueta, mensaje);
        Vistas.clase(grupo, "grupo-error", true);
        return grupo;
    }

    private void limpiarError(VBox grupo, Label etiqueta) {
        Vistas.ocultar(etiqueta);
        Vistas.clase(grupo, "grupo-error", false);
    }

    /** Lleva el scroll hasta el primer campo con error. */
    private void desplazarHasta(Node nodo) {
        double alto = desplazable.getContent().getBoundsInLocal().getHeight();
        double y = desplazable.getContent().sceneToLocal(nodo.localToScene(nodo.getBoundsInLocal())).getMinY();
        double visible = desplazable.getViewportBounds().getHeight();
        desplazable.setVvalue(Math.max(0, Math.min(1, (y - 40) / Math.max(1, alto - visible))));
    }

    @FXML
    private void volver() {
        Navegador.mostrar(Pantalla.PERFILES);
    }
}
