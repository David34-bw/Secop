package co.secopradar.controlador;

import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.Plan;
import co.secopradar.modelo.Sesion;
import co.secopradar.modelo.Usuario;
import co.secopradar.modelo.servicio.Servicios;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** Controlador de perfiles.fxml: lista de perfiles del usuario. */
public class PerfilesControlador {

    @FXML private Button botonNuevo;
    @FXML private Label bienvenida;
    @FXML private HBox cajaLimite;
    @FXML private Label textoLimite;
    @FXML private VBox vacio;
    @FXML private VBox lista;

    private final Servicios s = Servicios.get();

    @FXML
    private void initialize() {
        Vistas.mostrar(bienvenida, false);
        refrescar();
    }

    /** Recién registrado: mensaje de bienvenida. */
    public void bienvenida() {
        Vistas.mostrar(bienvenida, true);
    }

    /** Recién creado un perfil: cuántas coincidencias encontró. */
    public void creado(int coincidencias) {
        bienvenida.setText("Perfil guardado: " + coincidencias
                + (coincidencias == 1 ? " proceso vigente coincide." : " procesos vigentes coinciden.")
                + " Míralos en Oportunidades.");
        Vistas.mostrar(bienvenida, true);
    }

    private void refrescar() {
        Usuario u = s.usuarios.porId(Sesion.usuarioId()).orElseThrow();
        boolean puedeCrear = s.perfilesService.puedeCrear(u);
        botonNuevo.setDisable(!puedeCrear);
        Vistas.mostrar(cajaLimite, !puedeCrear);
        if (!puedeCrear) textoLimite.setText(Plan.efectivo(u).mensajeLimitePerfiles());

        List<Perfil> perfiles = s.perfiles.deUsuario(u.id());
        Vistas.mostrar(vacio, perfiles.isEmpty());
        lista.getChildren().clear();
        for (Perfil p : perfiles) {
            Navegador.Vista<TarjetaPerfilControlador> v = Navegador.cargar("tarjetaPerfil.fxml");
            v.controlador().mostrar(p, () -> {
                s.perfilesService.eliminar(p.id(), u);
                refrescar();
            });
            lista.getChildren().add(v.raiz());
        }
    }

    @FXML
    private void nuevoPerfil() {
        Navegador.mostrar(Pantalla.NUEVO_PERFIL);
    }

    @FXML
    private void verPlanes() {
        Navegador.mostrar(Pantalla.PLANES);
    }
}
