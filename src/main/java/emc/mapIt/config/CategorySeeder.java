package emc.mapIt.config;

import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.MainCategory;
import emc.mapIt.entity.SubCategory;
import emc.mapIt.repository.LocationTypeRepository;
import emc.mapIt.repository.MainCategoryRepository;
import emc.mapIt.repository.SubCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CategorySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

    private final MainCategoryRepository mainCategoryRepository;
    private final SubCategoryRepository subCategoryRepository;
    private final LocationTypeRepository locationTypeRepository;

    public CategorySeeder(MainCategoryRepository mainCategoryRepository,
                          SubCategoryRepository subCategoryRepository,
                          LocationTypeRepository locationTypeRepository) {
        this.mainCategoryRepository = mainCategoryRepository;
        this.subCategoryRepository = subCategoryRepository;
        this.locationTypeRepository = locationTypeRepository;
    }

    @Override
    public void run(String... args) {
        if (mainCategoryRepository.count() > 0) {
            log.info("Categories already seeded. Skipping.");
            return;
        }
        seed("Deportes", "⚽", "#3b82f6", List.of(
            sub("Ciclismo", "🚴", List.of(
                lt("Quedadas", "Eventos y rutas organizadas por particulares o grupos de ciclistas."),
                lt("Profesional", "Tiendas, talleres y servicios profesionales de ciclismo."))),
            sub("Running", "🏃", List.of(
                lt("Quedadas", "Grupos de running y carreras populares organizadas por usuarios."),
                lt("Profesional", "Tiendas de running, clubs y academias de atletismo."))),
            sub("Fútbol", "⚽", List.of(
                lt("Quedadas", "Partidos informales y quedadas para jugar al fútbol."),
                lt("Profesional", "Clubs, academias y estadios de fútbol."))),
            sub("Natación", "🏊", List.of(
                lt("Quedadas", "Grupos de natación y quedadas en piscinas o zonas acuáticas."),
                lt("Profesional", "Piscinas, clubs de natación y escuelas acuáticas.")))));
        seed("Cultura", "🎭", "#8b5cf6", List.of(
            sub("Museos", "🏛️", List.of(
                lt("Visita", "Puntos de interés cultural para visitar: museos, exposiciones, etc."),
                lt("Profesional", "Museos y espacios culturales con gestión profesional."))),
            sub("Música", "🎵", List.of(
                lt("Quedadas", "Jam sessions, conciertos y encuentros musicales entre aficionados."),
                lt("Profesional", "Escuelas de música, salas de ensayo y estudios de grabación."))),
            sub("Teatro", "🎭", List.of(
                lt("Quedadas", "Grupos de teatro amateur y quedadas para asistir a obras."),
                lt("Profesional", "Teatros, compañías y escuelas de artes escénicas.")))));
        seed("Gastronomía", "🍽️", "#f59e0b", List.of(
            sub("Restaurantes", "🍴", List.of(
                lt("Quedadas", "Quedadas gastronómicas y cenas grupales organizadas por usuarios."),
                lt("Profesional", "Restaurantes y locales de hostelería registrados en la plataforma."))),
            sub("Bares & Cafeterías", "☕", List.of(
                lt("Quedadas", "Afterworks y quedadas en bares organizadas por usuarios."),
                lt("Profesional", "Bares, cafeterías y locales nocturnos profesionales."))),
            sub("Mercados", "🛒", List.of(
                lt("Visita", "Mercados locales, de temporada y mercadillos gastronómicos."),
                lt("Profesional", "Mercados municipales y establecimientos de alimentación profesional.")))));
        seed("Naturaleza", "🌿", "#10b981", List.of(
            sub("Senderismo", "🥾", List.of(
                lt("Quedadas", "Rutas y excursiones organizadas por grupos de senderismo."),
                lt("Profesional", "Empresas de guías de montaña, tiendas de material y clubs de senderismo."))),
            sub("Playas & Ríos", "🏖️", List.of(
                lt("Quedadas", "Quedadas en playas, ríos y zonas de baño organizadas por usuarios."),
                lt("Profesional", "Escuelas de surf, alquiler de embarcaciones y servicios náuticos."))),
            sub("Parques & Jardines", "🌳", List.of(
                lt("Quedadas", "Pícnics, juegos al aire libre y quedadas en parques."),
                lt("Visita", "Parques naturales, jardines botánicos y reservas naturales.")))));
        seed("Tecnología", "💻", "#06b6d4", List.of(
            sub("Gaming", "🎮", List.of(
                lt("Quedadas", "Torneos, LAN parties y quedadas de gaming entre usuarios."),
                lt("Profesional", "Tiendas, arcades y centros de gaming profesionales."))),
            sub("Maker & DIY", "🔧", List.of(
                lt("Quedadas", "Talleres, hackathons y meetups de makers y DIY."),
                lt("Profesional", "Fab Labs, makerspaces y tiendas de electrónica profesionales.")))));
        seed("Educación", "📚", "#f97316", List.of(
            sub("Idiomas", "🌍", List.of(
                lt("Quedadas", "Intercambios de idiomas y grupos de estudio organizados por usuarios."),
                lt("Profesional", "Escuelas de idiomas, academias y profesores particulares."))),
            sub("Formación & Talleres", "🎓", List.of(
                lt("Quedadas", "Talleres gratuitos y grupos de estudio organizados por usuarios."),
                lt("Profesional", "Academias, coworkings y centros de formación profesional."))),
            sub("Educación Infantil", "🧸", List.of(
                lt("Quedadas", "Grupos de juego y actividades para niños organizadas por padres y madres."),
                lt("Profesional", "Guarderías, escuelas infantiles y centros de educación temprana.")))));
        log.info("Category seed completed.");
    }

    private record SubDef(String name, String icon, List<LocDef> locs) {}
    private record LocDef(String name, String desc) {}

    private SubDef sub(String name, String icon, List<LocDef> locs) {
        return new SubDef(name, icon, locs);
    }

    private LocDef lt(String name, String desc) {
        return new LocDef(name, desc);
    }

    private void seed(String catName, String icon, String color, List<SubDef> subDefs) {
        MainCategory mc = new MainCategory();
        mc.setName(catName);
        mc.setIcon(icon);
        mc.setColor(color);
        mc = mainCategoryRepository.save(mc);

        for (SubDef sd : subDefs) {
            SubCategory sc = new SubCategory();
            sc.setName(sd.name());
            sc.setIcon(sd.icon());
            sc.setMainCategoryId(mc.getId());
            sc = subCategoryRepository.save(sc);

            for (LocDef ld : sd.locs()) {
                LocationType lt = new LocationType();
                lt.setName(ld.name());
                lt.setDescription(ld.desc());
                lt.setSubCategoryId(sc.getId());
                locationTypeRepository.save(lt);
            }
        }
    }
}
