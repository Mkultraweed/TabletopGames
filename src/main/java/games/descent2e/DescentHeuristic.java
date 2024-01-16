package games.descent2e;

import core.AbstractGameState;
import core.AbstractParameters;
import core.CoreConstants;
import core.interfaces.IStateHeuristic;
import evaluation.optimisation.TunableParameters;
import games.descent2e.components.Figure;
import games.descent2e.components.Hero;
import games.descent2e.components.Monster;
import static games.descent2e.DescentHelper.*;
import utilities.Utils;
import utilities.Vector2D;

import java.util.ArrayList;
import java.util.List;

import static games.descent2e.components.Figure.Attribute.Fatigue;
import static games.descent2e.components.Figure.Attribute.Health;

public class DescentHeuristic extends TunableParameters implements IStateHeuristic {

    // The total HP of the Heroes   - Beneficial to the Heroes
    double FACTOR_HERO_HP = 0.5;
    // The number of Heroes defeated - Beneficial to the Overlord
    double FACTOR_HERO_DEFEATED = 0.7;
    // The total HP of the monsters - Beneficial to the Overlord
    double FACTOR_MONSTERS_HP = 0.5;
    // The number of monsters defeated - Beneficial to the Heroes
    double FACTOR_MONSTERS_DEFEATED = 0.7;
    // The Overlord's fatigue value - Beneficial to the Overlord
    double FACTOR_OVERLORD_FATIGUE = 0.7;
    // How close the Overlord is to increasing their fatigue - Beneficial to the Overlord
    double FACTOR_OVERLORD_THREAT = 0.5;
    // How close the Heroes are to winning - Beneficial to the Heroes
    double FACTOR_HEROES_THREAT = 0.1;

    public DescentHeuristic() {
        addTunableParameter("FACTOR_HERO_HP", 0.5);
        addTunableParameter("FACTOR_HERO_DEFEATED", 0.7);
        addTunableParameter("FACTOR_MONSTERS_HP", 0.5);
        addTunableParameter("FACTOR_MONSTERS_DEFEATED", 0.7);
        addTunableParameter("FACTOR_OVERLORD_FATIGUE", 0.7);
        addTunableParameter("FACTOR_OVERLORD_THREAT", 0.5);
        addTunableParameter("FACTOR_HEROES_THREAT", 0.1);
    }


    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {

        List<Double> heuristics = new ArrayList<>();

        DescentGameState dgs = (DescentGameState) gs;
        DescentParameters dp = (DescentParameters) gs.getGameParameters();
        CoreConstants.GameResult playerResult = gs.getPlayerResults()[playerId];

        if (!gs.isNotTerminal()) {
            return playerResult.value;
        }

        String questName = dgs.getCurrentQuest().getName();

        // Some heuristics will be beneficial to the overlord and detrimental to the heroes
        // Likewise, some will be beneficial to the heroes and detrimental to the overlord
        // We need to flip the sign of the heuristic if the player is the overlord where relevant
        Figure overlord = dgs.getOverlord();
        double isOverlord = playerId == overlord.getOwnerId() ? -1.0 : 1.0;

        double retValue = 0.0;

        heuristics.add(FACTOR_HERO_HP * isOverlord * (getHeroesHP(dgs) / getHeroesMaxHP(dgs)));
        heuristics.add(-1 * FACTOR_HERO_DEFEATED * isOverlord * (getHeroesDefeated(dgs) / dgs.heroes.size()));

        heuristics.add(-1 * FACTOR_OVERLORD_FATIGUE * isOverlord * ((double) overlord.getAttributeValue(Fatigue) / (double) overlord.getAttributeMax(Fatigue)));
        heuristics.add(-1 * FACTOR_OVERLORD_THREAT * isOverlord * (getOverlordThreat(dgs, questName)));

        switch (questName)
        {
            case "Acolyte of Saradyn":
                // We only care about the Barghests, as their defeat is the only way for the Heroes to win, as the Goblin Archers infinitely respawn
                // The Barghests are the second monsters in the list, i.e. index 1
                heuristics.add(-1 * FACTOR_MONSTERS_HP * isOverlord * (getMonstersHP(dgs, 1) / getMonstersMaxHP(dgs, 1)));
                heuristics.add(FACTOR_MONSTERS_DEFEATED * isOverlord * (getMonstersDefeated(dgs, 1) / dgs.monstersOriginal.get(1).size()));
                break;
            default:
                heuristics.add(FACTOR_MONSTERS_HP * isOverlord * (getMonstersHP(dgs, 0) / getMonstersMaxHP(dgs, 0)) / dgs.monsters.size());
                heuristics.add(FACTOR_MONSTERS_DEFEATED * isOverlord * (getMonstersDefeated(dgs, 0) / dgs.monstersOriginal.get(0).size()) / dgs.monsters.size());
                break;
        }

        heuristics.add(FACTOR_HEROES_THREAT * isOverlord * (getHeroesThreat(dgs, questName) / dgs.heroes.size()));

        retValue = heuristics.stream().mapToDouble(Double::doubleValue).sum();

        return Utils.clamp(retValue, -1.0, 1.0);
    }

    private double getHeroesHP(DescentGameState dgs) {
        return dgs.heroes.stream().mapToDouble(h -> h.getAttributeValue(Health)).sum();
    }
    private double getHeroesMaxHP(DescentGameState dgs) {
        return dgs.heroes.stream().mapToDouble(h -> h.getAttributeMax(Health)).sum();
    }
    private double getHeroesDefeated(DescentGameState dgs) {
        return dgs.heroes.stream().filter(Hero::isDefeated).count();
    }

    private double getMonstersHP(DescentGameState dgs, int index) {
        return dgs.monsters.get(index).stream().mapToDouble(m -> m.getAttributeValue(Health)).sum();
    }
    private double getMonstersMaxHP(DescentGameState dgs, int index) {
        return dgs.monsters.get(index).stream().mapToDouble(m -> m.getAttributeMax(Health)).sum();
    }
    private double getMonstersDefeated(DescentGameState dgs, int index) {
        // Subtract the number of monsters in the original list from the number in the current list
        return dgs.monstersOriginal.get(index).size() - dgs.monsters.get(index).size();
    }
    private double getOverlordThreat(DescentGameState dgs, String questName) {
        double retVal = 0.0;
        switch (questName)
        {
            case "Acolyte of Saradyn":
                // We need to check how many Goblin Archers (index 0) are within the scorezone of 9A
                // or in the neighbouring zones of 21A, entrance1A and 8A
                String scoreZone = "9A";
                List<Vector2D> tileCoords = new ArrayList<>(dgs.gridReferences.get(scoreZone).keySet());

                List<Vector2D> neighbourCoords = new ArrayList<>(dgs.gridReferences.get("21A").keySet());
                neighbourCoords.addAll(dgs.gridReferences.get("entrance1A").keySet());
                neighbourCoords.addAll(dgs.gridReferences.get("8A").keySet());

                for (Monster m : dgs.monsters.get(0)) {
                    if (tileCoords.contains(m.getPosition())) {
                        retVal += 1.0;
                    }
                    else if (neighbourCoords.contains(m.getPosition())) {
                        retVal += 0.1;
                    }
                }
                break;
            default:
                break;
        }
        return retVal;
    }

    private double getHeroesThreat(DescentGameState dgs, String questName) {
        double retVal = 0.0;
        switch (questName)
        {
            case "Acolyte of Saradyn":
                // We need to check how far away the Heroes are from the Barghests
                // The closer the Heroes are, the better
                List<Monster> barghests = dgs.monsters.get(1);
                int closest = 0;
                for (Hero h : dgs.heroes) {
                    double distance = 10000.0;
                    Vector2D position = h.getPosition();
                    for (int i = 0; i < barghests.size(); i++) {
                        Monster m = barghests.get(i);
                        double dist = getDistance(position, m.getPosition());
                        if (dist < distance) {
                            distance = dist;
                            closest = i;
                        }
                    }
                    Vector2D range = getRange(position, barghests.get(closest).getPosition());
                    double d = 1.0 - (Math.max(range.getX(), range.getY()) / 10.0);
                    retVal += d;
                }
                break;
            default:
                break;
        }
        return retVal;
    }


    @Override
    protected DescentHeuristic _copy() {
        DescentHeuristic retVal = new DescentHeuristic();
        retVal.FACTOR_HERO_HP = FACTOR_HERO_HP;
        retVal.FACTOR_HERO_DEFEATED = FACTOR_HERO_DEFEATED;
        retVal.FACTOR_MONSTERS_HP = FACTOR_MONSTERS_HP;
        retVal.FACTOR_MONSTERS_DEFEATED = FACTOR_MONSTERS_DEFEATED;
        retVal.FACTOR_OVERLORD_FATIGUE = FACTOR_OVERLORD_FATIGUE;
        retVal.FACTOR_OVERLORD_THREAT = FACTOR_OVERLORD_THREAT;
        return retVal;
    }

    @Override
    protected boolean _equals(Object o) {
        if (o instanceof DescentHeuristic)
        {
            DescentHeuristic other = (DescentHeuristic) o;
            return FACTOR_HERO_HP == other.FACTOR_HERO_HP &&
                    FACTOR_HERO_DEFEATED == other.FACTOR_HERO_DEFEATED &&
                    FACTOR_MONSTERS_HP == other.FACTOR_MONSTERS_HP &&
                    FACTOR_MONSTERS_DEFEATED == other.FACTOR_MONSTERS_DEFEATED &&
                    FACTOR_OVERLORD_FATIGUE == other.FACTOR_OVERLORD_FATIGUE &&
                    FACTOR_OVERLORD_THREAT == other.FACTOR_OVERLORD_THREAT;
        }
        return false;
    }

    @Override
    public Object instantiate() {
        return this._copy();
    }

    @Override
    public void _reset() {
        FACTOR_HERO_HP = (double) getParameterValue("FACTOR_HERO_HP");
        FACTOR_HERO_DEFEATED = (double) getParameterValue("FACTOR_HERO_DEFEATED");
        FACTOR_MONSTERS_HP = (double) getParameterValue("FACTOR_MONSTERS_HP");
        FACTOR_MONSTERS_DEFEATED = (double) getParameterValue("FACTOR_MONSTERS_DEFEATED");
        FACTOR_OVERLORD_FATIGUE = (double) getParameterValue("FACTOR_OVERLORD_FATIGUE");
        FACTOR_OVERLORD_THREAT = (double) getParameterValue("FACTOR_OVERLORD_THREAT");
    }
}
