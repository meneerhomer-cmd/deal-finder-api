package be.dealfinder.service;

import be.dealfinder.entity.Category;
import be.dealfinder.entity.Retailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DataInitService {

    private static final Logger LOG = Logger.getLogger(DataInitService.class);

    @Transactional
    public void initializeRetailers() {
        LOG.info("Initializing retailers...");

        upsertRetailer("Lidl", "lidl");
        upsertRetailer("Kruidvat", "kruidvat");
        upsertRetailer("Carrefour", "carrefour");
        upsertRetailer("Delhaize", "delhaize");
        upsertRetailer("ALDI", "aldi");
        upsertRetailer("Colruyt", "colruyt");
        upsertRetailer("Albert Heijn", "albert-heijn");
        upsertRetailer("Jumbo", "jumbo");
        upsertRetailer("SPAR", "spar");
        upsertRetailer("Carrefour Market", "carrefour-market");
        upsertRetailer("Intermarché", "intermarche");
        upsertRetailer("Renmans", "renmans");
        upsertRetailer("Bol.com", "bol-com");
        upsertRetailer("MediaMarkt", "mediamarkt");
        upsertRetailer("IKEA", "ikea");
        upsertRetailer("GAMMA", "gamma");
        upsertRetailer("Brico & BricoPlanit", "brico-bricoplanit");

        LOG.info("Retailers: " + Retailer.count() + " total");
    }

    private void upsertCategory(String slug, String nameEn, String nameNl, String nameFr, String keywords) {
        Category existing = Category.findBySlug(slug);
        if (existing == null) {
            Category.create(slug, nameEn, nameNl, nameFr, keywords).persist();
        } else if (keywords != null && !keywords.equals(existing.keywords)) {
            existing.keywords = keywords;
            existing.persist();
        }
    }

    private void upsertRetailer(String name, String slug) {
        if (Retailer.findBySlug(slug) == null) {
            Retailer.create(name, slug,
                "https://www.myshopi.com/nl/" + slug + "/folder-aanbiedingen").persist();
        }
    }

    @Transactional
    public void initializeCategories() {
        LOG.info("Initializing categories...");

        // Slugs use Dutch names to match the frontend app categories
        upsertCategory("vlees", "Meat", "Vlees", "Viande",
            "vlees,kip,kipfilet,kippenbout,rund,rundvlees,varken,varkensvlees,gehakt,worst,ham,bacon,steak,kotelet,poulet,boeuf,porc,viande,chicken,beef,pork,meat,sausage"
        );

        upsertCategory("charcuterie", "Charcuterie", "Charcuterie", "Charcuterie",
            "charcuterie,salami,hesp,prosciutto,coppa,bresaola,pâté,paté,filet d'anvers"
        );

        upsertCategory("vis", "Fish & Seafood", "Vis & Zeevruchten", "Poisson & Fruits de mer",
            "vis,zalm,kabeljauw,garnaal,garnalen,tonijn,mosselen,scampi,haring,makreel,forel,poisson,saumon,crevettes,moules,thon,fish,salmon,shrimp,tuna,seafood"
        );

        upsertCategory("zuivel", "Dairy", "Zuivel", "Produits laitiers",
            "melk,yoghurt,boter,room,kwark,cottage,mozzarella,cheddar,gouda,emmental,lait,yaourt,beurre,crème,milk,yogurt,butter,cream,dairy"
        );

        upsertCategory("kaas", "Cheese", "Kaas", "Fromage",
            "kaas,fromage,cheese,brie,camembert,gruyère,parmesan,raclette"
        );

        upsertCategory("dranken", "Drinks", "Dranken", "Boissons",
            "water,cola,fanta,sprite,sap,sinaasappel,limonade,frisdrank,koffie,thee,eau,jus,café,thé,juice,coffee,tea,soda,drink,energy,blikjes,oasis,ice tea,fuze,nescafé,senseo,dolce gusto,tassimo,capsules"
        );

        upsertCategory("bier", "Beer", "Bier", "Bière",
            "bier,pils,tripel,dubbel,abdij,trappist,geuze,kriek,ipa,lager,bière,beer"
        );

        upsertCategory("wijn", "Wine", "Wijn", "Vin",
            "wijn,rosé,champagne,prosecco,cava,vin,wine,blanc,rouge"
        );

        upsertCategory("snoep", "Snacks & Sweets", "Snoep", "Bonbons",
            "chocolade,koekjes,snoep,noten,crackers,chocolat,biscuits,bonbons,noix,chocolate,cookies,candy,nuts"
        );

        upsertCategory("chips", "Chips & Crisps", "Chips", "Chips",
            "chips,crisps,popcorn,tortilla,nachos,pretzels"
        );

        upsertCategory("ontbijt", "Breakfast", "Ontbijt", "Petit-déjeuner",
            "cornflakes,muesli,ontbijt,cereals,granola,haver,havermout,confituur,honing,nutella,choco,céréales,breakfast"
        );

        upsertCategory("brood", "Bread & Bakery", "Brood", "Pain",
            "brood,croissant,pistolet,taart,cake,koek,gebak,pain,gâteau,pâtisserie,bread,pastry"
        );

        upsertCategory("diepvries", "Frozen", "Diepvries", "Surgelés",
            "diepvries,pizza,frieten,ijsje,surgelé,glace,frites,frozen,ice cream,fries"
        );

        upsertCategory("conserven", "Canned & Preserved", "Conserven", "Conserves",
            "conserven,blik,ingeblikt,tomaat,bonen,soep,conserve,boîte,canned"
        );

        upsertCategory("pasta", "Pasta & Rice", "Pasta", "Pâtes",
            "pasta,spaghetti,penne,rijst,noodles,couscous,pâtes,riz,rice"
        );

        upsertCategory("sauzen", "Sauces", "Sauzen", "Sauces",
            "saus,ketchup,mayonaise,mayo,mosterd,vinaigrette,dressing,sauce,moutarde"
        );

        upsertCategory("groenten", "Vegetables", "Groenten", "Légumes",
            "tomaat,sla,komkommer,wortel,aardappel,paprika,groente,groenten,légumes,tomate,salade,carotte,potato,onion,pepper,vegetables,courgette,aubergine,champignon"
        );

        upsertCategory("fruit", "Fruit", "Fruit", "Fruits",
            "appel,banaan,sinaasappel,peer,druiven,aardbei,framboos,mango,ananas,kiwi,fruit,pomme,orange,fruits"
        );

        upsertCategory("huishouden", "Household", "Huishouden", "Ménage",
            "wasmiddel,afwasmiddel,toiletpapier,keukenpapier,vaatwas,doekjes,sponzen,lessive,nettoyage,papier,detergent,cleaning,paper,dishwasher,laundry,wasverzachter,geurbooster,wasgel,waspoeder,pods,capsules,tabs,opbergdozen,opberg,strijken,stoomcentrale,droger"
        );

        upsertCategory("schoonmaak", "Cleaning", "Schoonmaak", "Nettoyage",
            "schoonmaak,reiniger,bleek,allesreiniger,glasreiniger,ontkalker,cleaning,cleaner,toiletblok,toiletreiniger,wc-blok,wcblok,ontstopper,ontvetter,desinfectie,doekjes"
        );

        upsertCategory("verzorging", "Personal Care", "Verzorging", "Soins personnels",
            "shampoo,tandpasta,deo,deodorant,douche,zeep,scheermesjes,bodylotion,gezicht,huid,dentifrice,savon,rasoir,toothpaste,soap,shower,razor,skincare"
        );

        upsertCategory("baby", "Baby", "Baby", "Bébé",
            "luiers,pampers,babydoekjes,babyvoeding,fles,speen,baby,couches,biberon,diaper,nappy,formula,wipes"
        );

        upsertCategory("huisdier", "Pets", "Huisdier", "Animaux",
            "hond,kat,hondenvoer,kattenvoer,dierenvoer,kattenbak,chien,chat,nourriture,dog,cat,pet food"
        );

        upsertCategory("kruiden", "Herbs & Spices", "Kruiden", "Épices",
            "kruiden,specerijen,peper,zout,kaneel,paprika,curry,herbs,spices,épices"
        );

        upsertCategory("kleding", "Clothing", "Kleding", "Vêtements",
            "t-shirt,tshirt,boxer,boxers,slip,sokken,jas,broek,jurk,trui,hemd,pyjama,lingerie,ondergoed,panty,kousen,schoenen,vêtements,chaussures,clothing,shoes,pants,shirt,dress,jeans,sweater,jacket"
        );

        upsertCategory("elektronica", "Electronics", "Elektronica", "Électronique",
            "televisie,smartphone,tablet,laptop,computer,printer,koptelefoon,headphone,speaker,kabel,usb,hdmi,bluetooth,wifi,camera,drone,monitor,toetsenbord,muis,gaming,console,écran,ordinateur,electronics"
        );

        upsertCategory("keuken", "Kitchen", "Keuken & Apparaten", "Cuisine",
            "koffiemachine,koffiezetapparaat,blender,mixer,broodrooster,waterkoker,magnetron,oven,pan,kookpot,keukenmachine,airfryer,cuisine,appareil,kitchen,appliance,stofzuiger,stoomcentrale"
        );

        upsertCategory("tuin", "Garden & Outdoor", "Tuin & Buiten", "Jardin",
            "tuin,plant,bloem,zaden,gazon,barbecue,bbq,tuinmeubilair,parasol,zwembad,tent,koepeltent,camping,outdoor,jardin,garden,gras,slang,snoeischaar"
        );

        upsertCategory("vrije-tijd", "Leisure", "Vrije Tijd", "Loisirs",
            "pretpark,walibi,bellewaerde,bobbejaanland,plopsaland,bioscoop,cinema,tickets,speelgoed,puzzel,spel,boek,magazine,sport,fiets,loisirs,leisure,entertainment"
        );

        upsertCategory("doe-het-zelf", "DIY", "Doe-het-zelf", "Bricolage",
            "boor,schroef,moer,verf,kwast,hamer,tang,zaag,schroevendraaier,accuboormachine,slagschroevendraaier,garagekrik,gereedschap,lamp,werklamp,bricolage,tools,drill,paint"
        );

        upsertCategory("auto", "Automotive", "Auto", "Auto",
            "auto,motorolie,bandenspanning,zekering,ruitenwisser,autoshampoo,dashcam,parkeer,voiture,automobile,car,battery,automotive"
        );

        upsertCategory("andere", "Other", "Andere", "Autres",
            ""
        );

        LOG.info("Categories: " + Category.count() + " total");
    }

    @Transactional
    public void initializeAll() {
        initializeRetailers();
        initializeCategories();
    }
}
