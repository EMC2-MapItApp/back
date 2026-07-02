package emc.mapIt.config;

import emc.mapIt.entity.LocationType;
import emc.mapIt.entity.MainCategory;
import emc.mapIt.entity.SubCategory;
import emc.mapIt.repository.MainCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class CategorySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CategorySeeder.class);

    private final MainCategoryRepository mainCategoryRepository;

    public CategorySeeder(MainCategoryRepository mainCategoryRepository) {
        this.mainCategoryRepository = mainCategoryRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (mainCategoryRepository.count() > 0) {
            log.info("Categories already seeded. Skipping.");
            return;
        }

        List<MainCategory> categories = buildCategories();
        mainCategoryRepository.saveAll(categories);
        log.info("Category seed completed. Main categories created: {}", categories.size());
    }

    private List<MainCategory> buildCategories() {
        List<MainCategory> out = new ArrayList<>();

        MainCategory deportes = main("Deportes", "⚽", "#3b82f6");
        sub(deportes, "Ciclismo", "🚴", List.of(
                lt("Quedadas", "Eventos y rutas organizadas por particulares o grupos de ciclistas."),
                lt("Profesional", "Tiendas, talleres y servicios profesionales de ciclismo.")
        ));
        sub(deportes, "Running", "🏃", List.of(
                lt("Quedadas", "Grupos de running y carreras populares organizadas por usuarios."),
                lt("Profesional", "Tiendas de running, clubs y academias de atletismo.")
        ));
        sub(deportes, "Fútbol", "⚽", List.of(
                lt("Quedadas", "Partidos informales y quedadas para jugar al fútbol."),
                lt("Profesional", "Clubs, academias y estadios de fútbol.")
        ));
        sub(deportes, "Natación", "🏊", List.of(
                lt("Quedadas", "Grupos de natación y quedadas en piscinas o zonas acuáticas."),
                lt("Profesional", "Piscinas, clubs de natación y escuelas acuáticas.")
        ));
        out.add(deportes);

        MainCategory cultura = main("Cultura", "🎭", "#8b5cf6");
        sub(cultura, "Museos", "🏛️", List.of(
                lt("Visita", "Puntos de interés cultural para visitar: museos, exposiciones, etc."),
                lt("Profesional", "Museos y espacios culturales con gestión profesional.")
        ));
        sub(cultura, "Música", "🎵", List.of(
                lt("Quedadas", "Jam sessions, conciertos y encuentros musicales entre aficionados."),
                lt("Profesional", "Escuelas de música, salas de ensayo y estudios de grabación.")
        ));
        sub(cultura, "Teatro", "🎭", List.of(
                lt("Quedadas", "Grupos de teatro amateur y quedadas para asistir a obras."),
                lt("Profesional", "Teatros, compañías y escuelas de artes escénicas.")
        ));
        out.add(cultura);

        MainCategory gastronomia = main("Gastronomía", "🍽️", "#f59e0b");
        sub(gastronomia, "Restaurantes", "🍴", List.of(
                lt("Quedadas", "Quedadas gastronómicas y cenas grupales organizadas por usuarios."),
                lt("Profesional", "Restaurantes y locales de hostelería registrados en la plataforma.")
        )); 
        sub(gastronomia, "Bares & Cafeterías", "☕", List.of(
                lt("Quedadas", "Afterworks y quedadas en bares organizadas por usuarios."),
                lt("Profesional", "Bares, cafeterías y locales nocturnos profesionales.")
        ));
        sub(gastronomia, "Mercados", "🛒", List.of(
                lt("Visita", "Mercados locales, de temporada y mercadillos gastronómicos."),
                lt("Profesional", "Mercados municipales y establecimientos de alimentación profesional.")
        ));
        out.add(gastronomia);

        MainCategory naturaleza = main("Naturaleza", "🌿", "#10b981");
        sub(naturaleza, "Senderismo", "🥾", List.of(
                lt("Quedadas", "Rutas y excursiones organizadas por grupos de senderismo."),
                lt("Profesional", "Empresas de guías de montaña, tiendas de material y clubs de senderismo.")
        ));
        sub(naturaleza, "Playas & Ríos", "🏖️", List.of(
                lt("Quedadas", "Quedadas en playas, ríos y zonas de baño organizadas por usuarios."),
                lt("Profesional", "Escuelas de surf, alquiler de embarcaciones y servicios náuticos.")
        ));
        sub(naturaleza, "Parques & Jardines", "🌳", List.of(
                lt("Quedadas", "Pícnics, juegos al aire libre y quedadas en parques."),
                lt("Visita", "Parques naturales, jardines botánicos y reservas naturales.")
        ));
        sub(naturaleza, "Observación de fauna y flora", "🦉", List.of(
                lt("Quedadas", "Excursiones y quedadas para observar fauna y flora en su hábitat natural."),
                lt("Profesional", "Guías de naturaleza, reservas y centros de interpretación.")
        ));
        out.add(naturaleza);

        MainCategory tecnologia = main("Tecnología", "💻", "#06b6d4");
        sub(tecnologia, "Gaming", "🎮", List.of(
                lt("Quedadas", "Torneos, LAN parties y quedadas de gaming entre usuarios."),
                lt("Profesional", "Tiendas, arcades y centros de gaming profesionales.")
        ));
        sub(tecnologia, "Maker & DIY", "🔧", List.of(
                lt("Quedadas", "Talleres, hackathons y meetups de makers y DIY."),
                lt("Profesional", "Fab Labs, makerspaces y tiendas de electrónica profesionales.")
        ));

        out.add(tecnologia);

        MainCategory educacion = main("Educación", "📚", "#f97316");
        sub(educacion, "Idiomas", "🌍", List.of(
                lt("Quedadas", "Intercambios de idiomas y grupos de estudio organizados por usuarios."),
                lt("Profesional", "Escuelas de idiomas, academias y profesores particulares.")
        ));
        sub(educacion, "Formación & Talleres", "🎓", List.of(
                lt("Quedadas", "Talleres gratuitos y grupos de estudio organizados por usuarios."),
                lt("Profesional", "Academias, coworkings y centros de formación profesional.")
        ));
        sub(educacion, "Educación Infantil", "🧸", List.of(
                lt("Quedadas", "Grupos de juego y actividades para niños organizadas por padres y madres."),
                lt("Profesional", "Guarderías, escuelas infantiles y centros de educación temprana.")
        ));
        out.add(educacion);

        // Repite el mismo patrón para:
        // Gastronomía, Naturaleza, Tecnología y Educación (igual que tu category.service.ts)

        return out;
    }

    private MainCategory main(String name, String icon, String color) {
        MainCategory m = new MainCategory();
        m.setName(name);
        m.setIcon(icon);
        m.setColor(color);
        m.setSubCategories(new ArrayList<>());
        return m;
    }

    private void sub(MainCategory parent, String name, String icon, List<LocationType> locationTypes) {
        SubCategory s = new SubCategory();
        s.setName(name);
        s.setIcon(icon);
        s.setMainCategory(parent);
        s.setLocationTypes(new ArrayList<>());

        for (LocationType lt : locationTypes) {
            lt.setSubCategory(s);
            s.getLocationTypes().add(lt);
        }

        parent.getSubCategories().add(s);
    }

    private LocationType lt(String name, String description) {
        LocationType lt = new LocationType();
        lt.setName(name);
        lt.setDescription(description);
        return lt;
    }
}

/*
const CATEGORIES_TREE: MainCategory[] = [
  // ── DEPORTES ──────────────────────────────────────────────────────────────
  {
    id: 'deportes',
    name: 'Deportes',
    icon: '⚽',
    color: '#3b82f6',        // azul
    subcategories: [
      {
        id: 'ciclismo',
        name: 'Ciclismo',
        icon: '🚴',
        mainCategoryId: 'deportes',
        locationTypes: [
          {
            id: 'ciclismo-quedadas',
            name: 'Quedadas',
            description: 'Eventos y rutas organizadas por particulares o grupos de ciclistas.',
            subcategoryId: 'ciclismo',
          },
          {
            id: 'ciclismo-profesional',
            name: 'Profesional',
            description: 'Tiendas, talleres y servicios profesionales de ciclismo.',
            subcategoryId: 'ciclismo',
          },
        ],
      },
      {
        id: 'running',
        name: 'Running',
        icon: '🏃',
        mainCategoryId: 'deportes',
        locationTypes: [
          {
            id: 'running-quedadas',
            name: 'Quedadas',
            description: 'Grupos de running y carreras populares organizadas por usuarios.',
            subcategoryId: 'running',
          },
          {
            id: 'running-profesional',
            name: 'Profesional',
            description: 'Tiendas de running, clubs y academias de atletismo.',
            subcategoryId: 'running',
          },
        ],
      },
      {
        id: 'futbol',
        name: 'Fútbol',
        icon: '⚽',
        mainCategoryId: 'deportes',
        locationTypes: [
          {
            id: 'futbol-quedadas',
            name: 'Quedadas',
            description: 'Partidos informales y quedadas para jugar al fútbol.',
            subcategoryId: 'futbol',
          },
          {
            id: 'futbol-profesional',
            name: 'Profesional',
            description: 'Clubs, academias y estadios de fútbol.',
            subcategoryId: 'futbol',
          },
        ],
      },
      {
        id: 'natacion',
        name: 'Natación',
        icon: '🏊',
        mainCategoryId: 'deportes',
        locationTypes: [
          {
            id: 'natacion-quedadas',
            name: 'Quedadas',
            description: 'Grupos de natación y quedadas en piscinas o zonas acuáticas.',
            subcategoryId: 'natacion',
          },
          {
            id: 'natacion-profesional',
            name: 'Profesional',
            description: 'Piscinas, clubs de natación y escuelas acuáticas.',
            subcategoryId: 'natacion',
          },
        ],
      },
    ],
  },

  // ── CULTURA ───────────────────────────────────────────────────────────────
  {
    id: 'cultura',
    name: 'Cultura',
    icon: '🎭',
    color: '#8b5cf6',        // violeta
    subcategories: [
      {
        id: 'museos',
        name: 'Museos',
        icon: '🏛️',
        mainCategoryId: 'cultura',
        locationTypes: [
          {
            id: 'museos-visita',
            name: 'Visita',
            description: 'Puntos de interés cultural para visitar: museos, exposiciones, etc.',
            subcategoryId: 'museos',
          },
          {
            id: 'museos-profesional',
            name: 'Profesional',
            description: 'Museos y espacios culturales con gestión profesional.',
            subcategoryId: 'museos',
          },
        ],
      },
      {
        id: 'musica',
        name: 'Música',
        icon: '🎵',
        mainCategoryId: 'cultura',
        locationTypes: [
          {
            id: 'musica-quedadas',
            name: 'Quedadas',
            description: 'Jam sessions, conciertos y encuentros musicales entre aficionados.',
            subcategoryId: 'musica',
          },
          {
            id: 'musica-profesional',
            name: 'Profesional',
            description: 'Escuelas de música, salas de ensayo y estudios de grabación.',
            subcategoryId: 'musica',
          },
        ],
      },
      {
        id: 'teatro',
        name: 'Teatro',
        icon: '🎭',
        mainCategoryId: 'cultura',
        locationTypes: [
          {
            id: 'teatro-quedadas',
            name: 'Quedadas',
            description: 'Grupos de teatro amateur y quedadas para asistir a obras.',
            subcategoryId: 'teatro',
          },
          {
            id: 'teatro-profesional',
            name: 'Profesional',
            description: 'Teatros, compañías y escuelas de artes escénicas.',
            subcategoryId: 'teatro',
          },
        ],
      },
    ],
  },

  // ── GASTRONOMÍA ───────────────────────────────────────────────────────────
  {
    id: 'gastronomia',
    name: 'Gastronomía',
    icon: '🍽️',
    color: '#f59e0b',        // ámbar
    subcategories: [
      {
        id: 'restaurantes',
        name: 'Restaurantes',
        icon: '🍴',
        mainCategoryId: 'gastronomia',
        locationTypes: [
          {
            id: 'restaurantes-quedadas',
            name: 'Quedadas',
            description: 'Quedadas gastronómicas y cenas grupales organizadas por usuarios.',
            subcategoryId: 'restaurantes',
          },
          {
            id: 'restaurantes-profesional',
            name: 'Profesional',
            description: 'Restaurantes y locales de hostelería registrados en la plataforma.',
            subcategoryId: 'restaurantes',
          },
        ],
      },
      {
        id: 'bares',
        name: 'Bares & Cafeterías',
        icon: '☕',
        mainCategoryId: 'gastronomia',
        locationTypes: [
          {
            id: 'bares-quedadas',
            name: 'Quedadas',
            description: 'Afterworks y quedadas en bares organizadas por usuarios.',
            subcategoryId: 'bares',
          },
          {
            id: 'bares-profesional',
            name: 'Profesional',
            description: 'Bares, cafeterías y locales nocturnos profesionales.',
            subcategoryId: 'bares',
          },
        ],
      },
      {
        id: 'mercados',
        name: 'Mercados',
        icon: '🛒',
        mainCategoryId: 'gastronomia',
        locationTypes: [
          {
            id: 'mercados-visita',
            name: 'Visita',
            description: 'Mercados locales, de temporada y mercadillos gastronómicos.',
            subcategoryId: 'mercados',
          },
          {
            id: 'mercados-profesional',
            name: 'Profesional',
            description: 'Mercados municipales y establecimientos de alimentación profesional.',
            subcategoryId: 'mercados',
          },
        ],
      },
    ],
  },

  // ── NATURALEZA ────────────────────────────────────────────────────────────
  {
    id: 'naturaleza',
    name: 'Naturaleza',
    icon: '🌿',
    color: '#10b981',        // esmeralda
    subcategories: [
      {
        id: 'senderismo',
        name: 'Senderismo',
        icon: '🥾',
        mainCategoryId: 'naturaleza',
        locationTypes: [
          {
            id: 'senderismo-quedadas',
            name: 'Quedadas',
            description: 'Rutas y excursiones organizadas por grupos de senderismo.',
            subcategoryId: 'senderismo',
          },
          {
            id: 'senderismo-profesional',
            name: 'Profesional',
            description: 'Empresas de guías de montaña, tiendas de material y clubs de senderismo.',
            subcategoryId: 'senderismo',
          },
        ],
      },
      {
        id: 'playas',
        name: 'Playas & Ríos',
        icon: '🏖️',
        mainCategoryId: 'naturaleza',
        locationTypes: [
          {
            id: 'playas-quedadas',
            name: 'Quedadas',
            description: 'Quedadas en playas, ríos y zonas de baño organizadas por usuarios.',
            subcategoryId: 'playas',
          },
          {
            id: 'playas-profesional',
            name: 'Profesional',
            description: 'Escuelas de surf, alquiler de embarcaciones y servicios náuticos.',
            subcategoryId: 'playas',
          },
        ],
      },
      {
        id: 'parques',
        name: 'Parques & Jardines',
        icon: '🌳',
        mainCategoryId: 'naturaleza',
        locationTypes: [
          {
            id: 'parques-quedadas',
            name: 'Quedadas',
            description: 'Pícnics, juegos al aire libre y quedadas en parques.',
            subcategoryId: 'parques',
          },
          {
            id: 'parques-visita',
            name: 'Visita',
            description: 'Parques naturales, jardines botánicos y reservas naturales.',
            subcategoryId: 'parques',
          },
        ],
      },
    ],
  },

  // ── TECNOLOGÍA ────────────────────────────────────────────────────────────
  {
    id: 'tecnologia',
    name: 'Tecnología',
    icon: '💻',
    color: '#06b6d4',        // cian
    subcategories: [
      {
        id: 'gaming',
        name: 'Gaming',
        icon: '🎮',
        mainCategoryId: 'tecnologia',
        locationTypes: [
          {
            id: 'gaming-quedadas',
            name: 'Quedadas',
            description: 'Torneos, LAN parties y quedadas de gaming entre usuarios.',
            subcategoryId: 'gaming',
          },
          {
            id: 'gaming-profesional',
            name: 'Profesional',
            description: 'Tiendas, arcades y centros de gaming profesionales.',
            subcategoryId: 'gaming',
          },
        ],
      },
      {
        id: 'maker',
        name: 'Maker & DIY',
        icon: '🔧',
        mainCategoryId: 'tecnologia',
        locationTypes: [
          {
            id: 'maker-quedadas',
            name: 'Quedadas',
            description: 'Talleres, hackathons y meetups de makers y DIY.',
            subcategoryId: 'maker',
          },
          {
            id: 'maker-profesional',
            name: 'Profesional',
            description: 'Fab Labs, makerspaces y tiendas de electrónica profesionales.',
            subcategoryId: 'maker',
          },
        ],
      },
    ],
  },

  // ── EDUCACIÓN ─────────────────────────────────────────────────────────────
  {
    id: 'educacion',
    name: 'Educación',
    icon: '📚',
    color: '#f97316',        // naranja
    subcategories: [
      {
        id: 'idiomas',
        name: 'Idiomas',
        icon: '🌍',
        mainCategoryId: 'educacion',
        locationTypes: [
          {
            id: 'idiomas-quedadas',
            name: 'Quedadas',
            description: 'Intercambios de idiomas y quedadas de conversation entre usuarios.',
            subcategoryId: 'idiomas',
          },
          {
            id: 'idiomas-profesional',
            name: 'Profesional',
            description: 'Academias de idiomas y centros de formación lingüística.',
            subcategoryId: 'idiomas',
          },
        ],
      },
      {
        id: 'formacion',
        name: 'Formación & Talleres',
        icon: '🎓',
        mainCategoryId: 'educacion',
        locationTypes: [
          {
            id: 'formacion-quedadas',
            name: 'Quedadas',
            description: 'Talleres gratuitos y grupos de estudio organizados por usuarios.',
            subcategoryId: 'formacion',
          },
          {
            id: 'formacion-profesional',
            name: 'Profesional',
            description: 'Academias, coworkings y centros de formación profesional.',
            subcategoryId: 'formacion',
          },
        ],
      },
    ],
  },
];
*/