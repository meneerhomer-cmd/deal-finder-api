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

        // Meat
        Category.create("meat", "Meat", "Vlees", "Viande",
            "vlees,kip,kipfilet,kippenbout,rund,rundvlees,varken,varkensvlees,gehakt,worst,ham,bacon,steak,kotelet,poulet,boeuf,porc,viande,chicken,beef,pork,meat,sausage"
        ).persist();

        // Fish & Seafood
        Category.create("fish", "Fish & Seafood", "Vis & Zeevruchten", "Poisson & Fruits de mer",
            "vis,zalm,kabeljauw,garnaal,garnalen,tonijn,mosselen,scampi,haring,makreel,forel,poisson,saumon,crevettes,moules,thon,fish,salmon,shrimp,tuna,seafood"
        ).persist();

        // Dairy
        Category.create("dairy", "Dairy", "Zuivel", "Produits laitiers",
            "melk,kaas,yoghurt,boter,room,kwark,cottage,mozzarella,cheddar,gouda,emmental,lait,fromage,yaourt,beurre,crème,milk,cheese,yogurt,butter,cream,dairy"
        ).persist();

        // Drinks
        Category.create("drinks", "Drinks", "Dranken", "Boissons",
            "water,cola,fanta,sprite,bier,wijn,sap,sinaasappel,appel,limonade,frisdrank,koffie,thee,eau,bière,vin,jus,café,thé,beer,wine,juice,coffee,tea,soda,drink"
        ).persist();

        // Household
        Category.create("household", "Household", "Huishouden", "Ménage",
            "wasmiddel,afwasmiddel,schoonmaak,toiletpapier,keukenpapier,vaatwas,was,doekjes,sponzen,lessive,nettoyage,papier,detergent,cleaning,paper,dishwasher,laundry"
        ).persist();

        // Personal Care
        Category.create("personal-care", "Personal Care", "Verzorging", "Soins personnels",
            "shampoo,tandpasta,deo,deodorant,douche,zeep,scheermesjes,bodylotion,gezicht,huid,dentifrice,savon,douche,rasoir,toothpaste,soap,shower,razor,skincare"
        ).persist();

        // Baby
        Category.create("baby", "Baby", "Baby", "Bébé",
            "luiers,pampers,babydoekjes,babyvoeding,fles,speen,baby,couches,biberon,diaper,nappy,formula,wipes"
        ).persist();

        // Snacks & Sweets
        Category.create("snacks", "Snacks & Sweets", "Snacks & Snoep", "Snacks & Bonbons",
            "chips,chocolade,koekjes,snoep,noten,crackers,chocolat,biscuits,bonbons,noix,chocolate,cookies,candy,nuts,crisps"
        ).persist();

        // Frozen
        Category.create("frozen", "Frozen", "Diepvries", "Surgelés",
            "diepvries,ijs,pizza,frieten,groenten,vis,ijsje,surgelé,glace,frites,légumes,frozen,ice cream,fries,vegetables"
        ).persist();

        // Bread & Bakery
        Category.create("bakery", "Bread & Bakery", "Brood & Gebak", "Pain & Pâtisserie",
            "brood,croissant,pistolet,taart,cake,koek,gebak,pain,gâteau,pâtisserie,bread,pastry,cake"
        ).persist();

        // Fruits & Vegetables
        Category.create("fruits-vegetables", "Fruits & Vegetables", "Groenten & Fruit", "Fruits & Légumes",
            "appel,banaan,sinaasappel,tomaat,sla,komkommer,wortel,aardappel,ui,paprika,fruit,groente,légumes,pomme,tomate,salade,carotte,potato,onion,pepper,vegetables"
        ).persist();

        // Pets
        Category.create("pets", "Pets", "Huisdieren", "Animaux",
            "hond,kat,hondenvoer,kattenvoer,dierenvoer,kattenbak,chien,chat,nourriture,dog,cat,pet food"
        ).persist();

        LOG.info("Initialized " + Category.count() + " categories");
    }

    @Transactional
    public void initializeAll() {
        initializeRetailers();
        initializeCategories();
    }
}
