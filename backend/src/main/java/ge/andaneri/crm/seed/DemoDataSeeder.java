package ge.andaneri.crm.seed;

import ge.andaneri.crm.config.CrmProperties;
import ge.andaneri.crm.domain.ActivityResult;
import ge.andaneri.crm.domain.ActivityType;
import ge.andaneri.crm.domain.Brand;
import ge.andaneri.crm.domain.BrandRepository;
import ge.andaneri.crm.domain.Business;
import ge.andaneri.crm.domain.BusinessRepository;
import ge.andaneri.crm.domain.BusinessStatus;
import ge.andaneri.crm.domain.BusinessType;
import ge.andaneri.crm.domain.BusinessTypeRepository;
import ge.andaneri.crm.domain.DrinkType;
import ge.andaneri.crm.domain.Flavor;
import ge.andaneri.crm.domain.FlavorRepository;
import ge.andaneri.crm.domain.InterestReason;
import ge.andaneri.crm.domain.InterestStatus;
import ge.andaneri.crm.domain.Priority;
import ge.andaneri.crm.domain.Product;
import ge.andaneri.crm.domain.ProductCategory;
import ge.andaneri.crm.domain.ProductCategoryRepository;
import ge.andaneri.crm.domain.ProductRepository;
import ge.andaneri.crm.domain.Role;
import ge.andaneri.crm.domain.TaskType;
import ge.andaneri.crm.domain.UsageAnswer;
import ge.andaneri.crm.domain.User;
import ge.andaneri.crm.domain.UserRepository;
import ge.andaneri.crm.service.BusinessService;
import ge.andaneri.crm.service.WorkService;
import ge.andaneri.crm.web.BusinessDtos.BusinessRequest;
import ge.andaneri.crm.web.BusinessDtos.ContactRequest;
import ge.andaneri.crm.web.BusinessDtos.InterestRequest;
import ge.andaneri.crm.web.BusinessDtos.UsageRequest;
import ge.andaneri.crm.web.WorkDtos.ActivityRequest;
import ge.andaneri.crm.web.WorkDtos.CommentRequest;
import ge.andaneri.crm.web.WorkDtos.PurchaseItemRequest;
import ge.andaneri.crm.web.WorkDtos.PurchaseRequest;
import ge.andaneri.crm.web.WorkDtos.TaskRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Example accounts and a few weeks of made-up work, only for the demo profile, so every screen has
 * something on it: an overdue call, a meeting today, a customer due to reorder, a lead gone cold.
 * Every business name starts with "დემო" so none of it can be mistaken for a real account.
 */
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DEMO_PASSWORD = "demo12345";

    private final CrmProperties properties;
    private final UserRepository users;
    private final BusinessRepository businesses;
    private final BusinessTypeRepository types;
    private final BrandRepository brands;
    private final FlavorRepository flavors;
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final BusinessService businessService;
    private final WorkService workService;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(CrmProperties properties, UserRepository users, BusinessRepository businesses,
            BusinessTypeRepository types, BrandRepository brands, FlavorRepository flavors, ProductRepository products,
            ProductCategoryRepository categories, BusinessService businessService, WorkService workService,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.businesses = businesses;
        this.types = types;
        this.brands = brands;
        this.flavors = flavors;
        this.products = products;
        this.categories = categories;
        this.businessService = businessService;
        this.workService = workService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.demoData() || businesses.count() > 0) {
            return;
        }
        ZoneId zone = properties.zoneId();
        LocalDate today = LocalDate.now(zone);
        User toko = user("toko", "თოკო", Role.SALES);
        User nino = user("nino", "ნინო", Role.SALES);
        user("supervisor", "ლევანი (სუპერვაიზორი)", Role.SUPERVISOR);

        Long syrup = category("Syrup");
        Long puree = category("Fruit puree");
        Brand monin = brand("Monin");
        Brand routin = brand("1883 Maison Routin");
        Brand boiron = brand("Boiron");
        Brand andaneri = brand("Andaneri");

        // 1. A cocktail bar using Monin, meeting today at 15:00, asked for mango samples.
        Business vake = business(toko, "დემო: კოქტეილ ბარი ლეღვი", "Cocktail bar", "ჭავჭავაძის 37", "ვაკე", "+995 555 11 22 33",
                BusinessStatus.MEETING, Priority.HIGH, "20:00-ის შემდეგ", Set.of(DrinkType.COCKTAILS, DrinkType.MOCKTAILS, DrinkType.LEMONADES), 30,
                new ContactRequest("გიორგი", "ბარ მენეჯერი", "+995 599 12 34 56", null, null, true, "ჩვენი მთავარი კონტაქტი"));
        businessService.addUsagesTo(vake, new UsageRequest(syrup, monin.getId(), ids("Vanilla", "Caramel", "Hazelnut"), null, "6 ბოთლი / თვე", null, null), toko);
        businessService.upsertCategoryAnswer(vake, puree, UsageAnswer.YES, "მარწყვის პიურე, მარაკუიას ვერ შოულობენ");
        businessService.addUsagesTo(vake, new UsageRequest(puree, boiron.getId(), ids("Strawberry"), null, null, null, null), toko);
        businessService.addInterestsTo(vake, new InterestRequest(ids("Mango", "Passion Fruit"), null, InterestStatus.SAMPLE_REQUESTED,
                InterestReason.PUREE_GAP, null, "მარაკუიას პიურე არ აქვთ"), toko);
        activity(vake, toko, ActivityType.CALL, ActivityResult.NO_ANSWER, 12, "არ აიღეს");
        activity(vake, toko, ActivityType.CALL, ActivityResult.MEETING_SET, 5, "გიორგი: მონინს ხმარობენ, ფასი აინტერესებთ. შეხვედრა შეთანხმდით.");
        task(vake, toko, TaskType.MEETING, today, 15, "დეგუსტაცია: მანგო, მარაკუია, ვანილი", "ჭავჭავაძის 37");

        // 2. A cafe customer who ordered twice, the last time 26 days ago: time to ask about a reorder.
        Business cafe = business(toko, "დემო: კაფე ფინჯანი", "Cafe", "პეკინის 12", "საბურთალო", "+995 555 22 33 44",
                BusinessStatus.NEW, Priority.NORMAL, "11:00-13:00", Set.of(DrinkType.COFFEE, DrinkType.ICED_TEA, DrinkType.DESSERTS), 60,
                new ContactRequest("ანა", "მფლობელი", "+995 577 22 33 44", null, null, true, null));
        activity(cafe, toko, ActivityType.VISIT, ActivityResult.INTERESTED, 55, "1883-ის ვანილი და კარამელი. ფასი ძვირია მათთვის.");
        purchase(cafe, toko, today.minusDays(48), "Vanilla", 6, "Caramel", 4);
        purchase(cafe, toko, today.minusDays(26), "Vanilla", 6, "Salted Caramel", 3);
        businessService.addUsagesTo(cafe, new UsageRequest(syrup, routin.getId(), ids("Hazelnut"), null, null, null, null), toko);

        // 3. A lead nobody has called for three weeks, with nothing planned.
        Business cold = business(nino, "დემო: ბარი ძველი უბანი", "Bar", "ლესელიძის 20", "ძველი თბილისი", "+995 555 33 44 55",
                BusinessStatus.CONTACTED, Priority.NORMAL, null, Set.of(), 40, null);
        activity(cold, nino, ActivityType.CALL, ActivityResult.CALL_BACK, 21, "მენეჯერი არ იყო, მერე დარეკეთო");

        // 4. An overdue call-back from yesterday.
        Business pub = business(nino, "დემო: პაბი ვერე", "Pub", "ვერეს დაღმართი 4", "ვერე", "+995 555 44 55 66",
                BusinessStatus.INTERESTED, Priority.HIGH, "18:00-ის შემდეგ", Set.of(DrinkType.COCKTAILS), 15,
                new ContactRequest("ლუკა", "ბარმენი", "+995 593 44 55 66", null, null, false, null));
        activity(pub, nino, ActivityType.VISIT, ActivityResult.INTERESTED, 3, "გრენადინი და ბლუ კურასაო გინდათ. დირექტორთან უნდა შეთანხმდეს.");
        businessService.addInterestsTo(pub, new InterestRequest(ids("Grenadine", "Blue Curacao"), null, InterestStatus.INTERESTED,
                InterestReason.GENERAL, null, null), nino);
        task(pub, nino, TaskType.CALL, today.minusDays(1), 17, "ლუკა: დირექტორმა რა თქვა?", null);

        // 5. Samples out for tasting, feedback due tomorrow.
        Business hotel = business(toko, "დემო: სასტუმრო მთაწმინდა", "Hotel", "ბესიკის 8", "მთაწმინდა", "+995 322 55 66 77",
                BusinessStatus.TESTING, Priority.HIGH, null, Set.of(DrinkType.COCKTAILS, DrinkType.COFFEE, DrinkType.HOT_TEA), 25,
                new ContactRequest("მარიამი", "F&B მენეჯერი", "+995 511 55 66 77", "fb@example.ge", null, true, null));
        activity(hotel, toko, ActivityType.MEETING, ActivityResult.SAMPLES_REQUESTED, 8, "20-ზე საავტორო ვარიანტი ლობი ბარისთვის.");
        activity(hotel, toko, ActivityType.SAMPLES, ActivityResult.OTHER, 6, "გადაეცა: დრაგონფრუტი და მანგო, შავი ჩაი მოცვით, ბაობაბი");
        businessService.addInterestsTo(hotel, new InterestRequest(ids("Dragon Fruit", "Black Tea"), null, InterestStatus.TESTING,
                InterestReason.NEW_MENU, null, null), toko);
        task(hotel, toko, TaskType.FOLLOW_UP, today.plusDays(1), 12, "მარიამი: სემპლების შემდეგ რა აზრის არიან?", null);
        workService.addComment(hotel.getId(), new CommentRequest("ზამთრის მენიუ ოქტომბრიდან. გლინტვეინიც შესთავაზეთ.", null),
                users.findByUsernameIgnoreCase("supervisor").orElseThrow());

        // 6. A plain new lead, added from Google Maps and not yet called.
        business(toko, "დემო: ყავის შოპი ბაღი", "Coffee shop", "აბაშიძის 50", "ვაკე", "+995 555 66 77 88",
                BusinessStatus.NEW, Priority.LOW, null, Set.of(), 1, null);

        // 7. Turned us down.
        Business no = business(nino, "დემო: რესტორანი ჩუღურეთი", "Restaurant", "აღმაშენებლის 100", "ჩუღურეთი", "+995 555 77 88 99",
                BusinessStatus.NOT_INTERESTED, Priority.LOW, null, Set.of(DrinkType.LEMONADES), 35, null);
        activity(no, nino, ActivityType.CALL, ActivityResult.NOT_INTERESTED, 10, "ლიმონათები თვითონ ფეხზე, სიროფები არ გვჭირდებაო.");

        // 8. Next week's visit and a call later today.
        Business lounge = business(toko, "დემო: ლაუნჯი საბურთალო", "Lounge", "ვაჟა-ფშაველას 71", "საბურთალო", "+995 555 88 99 00",
                BusinessStatus.CONTACTED, Priority.NORMAL, "14:00-ის შემდეგ", Set.of(DrinkType.MOCKTAILS, DrinkType.ICED_TEA), 9, null);
        activity(lounge, toko, ActivityType.CALL, ActivityResult.CALL_BACK, 2, "უშაქრო ვარიანტები აინტერესებთ");
        task(lounge, toko, TaskType.CALL, today, 18, "უშაქრო ფასები", null);
        task(lounge, toko, TaskType.VISIT, today.plusDays(6), 14, "უშაქრო სემპლები წავუღოთ", null);
        businessService.addUsagesTo(lounge, new UsageRequest(syrup, andaneri.getId(), ids("Lemon"), null, null, null, null), toko);

        log.warn("Demo data loaded. Sign in as toko, nino or supervisor with password '{}'.", DEMO_PASSWORD);
    }

    private User user(String username, String fullName, Role role) {
        return users.findByUsernameIgnoreCase(username).orElseGet(() -> {
            User user = new User();
            user.setUsername(username);
            user.setFullName(fullName);
            user.setRole(role);
            user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
            user.setCreatedAt(Instant.now());
            return users.save(user);
        });
    }

    private Business business(User owner, String name, String typeEn, String address, String district, String phone,
            BusinessStatus status, Priority priority, String visitHours, Set<DrinkType> drinks, int daysAgo, ContactRequest contact) {
        BusinessType type = types.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(typeEn, typeEn).orElse(null);
        String maps = "https://www.google.com/maps/search/?api=1&query=" + java.net.URLEncoder.encode(address + ", Tbilisi", java.nio.charset.StandardCharsets.UTF_8);
        BusinessRequest request = new BusinessRequest(name, type == null ? null : type.getId(), status, priority, address,
                "თბილისი", district, phone, null, null, maps, null, null, null, null, null, visitHours, null, null,
                null, null, null, null, null, owner.getId(), drinks, null, contact, null, null);
        Long id = businessService.create(request, true, owner).id();
        Business business = businesses.findById(id).orElseThrow();
        business.setCreatedAt(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        return business;
    }

    private void activity(Business b, User user, ActivityType type, ActivityResult result, int daysAgo, String notes) {
        workService.logActivity(b.getId(), new ActivityRequest(type, result, null,
                Instant.now().minus(daysAgo, ChronoUnit.DAYS).minus(3, ChronoUnit.HOURS), notes, null, null, null, null, null, null), user);
    }

    private void task(Business b, User user, TaskType type, LocalDate day, int hour, String title, String location) {
        Instant due = day.atTime(hour, 0).atZone(properties.zoneId()).toInstant();
        Instant end = type == TaskType.MEETING ? due.plus(1, ChronoUnit.HOURS) : null;
        workService.createTask(new TaskRequest(b.getId(), null, user.getId(), type, title, due, end, false, location, null, null, null), user);
    }

    private void purchase(Business b, User user, LocalDate date, String flavorA, int qtyA, String flavorB, int qtyB) {
        workService.addPurchase(b.getId(), new PurchaseRequest(date, null, List.of(line(flavorA, qtyA), line(flavorB, qtyB))), user);
    }

    private PurchaseItemRequest line(String flavorEn, int quantity) {
        Product product = products.findAllForCatalog().stream()
                .filter(p -> p.getBrand().isOwn() && p.getNameEn().equalsIgnoreCase(flavorEn))
                .findFirst().orElseThrow();
        return new PurchaseItemRequest(product.getId(), null, BigDecimal.valueOf(quantity), product.getPrice());
    }

    private List<Long> ids(String... namesEn) {
        List<Flavor> all = flavors.findAll();
        return List.of(namesEn).stream()
                .map(name -> all.stream().filter(f -> f.getNameEn().equalsIgnoreCase(name)).findFirst().orElseThrow().getId())
                .toList();
    }

    private Long category(String nameEn) {
        return categories.findFirstByNameKaIgnoreCaseOrNameEnIgnoreCase(nameEn, nameEn).map(ProductCategory::getId).orElseThrow();
    }

    private Brand brand(String name) {
        return brands.findByNameIgnoreCase(name).orElseThrow();
    }
}
