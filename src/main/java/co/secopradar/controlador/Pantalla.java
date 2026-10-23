package co.secopradar.controlador;

/** Pantallas que se muestran en el centro de la ventana principal, con su archivo FXML. */
public enum Pantalla {
    INICIO("inicio.fxml", "Inicio"),
    OPORTUNIDADES("oportunidades.fxml", "Oportunidades"),
    PERFILES("perfiles.fxml", "Mis búsquedas"),
    NUEVO_PERFIL("nuevoPerfil.fxml", "Nuevo perfil"),
    BUSCAR("buscar.fxml", "Buscar"),
    PLANES("planes.fxml", "Planes"),
    CUENTA("cuenta.fxml", "Mi cuenta"),
    ADMIN("admin.fxml", "Administración");

    public final String fxml;
    public final String titulo;

    Pantalla(String fxml, String titulo) {
        this.fxml = fxml;
        this.titulo = titulo;
    }
}
