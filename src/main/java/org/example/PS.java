package org.example;

import org.zeromq.ZMQ;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

public class PS {

    // Dirección del Gestor de Carga
    private static final String DIRECCION_GC = "tcp://localhost:5555";
    private static final String ARCHIVO_SOLICITUDES = "PS.txt";

    private ZMQ.Context context;
    private ZMQ.Socket socketGC;
    private Scanner scanner;

    public static void main(String[] args) {
        new PS().iniciar();
    }

    public void iniciar() {
        inicializarConexion();
        scanner = new Scanner(System.in);

        System.out.println(" PS iniciado.");
        System.out.println("️  Leer solicitudes desde archivo (" + ARCHIVO_SOLICITUDES + ")");
        System.out.println("  Ingresar solicitudes manualmente");
        System.out.print("Seleccione una opción (1 o 2): ");
        String opcion = scanner.nextLine();

        try {
            if (opcion.equals("1")) {
                leerDesdeArchivo();
            } else if (opcion.equals("2")) {
                ingresoManual();
            } else {
                System.out.println(" Opción no válida. Finalizando programa.");
            }
        } finally {
            cerrarConexion();
        }
    }

    // Conexion
    private void inicializarConexion() {
        context = ZMQ.context(1);
        socketGC = context.socket(ZMQ.REQ);
        socketGC.connect(DIRECCION_GC);
        System.out.println(" Conectado al Gestor de Carga en " + DIRECCION_GC);
    }

    // Lectura de archivo PS.txt
    private void leerDesdeArchivo() {
        try (BufferedReader br = new BufferedReader(new FileReader(ARCHIVO_SOLICITUDES))) {
            System.out.println("\n Leyendo solicitudes desde " + ARCHIVO_SOLICITUDES + "...\n");

            String linea;
            while ((linea = br.readLine()) != null) {
                procesarLineaArchivo(linea);
                Thread.sleep(500); // pausa entre solicitudes
            }

            System.out.println(" Lectura desde archivo finalizada.");

        } catch (IOException e) {
            System.out.println(" Error al leer el archivo: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Solicitudes manuales
    private void ingresoManual() {
        System.out.println("\nIngrese solicitudes (formato: <TIPO> <ISBN>)");
        System.out.println("Ejemplo: PRESTAMO 9780134685991");
        System.out.println("Escriba 'SALIR' para terminar.\n");

        while (true) {
            System.out.print("Ingrese solicitud: ");
            String linea = scanner.nextLine();

            if (linea.equalsIgnoreCase("SALIR")) break;

            procesarLineaArchivo(linea);
        }
    }

    // Lineas del archivo
    private void procesarLineaArchivo(String linea) {
        linea = linea.trim();
        if (linea.isEmpty()) return;

        String[] partes = linea.split("[ ,:]+"); // acepta espacio, coma o dos puntos
        if (partes.length < 2) {
            System.out.println(" Formato inválido en línea: " + linea);
            return;
        }

        String tipo = partes[0].toUpperCase();
        String isbn = partes[1];
        String mensaje = tipo + ":" + isbn;

        enviarSolicitud(mensaje);
    }

    // Enviar solicitud y recibir respuesta
    private void enviarSolicitud(String mensaje) {
        System.out.println(" Enviando solicitud -> " + mensaje);
        socketGC.send(mensaje, 0);

        String respuesta = socketGC.recvStr();
        System.out.println(" Respuesta GC: " + respuesta + "\n");
    }

  // Cierre
    private void cerrarConexion() {
        socketGC.close();
        context.term();
        if (scanner != null) scanner.close();
        System.out.println(" PS finalizado correctamente.");
    }
}
