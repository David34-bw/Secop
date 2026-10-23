package co.secopradar.modelo;

import co.secopradar.modelo.Modalidades;
import co.secopradar.modelo.Proceso;
import co.secopradar.modelo.util.Formato;
import co.secopradar.modelo.util.TextoOracion;

/** Un proceso listo para mostrar: puntaje, perfil y textos formateados. */
public record Oportunidad(int coincidenciaId, int score, String perfil, boolean notificado, Proceso proceso) {

    public String entidad() {
        return TextoOracion.aTitulo(proceso.entidad());
    }

    public String objeto() {
        String o = TextoOracion.aOracion(proceso.objeto());
        return o == null || o.isBlank() ? "Sin descripción" : o;
    }

    public String nombre() {
        return TextoOracion.aOracion(proceso.nombre());
    }

    public Integer dias() {
        return Formato.diasRestantes(proceso.fechaCierre());
    }

    public Formato.Estado estado() {
        return Formato.estadoOportunidad(dias());
    }

    public String textoCierre() {
        return Formato.textoCierre(dias());
    }

    public String valor() {
        return Formato.pesos(proceso.valor());
    }

    public String departamento() {
        return Formato.labelDepartamento(proceso.departamento());
    }

    public String modalidad() {
        return Modalidades.label(proceso.modalidad());
    }

    public boolean avisoTemprano() {
        return Modalidades.esAvisoTemprano(proceso.modalidad());
    }

    public String fechaCierre() {
        return Formato.fechaLarga(proceso.fechaCierre());
    }

    public String url() {
        return proceso.url();
    }
}
