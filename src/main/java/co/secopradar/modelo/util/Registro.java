package co.secopradar.modelo.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Mensajes en la consola de IntelliJ con java.util.logging. Acepta "{}"
 * como marcador: log.info("{} procesos leídos", n).
 */
public final class Registro {

    private final Logger logger;

    private Registro(Logger logger) {
        this.logger = logger;
    }

    public static Registro de(Class<?> clase) {
        return new Registro(Logger.getLogger(clase.getName()));
    }

    private static String formatear(String plantilla, Object... args) {
        StringBuilder sb = new StringBuilder();
        int desde = 0, i = 0;
        for (int pos; (pos = plantilla.indexOf("{}", desde)) >= 0 && i < args.length; desde = pos + 2) {
            sb.append(plantilla, desde, pos).append(args[i++]);
        }
        return sb.append(plantilla.substring(desde)).toString();
    }

    private Throwable error(Object... args) {
        return args.length > 0 && args[args.length - 1] instanceof Throwable t ? t : null;
    }

    public void info(String plantilla, Object... args) {
        logger.info(formatear(plantilla, args));
    }

    public void warn(String plantilla, Object... args) {
        logger.log(Level.WARNING, formatear(plantilla, args), error(args));
    }

    public void error(String plantilla, Object... args) {
        logger.log(Level.SEVERE, formatear(plantilla, args), error(args));
    }
}
