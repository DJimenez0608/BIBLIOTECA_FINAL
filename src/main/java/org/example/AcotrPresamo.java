package org.example;

import org.zeromq.ZMQ;
import java.util.Objects;

public class AcotrPresamo {

    //  Conexion
    private static final String PUERTO_RECIBIR = "tcp://*:5556";      // Puerto donde GC se conecta
    private static final String PUERTO_GA = "tcp://localhost:5557";   // Puerto del Gestor de Almacenamiento (GA)
    private static final String PUERTO_GA2 = "tcp://localhost:5580";  // Puerto del GA2 (fallback directo)

    private ZMQ.Context context;
    private ZMQ.Socket responder;  // Para recibir solicitudes desde GC
    private ZMQ.Socket socketGA;   // Para consultar disponibilidad con GA
    private ZMQ.Socket socketGA2;  // Para consultar disponibilidad con GA2 (fallback)

    public static void main(String[] args) {
        new AcotrPresamo().iniciar();
    }

    public void iniciar() {
        context = ZMQ.context(1);
        inicializarSockets();

        System.out.println(" Actor de Préstamo activo en " + PUERTO_RECIBIR + "...");
        System.out.println(" Conectado a GA en " + PUERTO_GA);

        while (!Thread.currentThread().isInterrupted()) {
            procesarSolicitudes();
        }

        cerrarSockets();
    }

    //Sockets
    private void inicializarSockets() {
        responder = context.socket(ZMQ.REP);
        responder.bind(PUERTO_RECIBIR);

        socketGA = context.socket(ZMQ.REQ);
        socketGA.connect(PUERTO_GA);
        // Configurar timeout de 3 segundos para detectar si GA no está disponible
        socketGA.setReceiveTimeOut(3000);
        
        socketGA2 = context.socket(ZMQ.REQ);
        socketGA2.connect(PUERTO_GA2);
        System.out.println(" Conectado a GA2 (fallback) en " + PUERTO_GA2);
    }

    // Solicitudes
    private void procesarSolicitudes() {
        long tiempoInicioTotal = Metricas.tiempoActual();
        String solicitud = responder.recvStr();
        System.out.println("\n Solicitud recibida del GC: " + solicitud);

        // Extraer ID del libro de la solicitud (formato: PRESTAMO:ID)
        String idLibro = extraerIdLibro(solicitud);
        if (idLibro == null || idLibro.isEmpty()) {
            Metricas.registrarMetrica("ACTOR_PRESTAMO", "PROCESAR", 
                tiempoInicioTotal, Metricas.tiempoActual(), false, "ID inválido");
            responder.send("Error: No se pudo extraer el ID del libro");
            return;
        }

        // Consultar disponibilidad con GA enviando el ID
        String mensajeDisponibilidad = "Disponibilidad?" + idLibro;
        String respuestaGA = null;
        
        // Medir tiempo de comunicación con GA
        long tiempoInicioGA = Metricas.tiempoActual();
        try {
            socketGA.send(mensajeDisponibilidad);
            respuestaGA = socketGA.recvStr();
            
            long tiempoFinGA = Metricas.tiempoActual();
            boolean exitoGA = respuestaGA != null;
            Metricas.registrarMetrica("ACTOR_PRESTAMO", "CONSULTA_GA", 
                tiempoInicioGA, tiempoFinGA, exitoGA, "GA principal");
            
            // Si recibimos null (timeout), GA no está disponible
            if (respuestaGA == null) {
                System.out.println(" ⚠ GA no respondió (timeout), intentando con GA2...");
                respuestaGA = consultarConGA2(mensajeDisponibilidad);
            } else {
                System.out.println(" Respuesta del GA: " + respuestaGA);
            }
        } catch (Exception e) {
            long tiempoFinGA = Metricas.tiempoActual();
            Metricas.registrarMetrica("ACTOR_PRESTAMO", "CONSULTA_GA", 
                tiempoInicioGA, tiempoFinGA, false, "Error: " + e.getMessage());
            System.out.println(" ⚠ Error al comunicarse con GA: " + e.getMessage() + ", intentando con GA2...");
            respuestaGA = consultarConGA2(mensajeDisponibilidad);
        }

        String respuestaFinal = manejarRespuestaGA(respuestaGA);
        long tiempoFinTotal = Metricas.tiempoActual();
        
        boolean exito = respuestaFinal != null && respuestaFinal.contains("confirmado");
        Metricas.registrarMetrica("ACTOR_PRESTAMO", "PROCESAR_SOLICITUD", 
            tiempoInicioTotal, tiempoFinTotal, exito, idLibro);
        
        responder.send(respuestaFinal);
    }

    // Extraer ID del libro de la solicitud
    private String extraerIdLibro(String solicitud) {
        if (solicitud == null || solicitud.isEmpty()) {
            return null;
        }
        // Formato esperado: PRESTAMO:ID
        String[] partes = solicitud.split(":");
        if (partes.length >= 2) {
            return partes[1].trim();
        }
        return null;
    }

    // Respuestas del GA
    private String manejarRespuestaGA(String respuestaGA) {
        if (Objects.equals(respuestaGA, "SI")) {
            System.out.println(" Libro disponible. Préstamo confirmado.");
            return "Préstamo confirmado";
        } else if (respuestaGA != null && respuestaGA.startsWith("NO")) {
            System.out.println(" Libro no disponible. Solicitud rechazada.");
            // Mantener el mensaje completo del GA para mayor información
            return "Préstamo rechazado: " + respuestaGA;
        } else {
            System.out.println("️ Respuesta desconocida del GA: " + respuestaGA);
            return "Error: respuesta desconocida del GA";
        }
    }

    // Consultar con GA2 (fallback)
    private String consultarConGA2(String mensaje) {
        long tiempoInicio = Metricas.tiempoActual();
        try {
            System.out.println(" Enviando solicitud a GA2 (fallback): " + mensaje);
            socketGA2.send(mensaje);
            String respuesta = socketGA2.recvStr();
            long tiempoFin = Metricas.tiempoActual();
            
            boolean exito = respuesta != null;
            Metricas.registrarMetrica("ACTOR_PRESTAMO", "CONSULTA_GA2", 
                tiempoInicio, tiempoFin, exito, "GA2 fallback");
            
            System.out.println(" ✓ Respuesta recibida de GA2 (fallback): " + respuesta);
            return respuesta;
        } catch (Exception e) {
            long tiempoFin = Metricas.tiempoActual();
            Metricas.registrarMetrica("ACTOR_PRESTAMO", "CONSULTA_GA2", 
                tiempoInicio, tiempoFin, false, "Error: " + e.getMessage());
            System.err.println(" ✗ Error al comunicarse con GA2: " + e.getMessage());
            return "Error: No se pudo comunicar ni con GA ni con GA2";
        }
    }

    // Cierre de sockets
    private void cerrarSockets() {
        responder.close();
        socketGA.close();
        socketGA2.close();
        context.term();
        System.out.println("\n Actor de préstamo finalizado correctamente.");
    }
}
