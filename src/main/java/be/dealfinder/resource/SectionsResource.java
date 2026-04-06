package be.dealfinder.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/api/v1/sections")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Sections", description = "Retailer sections/categories")
public class SectionsResource {

    private static final List<Map<String, Object>> SECTIONS = List.of(
        section("supermarkt", "Supermarkt", "Dagelijkse boodschappen",
            List.of("lidl", "carrefour", "delhaize", "aldi", "colruyt", "albert-heijn", "jumbo", "spar", "carrefour-market", "intermarche", "renmans")),
        section("drogisterij", "Drogisterij", "Verzorging & gezondheid",
            List.of("kruidvat")),
        section("elektronica", "Elektronica", "TV, smartphone, laptop",
            List.of("mediamarkt", "bol-com")),
        section("wonen", "Wonen & Interieur", "Meubels, decoratie, keuken",
            List.of("ikea")),
        section("doe-het-zelf", "Doe-het-zelf", "Bouw, tuin, gereedschap",
            List.of("gamma", "brico-bricoplanit"))
    );

    @GET
    @Operation(summary = "Get deal sections with grouped retailers",
               description = "Returns retailer groups by sector: supermarkt, drogisterij, elektronica, wonen, doe-het-zelf")
    public List<Map<String, Object>> getSections() {
        return SECTIONS;
    }

    private static Map<String, Object> section(String slug, String name, String description, List<String> retailerSlugs) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("slug", slug);
        s.put("name", name);
        s.put("description", description);
        s.put("retailers", retailerSlugs);
        return s;
    }
}
