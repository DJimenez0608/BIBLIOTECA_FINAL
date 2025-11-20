package org.example;

import org.zeromq.ZMQ;

public class GC {

    private static final String PUERTO_PS = "tcp://localhost:5555";  // Puerto donde recibe solicitudes de PS
    private static final String PUERTO_PUBLICADOR = "tcp://*:5560";  // Canal de publicación
    private static final String PUERTO_PRESTAMO = "tcp://localhost:5556"; // Comunicación con actor de préstamo

    private ZMQ.Context context;
    private ZMQ.Socket socketPS;
    private ZMQ.Socket publicador;
    private ZMQ.Socket actorPrestamo;

    public static void main(String[] args) throws InterruptedException {
        new GC().iniciar();
    }

    public void iniciar() throws InterruptedException {
        context = ZMQ.context(1);

        inicializarSockets();

        System.out.println(" GC escuchando solicitudes en " + PUERTO_PS + "...");

        while (!Thread.currentThread().isInterrupted()) {
            String solicitud = socketPS.recvStr();
            System.out.println(" Solicitud recibida: " + solicitud);

            String respuesta = procesarSolicitud(solicitud);
            socketPS.send(respuesta, 0);

            Thread.sleep(100);
        }

        cerrarSockets();
    }

    // inicializar sockets
    private void inicializarSockets() {
        socketPS = context.socket(ZMQ.REP);
        socketPS.bind(PUERTO_PS);

        publicador = context.socket(ZMQ.PUB);
        publicador.bind(PUERTO_PUBLICADOR);

        actorPrestamo = context.socket(ZMQ.REQ);
        actorPrestamo.connect(PUERTO_PRESTAMO);
    }

    // Procesamiento de solicitudes
    private String procesarSolicitud(String solicitud) {
        if (solicitud == null || solicitud.isEmpty()) {
            return "Solicitud vacía o nula";
        }

        if (solicitud.startsWith("DEVOLVER")) {
            return manejarDevolucion(solicitud);

        } else if (solicitud.startsWith("RENOVAR")) {
            return manejarRenovacion(solicitud);

        } else if (solicitud.startsWith("PRESTAMO")) {
            return manejarPrestamo(solicitud);

        } else {
            System.out.println("️ Solicitud no reconocida: " + solicitud);
            return "Solicitud no reconocida";
        }
    }



    //  Devolución
    private String manejarDevolucion(String solicitud) {
        System.out.println(" Procesando devolución...");
        publicador.send("DEVOLUCION " + solicitud);
        System.out.println(" Publicado en canal DEVOLUCION: " + solicitud);
        return "Devolución aceptada, gracias.";
    }

    //  Renovación
    private String manejarRenovacion(String solicitud) {
        System.out.println(" Procesando renovación...");
        String nuevaFecha = obtenerFechaRenovacion();
        publicador.send("RENOVACION " + solicitud);
        System.out.println(" Publicado en canal RENOVACION: " + solicitud);
        return "Renovación aceptada, nueva fecha: " + nuevaFecha;
    }

    //  Préstamo
    private String manejarPrestamo(String solicitud) {
        System.out.println(" Procesando préstamo...");
        actorPrestamo.send(solicitud, 0);
        String respuestaPrestamo = actorPrestamo.recvStr();
        System.out.println(" Respuesta del actor de préstamo: " + respuestaPrestamo);
        return respuestaPrestamo;
    }

    // Nuevo tiempo
    private String obtenerFechaRenovacion() {
        java.time.LocalDate nuevaFecha = java.time.LocalDate.now().plusWeeks(1);
        return nuevaFecha.toString();
    }

    //Cierre de socket
    private void cerrarSockets() {
        socketPS.close();
        publicador.close();
        actorPrestamo.close();
        context.term();
        System.out.println(" Sockets cerrados correctamente.");
    }
}
