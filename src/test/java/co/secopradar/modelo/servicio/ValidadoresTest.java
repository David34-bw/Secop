package co.secopradar.modelo.servicio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas del nombre y de la contraseña. */
class ValidadoresTest {

    // ---- Nombre ----

    @Test
    void nombresReales() {
        for (String ok : List.of("David", "María José Pérez", "José Ñúñez", "Ana-María Gómez", "Daniel D'Alessandro",
                "María de la Cruz", "Juan Pablo Rodríguez Mejía", "Lu Wang", "Iñaki Ortúzar", "  ana   lucía  ")) {
            assertNull(ValidadorNombre.validar(ok), ok);
        }
    }

    @Test
    void nombresQueNoTienenSentido() {
        for (String malo : List.of("", "   ", "a", "D4vid", "juan123", "pedro@", "ana_maria", "juan.perez", "😀 Ana",
                "Brrr", "Daaavid", "asdfgh", "qwerty", "Prueba", "usuario", "Test User", "xzkqtp", "J P", "Juan P",
                "-Ana", "Ana-", "Ana ' Pérez", "Uno Dos Tres Cuatro Cinco Seis")) {
            assertNotNull(ValidadorNombre.validar(malo), malo);
        }
    }

    @Test
    void elNombreSeGuardaConMayusculasIniciales() {
        assertEquals("María de la Cruz", ValidadorNombre.formatear("  MARÍA   DE LA cruz "));
        assertEquals("Ana-María D'Alessandro", ValidadorNombre.formatear("ana-maría d'alessandro"));
        assertEquals("De la Torre Ana", ValidadorNombre.formatear("de la torre ana"));
    }

    // ---- Contraseña ----

    private static boolean segura(String p) {
        return ValidadorPassword.evaluar(p, "david.chacon@gmail.com", "David Chacón").valida();
    }

    @Test
    void contrasenasSeguras() {
        assertTrue(segura("Lic1taciones#Bog"));
        assertTrue(segura("M1-perro-Firulais!"));
        assertTrue(segura("Tr4nsp0rte&Agua9"));
    }

    @Test
    void contrasenasDebiles() {
        for (String mala : List.of("", "Corta1!", "sinmayusculas1!", "SINMINUSCULAS1!", "SinNumeros!!x", "SinSimbolo123x",
                "Con espacio 1!A", "Aaaa1!xyzwq", "Clave1234!xy", "Abcd#9xyzwqt", "Qwer#9xyzwqt", "Password2026!",
                "Colombia#123x", "David#Chacon9", "Chacon!9xyzwq", "Secop-Radar7!")) {
            assertFalse(segura(mala), mala);
        }
    }

    @Test
    void indicaQueReglaFaltaYElNivel() {
        ValidadorPassword.Resultado r = ValidadorPassword.evaluar("hola", null, null);
        assertEquals("Al menos " + ValidadorPassword.MIN + " caracteres", r.primerError());
        assertEquals(ValidadorPassword.Nivel.MUY_DEBIL, r.nivel());
        assertEquals(ValidadorPassword.Nivel.FUERTE, ValidadorPassword.evaluar("Mi-Gato.Come7Pescado", null, null).nivel());
        assertEquals(ValidadorPassword.Nivel.MEDIA, ValidadorPassword.evaluar("Lic1taciones#", null, null).nivel());
    }

    // ---- Registro: el nombre y la contraseña se validan en el modelo ----

    @Test
    void elRegistroRechazaNombreYContrasenaInvalidos() {
        CuentaService cuentas = new CuentaService(null, dominio -> true);
        CuentaService.ErrorCuenta e1 = org.junit.jupiter.api.Assertions.assertThrows(CuentaService.ErrorCuenta.class,
                () -> cuentas.registrar("ana@gmail.com", "Lic1taciones#Bog", "asdf", true));
        assertEquals(ValidadorNombre.validar("asdf"), e1.getMessage());
        CuentaService.ErrorCuenta e2 = org.junit.jupiter.api.Assertions.assertThrows(CuentaService.ErrorCuenta.class,
                () -> cuentas.registrar("ana@gmail.com", "12345678", "Ana Pérez", true));
        assertTrue(e2.getMessage().startsWith("La contraseña no es segura"), e2.getMessage());
    }
}
