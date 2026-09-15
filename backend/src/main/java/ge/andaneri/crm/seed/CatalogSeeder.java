package ge.andaneri.crm.seed;

import static ge.andaneri.crm.domain.DrinkType.COCKTAILS;
import static ge.andaneri.crm.domain.DrinkType.COFFEE;
import static ge.andaneri.crm.domain.DrinkType.DESSERTS;
import static ge.andaneri.crm.domain.DrinkType.ENERGY_DRINKS;
import static ge.andaneri.crm.domain.DrinkType.HOT_TEA;
import static ge.andaneri.crm.domain.DrinkType.ICED_TEA;
import static ge.andaneri.crm.domain.DrinkType.ICE_CREAM;
import static ge.andaneri.crm.domain.DrinkType.LEMONADES;
import static ge.andaneri.crm.domain.DrinkType.MILKSHAKES;
import static ge.andaneri.crm.domain.DrinkType.MOCKTAILS;
import static ge.andaneri.crm.domain.DrinkType.SMOOTHIES;
import static ge.andaneri.crm.domain.DrinkType.SPRITZES;
import static ge.andaneri.crm.domain.DrinkType.WATER_PLUS;
import static ge.andaneri.crm.domain.DrinkType.WINE;

import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.Brand;
import ge.andaneri.crm.domain.BrandRepository;
import ge.andaneri.crm.domain.BusinessType;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.domain.DrinkType;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.FlavorRepository;
import ge.andaneri.crm.domain.Product;
import ge.andaneri.crm.domain.ProductCategory;
import ge.andaneri.crm.domain.ProductCategoryRepository;
import ge.andaneri.crm.domain.ProductRepository;
import ge.andaneri.crm.domain.ProductStatus;
import ge.andaneri.crm.domain.Role;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fills an empty database with what every install needs: the first admin, business types,
 * product categories, the brands a salesperson meets in Tbilisi, and the Andaneri price list
 * (September 2026 sheet, Georgian and English). Each part runs only while its table is empty,
 * so edits made in the app are never overwritten on restart.
 */
@Component
@Order(1)
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final UserRepository users;
    private final BusinessTypeRepository types;
    private final ProductCategoryRepository categories;
    private final BrandRepository brands;
    private final FlavorRepository flavors;
    private final ProductRepository products;
    private final PasswordEncoder passwordEncoder;
    private final CrmProperties properties;

    public CatalogSeeder(UserRepository users, BusinessTypeRepository types, ProductCategoryRepository categories,
            BrandRepository brands, FlavorRepository flavors, ProductRepository products,
            PasswordEncoder passwordEncoder, CrmProperties properties) {
        this.users = users;
        this.types = types;
        this.categories = categories;
        this.brands = brands;
        this.flavors = flavors;
        this.products = products;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedRoot();
        seedAdmin();
        seedTestAccount();
        seedBusinessTypes();
        seedCategories();
        seedBrands();
        seedPriceList();
    }

    /**
     * The root account is whatever ROOT_USERNAME and ROOT_PASSWORD say, on every start: created if missing,
     * its password reset (and old sessions ended) when the variable changed. Anyone else holding the ROOT
     * role, for example after the username was changed, becomes an ordinary admin.
     */
    private void seedRoot() {
        String username = properties.rootUsername() == null ? "" : properties.rootUsername().trim();
        String password = properties.rootPassword();
        if (username.isEmpty() || password == null || password.isBlank()) {
            log.warn("ROOT_USERNAME / ROOT_PASSWORD are not set: there is no root account and no security centre.");
            return;
        }
        User root = users.findByUsernameIgnoreCase(username).orElseGet(() -> {
            User created = new User();
            created.setUsername(username);
            created.setFullName("Root");
            created.setPasswordHash(passwordEncoder.encode(password));
            created.setCreatedAt(Instant.now());
            return created;
        });
        if (root.getId() != null && !passwordEncoder.matches(password, root.getPasswordHash())) {
            root.setPasswordHash(passwordEncoder.encode(password));
            root.setTokenVersion(root.getTokenVersion() + 1);
        }
        root.setRole(Role.ROOT);
        root.setActive(true);
        User saved = users.save(root);
        for (User other : users.findAll()) {
            if (other.isRoot() && !other.getId().equals(saved.getId())) {
                other.setRole(Role.ADMIN);
            }
        }
    }

    /** The first admin, when nobody but the root account exists yet (root alone does not count as a team). */
    private void seedAdmin() {
        if (users.findAll().stream().anyMatch(u -> !u.isRoot())) {
            return;
        }
        String password = properties.adminPassword();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("No users yet: set ADMIN_PASSWORD so the first admin can be created");
        }
        User admin = new User();
        admin.setUsername(properties.adminUsername());
        admin.setFullName("ადმინისტრატორი");
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setCreatedAt(Instant.now());
        users.save(admin);
        log.warn("Created the first admin account '{}'. Sign in and change its password.", admin.getUsername());
    }

    /**
     * test / test, an admin for trying the app out. Its password is far below the real password rule on
     * purpose, so it only exists while crm.test-account is on; turning that off deactivates it.
     */
    private void seedTestAccount() {
        User existing = users.findByUsernameIgnoreCase("test").orElse(null);
        if (!properties.testAccount()) {
            if (existing != null && existing.isActive()) {
                existing.setActive(false);
                log.warn("crm.test-account is off: the 'test' login has been deactivated.");
            }
            return;
        }
        if (existing == null) {
            User test = new User();
            test.setUsername("test");
            test.setFullName("Test");
            test.setRole(Role.ADMIN);
            test.setPasswordHash(passwordEncoder.encode("test"));
            test.setCreatedAt(Instant.now());
            users.save(test);
        } else if (!existing.isActive()) {
            existing.setActive(true);
        }
        log.warn("The test login (test / test) is enabled. Set TEST_ACCOUNT=false before real use.");
    }

    private void seedBusinessTypes() {
        if (types.count() > 0) {
            return;
        }
        String[][] rows = {
                {"რესტორანი", "Restaurant"}, {"ბარი", "Bar"}, {"კოქტეილ ბარი", "Cocktail bar"}, {"კაფე", "Cafe"},
                {"ყავის შოპი", "Coffee shop"}, {"სასტუმრო", "Hotel"}, {"საცხობი / კონდიტერია", "Bakery / pastry"},
                {"ღამის კლუბი", "Nightclub"}, {"პაბი", "Pub"}, {"ლაუნჯი", "Lounge"}, {"ვაინ ბარი", "Wine bar"},
                {"ნაყინის სალონი", "Ice cream shop"}, {"კეითერინგი", "Catering"}, {"სხვა", "Other"}};
        for (int i = 0; i < rows.length; i++) {
            types.save(new BusinessType(rows[i][0], rows[i][1], i));
        }
    }

    private void seedCategories() {
        if (categories.count() > 0) {
            return;
        }
        String[][] rows = {
                {"სიროფი", "Syrup"}, {"ფრუტ-პიურე", "Fruit puree"}, {"ყავის ნაღები", "Coffee creamer"},
                {"სოუსი / ტოპინგი", "Sauce / topping"}, {"ყავა", "Coffee"}, {"ლიქიორი", "Liqueur"}, {"სხვა", "Other"}};
        for (int i = 0; i < rows.length; i++) {
            categories.save(new ProductCategory(rows[i][0], rows[i][1], i));
        }
    }

    private void seedBrands() {
        if (brands.count() > 0) {
            return;
        }
        brands.save(new Brand("Andaneri", true));
        for (String name : List.of("Monin", "1883 Maison Routin", "Black Sea", "Döhler", "Torani",
                "DaVinci Gourmet", "Barinoff", "Boiron", "Ponthier")) {
            brands.save(new Brand(name, false));
        }
    }

    // ------------------------------------------------------------------ the price list

    private record Section(String ka, String en, List<DrinkType> uses) {
    }

    private static final Section COFFEE_DESSERT = new Section("ყავა და დესერტები", "Coffee & Dessert", List.of(COFFEE, DESSERTS, MILKSHAKES, ICE_CREAM));
    private static final Section BERRIES = new Section("კენკროვნები", "Berries", List.of(COCKTAILS, MOCKTAILS, LEMONADES, SMOOTHIES, ICE_CREAM));
    private static final Section FRUITS = new Section("ხილი", "Fruits", List.of(COCKTAILS, MOCKTAILS, LEMONADES, SMOOTHIES));
    private static final Section CITRUS = new Section("ციტრუსი და ტროპიკული ხილი", "Citrus and Tropic Fruits", List.of(COCKTAILS, MOCKTAILS, LEMONADES, SMOOTHIES));
    private static final Section BOTANICALS = new Section("მცენარეები და ყვავილები", "Botanicals", List.of(COCKTAILS, MOCKTAILS, LEMONADES, ICED_TEA));
    private static final Section SUGAR_FREE = new Section("უშაქრო", "Sugar Free", List.of(COFFEE, LEMONADES, ICED_TEA));
    private static final Section NO_ADDED_SUGAR = new Section("შაქრის დამატების გარეშე", "No Added Sugar", List.of(LEMONADES, SMOOTHIES, ICED_TEA));
    private static final Section SIGNATURE = new Section("საავტორო სიროფები", "Signature Syrups", List.of(COCKTAILS, MOCKTAILS, LEMONADES));
    private static final Section FUNCTIONAL = new Section("ფუნქციური სიროფი", "Functional Syrups", List.of(ENERGY_DRINKS));

    /** English flavor name to Georgian. Products and usages both point at these rows. */
    private static final String[][] FLAVORS = {
            {"Vanilla", "ვანილი"}, {"Caramel", "კარამელი"}, {"Salted Caramel", "მარილიანი კარამელი"},
            {"Chocolate", "შოკოლადი"}, {"Cream Soda", "ნაღები"}, {"Hazelnut", "თხილი"}, {"Pistachio", "ფისტა"},
            {"Almond", "ნუში"}, {"Coffee", "ყავა"}, {"Wild Berries", "ტყის კენკრა"}, {"Strawberry", "მარწყვი"},
            {"Raspberry", "ჟოლო"}, {"Blueberry", "მოცვი"}, {"Blackberry", "მაყვალი"}, {"Green Apple", "მწვანე ვაშლი"},
            {"Peach", "ატამი"}, {"Pear", "მსხალი"}, {"Pomegranate", "ბროწეული"}, {"Watermelon", "საზამთრო"},
            {"Saperavi", "საფერავი"}, {"Cherry", "ალუბალი"}, {"Lemon", "ლიმონი"}, {"Blue Curacao", "ბლუ კურასაო"},
            {"Orange", "ფორთოხალი"}, {"Grapefruit", "გრეიფრუტი"}, {"Mojito", "მოხიტო"}, {"Mango", "მანგო"},
            {"Passion Fruit", "მარაკუია"}, {"Pineapple", "ანანასი"}, {"Banana", "ბანანი"}, {"Coconut", "ქოქოსი"},
            {"Kiwi", "კივი"}, {"Mint", "პიტნა"}, {"Elderflower", "ანწლი"}, {"Lavender", "ლავანდა"},
            {"Rosemary", "როზმარინი"}, {"Rose", "ვარდი"}, {"Tarragon", "ტარხუნა"}, {"Orgeat", "ორშანდი"},
            {"Dragon Fruit", "დრაგონფრუტი"}, {"Black Tea", "შავი ჩაი"}, {"Green Tea", "მწვანე ჩაი"},
            {"Baobab", "ბაობაბი"}, {"Guava", "გუავა"}, {"Lime", "ლაიმი"}, {"Ginger", "ჯინჯერი"},
            {"Bubble Gum", "ბაბლ-გამი"}, {"Mulled Wine", "გლინტვეინი"}, {"Spices", "სანელებლები"},
            {"Energy", "ენერგეტიკი"},
            // Not on our price list, but asked for again and again in the field notes.
            {"Grenadine", "გრენადინი"}, {"Honey", "თაფლი"}, {"Black Currant", "შავი მოცხარი"},
            {"Tiramisu", "ტირამისუ"}, {"Cucumber", "კიტრი"}, {"Basil", "ბაზილიკი"}, {"Red Orange", "წითელი ფორთოხალი"},
            {"Cinnamon", "დარიჩინი"}};

    private void seedPriceList() {
        if (products.count() > 0) {
            return;
        }
        Map<String, Flavor> flavorByName = new HashMap<>();
        if (flavors.count() == 0) {
            for (String[] row : FLAVORS) {
                flavorByName.put(row[0], flavors.save(new Flavor(row[1], row[0])));
            }
        } else {
            flavors.findAll().forEach(flavor -> flavorByName.put(flavor.getNameEn(), flavor));
        }
        Brand andaneri = brands.findByNameIgnoreCase("Andaneri").orElseGet(() -> brands.save(new Brand("Andaneri", true)));
        ProductCategory syrup = categories.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase("სიროფი", "Syrup")
                .orElseGet(() -> categories.save(new ProductCategory("სიროფი", "Syrup", 0)));

        PriceList list = new PriceList(andaneri, syrup, flavorByName);

        list.add(COFFEE_DESSERT, "ვანილი", "Vanilla", 18, null, "Vanilla");
        list.add(COFFEE_DESSERT, "კარამელი", "Caramel", 18, null, "Caramel");
        list.add(COFFEE_DESSERT, "მარილიანი კარამელი", "Salted Caramel", 18, null, "Salted Caramel");
        list.add(COFFEE_DESSERT, "შოკოლადი", "Chocolate", 19, null, "Chocolate");
        list.add(COFFEE_DESSERT, "ნაღები", "Cream Soda", 17, null, "Cream Soda");
        list.add(COFFEE_DESSERT, "თხილი", "Hazelnut", 21, null, "Hazelnut");
        list.add(COFFEE_DESSERT, "ფისტა", "Pistachio", 23, null, "Pistachio");
        list.add(COFFEE_DESSERT, "ნუში", "Almond", 18, null, "Almond");
        list.add(COFFEE_DESSERT, "ყავა", "Coffee", 21, null, "Coffee");

        // Sold as grenadine too, which is what bars ask for by name.
        list.add(BERRIES, "ტყის კენკრის გრენადინი", "Wild Berries", 18, 32, "Wild Berries", "Grenadine");
        list.add(BERRIES, "მარწყვი", "Strawberry", 18, 45, "Strawberry");
        list.add(BERRIES, "ჟოლო", "Raspberry", 18, 45, "Raspberry");
        list.add(BERRIES, "მოცვი", "Blueberry", 18, null, "Blueberry");
        list.add(BERRIES, "მაყვალი", "Blackberry", 19, 45, "Blackberry");

        list.add(FRUITS, "მწვანე ვაშლი", "Green Apple", 18, 46, "Green Apple");
        list.add(FRUITS, "ატამი", "Peach", 19, 38, "Peach");
        list.add(FRUITS, "მსხალი", "Pear", 17, null, "Pear");
        list.add(FRUITS, "ბროწეული", "Pomegranate", 18, 33, "Pomegranate");
        list.add(FRUITS, "საზამთრო", "Watermelon", 18, null, "Watermelon");
        list.add(FRUITS, "საფერავი", "Saperavi Red Wine", 17, null, "Saperavi");
        list.add(FRUITS, "ალუბალი", "Cherry", 18, 33, "Cherry");

        list.add(CITRUS, "ლიმონი", "Lemon", 17, null, "Lemon");
        list.add(CITRUS, "ბლუ კურასაო", "Blue Curacao", 19, null, "Blue Curacao");
        list.add(CITRUS, "ფორთოხალი", "Orange", 20, null, "Orange");
        list.add(CITRUS, "გრეიფრუტი", "Grapefruit", 23, 30, "Grapefruit");
        list.add(CITRUS, "მწვანე მოხიტო", "Green Mojito", 20, null, "Mojito", "Mint", "Lime");
        list.add(CITRUS, "მოხიტო", "Mojito", 19, null, "Mojito", "Mint", "Lime");
        list.add(CITRUS, "მანგო", "Mango", 24, null, "Mango");
        list.add(CITRUS, "მარაკუია", "Passion Fruit", 24, null, "Passion Fruit");
        list.add(CITRUS, "ანანასი", "Pineapple", 21, 46, "Pineapple");
        list.add(CITRUS, "ბანანი", "Banana", 19, null, "Banana");
        list.add(CITRUS, "ქოქოსი", "Coconut", 20, null, "Coconut");
        list.soon(CITRUS, "კივი", "Kiwi", 23, "Kiwi");

        list.add(BOTANICALS, "პიტნა", "Mint", 20, null, "Mint");
        list.soon(BOTANICALS, "ანწლი", "Elderflower", 23, "Elderflower");
        list.soon(BOTANICALS, "ლავანდა", "Lavender", 24, "Lavender");
        list.soon(BOTANICALS, "როზმარინი", "Rosemary", 24, "Rosemary");
        list.add(BOTANICALS, "ვარდი", "Rose", 21, null, "Rose");
        list.add(BOTANICALS, "ტარხუნა", "Tarragon", 17, null, "Tarragon");
        list.add(BOTANICALS, "ორშანდი (ნუში და ფორთოხლის ყვავილი)", "Orgeat (Almond & Orange Flower)", 23, null, "Orgeat", "Almond");

        list.add(SUGAR_FREE, "კარამელი უშაქრო", "Caramel SF", 17, null, "Caramel");
        list.add(SUGAR_FREE, "ვანილი უშაქრო", "Vanilla SF", 17, null, "Vanilla");
        list.add(SUGAR_FREE, "ლიმონი უშაქრო", "Lemon SF", 16, null, "Lemon");
        list.add(SUGAR_FREE, "ტროპიკი უშაქრო: მანგო-მარაკუია-ფორთოხალი", "Mango-Passion Fruit-Orange SF (Tropic)", 20, null, "Mango", "Passion Fruit", "Orange");
        list.add(SUGAR_FREE, "ჟოლო და მარაკუია უშაქრო", "Raspberry & Passion Fruit SF", 24, null, "Raspberry", "Passion Fruit");
        list.add(SUGAR_FREE, "მწვანე ჩაი, ბაობაბი, გუავა უშაქრო", "Green Tea, Baobab, Guava SF", 25, null, "Green Tea", "Baobab", "Guava");
        list.add(SUGAR_FREE, "შავი ჩაი მოცვით უშაქრო", "Black Tea with Blueberry SF", 22, null, "Black Tea", "Blueberry");
        list.add(SUGAR_FREE, "დრაგონფრუტი და მანგო უშაქრო", "Dragon Fruit & Mango SF", 25, null, "Dragon Fruit", "Mango");

        list.add(NO_ADDED_SUGAR, "მწვანე ვაშლი (შაქრის დამატების გარეშე)", "Green Apple (No Added Sugar)", 18, 46, "Green Apple");
        list.add(NO_ADDED_SUGAR, "ბროწეული (შაქრის დამატების გარეშე)", "Pomegranate (No Added Sugar)", 18, 33, "Pomegranate");

        // The four 2026 trend syrups carry the uses printed on the trends sheet.
        list.add(SIGNATURE, "დრაგონფრუტი და მანგო", "Dragon Fruit & Mango", 26, 41, "Dragon Fruit", "Mango")
                .uses(COCKTAILS, SPRITZES, MOCKTAILS, LEMONADES, SMOOTHIES, ICE_CREAM, ICED_TEA, ENERGY_DRINKS);
        list.add(SIGNATURE, "შავი ჩაი მოცვით", "Black Tea with Blueberry", 23, null, "Black Tea", "Blueberry")
                .uses(COCKTAILS, MOCKTAILS, LEMONADES, SMOOTHIES, ICE_CREAM, ICED_TEA, HOT_TEA, WATER_PLUS);
        list.add(SIGNATURE, "ჟოლო და მარაკუია", "Raspberry & Passionfruit", 25, 41, "Raspberry", "Passion Fruit")
                .uses(COCKTAILS, SPRITZES, MOCKTAILS, LEMONADES, SMOOTHIES, ICE_CREAM, ICED_TEA);
        list.add(SIGNATURE, "ბაობაბი, გუავა, ლაიმი", "Baobab, Guava, Lime", 25, null, "Baobab", "Guava", "Lime")
                .uses(COCKTAILS, WINE, MOCKTAILS, LEMONADES, SMOOTHIES, ICED_TEA, WATER_PLUS);
        list.add(SIGNATURE, "მწვანე ჩაი, ბაობაბი, გუავა, ლაიმი", "Green Tea, Baobab, Guava, Lime", 26, null, "Green Tea", "Baobab", "Guava", "Lime")
                .uses(COCKTAILS, MOCKTAILS, LEMONADES, ICED_TEA, WATER_PLUS);
        list.add(SIGNATURE, "ტროპიკი (ფორთოხალი, მანგო, მარაკუია)", "Citrus (Orange, Mango, Passionfruit)", 21, null, "Orange", "Mango", "Passion Fruit");
        list.add(SIGNATURE, "ჯინჯერი ექსტრაქტით", "Ginger with Extract", 21, null, "Ginger");
        list.soon(SIGNATURE, "შემოდგომის სანელებლები (დარიჩინი, კარდამონი, მუსკატი, ფორთოხლის ზეთი)", "Autumn Spices (Cinnamon, Cardamom, Nutmeg, Orange Oil)", 22, "Spices", "Cinnamon")
                .uses(COFFEE, HOT_TEA, COCKTAILS);
        list.add(SIGNATURE, "ბაბლ-გამი ცისფერი", "Bubble Gum Blue", 20, null, "Bubble Gum");
        list.add(SIGNATURE, "ბაბლ-გამი ვარდისფერი", "Bubble Gum Pink", 20, null, "Bubble Gum");
        list.add(SIGNATURE, "გლინტვეინი", "Mulled Wine", 22, null, "Mulled Wine").uses(WINE, HOT_TEA);

        list.add(FUNCTIONAL, "ენერგეტიკული კოფეინითა და B ჯგუფის ვითამინებით", "Energy Syrup with Caffeine & B Group Vitamins", 20, null, "Energy");
        list.soon(FUNCTIONAL, "ენერგეტიკული ბაბლ-გამის არომატით", "Energy with Bubblegum", 22, "Energy", "Bubble Gum");
        list.soon(FUNCTIONAL, "ენერგეტიკული მანგო-მარაკუიას არომატით", "Energy with Mango-Passion Fruit", 22, "Energy", "Mango", "Passion Fruit");

        log.info("Loaded the Andaneri price list: {} products", list.count);
    }

    /** Small helper so each price-list line above reads like the sheet it was copied from. */
    private final class PriceList {

        private final Brand brand;
        private final ProductCategory category;
        private final Map<String, Flavor> flavorByName;
        private int count;

        PriceList(Brand brand, ProductCategory category, Map<String, Flavor> flavorByName) {
            this.brand = brand;
            this.category = category;
            this.flavorByName = flavorByName;
        }

        Line add(Section section, String ka, String en, int price, Integer juice, String... flavorNames) {
            return save(section, ka, en, price, juice, ProductStatus.ACTIVE, flavorNames);
        }

        Line soon(Section section, String ka, String en, int price, String... flavorNames) {
            return save(section, ka, en, price, null, ProductStatus.COMING_SOON, flavorNames);
        }

        private Line save(Section section, String ka, String en, int price, Integer juice, ProductStatus status, String... flavorNames) {
            Product product = new Product();
            product.setBrand(brand);
            product.setCategory(category);
            product.setSectionKa(section.ka());
            product.setSectionEn(section.en());
            product.setNameKa(ka);
            product.setNameEn(en);
            product.setPrice(BigDecimal.valueOf(price));
            product.setJuicePercent(juice);
            // Every bottle on the price list is 700 ml, and a bottle is the unit sales are counted in.
            product.setPackSize("700 ml");
            product.setUnit("bottle");
            product.setStatus(status);
            product.setSortOrder(++count);
            for (String name : flavorNames) {
                Flavor flavor = flavorByName.get(name);
                if (flavor != null) {
                    product.getFlavors().add(flavor);
                }
            }
            product.getApplications().addAll(section.uses());
            return new Line(products.save(product));
        }
    }

    private final class Line {

        private final Product product;

        Line(Product product) {
            this.product = product;
        }

        /** Replaces the section's default uses with the ones printed for this product. */
        void uses(DrinkType... uses) {
            product.getApplications().clear();
            product.getApplications().addAll(List.of(uses));
            products.save(product);
        }
    }
}
