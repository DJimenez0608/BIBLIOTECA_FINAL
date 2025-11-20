package org.example;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Metricas {
    
    private static final String ARCHIVO_METRICAS = "metricas.txt";
    private static List<Metrica> metricas = new ArrayList<>();
    private static AtomicLong solicitudesProcesadas = new AtomicLong(0);
    private static AtomicLong solicitudesExitosas = new AtomicLong(0);
    private static AtomicLong solicitudesFallidas = new AtomicLong(0);
    
    public static class Metrica {
        public String componente;
        public String operacion;
        public long tiempoInicio;
        public long tiempoFin;
        public long duracion;
        public boolean exito;
        public String detalles;
        
        public Metrica(String componente, String operacion, long tiempoInicio, long tiempoFin, boolean exito, String detalles) {
            this.componente = componente;
            this.operacion = operacion;
            this.tiempoInicio = tiempoInicio;
            this.tiempoFin = tiempoFin;
            this.duracion = tiempoFin - tiempoInicio;
            this.exito = exito;
            this.detalles = detalles;
        }
    }
    
    // Registrar una métrica
    public static void registrarMetrica(String componente, String operacion, long tiempoInicio, long tiempoFin, boolean exito, String detalles) {
        Metrica m = new Metrica(componente, operacion, tiempoInicio, tiempoFin, exito, detalles);
        metricas.add(m);
        
        if (exito) {
            solicitudesExitosas.incrementAndGet();
        } else {
            solicitudesFallidas.incrementAndGet();
        }
        solicitudesProcesadas.incrementAndGet();
    }
    
    // Obtener tiempo actual en nanosegundos
    public static long tiempoActual() {
        return System.nanoTime();
    }
    
    // Obtener tiempo actual en milisegundos
    public static long tiempoActualMs() {
        return System.currentTimeMillis();
    }
    
    // Obtener estadísticas de CPU
    public static String obtenerEstadisticasCPU() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        if (threadBean.isCurrentThreadCpuTimeSupported()) {
            long cpuTime = threadBean.getCurrentThreadCpuTime();
            return String.format("CPU Time: %.2f ms", cpuTime / 1_000_000.0);
        }
        return "CPU Time: No disponible";
    }
    
    // Obtener estadísticas de memoria
    public static String obtenerEstadisticasMemoria() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        return String.format("Memoria: %.2f MB / %.2f MB (%.1f%%)", 
            heapUsed / (1024.0 * 1024.0),
            heapMax / (1024.0 * 1024.0),
            (heapUsed * 100.0) / heapMax);
    }
    
    // Calcular estadísticas
    public static void generarReporte() {
        if (metricas.isEmpty()) {
            System.out.println("No hay métricas registradas.");
            return;
        }
        
        try (PrintWriter pw = new PrintWriter(new FileWriter(ARCHIVO_METRICAS))) {
            pw.println("=== REPORTE DE MÉTRICAS ===");
            pw.println("Fecha: " + java.time.LocalDateTime.now());
            pw.println();
            
            // Estadísticas generales
            pw.println("--- ESTADÍSTICAS GENERALES ---");
            pw.println("Total solicitudes procesadas: " + solicitudesProcesadas.get());
            pw.println("Solicitudes exitosas: " + solicitudesExitosas.get());
            pw.println("Solicitudes fallidas: " + solicitudesFallidas.get());
            if (solicitudesProcesadas.get() > 0) {
                pw.println("Tasa de éxito: " + String.format("%.2f%%", 
                    (solicitudesExitosas.get() * 100.0) / solicitudesProcesadas.get()));
            }
            pw.println();
            
            // Estadísticas por componente
            pw.println("--- ESTADÍSTICAS POR COMPONENTE ---");
            metricas.stream()
                .collect(java.util.stream.Collectors.groupingBy(m -> m.componente))
                .forEach((componente, lista) -> {
                    double promedio = lista.stream().mapToLong(m -> m.duracion).average().orElse(0) / 1_000_000.0;
                    long min = lista.stream().mapToLong(m -> m.duracion).min().orElse(0) / 1_000_000;
                    long max = lista.stream().mapToLong(m -> m.duracion).max().orElse(0) / 1_000_000;
                    long exitosas = lista.stream().filter(m -> m.exito).count();
                    pw.println(String.format("%s: Promedio=%.2f ms, Min=%d ms, Max=%d ms, Total=%d, Exitosas=%d",
                        componente, promedio, min, max, lista.size(), exitosas));
                });
            pw.println();
            
            // Estadísticas por operación
            pw.println("--- ESTADÍSTICAS POR OPERACIÓN ---");
            metricas.stream()
                .collect(java.util.stream.Collectors.groupingBy(m -> m.operacion))
                .forEach((operacion, lista) -> {
                    double promedio = lista.stream().mapToLong(m -> m.duracion).average().orElse(0) / 1_000_000.0;
                    long exitosas = lista.stream().filter(m -> m.exito).count();
                    long min = lista.stream().mapToLong(m -> m.duracion).min().orElse(0) / 1_000_000;
                    long max = lista.stream().mapToLong(m -> m.duracion).max().orElse(0) / 1_000_000;
                    pw.println(String.format("%s: Promedio=%.2f ms, Min=%d ms, Max=%d ms, Exitosas=%d/%d",
                        operacion, promedio, min, max, exitosas, lista.size()));
                });
            pw.println();
            
            // Detalles de cada métrica
            pw.println("--- DETALLES DE MÉTRICAS ---");
            pw.println("Componente | Operación | Duración (ms) | Éxito | Detalles");
            pw.println("------------------------------------------------------------");
            metricas.forEach(m -> {
                pw.println(String.format("%s | %s | %.2f | %s | %s",
                    m.componente, m.operacion, m.duracion / 1_000_000.0, 
                    m.exito ? "Sí" : "No", m.detalles));
            });
            
            // Recursos del sistema
            pw.println();
            pw.println("--- RECURSOS DEL SISTEMA ---");
            pw.println(obtenerEstadisticasMemoria());
            pw.println(obtenerEstadisticasCPU());
            
        } catch (IOException e) {
            System.err.println("Error al generar reporte: " + e.getMessage());
        }
        
        System.out.println("Reporte generado en: " + ARCHIVO_METRICAS);
    }
    
    // Limpiar métricas
    public static void limpiar() {
        metricas.clear();
        solicitudesProcesadas.set(0);
        solicitudesExitosas.set(0);
        solicitudesFallidas.set(0);
    }
}

