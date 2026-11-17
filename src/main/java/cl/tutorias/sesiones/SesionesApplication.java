package cl.tutorias.sesiones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del servicio de Sesiones.
 *
 * <p>{@code @SpringBootApplication} activa tres cosas:
 * <ul>
 *   <li>configuración: esta clase puede declarar beans;</li>
 *   <li>escaneo de componentes: Spring busca clases anotadas ({@code @Service},
 *       {@code @Repository}, etc.) en este paquete y sus subpaquetes;</li>
 *   <li>autoconfiguración: según las dependencias del pom, Spring crea por su cuenta
 *       el DataSource, Hibernate, Flyway, Tomcat...</li>
 * </ul>
 *
 * <p>{@code @EnableScheduling} activa las tareas programadas ({@code @Scheduled}), como el
 * vencimiento de sesiones.
 */
@SpringBootApplication
@EnableScheduling
public class SesionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SesionesApplication.class, args);
    }
}
