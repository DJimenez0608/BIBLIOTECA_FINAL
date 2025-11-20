package org.example;

import org.zeromq.ZMQ;

public class DevolucionRenovacion {

    // Constantes de conexión
    private static final String PUERTO_SUB_GC = "tcp://localhost:5560";
    private static final String PUERTO_REQ_GA = "tcp://localhost:5557";
    private static final String PUERTO_GA2 = "tcp://localhost:5580";  // Puerto del GA2 (fallback directo)

    private ZMQ.Context context;
    private ZMQ.Socket subscriber; // Suscriptor de GC
    private ZMQ.Socket socketGA;   // Comunicador con GA
    private ZMQ.Socket socketGA2;  // Comunicador con GA2 (fallback)

    public static void main(String[] args) {
        new DevolucionRenovacion().iniciar();
    }


    public void iniciar() {
        context = ZMQ.context(1);
        inicializarSockets();

        System.out.println(" DevolucionRenovacion conectado a GC (" + PUERTO_SUB_GC + ") y GA (" + PUERTO_REQ_GA + ")");

        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

        while (!Thread.currentThread().isInterrupted()) {
            procesarMensajes();
        }

        cerrarSockets();
    }

    //Sockets
    private void inicializarSockets() {
        // SUB para recibir mensajes del GC
        subscriber = context.socket(ZMQ.SUB);
        subscriber.connect(PUERTO_SUB_GC);
        subscriber.subscribe("DEVOLUCION".getBytes());
        subscriber.subscribe("RENOVACION".getBytes());

        // REQ para enviar confirmación al GA
        socketGA = context.socket(ZMQ.REQ);
        socketGA.connect(PUERTO_REQ_GA);
        // Configurar timeout de 3 segundos para detectar si GA no está disponible
        socketGA.setReceiveTimeOut(3000);
        
        // REQ para enviar confirmación al GA2 (fallback)
        socketGA2 = context.socket(ZMQ.REQ);
        socketGA2.connect(PUERTO_GA2);
        System.out.println(" Conectado a GA2 (fallback) en " + PUERTO_GA2);
    }

    // Solicitudes
    private void procesarMensajes() {
        long tiempoInicioTotal = Metricas.tiempoActual();
        String mensajeCompleto = subscriber.recvStr();
        System.out.println("\n Mensaje recibido del GC: " + mensajeCompleto);

        // Separar tópico del contenido
        String[] partes = mensajeCompleto.split(" ", 2);
        String topico = partes[0];
        String contenido = partes.length > 1 ? partes[1] : "";

        boolean exito = false;
        try {
            if (topico.equals("DEVOLUCION")) {
                manejarDevolucion(contenido);
                exito = true;

            } else if (topico.equals("RENOVACION")) {
                manejarRenovacion(contenido);
                exito = true;

            } else {
                System.out.println("️ Tópico desconocido: " + topico);
            }
        } catch (Exception e) {
            System.err.println(" Error al procesar mensaje: " + e.getMessage());
        }
        
        long tiempoFinTotal = Metricas.tiempoActual();
        Metricas.registrarMetrica("DEVOLUCION_RENOVACION", topico, 
            tiempoInicioTotal, tiempoFinTotal, exito, contenido);
    }

    // Manejo de devoluciones
    private void manejarDevolucion(String contenido) {
        long tiempoInicio = Metricas.tiempoActual();
        System.out.println(" Procesando devolución -> " + contenido);
        String mensaje = "DEVOLVER " + contenido;
        String respGA = enviarAGa(mensaje);
        long tiempoFin = Metricas.tiempoActual();
        
        boolean exito = respGA != null && !respGA.contains("Error");
        Metricas.registrarMetrica("DEVOLUCION_RENOVACION", "PROCESAR_DEVOLUCION", 
            tiempoInicio, tiempoFin, exito, contenido);
        
        System.out.println(" Respuesta recibida: " + respGA);
    }

    //  Manejo de renovaciones
    private void manejarRenovacion(String contenido) {
        long tiempoInicio = Metricas.tiempoActual();
        System.out.println(" Procesando renovación -> " + contenido);
        String mensaje = "RENOVAR " + contenido;
        String respGA = enviarAGa(mensaje);
        long tiempoFin = Metricas.tiempoActual();
        
        boolean exito = respGA != null && !respGA.contains("Error");
        Metricas.registrarMetrica("DEVOLUCION_RENOVACION", "PROCESAR_RENOVACION", 
            tiempoInicio, tiempoFin, exito, contenido);
        
        System.out.println(" Respuesta recibida: " + respGA);
    }
    
    // Enviar mensaje a GA con fallback a GA2
    private String enviarAGa(String mensaje) {
        long tiempoInicio = Metricas.tiempoActual();
        // Intentar primero con GA
        try {
            socketGA.send(mensaje);
            String respuesta = socketGA.recvStr();
            long tiempoFin = Metricas.tiempoActual();
            
            // Si recibimos null (timeout), GA no está disponible
            if (respuesta == null) {
                Metricas.registrarMetrica("DEVOLUCION_RENOVACION", "CONSULTA_GA", 
                    tiempoInicio, tiempoFin, false, "Timeout GA");
                System.out.println(" ⚠ GA no respondió (timeout), intentando con GA2...");
                return enviarAGa2(mensaje);
            } else {
                boolean exito = !respuesta.contains("Error");
                Metricas.registrarMetrica("DEVOLUCION_RENOVACION", "CONSULTA_GA", 
                    tiempoInicio, tiempoFin, exito, "GA principal");
                System.out.println(" GA respondió: " + respuesta);
                return respuesta;
            }
        } catch (Exception e) {
            long tiempoFin = Metricas.tiempoActual();
            Metricas.registrarMetrica("DEVOLUCION_RENOVACION", "CONSULTA_GA", 
                tiempoInicio, tiempoFin, false, "Error: " + e.getMessage());
            System.out.println(" ⚠ Error al comunicarse con GA: " + e.getMessage() + ", intentando con GA2...");
            return enviarAGa2(mensaje);
        }
    }
    
    // Enviar mensaje a GA2 (fallback)
    private String enviarAGa2(String mensaje) {
        long tiempoInicio = Metricas.tiempoActual();
        try {
            System.out.println(" Enviando solicitud a GA2 (fallback): " + mensaje);
            socketGA2.send(mensaje);
            String respuesta = socketGA2.recvStr();
            long tiempoFin = Metricas.tiempoActual();
            
            boolean exito = respuesta != null && !respuesta.contains("Error");
            Metricas.registrarMetrica("DEVOLUCION_RENOVACION", "CONSULTA_GA2", 
                tiempoInicio, tiempoFin, exito, "GA2 fallback");
            
            System.out.println(" ✓ Respuesta recibida de GA2 (fallback): " + respuesta);
            return respuesta;
        } catch (Exception e) {
            long tiempoFin = Metricas.tiempoActual();
            Metricas.registrarMetrica("DEVOLUCION_RENOVACION", "CONSULTA_GA2", 
                tiempoInicio, tiempoFin, false, "Error: " + e.getMessage());
            System.err.println(" ✗ Error al comunicarse con GA2: " + e.getMessage());
            return "Error: No se pudo comunicar ni con GA ni con GA2";
        }
    }

    // Cierre
    private void cerrarSockets() {
        subscriber.close();
        socketGA.close();
        socketGA2.close();
        context.term();
        System.out.println("\n DevolucionRenovacion finalizado correctamente.");
    }
}
