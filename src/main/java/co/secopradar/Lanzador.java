package co.secopradar;

/**
 * Ejecuta esta clase desde IntelliJ (botón ▶ junto a main).
 *
 * Existe porque al correr directamente una clase que extiende Application
 * sin module-info.java, Java exige configurar el module-path de JavaFX a
 * mano. Llamando a App desde una clase normal, Maven/IntelliJ cargan
 * JavaFX desde el classpath sin configuración extra.
 */
public class Lanzador {

    public static void main(String[] args) {
        App.main(args);
    }
}
