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
import telraam.healthchecks.TemplateHealthCheck;

import java.io.IOException;
import java.util.logging.Logger;


public class App extends Application<AppConfiguration> {
    private static Logger logger = Logger.getLogger(App.class.getName());
    private AppConfiguration config;
    private Environment environment;
    private Jdbi database;

    public static void main(String[] args) throws Exception {
        new App().run(args);
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
    public void run(AppConfiguration configuration, Environment environment)
            throws IOException {
        this.config = configuration;
        this.environment = environment;
        // Add database
        final JdbiFactory factory = new JdbiFactory();
        database =
                factory.build(environment, configuration.getDataSourceFactory(),
                        "postgresql");


        // Add api resources
        JerseyEnvironment jersey = environment.jersey();
        jersey.register(new BatonResource(database.onDemand(BatonDAO.class)));
        jersey.register(new BeaconResource(database.onDemand(BeaconDAO.class)));
        jersey.register(
                new DetectionResource(database.onDemand(DetectionDAO.class)));
        jersey.register(new LapResource(database.onDemand(LapDAO.class)));
        jersey.register(new TeamResource(database.onDemand(TeamDAO.class)));
        jersey.register(new LapSourceResource(database.onDemand(LapSourceDAO.class)));
        environment.healthChecks().register("template",
                new TemplateHealthCheck(configuration.getTemplate()));

        BeaconAggregator ba;
        if (configuration.getBeaconPort() < 0) {
            ba = new BeaconAggregator();
        } else {
            ba = new BeaconAggregator(configuration.getBeaconPort());
        }
        ba.onError(e -> {
            logger.warning(e.getMessage());
            return null;
        });
        ba.onData(e -> {
            logger.info(e.toString());
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
        Thread beaconMessages = new Thread(ba);
        beaconMessages.start();
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
