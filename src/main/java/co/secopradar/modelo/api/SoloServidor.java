package co.secopradar.modelo.api;

/**
 * Operación que en modo API no existe en la app: la hace el servidor
 * (ingesta, cruce, ajuste de planes) o un servicio con una sola llamada
 * (registro, crear perfil...). Si aparece, es un error de programación.
 */
public class SoloServidor extends UnsupportedOperationException {

    public SoloServidor(String que) {
        super("En modo API esto lo hace el servidor: " + que);
    }
}
