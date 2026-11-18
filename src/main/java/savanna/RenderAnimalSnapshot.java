package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable render-only fact for one currently living animal. */
public final class RenderAnimalSnapshot
{
    public final long animalId;
    public final String species;
    public final int row;
    public final int col;
    public final boolean alive;
    public final boolean predator;
    public final boolean infected;
    public final boolean thirsty;
    public final boolean survivalCritical;
    public final double staminaPercent;
    public final StaminaStage staminaStage;
    public final double hydrationPercent;
    public final String hydrationStage;
    public final String survivalStage;
    public final ActionType actionType;
    public final BehaviorReason behaviorReason;
    public final ActionType primaryIntentAction;
    public final List<ExecutedEffect> executedEffects;
    public final MovementDisposition movementDisposition;
    public final boolean restCredit;
    public final String lastBehavior;
    public final Sex sex;
    public final LifeStage lifeStage;

    /** Compatibility constructor retained for older sidecar tests. */
    public RenderAnimalSnapshot(long animalId, String species, int row, int col,
                                boolean alive, String hydrationStage,
                                String survivalStage, String lastBehavior)
    {
        this(animalId, species, row, col, alive, false, false, false,
             "CRITICAL".equals(survivalStage), 100.0, StaminaStage.HIGH,
             100.0, hydrationStage, survivalStage, null, null, lastBehavior,
             Sex.FEMALE, LifeStage.ADULT, null,
             Collections.<ExecutedEffect>emptyList(),
             MovementDisposition.NOT_ATTEMPTED, false);
    }

    public RenderAnimalSnapshot(long animalId, String species, int row, int col,
                                boolean alive, boolean predator,
                                boolean infected, boolean thirsty,
                                boolean survivalCritical,
                                double staminaPercent,
                                StaminaStage staminaStage,
                                double hydrationPercent,
                                String hydrationStage, String survivalStage,
                                ActionType actionType,
                                BehaviorReason behaviorReason,
                                String lastBehavior, Sex sex,
                                LifeStage lifeStage)
    {
        this(animalId, species, row, col, alive, predator, infected, thirsty,
             survivalCritical, staminaPercent, staminaStage, hydrationPercent,
             hydrationStage, survivalStage, actionType, behaviorReason,
             lastBehavior, sex, lifeStage, actionType,
             Collections.<ExecutedEffect>emptyList(),
             MovementDisposition.NOT_ATTEMPTED, false);
    }

    public RenderAnimalSnapshot(long animalId, String species, int row, int col,
                                boolean alive, boolean predator,
                                boolean infected, boolean thirsty,
                                boolean survivalCritical,
                                double staminaPercent,
                                StaminaStage staminaStage,
                                double hydrationPercent,
                                String hydrationStage, String survivalStage,
                                ActionType actionType,
                                BehaviorReason behaviorReason,
                                String lastBehavior, Sex sex,
                                LifeStage lifeStage,
                                ActionType primaryIntentAction,
                                List<ExecutedEffect> executedEffects,
                                MovementDisposition movementDisposition,
                                boolean restCredit)
    {
        this.animalId = animalId;
        this.species = species == null ? "" : species;
        this.row = row;
        this.col = col;
        this.alive = alive;
        this.predator = predator;
        this.infected = infected;
        this.thirsty = thirsty;
        this.survivalCritical = survivalCritical;
        this.staminaPercent = Math.max(0.0, Math.min(100.0, staminaPercent));
        this.staminaStage = staminaStage == null ? StaminaStage.HIGH : staminaStage;
        this.hydrationPercent = Math.max(0.0, Math.min(100.0, hydrationPercent));
        this.hydrationStage = hydrationStage == null ? "" : hydrationStage;
        this.survivalStage = survivalStage == null ? "" : survivalStage;
        this.actionType = actionType;
        this.behaviorReason = behaviorReason;
        this.primaryIntentAction = primaryIntentAction;
        this.executedEffects = Collections.unmodifiableList(new ArrayList<>(
            executedEffects == null ? Collections.<ExecutedEffect>emptyList()
                                    : executedEffects));
        this.movementDisposition = movementDisposition == null
            ? MovementDisposition.NOT_ATTEMPTED : movementDisposition;
        this.restCredit = restCredit;
        this.lastBehavior = lastBehavior == null ? "" : lastBehavior;
        this.sex = sex == null ? Sex.FEMALE : sex;
        this.lifeStage = lifeStage == null ? LifeStage.ADULT : lifeStage;
    }

    public static RenderAnimalSnapshot capture(SavannahAnimal animal,
                                               String lastEvent)
    {
        Location location = animal.getLocation();
        String hydration = hydrationStage(animal);
        BehaviorOutcome outcome = animal.getLastBehaviorOutcome();
        BehaviorIntent primary = outcome == null ? null : outcome.getPrimaryIntent();
        return new RenderAnimalSnapshot(animal.getId(),
            animal.getProfile().getName(),
            location == null ? -1 : location.row(),
            location == null ? -1 : location.col(), animal.isAlive(),
            animal.getProfile().isPredator(), animal.isInfected(),
            animal.isThirsty(), animal.isSurvivalCritical(),
            animal.getStaminaPercent(), animal.getStaminaStage(),
            animal.getHydrationPercent(), hydration,
            animal.isSurvivalCritical() ? "CRITICAL" : "NORMAL",
            animal.getLastBehaviorAction(), animal.getLastBehaviorReason(),
            lastEvent, animal.getSex(), animal.getLifeStage(),
            primary == null ? null : primary.getActionType(),
            outcome == null ? Collections.<ExecutedEffect>emptyList()
                            : outcome.getExecutedEffects(),
            outcome == null ? MovementDisposition.NOT_ATTEMPTED
                            : outcome.getMovementDisposition(),
            outcome != null && outcome.receivesRestCredit());
    }

    public Location location()
    {
        return row < 0 || col < 0 ? null : new Location(row, col);
    }

    private static String hydrationStage(SavannahAnimal animal)
    {
        if(animal.getHydrationPercent() <= 30.0) return HydrationStage.CRITICAL.name();
        if(animal.getHydrationPercent() <= 70.0 || animal.isThirsty()) {
            return HydrationStage.THIRSTY.name();
        }
        return HydrationStage.NORMAL.name();
    }
}
