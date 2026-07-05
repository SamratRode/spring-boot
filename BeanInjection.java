// ============================================================================
// SPRING BOOT: @Component vs @Configuration -- ALL SCENARIOS REFERENCE SHEET
// ============================================================================

// ----------------------------------------------------------------------------
// 1. Basic @Component -- your own class, no dependencies
// ----------------------------------------------------------------------------
@Component
public class NotificationService {
    public void notify(String msg) {
        System.out.println("Notify: " + msg);
    }
}
// Spring scans, finds @Component, instantiates via default constructor.
// Bean name defaults to: notificationService


// ----------------------------------------------------------------------------
// 2. @Component with constructor injection (the modern standard)
// ----------------------------------------------------------------------------
@Component
public class OrderService {

    private final PaymentClient paymentClient;
    private final NotificationService notificationService;

    // No @Autowired needed if there's only ONE constructor (Spring 4.3+)
    public OrderService(PaymentClient paymentClient, NotificationService notificationService) {
        this.paymentClient = paymentClient;
        this.notificationService = notificationService;
    }
}
// Interview point: with a single constructor, @Autowired is optional.
// With multiple constructors, exactly one must be marked @Autowired.


// ----------------------------------------------------------------------------
// 3. Stereotype specializations of @Component
// ----------------------------------------------------------------------------
@Repository
public class OrderRepository {
    // adds exception translation for persistence exceptions
}

@Service
public class OrderBusinessLogic {
    // semantic marker, no extra behavior by default
}

@Controller
public class OrderController {
    // handles web requests, works with view resolvers
}

@RestController // = @Controller + @ResponseBody
public class OrderRestController {
    // returns JSON/XML directly
}
// Interview point: all four are meta-annotated with @Component, so
// component-scanning picks them up identically. @Repository adds one real
// extra feature (exception translation via PersistenceExceptionTranslationPostProcessor);
// the rest are mostly semantic -- until AOP pointcuts target them specifically.


// ----------------------------------------------------------------------------
// 4. @Configuration -- simplest form, third-party bean
// ----------------------------------------------------------------------------
@Configuration
public class WebClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}


// ----------------------------------------------------------------------------
// 5. @Configuration with parameter injection (recommended pattern)
// ----------------------------------------------------------------------------
@Configuration
public class AppConfig {

    @Bean
    public PaymentClient paymentClient(RestTemplate restTemplate) {
        return new PaymentClient(restTemplate); // restTemplate resolved from container
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
// Works correctly whether proxyBeanMethods is true or false -- this is the
// pattern Spring Boot itself uses internally.


// ----------------------------------------------------------------------------
// 6. @Configuration using @Value / externalized properties
// ----------------------------------------------------------------------------
@Configuration
public class MailConfig {

    @Value("${mail.host}")
    private String host;

    @Bean
    public JavaMailSender mailSender(@Value("${mail.port}") int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        return sender;
    }
}
// Interview point: bean creation logic often needs config values -- strong
// reason to prefer @Bean over @Component when the object needs setup steps
// beyond a plain constructor call.


// ----------------------------------------------------------------------------
// 7. Multiple beans of the same type + @Primary + @Qualifier
// ----------------------------------------------------------------------------
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource primaryDataSource() {
        return DataSourceBuilder.create().url("jdbc:...primary").build();
    }

    @Bean
    public DataSource reportingDataSource() {
        return DataSourceBuilder.create().url("jdbc:...reporting").build();
    }
}

@Service
public class ReportService {
    private final DataSource dataSource;

    public ReportService(@Qualifier("reportingDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }
}

// --- @Component equivalent using @Qualifier on candidates directly ---
@Component
@Qualifier("fastCache")
public class InMemoryCache implements Cache {
}

@Component
@Qualifier("persistentCache")
public class RedisCache implements Cache {
}

@Service
public class CacheConsumer {
    public CacheConsumer(@Qualifier("fastCache") Cache cache) {
        // uses InMemoryCache
    }
}


// ----------------------------------------------------------------------------
// 8. Conditional bean creation -- @Configuration territory
// ----------------------------------------------------------------------------
@Configuration
public class FeatureConfig {

    @Bean
    @ConditionalOnProperty(name = "feature.new-search", havingValue = "true")
    public SearchService newSearchService() {
        return new ElasticSearchService();
    }

    @Bean
    @ConditionalOnMissingBean(SearchService.class)
    public SearchService defaultSearchService() {
        return new BasicSearchService();
    }
}
// Interview point: @Conditional CAN be placed on a @Component class too,
// but it's far less common -- conditional logic naturally fits @Bean factory
// methods where you can branch inside code, not just via annotation metadata.


// ----------------------------------------------------------------------------
// 9. Bean scope -- identical syntax on both styles
// ----------------------------------------------------------------------------
@Component
@Scope("prototype")
public class RequestScopedTask {
}

@Configuration
public class TaskConfig {
    @Bean
    @Scope("prototype")
    public Task task() {
        return new Task();
    }
}
// Common scopes: singleton (default), prototype, request, session.


// ----------------------------------------------------------------------------
// 10. Lifecycle callbacks -- different mechanisms per style
// ----------------------------------------------------------------------------

// --- @Component style: annotation-based ---
@Component
public class CacheManager {

    @PostConstruct
    public void init() {
        System.out.println("Warming cache...");
    }

    @PreDestroy
    public void cleanup() {
        System.out.println("Flushing cache...");
    }
}

// --- @Configuration style: initMethod/destroyMethod attributes ---
// (needed for third-party classes you cannot annotate with @PostConstruct)
@Configuration
public class ResourceConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ThirdPartyResource resource() {
        return new ThirdPartyResource();
    }
}
// Interview point: this is another reason @Bean exists -- you can hook into
// a third-party object's own start()/stop()/close() methods without touching
// its source. Objects implementing Closeable/AutoCloseable often get
// destroyMethod inferred automatically -- no need to specify it.


// ----------------------------------------------------------------------------
// 11. @Bean returning an interface type -- hiding the implementation
// ----------------------------------------------------------------------------
@Configuration
public class NotifierConfig {

    @Bean
    public Notifier notifier(@Value("${notifier.type}") String type) {
        if ("sms".equals(type)) {
            return new SmsNotifier();
        }
        return new EmailNotifier();
    }
}
// Interview point: hard to do cleanly with @Component, since a @Component
// class must literally BE one concrete class. @Bean lets you write plain
// if/else logic in code to decide which implementation to return.


// ----------------------------------------------------------------------------
// 12. Importing configurations -- modularizing config classes
// ----------------------------------------------------------------------------
@Configuration
@Import({ DataSourceConfig.class, WebClientConfig.class })
public class MainAppConfig {
}
// Interview point: @Import is exclusive to config-style classes; there's no
// @Component equivalent because @ComponentScan already handles discovery of
// @Component classes across packages.


// ----------------------------------------------------------------------------
// 13. @Bean inside @Component ("lite" mode) -- the classic pitfall
// ----------------------------------------------------------------------------
@Component
public class LiteConfig {

    @Bean
    public Engine engine() {
        return new Engine();
    }

    @Bean
    public Car car() {
        return new Car(engine()); // NOT proxied -- creates a NEW Engine,
                                   // not the container's managed bean
    }
}
// Only relevant for comparison -- avoid this pattern in real code.
// Use @Configuration whenever you have inter-bean method calls.


// ----------------------------------------------------------------------------
// 14. BONUS: The CGLIB-proxied correct version of #13, for contrast
// ----------------------------------------------------------------------------
@Configuration // proxyBeanMethods defaults to true
public class ProperConfig {

    @Bean
    public Engine engine() {
        return new Engine();
    }

    @Bean
    public Car car() {
        return new Car(engine()); // intercepted by CGLIB proxy,
                                   // returns the SAME singleton Engine bean
    }
}