package telraam;

import io.dropwizard.Application;
import io.dropwizard.jdbi3.JdbiFactory;
import io.dropwizard.jdbi3.bundles.JdbiExceptionsBundle;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.jdbi.v3.core.Jdbi;
import telraam.api.*;
import telraam.beacon.BeaconAggregator;
import telraam.database.daos.*;
import telraam.database.models.Baton;
import telraam.database.models.Beacon;
import telraam.database.models.Detection;
import telraam.healthchecks.TemplateHealthCheck;
import telraam.logic.Lapper;
import telraam.logic.SimpleLapper;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;


public class App extends Application<AppConfiguration> {
    private static Logger logger = Logger.getLogger(App.class.getName());
    private AppConfiguration config;
    private Environment environment;
    private Jdbi database;
    private Lapper lapper;


    public static void main(String[] args) throws Exception {
        App app = new App();
        app.setUp(args);

        BeaconAggregator ba = app.initBeacons();
        Thread beaconMessages = new Thread(ba);
        beaconMessages.start();
    }

    @Override
    public String getName() {
        return "hello-world";
    }

    @Override
    public void initialize(Bootstrap<AppConfiguration> bootstrap) {
        // nothing to do yet
        bootstrap.addBundle(new JdbiExceptionsBundle());
    }


    @Override
    public void run(AppConfiguration configuration, Environment environment) {
        this.config = configuration;
        this.environment = environment;
        // this has to be done inside run
        initDb();
        // Add api resources
        initApi();
        environment.healthChecks().register("template",
                new TemplateHealthCheck(config.getTemplate()));
    }

    public void setUp(String[] args) throws Exception {

        run(args);
        // Add database

        lapper = new SimpleLapper(database);
    }

    private BeaconAggregator initBeacons() throws IOException {
        BeaconAggregator ba;
        DetectionDAO detectionDAO = database.onDemand(DetectionDAO.class);
        BatonDAO batonDAO = database.onDemand(BatonDAO.class);
        BeaconDAO beaconDAO = database.onDemand(BeaconDAO.class);
        if (config.getBeaconPort() < 0) {
            ba = new BeaconAggregator();
        } else {
            ba = new BeaconAggregator(config.getBeaconPort());
        }
        ba.onError(e -> {
            logger.warning(e.getMessage());
            return null;
        });
        ba.onData(e -> {
            logger.info(e.toString());
            Optional<Baton> baton = batonDAO.findByMac(e.battonMAC);
            Optional<Beacon> beacon = beaconDAO.findByMac(e.stationMAC);
            if (baton.isEmpty()) {
                logger.warning(String.format(
                        "Baton passed with unregistered mac address: [%s]",
                        e.battonMAC));
                return null;
            }
            if (beacon.isEmpty()) {
                logger.warning(String.format(
                        "Beacon passed with unregistered mac address: [%s]",
                        e.stationMAC));
                return null;
            }

            Detection
                    detection =
                    new Detection(baton.get().getId(), beacon.get().getId(),
                            e.time);
            detectionDAO.insert(detection);
            this.lapper.handle(detection);

            return null;
        });
        ba.onConnect(_e -> {
            logger.info("Connect");
            return null;
        });
        ba.onDisconnect(_e -> {
            logger.info("Disconnected");
            return null;
        });
        return ba;
    }

    private void initApi() {
        JerseyEnvironment jersey = environment.jersey();
        jersey.register(new BatonResource(database.onDemand(BatonDAO.class)));
        jersey.register(new BeaconResource(database.onDemand(BeaconDAO.class)));
        jersey.register(
                new DetectionResource(database.onDemand(DetectionDAO.class)));
        jersey.register(new LapResource(database.onDemand(LapDAO.class)));
        jersey.register(new TeamResource(database.onDemand(TeamDAO.class)));
    }

    private void initDb() {
        final JdbiFactory factory = new JdbiFactory();
        database =
                factory.build(environment,
                        config.getDataSourceFactory(),
                        "postgresql");
    }

    public AppConfiguration getConfig() {
        return config;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public Jdbi getDatabase() {
        return database;
    }
}
