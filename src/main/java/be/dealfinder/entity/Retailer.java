package be.dealfinder.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "retailers")
public class Retailer extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String slug;

    public String logoUrl;

    @Column(nullable = false)
    public String scrapingUrl;

    @Column(nullable = false)
    public boolean active = true;

    @OneToMany(mappedBy = "retailer", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Deal> deals;

    // === Panache Finders ===

    public static Retailer findBySlug(String slug) {
        return find("slug", slug).firstResult();
    }

    public static List<Retailer> findAllActive() {
        return find("active", true).list();
    }

    // === Factory Method ===

    public static Retailer create(String name, String slug, String scrapingUrl) {
        Retailer retailer = new Retailer();
        retailer.name = name;
        retailer.slug = slug;
        retailer.scrapingUrl = scrapingUrl;
        retailer.active = true;
        return retailer;
    }
}
