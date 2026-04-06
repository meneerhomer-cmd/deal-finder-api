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
        if (Retailer.count() > 0) {
            LOG.info("Retailers already initialized, skipping...");
            return;
        }

        LOG.info("Initializing retailers...");

        // Belgian supermarkets and drugstores
        Retailer.create("Lidl", "lidl", 
            "https://www.myshopi.com/nl/lidl/folder-aanbiedingen").persist();
        
        Retailer.create("Kruidvat", "kruidvat", 
            "https://www.myshopi.com/nl/kruidvat/folder-aanbiedingen").persist();
        
        Retailer.create("Carrefour", "carrefour", 
            "https://www.myshopi.com/nl/carrefour/folder-aanbiedingen").persist();
        
        Retailer.create("Delhaize", "delhaize", 
            "https://www.myshopi.com/nl/delhaize/folder-aanbiedingen").persist();
        
        Retailer.create("ALDI", "aldi", 
            "https://www.myshopi.com/nl/aldi/folder-aanbiedingen").persist();
        
        Retailer.create("Colruyt", "colruyt", 
            "https://www.myshopi.com/nl/colruyt/folder-aanbiedingen").persist();

        LOG.info("Initialized " + Retailer.count() + " retailers");
    }

    @Transactional
    public void initializeCategories() {
        if (Category.count() > 0) {
            LOG.info("Categories already initialized, skipping...");
            return;
        }

        LOG.info("Initializing categories...");

        // Slugs use Dutch names to match the frontend app categories
        Category.create("vlees", "Meat", "Vlees", "Viande",
            "vlees,kip,kipfilet,kippenbout,rund,rundvlees,varken,varkensvlees,gehakt,worst,ham,bacon,steak,kotelet,poulet,boeuf,porc,viande,chicken,beef,pork,meat,sausage"
        ).persist();

        Category.create("charcuterie", "Charcuterie", "Charcuterie", "Charcuterie",
            "charcuterie,salami,hesp,prosciutto,coppa,bresaola,pâté,paté,filet d'anvers"
        ).persist();

        Category.create("vis", "Fish & Seafood", "Vis & Zeevruchten", "Poisson & Fruits de mer",
            "vis,zalm,kabeljauw,garnaal,garnalen,tonijn,mosselen,scampi,haring,makreel,forel,poisson,saumon,crevettes,moules,thon,fish,salmon,shrimp,tuna,seafood"
        ).persist();

        Category.create("zuivel", "Dairy", "Zuivel", "Produits laitiers",
            "melk,yoghurt,boter,room,kwark,cottage,mozzarella,cheddar,gouda,emmental,lait,yaourt,beurre,crème,milk,yogurt,butter,cream,dairy"
        ).persist();

        Category.create("kaas", "Cheese", "Kaas", "Fromage",
            "kaas,fromage,cheese,brie,camembert,gruyère,parmesan,raclette"
        ).persist();

        Category.create("dranken", "Drinks", "Dranken", "Boissons",
            "water,cola,fanta,sprite,sap,sinaasappel,limonade,frisdrank,koffie,thee,eau,jus,café,thé,juice,coffee,tea,soda,drink,energy"
        ).persist();

        Category.create("bier", "Beer", "Bier", "Bière",
            "bier,pils,tripel,dubbel,abdij,trappist,geuze,kriek,ipa,lager,bière,beer"
        ).persist();

        Category.create("wijn", "Wine", "Wijn", "Vin",
            "wijn,rosé,champagne,prosecco,cava,vin,wine,blanc,rouge"
        ).persist();

        Category.create("snoep", "Snacks & Sweets", "Snoep", "Bonbons",
            "chocolade,koekjes,snoep,noten,crackers,chocolat,biscuits,bonbons,noix,chocolate,cookies,candy,nuts"
        ).persist();

        Category.create("chips", "Chips & Crisps", "Chips", "Chips",
            "chips,crisps,popcorn,tortilla,nachos,pretzels"
        ).persist();

        Category.create("ontbijt", "Breakfast", "Ontbijt", "Petit-déjeuner",
            "cornflakes,muesli,ontbijt,cereals,granola,haver,havermout,confituur,honing,nutella,choco,céréales,breakfast"
        ).persist();

        Category.create("brood", "Bread & Bakery", "Brood", "Pain",
            "brood,croissant,pistolet,taart,cake,koek,gebak,pain,gâteau,pâtisserie,bread,pastry"
        ).persist();

        Category.create("diepvries", "Frozen", "Diepvries", "Surgelés",
            "diepvries,pizza,frieten,ijsje,surgelé,glace,frites,frozen,ice cream,fries"
        ).persist();

        Category.create("conserven", "Canned & Preserved", "Conserven", "Conserves",
            "conserven,blik,ingeblikt,tomaat,bonen,soep,conserve,boîte,canned"
        ).persist();

        Category.create("pasta", "Pasta & Rice", "Pasta", "Pâtes",
            "pasta,spaghetti,penne,rijst,noodles,couscous,pâtes,riz,rice"
        ).persist();

        Category.create("sauzen", "Sauces", "Sauzen", "Sauces",
            "saus,ketchup,mayonaise,mayo,mosterd,vinaigrette,dressing,sauce,moutarde"
        ).persist();

        Category.create("groenten", "Vegetables", "Groenten", "Légumes",
            "tomaat,sla,komkommer,wortel,aardappel,paprika,groente,groenten,légumes,tomate,salade,carotte,potato,onion,pepper,vegetables,courgette,aubergine,champignon"
        ).persist();

        Category.create("fruit", "Fruit", "Fruit", "Fruits",
            "appel,banaan,sinaasappel,peer,druiven,aardbei,framboos,mango,ananas,kiwi,fruit,pomme,orange,fruits"
        ).persist();

        Category.create("huishouden", "Household", "Huishouden", "Ménage",
            "wasmiddel,afwasmiddel,toiletpapier,keukenpapier,vaatwas,doekjes,sponzen,lessive,nettoyage,papier,detergent,cleaning,paper,dishwasher,laundry"
        ).persist();

        Category.create("schoonmaak", "Cleaning", "Schoonmaak", "Nettoyage",
            "schoonmaak,reiniger,bleek,allesreiniger,glasreiniger,ontkalker,cleaning,cleaner"
        ).persist();

        Category.create("verzorging", "Personal Care", "Verzorging", "Soins personnels",
            "shampoo,tandpasta,deo,deodorant,douche,zeep,scheermesjes,bodylotion,gezicht,huid,dentifrice,savon,rasoir,toothpaste,soap,shower,razor,skincare"
        ).persist();

        Category.create("baby", "Baby", "Baby", "Bébé",
            "luiers,pampers,babydoekjes,babyvoeding,fles,speen,baby,couches,biberon,diaper,nappy,formula,wipes"
        ).persist();

        Category.create("huisdier", "Pets", "Huisdier", "Animaux",
            "hond,kat,hondenvoer,kattenvoer,dierenvoer,kattenbak,chien,chat,nourriture,dog,cat,pet food"
        ).persist();

        Category.create("kruiden", "Herbs & Spices", "Kruiden", "Épices",
            "kruiden,specerijen,peper,zout,kaneel,paprika,curry,herbs,spices,épices"
        ).persist();

        Category.create("andere", "Other", "Andere", "Autres",
            ""
        ).persist();

        LOG.info("Initialized " + Category.count() + " categories");
    }

    @Transactional
    public void initializeAll() {
        initializeRetailers();
        initializeCategories();
    }
}
