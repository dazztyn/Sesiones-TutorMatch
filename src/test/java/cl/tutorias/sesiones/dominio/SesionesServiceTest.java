package cl.tutorias.sesiones.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cl.tutorias.sesiones.ContenedoresDePrueba;
import cl.tutorias.sesiones.PruebaDeIntegracion;
import cl.tutorias.sesiones.dominio.SesionException.Motivo;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;

/** Reglas de negocio contra Postgres real (incluidas las restricciones EXCLUDE de la base). */
class SesionesServiceTest extends PruebaDeIntegracion {

    private static final String RAMO = "MAT1610";

    // Lunes 4 y martes 5 de marzo de 2030, primer y segundo bloque (hora de Chile).
    private static final Instant LUNES_0810 = bloque(3, 4, 8, 10);
    private static final Instant LUNES_0955 = bloque(3, 4, 9, 55);
    private static final Instant MARTES_0810 = bloque(3, 5, 8, 10);

    @Autowired
    private SesionesService servicio;

    @Autowired
    private CasillaRepository casillas;

    private static Motivo motivo(Executable accion) {
        return assertThrows(SesionException.class, accion).getMotivo();
    }

    private Sesion reservar(String tutor, String estudiante, Instant inicio) {
        servicio.marcarCasilla(tutor, RAMO, inicio);
        return servicio.crear(tutor, estudiante, RAMO, inicio, finDe(inicio));
    }

    // ------------------------------------------------------------------ Marcar

    @Test
    void marcarUnBloqueValidoCreaUnaCasillaDe90Minutos() {
        Casilla casilla = servicio.marcarCasilla(usuario(), RAMO, LUNES_0810);
        assertEquals(LUNES_0810, casilla.getInicio());
        assertEquals(finDe(LUNES_0810), casilla.getFin());
    }

    @Test
    void marcarRechazaSolapesHorasInvalidasPasadoYRamoInactivo() {
        String tutor = usuario();
        servicio.marcarCasilla(tutor, RAMO, LUNES_0810);
        // Mismo horario aunque sea otro ramo.
        assertEquals(Motivo.CHOQUE_DE_HORARIO, motivo(() -> servicio.marcarCasilla(tutor, "FIS1513", LUNES_0810)));
        assertEquals(Motivo.DATOS_INVALIDOS, motivo(() -> servicio.marcarCasilla(tutor, RAMO, LUNES_0810.plusSeconds(60))));
        assertEquals(Motivo.DATOS_INVALIDOS,
                motivo(() -> servicio.marcarCasilla(tutor, RAMO, Instant.parse("2020-03-02T11:10:00Z"))));
        assertEquals(Motivo.DATOS_INVALIDOS, motivo(() -> servicio.marcarCasilla(" ", RAMO, LUNES_0955)));
        assertEquals(Motivo.REGLA_INCUMPLIDA,
                motivo(() -> servicio.marcarCasilla(tutor, ContenedoresDePrueba.RAMO_INACTIVO, LUNES_0955)));
    }

    // ------------------------------------------------------------------- Crear

    @Test
    void crearTomaUnaCasillaLibreExacta() {
        String tutor = usuario();
        String estudiante = usuario();
        Sesion sesion = reservar(tutor, estudiante, LUNES_0810);
        assertEquals(EstadoSesion.PENDIENTE, sesion.getEstado());
        assertEquals(sesion.getId(), servicio.obtener(sesion.getId().toString()).getId());
    }

    @Test
    void crearExigeUnaCasillaDeEseTutorRamoYHorario() {
        String tutor = usuario();
        String estudiante = usuario();
        servicio.marcarCasilla(tutor, RAMO, LUNES_0810);
        // Sin casilla en ese horario, de ese ramo, o con otro fin.
        assertEquals(Motivo.REGLA_INCUMPLIDA,
                motivo(() -> servicio.crear(tutor, estudiante, RAMO, LUNES_0955, finDe(LUNES_0955))));
        assertEquals(Motivo.REGLA_INCUMPLIDA,
                motivo(() -> servicio.crear(tutor, estudiante, "FIS1513", LUNES_0810, finDe(LUNES_0810))));
        assertEquals(Motivo.REGLA_INCUMPLIDA,
                motivo(() -> servicio.crear(tutor, estudiante, RAMO, LUNES_0810, LUNES_0810.plusSeconds(3600))));
    }

    @Test
    void crearRechazaUnaCasillaOcupadaYReservarseASiMismo() {
        String tutor = usuario();
        reservar(tutor, usuario(), LUNES_0810);
        assertEquals(Motivo.REGLA_INCUMPLIDA,
                motivo(() -> servicio.crear(tutor, usuario(), RAMO, LUNES_0810, finDe(LUNES_0810))));
        assertEquals(Motivo.DATOS_INVALIDOS,
                motivo(() -> servicio.crear(tutor, tutor, RAMO, LUNES_0810, finDe(LUNES_0810))));
        assertEquals(Motivo.DATOS_INVALIDOS,
                motivo(() -> servicio.crear(tutor, usuario(), "", LUNES_0810, finDe(LUNES_0810))));
        assertEquals(Motivo.DATOS_INVALIDOS, motivo(() -> servicio.crear(tutor, usuario(), RAMO,
                Instant.parse("2020-03-02T11:10:00Z"), Instant.parse("2020-03-02T12:40:00Z"))));
    }

    @Test
    void crearRechazaElCruceEntrePapeles() {
        String ana = usuario();
        // Ana dicta una sesión el lunes a las 08:10...
        reservar(ana, usuario(), LUNES_0810);
        // ...así que no puede tomar, como alumna, una casilla de otro tutor a la misma hora.
        String otroTutor = usuario();
        servicio.marcarCasilla(otroTutor, RAMO, LUNES_0810);
        assertEquals(Motivo.CHOQUE_DE_HORARIO,
                motivo(() -> servicio.crear(otroTutor, ana, RAMO, LUNES_0810, finDe(LUNES_0810))));
    }

    @Test
    void crearConRamoInactivoFalla() {
        String tutor = usuario();
        // La casilla existe (por ejemplo, el tutor quitó el ramo después de marcarla).
        casillas.saveAndFlush(new Casilla(tutor, ContenedoresDePrueba.RAMO_INACTIVO, LUNES_0810));
        assertEquals(Motivo.REGLA_INCUMPLIDA, motivo(() -> servicio.crear(tutor, usuario(),
                ContenedoresDePrueba.RAMO_INACTIVO, LUNES_0810, finDe(LUNES_0810))));
    }

    // ---------------------------------------------------------- Cambiar estado

    @Test
    void soloElTutorConfirmaYFinaliza() {
        String tutor = usuario();
        String estudiante = usuario();
        String id = reservar(tutor, estudiante, LUNES_0810).getId().toString();

        assertEquals(Motivo.SIN_PERMISO, motivo(() -> servicio.cambiarEstado(id, estudiante, EstadoSesion.CONFIRMADA)));
        assertEquals(EstadoSesion.CONFIRMADA, servicio.cambiarEstado(id, tutor, EstadoSesion.CONFIRMADA).getEstado());
        assertEquals(Motivo.SIN_PERMISO, motivo(() -> servicio.cambiarEstado(id, estudiante, EstadoSesion.FINALIZADA)));
        assertEquals(EstadoSesion.FINALIZADA, servicio.cambiarEstado(id, tutor, EstadoSesion.FINALIZADA).getEstado());
        assertEquals(Motivo.TRANSICION_INVALIDA,
                motivo(() -> servicio.cambiarEstado(id, tutor, EstadoSesion.CANCELADA)));
    }

    @Test
    void elAlumnoPuedeCancelarYUnTerceroNo() {
        String tutor = usuario();
        String estudiante = usuario();
        String id = reservar(tutor, estudiante, LUNES_0810).getId().toString();

        assertEquals(Motivo.SIN_PERMISO, motivo(() -> servicio.cambiarEstado(id, usuario(), EstadoSesion.CANCELADA)));
        assertEquals(EstadoSesion.CANCELADA, servicio.cambiarEstado(id, estudiante, EstadoSesion.CANCELADA).getEstado());
        // Cancelada, la casilla vuelve a estar libre para otro alumno.
        assertEquals(EstadoSesion.PENDIENTE,
                servicio.crear(tutor, usuario(), RAMO, LUNES_0810, finDe(LUNES_0810)).getEstado());
    }

    @Test
    void obtenerUnaSesionInexistenteOConIdInvalido() {
        assertEquals(Motivo.NO_ENCONTRADA, motivo(() -> servicio.obtener(UUID.randomUUID().toString())));
        assertEquals(Motivo.NO_ENCONTRADA, motivo(() -> servicio.obtener("no-es-un-uuid")));
    }

    // ------------------------------------------------------------ Disponibilidad

    @Test
    void desmarcarFallaConSesionActivaYFuncionaDespuesDeCancelar() {
        String tutor = usuario();
        String estudiante = usuario();
        Sesion sesion = reservar(tutor, estudiante, LUNES_0810);
        String casilla = casillas.findByTutorIdAndRamoCodigoAndInicio(tutor, RAMO, LUNES_0810)
                .orElseThrow().getId().toString();

        assertEquals(Motivo.SIN_PERMISO, motivo(() -> servicio.desmarcarCasilla(casilla, usuario())));
        assertEquals(Motivo.REGLA_INCUMPLIDA, motivo(() -> servicio.desmarcarCasilla(casilla, tutor)));

        servicio.cambiarEstado(sesion.getId().toString(), estudiante, EstadoSesion.CANCELADA);
        servicio.desmarcarCasilla(casilla, tutor);
        assertTrue(casillas.findById(UUID.fromString(casilla)).isEmpty());
        assertEquals(Motivo.NO_ENCONTRADA, motivo(() -> servicio.desmarcarCasilla(casilla, tutor)));
    }

    @Test
    void disponibilidadMarcaLasCasillasOcupadas() {
        String tutor = usuario();
        reservar(tutor, usuario(), LUNES_0810);
        servicio.marcarCasilla(tutor, RAMO, LUNES_0955);

        List<CasillaConEstado> semana = servicio.disponibilidad(tutor, bloque(3, 4, 0, 0), bloque(3, 9, 0, 0));
        assertEquals(List.of(LUNES_0810, LUNES_0955), semana.stream().map(c -> c.casilla().getInicio()).toList());
        assertFalse(semana.get(0).libre());
        assertTrue(semana.get(1).libre());

        assertEquals(Motivo.DATOS_INVALIDOS, motivo(() -> servicio.disponibilidad(tutor, LUNES_0955, LUNES_0810)));
        assertEquals(Motivo.DATOS_INVALIDOS, motivo(() -> servicio.disponibilidad(tutor, null, LUNES_0810)));
    }

    @Test
    void buscarDisponibilidadDevuelveSoloCasillasLibresDelRamo() {
        String ramo = "BUS" + UUID.randomUUID().toString().substring(0, 6);
        String tutorA = usuario();
        String tutorB = usuario();
        servicio.marcarCasilla(tutorA, ramo, LUNES_0810);
        servicio.marcarCasilla(tutorB, ramo, LUNES_0955);
        servicio.crear(tutorA, usuario(), ramo, LUNES_0810, finDe(LUNES_0810));

        List<Casilla> libres = servicio.buscarDisponibilidad(ramo, bloque(3, 4, 0, 0), bloque(3, 9, 0, 0));
        assertEquals(List.of(tutorB), libres.stream().map(Casilla::getTutorId).toList());
    }

    // ------------------------------------------------------------------ Listar

    @Test
    void listarFiltraPorUsuarioEstadoYRango() {
        String tutor = usuario();
        String estudiante = usuario();
        Sesion lunes = reservar(tutor, estudiante, LUNES_0810);
        Sesion martes = reservar(tutor, usuario(), MARTES_0810);
        servicio.cambiarEstado(martes.getId().toString(), tutor, EstadoSesion.CONFIRMADA);

        assertEquals(List.of(lunes.getId(), martes.getId()), ids(servicio.listar(tutor, "", null, null, null)));
        assertEquals(List.of(martes.getId()), ids(servicio.listar(tutor, "", EstadoSesion.CONFIRMADA, null, null)));
        assertEquals(List.of(martes.getId()),
                ids(servicio.listar(tutor, "", null, bloque(3, 5, 0, 0), bloque(3, 6, 0, 0))));
        assertEquals(List.of(lunes.getId()), ids(servicio.listar("", estudiante, null, null, null)));
    }

    private static List<UUID> ids(List<Sesion> sesiones) {
        return sesiones.stream().map(Sesion::getId).toList();
    }

    // ------------------------------------------------------------------ Vencer

    @Test
    void vencerFinalizaLasConfirmadasTerminadasYCancelaLasPendientesSinConfirmar() {
        // Enero de 2030: ningún otro test usa esas fechas (ver PruebaDeIntegracion).
        Instant lunes = bloque(1, 7, 8, 10);
        Instant martes = bloque(1, 8, 8, 10);
        String tutor = usuario();
        Sesion confirmada = reservar(tutor, usuario(), lunes);
        servicio.cambiarEstado(confirmada.getId().toString(), tutor, EstadoSesion.CONFIRMADA);
        Sesion pendiente = reservar(tutor, usuario(), martes);

        // El martes a las 08:10: la del lunes ya terminó y la del martes empezó sin confirmar.
        assertTrue(servicio.vencer(martes) >= 2);
        assertEquals(EstadoSesion.FINALIZADA, servicio.obtener(confirmada.getId().toString()).getEstado());
        assertEquals(EstadoSesion.CANCELADA, servicio.obtener(pendiente.getId().toString()).getEstado());
    }
}
