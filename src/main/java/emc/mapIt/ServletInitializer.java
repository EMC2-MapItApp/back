package emc.mapIt;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Adaptador para despliegue de la aplicacion como WAR.
 */
public class ServletInitializer extends SpringBootServletInitializer {

	@Override
	/**
	 * Configura la aplicacion principal para el contenedor servlet.
	 *
	 * @param application builder de Spring.
	 * @return builder configurado.
	 */
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(MapItApplication.class);
	}

}
