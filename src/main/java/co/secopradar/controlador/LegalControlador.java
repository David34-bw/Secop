package co.secopradar.controlador;

import co.secopradar.modelo.servicio.LegalService;
import co.secopradar.modelo.servicio.Servicios;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Controlador de legal.fxml: pinta Términos o Política de datos. */
public class LegalControlador {

    @FXML private VBox documento;

    private String titulo = "Documento";

    public void mostrar(String nombre) {
        documento.getChildren().clear();
        for (LegalService.Bloque b : Servicios.get().legal.documento(nombre)) {
            Label l = new Label(b.tipo() == LegalService.TipoBloque.VINETA ? "•  " + b.texto() : b.texto());
            l.setWrapText(true);
            l.setMaxWidth(Double.MAX_VALUE);
            l.getStyleClass().add(switch (b.tipo()) {
                case TITULO -> "titulo-pagina";
                case SUBTITULO -> "subtitulo-documento";
                case VINETA -> "vineta";
                case PARRAFO -> "parrafo";
            });
            if (b.tipo() == LegalService.TipoBloque.TITULO) titulo = b.texto();
            documento.getChildren().add(l);
        }
    }

    public String titulo() {
        return titulo;
    }
}
