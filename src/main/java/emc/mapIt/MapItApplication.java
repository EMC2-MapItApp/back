package emc.mapIt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/**
 * Punto de entrada de la aplicacion MapIt.
 */
public class MapItApplication {

	/**
	 * Inicializa el contexto Spring Boot.
	 *
	 * @param args argumentos de arranque.
	 */
	public static void main(String[] args) {
		SpringApplication.run(MapItApplication.class, args);
	}

}
