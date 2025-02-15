package telraam.logic;

import org.jdbi.v3.core.Jdbi;
import telraam.database.daos.LapDAO;
import telraam.database.daos.LapSourceDAO;
import telraam.database.models.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimpleLapper implements Lapper {
    // Needs to be the same as in the lap_source database table.
    static final String SOURCE_NAME = "simple-lapper";
    private static final int MAX_SPEED = 50;

    private final LapSource source;

    private List<Team> teams;
    private List<Baton> batons;
    private List<Beacon> beacons;
    private Jdbi jdbi;
    private LapDAO lapDAO;
    private LapSourceDAO lapSourceDAO;
    private Map<Integer, List<Detection>> detections;
    private Map<Integer, Integer> positionMap;
    private Team testTeam;

    public SimpleLapper(Jdbi jdbi) {
        this.jdbi = jdbi;
        this.lapDAO = jdbi.onDemand(LapDAO.class);
        this.lapSourceDAO = jdbi.onDemand(LapSourceDAO.class);

        this.teams = new ArrayList<>();
        this.batons = new ArrayList<>();
        this.beacons = new ArrayList<>();
        this.positionMap = new HashMap<>();
        this.detections = new HashMap<>();

        // TODO: DELET THIS
        testTeam = new Team("test", 1);
        testTeam.setId(1);
        this.teams.add(testTeam);
        Beacon testBeacon = new Beacon("beacon A");
        testBeacon.setId(1);
        Baton testBaton = new Baton("baton 1");
        testBaton.setId(1);
        // TODO get this from the database
        this.source = lapSourceDAO.getByName(SOURCE_NAME).orElseThrow();

        this.positionMap.put(1, 1);


        for (Team team : teams) {
            detections.put(team.getBatonId(), new ArrayList<>());
        }
    }

    @Override
    public void handle(Detection msg) {
        List<Detection> currentDetections = detections.get(msg.getBatonId());
        currentDetections.add(msg);
        generateLap(currentDetections);
    }

    private void generateLap(List<Detection> detections) {
        Detection first = detections.get(0);

        for (int i = 1; i < detections.size(); i++) {
            Detection detection = detections.get(i);

            if (positionMap.get(first.getBeaconId()).equals(
                    positionMap.get(detection.getBeaconId()))) {
                this.lapDAO.insert(new Lap(detection.getBatonId(), this.source.getId(),
                        detection.getTimestamp()));
                detections.clear();
                detections.add(detection);
                break;
            }
        }
    }
}
