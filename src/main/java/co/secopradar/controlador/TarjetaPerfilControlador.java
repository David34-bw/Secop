package co.secopradar.controlador;

import co.secopradar.modelo.Catalogo;
import co.secopradar.modelo.Perfil;
import co.secopradar.modelo.RangoValor;
import co.secopradar.modelo.util.Formato;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** Controlador de tarjetaPerfil.fxml: muestra un perfil guardado y permite eliminarlo. */
public class TarjetaPerfilControlador {

    @FXML private VBox tarjeta;
    @FXML private Label nombre;
    @FXML private Label profesion;
    @FXML private Label apagadoPorPlan;
    @FXML private GridPane datos;

    private Perfil perfil;
    private Runnable alEliminar;

    public void mostrar(Perfil p, Runnable alEliminar) {
        this.perfil = p;
        this.alEliminar = alEliminar;
        Catalogo catalogo = Catalogo.get();

        nombre.setText(p.nombre());
        String prof = p.profesionId() == null ? null
                : catalogo.profesion(p.profesionId()).map(Catalogo.Profesion::nombre).orElse(null);
        profesion.setText(prof == null ? "" : "Profesión: " + prof);
        Vistas.mostrar(profesion, prof != null);
        Vistas.mostrar(apagadoPorPlan, p.desactivadoPorPlan());
        Vistas.clase(tarjeta, "inactivo", !p.activo());

        fila("Áreas", p.categorias().stream().map(c -> {
            String n = catalogo.nombreUnspsc(c);
            return n == null ? c : n;
        }).toList(), " · ");
        fila("Palabras clave", p.palabrasClave(), ", ");
        fila("Excluir", p.palabrasExcluir(), ", ");
        fila("Departamentos", p.departamentos().isEmpty() ? List.of("Todo el país") : p.departamentos(), ", ");
        fila("Ciudades", p.ciudades(), ", ");
        fila("Modalidades", p.modalidades().isEmpty() ? List.of("Todas") : p.modalidades(), ", ");
        fila("Tipos de contrato", p.tiposContrato().isEmpty() ? List.of("Todos") : p.tiposContrato(), ", ");
        fila("Entidades", p.tiposEntidad().isEmpty() ? List.of("Todas") : p.tiposEntidad(), ", ");
        fila("Valor", p.rangosValor().isEmpty() ? List.of("Cualquier valor")
                : p.rangosValor().stream().map(id -> RangoValor.porId(id).map(Formato::labelRango).orElse(id)).toList(), ", ");
        if (p.diasMinRestantes() != null) fila("Días mínimos", List.of(String.valueOf(p.diasMinRestantes())), "");
    }

    private void fila(String titulo, List<String> valores, String separador) {
        if (valores.isEmpty()) return;
        int n = datos.getRowCount();
        Label t = new Label(titulo);
        t.getStyleClass().add("texto-chico");
        Label v = new Label(String.join(separador, valores));
        v.setWrapText(true);
        v.getStyleClass().add("dato-perfil");
        datos.addRow(n, t, v);
    }

    @FXML
    private void eliminar() {
        if (Vistas.confirmar("¿Eliminar el perfil «" + perfil.nombre() + "»?",
                "Se borran también sus coincidencias. Esta acción no se puede deshacer.")) {
            alEliminar.run();
        }
    }
}
