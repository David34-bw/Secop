package co.secopradar.controlador;

import co.secopradar.modelo.servicio.PortadaService;
import co.secopradar.modelo.servicio.Servicios;
import co.secopradar.modelo.util.Formato;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.Map;
import java.util.stream.Collectors;

/** Controlador de inicio.fxml: cifras en vivo de SECOP II (port de la portada). */
public class InicioControlador {

    @FXML private Label abiertos;
    @FXML private Label nuevosHoy;
    @FXML private Label cierranSemana;
    @FXML private Label avisoVacio;
    @FXML private TableView<PortadaService.FilaRegion> tablaRegiones;
    @FXML private TableColumn<PortadaService.FilaRegion, String> colRegion;
    @FXML private TableColumn<PortadaService.FilaRegion, Integer> colTotal;
    @FXML private TableColumn<PortadaService.FilaRegion, Integer> colServicios;
    @FXML private TableColumn<PortadaService.FilaRegion, Integer> colObra;
    @FXML private TableColumn<PortadaService.FilaRegion, Integer> colCompraventa;
    @FXML private TableColumn<PortadaService.FilaRegion, Integer> colSuministros;
    @FXML private TableColumn<PortadaService.FilaRegion, Integer> colConsultoria;
    @FXML private TableColumn<PortadaService.FilaRegion, Integer> colOtros;
    @FXML private Label departamentos;
    @FXML private VBox cajaImpacto;
    @FXML private Label porcentaje;
    @FXML private Label fraseOfertas;
    @FXML private Label notaOfertas;

    @FXML
    private void initialize() {
        PortadaService.Datos d = Servicios.get().portada.datos();
        abiertos.setText(Formato.numero(d.abiertos()));
        nuevosHoy.setText(Formato.numero(d.nuevosHoy()));
        cierranSemana.setText(Formato.numero(d.cierranSemana()));
        Vistas.mostrar(avisoVacio, d.abiertos() == 0);

        colRegion.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().region().nombre()));
        colTotal.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().total()));
        sector(colServicios, "servicios");
        sector(colObra, "obra");
        sector(colCompraventa, "compraventa");
        sector(colSuministros, "suministros");
        sector(colConsultoria, "consultoria");
        sector(colOtros, "otros");
        tablaRegiones.getItems().setAll(d.regiones());

        // Al seleccionar una región se ven sus departamentos.
        departamentos.setText("Selecciona una región para ver sus departamentos. Sin contar "
                + Formato.numero(d.sinDepartamento()) + " procesos sin departamento definido.");
        tablaRegiones.getSelectionModel().selectedItemProperty().addListener((o, a, fila) -> {
            if (fila == null) return;
            departamentos.setText(fila.region().nombre() + ": " + (fila.departamentos().isEmpty() ? "sin procesos abiertos"
                    : fila.departamentos().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(" · "))));
        });

        boolean hayDato = d.porcentaje() != null && d.ofertasCerrados() >= 100;
        Vistas.mostrar(cajaImpacto, hayDato);
        if (hayDato) {
            porcentaje.setText(d.porcentaje() + "%");
            fraseOfertas.setText(d.fraseOfertas() + " Hay contratos que nadie pelea.");
            notaOfertas.setText("En mínima cuantía, la modalidad más común: " + d.numero(d.ofertasUnaONinguna()) + " de "
                    + d.numero(d.ofertasCerrados()) + " procesos ya cerrados"
                    + (d.desde() != null ? " publicados desde el " + d.desde() : "")
                    + " recibieron una oferta o ninguna. Cálculo propio con datos públicos de SECOP II.");
        }
    }

    private static void sector(TableColumn<PortadaService.FilaRegion, Integer> col, String sector) {
        col.setCellValueFactory(c -> {
            Map<String, Integer> m = c.getValue().porSector();
            return new ReadOnlyObjectWrapper<>(m.getOrDefault(sector, 0));
        });
    }
}
