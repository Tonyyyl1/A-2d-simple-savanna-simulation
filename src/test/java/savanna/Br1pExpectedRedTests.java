package savanna;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Standalone BR1-P expected-red fixture. It is intentionally not registered in
 * AllTests. Every invocation selects one case and one mode.
 */
public final class Br1pExpectedRedTests
{
    private static final String[] CASE_ORDER = {
        "BR1P-F01-global-rich-local-zero",
        "BR1P-F02-zero-kill-zero-credit",
        "BR1P-F03-realized-kill-credit-once",
        "BR1P-F04-eligible-conception-ledger",
        "BR1P-F05-no-child-at-conception",
        "BR1P-F06-prey-moves-after-conception",
        "BR1P-F07-winning-birth-conservation",
        "BR1P-F08-losing-spawn-remains-pending",
        "BR1P-F09-partial-litter",
        "BR1P-F10-maternal-death",
        "BR1P-F11-all-registered-predators",
        "BR1P-F12-prey-legacy-adapter",
        "BR1P-F13-position-kernel-unchanged",
        "BR1P-F14-fingerprint-complete"
    };

    private static final Map<String, CaseDefinition> CASES = buildCases();

    private Br1pExpectedRedTests()
    {
    }

    public static void main(String[] arguments)
    {
        if(arguments.length != 2) {
            throw new IllegalArgumentException(
                "usage: Br1pExpectedRedTests <SELF_PASS|FAULT|BASELINE> <case-id>");
        }
        Mode mode = Mode.valueOf(arguments[0].toUpperCase(Locale.ROOT));
        CaseDefinition definition = CASES.get(arguments[1]);
        if(definition == null) {
            throw new IllegalArgumentException("unknown BR1-P case: " + arguments[1]);
        }

        Observation expected = expectedObservation(definition.id);
        Observation observed = expected.copy();
        String evidence = "conforming typed observation";
        if(mode == Mode.FAULT) {
            observed.put(definition.anchorField,
                definition.frozenFaultValue.get());
            evidence = "single frozen predecessor-valued field mutation";
        }
        else if(mode == Mode.BASELINE) {
            ProbeResult probe = definition.predecessorProbe.get();
            observed.put(definition.anchorField, probe.actual);
            evidence = probe.evidence;
        }

        System.out.println("MODE=" + mode + " CASE=" + definition.id);
        System.out.println("OBS field=" + definition.anchorField +
            " actual=" + render(observed.get(definition.anchorField)) +
            " evidence=" + evidence);
        requireExactObservation(definition.id, expected, observed);
        System.out.println("PASS " + mode + " " + definition.id +
            " assertions=" + expected.size());
    }

    private static Map<String, CaseDefinition> buildCases()
    {
        LinkedHashMap<String, CaseDefinition> cases = new LinkedHashMap<>();
        add(cases, new CaseDefinition(CASE_ORDER[0],
            "predatorBehaviorAdapters",
            () -> predatorAdapters("PREDATOR_FACT_ONLY"),
            Br1pExpectedRedTests::probePredatorAdapters));
        add(cases, new CaseDefinition(CASE_ORDER[1],
            "conceptionNamedLedgerSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probeConceptionLedgerSurface));
        add(cases, new CaseDefinition(CASE_ORDER[2],
            "killCreditLedgerSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probeKillCreditLedgerSurface));
        add(cases, new CaseDefinition(CASE_ORDER[3],
            "policyConceptionLifecycleSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probeConceptionLifecycleSurface));
        add(cases, new CaseDefinition(CASE_ORDER[4],
            "pendingBirthTokenSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probePendingBirthTokenSurface));
        add(cases, new CaseDefinition(CASE_ORDER[5],
            "lifecycleStates",
            () -> "<absent>",
            Br1pExpectedRedTests::probeLifecycleStates));
        add(cases, new CaseDefinition(CASE_ORDER[6],
            "newbornReserves",
            Br1pExpectedRedTests::fullNewbornReserveExpectation,
            Br1pExpectedRedTests::probeNewbornReserves));
        add(cases, new CaseDefinition(CASE_ORDER[7],
            "positionClaimCarriesPendingToken",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probePositionClaimPendingToken));
        add(cases, new CaseDefinition(CASE_ORDER[8],
            "independentOrdinalStateSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probeIndependentOrdinalSurface));
        add(cases, new CaseDefinition(CASE_ORDER[9],
            "pregnancyLossProductionSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probePregnancyLossSurface));
        add(cases, new CaseDefinition(CASE_ORDER[10],
            "hashBoundRegistryDispatch",
            Br1pExpectedRedTests::frozenFactOnlyDispatch,
            Br1pExpectedRedTests::probeHashBoundRegistryDispatch));
        add(cases, new CaseDefinition(CASE_ORDER[11],
            "preyBehaviorDispatchSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probePreyBehaviorDispatch));
        add(cases, new CaseDefinition(CASE_ORDER[12],
            "dueTokenSolePositionOwnerSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probeDueTokenPositionOwner));
        add(cases, new CaseDefinition(CASE_ORDER[13],
            "completeFingerprintSurface",
            () -> Boolean.FALSE,
            Br1pExpectedRedTests::probeCompleteFingerprint));
        if(!new ArrayList<>(cases.keySet()).equals(Arrays.asList(CASE_ORDER))) {
            throw new IllegalStateException("BR1-P case order is not exact");
        }
        return cases;
    }

    private static void add(Map<String, CaseDefinition> cases,
                            CaseDefinition definition)
    {
        if(cases.put(definition.id, definition) != null) {
            throw new IllegalStateException("duplicate BR1-P case: " + definition.id);
        }
    }

    private static Observation expectedObservation(String id)
    {
        Observation expected = new Observation();
        switch(id) {
            case "BR1P-F01-global-rich-local-zero":
                return expected
                    .put("predatorBehaviorAdapters",
                        predatorAdapters("PREDATOR_POLICY"))
                    .put("globalPreyIgnored", Boolean.TRUE)
                    .put("zeroLocalBelowCreditIneligible", Boolean.TRUE)
                    .put("pregnancyStateMutations", Long.valueOf(0L))
                    .put("dueClaims", Long.valueOf(0L));
            case "BR1P-F02-zero-kill-zero-credit":
                return expected
                    .put("conceptionNamedLedgerSurface", Boolean.TRUE)
                    .put("sameStepKillSpendable", Boolean.FALSE)
                    .put("mateShuffle", Long.valueOf(0L))
                    .put("conceptionRoll", Long.valueOf(0L))
                    .put("litterDraw", Long.valueOf(0L))
                    .put("sexDraw", Long.valueOf(0L))
                    .put("creditDebit", Long.valueOf(0L))
                    .put("maternalDebit", Long.valueOf(0L))
                    .put("escrowMutation", Long.valueOf(0L))
                    .put("positionClaims", Long.valueOf(0L))
                    .put("childIds", Long.valueOf(0L))
                    .put("events", Long.valueOf(0L));
            case "BR1P-F03-realized-kill-credit-once":
                return expected
                    .put("killCreditLedgerSurface", Boolean.TRUE)
                    .put("acceptedFoodCreditDelaySteps",
                        Integer.valueOf(FrozenPolicy.CREDIT_DELAY_STEPS))
                    .put("killCreditWindowSteps",
                        Integer.valueOf(FrozenPolicy.KILL_CREDIT_WINDOW_STEPS))
                    .put("conceptionCreditDebit",
                        Integer.valueOf(FrozenPolicy.CONCEPTION_CREDIT_DEBIT))
                    .put("boundary", "k:not-spendable;k+1..k+" +
                        FrozenPolicy.KILL_CREDIT_WINDOW_STEPS +
                        ":spendable;k+" +
                        (FrozenPolicy.KILL_CREDIT_WINDOW_STEPS + 1) +
                        ":expired")
                    .put("acceptedFoodOnly", Boolean.TRUE)
                    .put("singleFifoDebit", Boolean.TRUE)
                    .put("duplicateDebit", Long.valueOf(0L))
                    .put("localBranchPreservesCredit", Boolean.TRUE);
            case "BR1P-F04-eligible-conception-ledger":
                return expected
                    .put("policyConceptionLifecycleSurface", Boolean.TRUE)
                    .put("capacityDensityCeilingMicros",
                        Integer.valueOf(FrozenPolicy.CAPACITY_DENSITY_CEILING_MICROS))
                    .put("localResourceFloor",
                        Integer.valueOf(FrozenPolicy.LOCAL_RESOURCE_FLOOR))
                    .put("maternalHydrationFloorMicros",
                        Integer.valueOf(FrozenPolicy.MATERNAL_HYDRATION_FLOOR_MICROS))
                    .put("maternalStaminaFloorMicros",
                        Integer.valueOf(FrozenPolicy.MATERNAL_STAMINA_FLOOR_MICROS))
                    .put("conceptionInvestmentStaminaMicros",
                        Integer.valueOf(FrozenPolicy.CONCEPTION_INVESTMENT_STAMINA_MICROS))
                    .put("stepStartFactImmutable", Boolean.TRUE)
                    .put("zeroAffordableRngDraws", Long.valueOf(0L))
                    .put("litterBoundIsAffordableMaximum", Boolean.TRUE)
                    .put("namedRngOrder",
                        "mate-query-shuffle>conception-roll>litter-draw")
                    .put("dueStepOffset",
                        Integer.valueOf(FrozenPolicy.GESTATION_STEPS));
            case "BR1P-F05-no-child-at-conception":
                return expected
                    .put("pendingBirthTokenSurface", Boolean.TRUE)
                    .put("pregnancyTransactions", Long.valueOf(1L))
                    .put("conceptionChildObjects", Long.valueOf(0L))
                    .put("conceptionChildIds", Long.valueOf(0L))
                    .put("conceptionSexDraws", Long.valueOf(0L))
                    .put("conceptionBirthEvents", Long.valueOf(0L))
                    .put("conceptionSpawnTransitions", Long.valueOf(0L))
                    .put("preDueClaims", Long.valueOf(0L))
                    .put("dueStepOffset",
                        Integer.valueOf(FrozenPolicy.GESTATION_STEPS));
            case "BR1P-F06-prey-moves-after-conception":
                return expected
                    .put("lifecycleStates", "OPEN,GESTATING,POSTPARTUM")
                    .put("pregnancySurvivesLaterResourceMovement", Boolean.TRUE)
                    .put("gestationSteps",
                        Integer.valueOf(FrozenPolicy.GESTATION_STEPS))
                    .put("postpartumSteps",
                        Integer.valueOf(FrozenPolicy.POSTPARTUM_STEPS))
                    .put("reconceptionWhileGestating", Long.valueOf(0L))
                    .put("reconceptionWhilePostpartum", Long.valueOf(0L))
                    .put("openAtFinalTerminalPlus",
                        Integer.valueOf(FrozenPolicy.POSTPARTUM_STEPS));
            case "BR1P-F07-winning-birth-conservation":
                return expected
                    .put("newbornReserves", frozenNewbornReserveExpectation())
                    .put("maternalDebitCount", Long.valueOf(1L))
                    .put("birthDebitCount", Long.valueOf(0L))
                    .put("winnerEscrowReleases", Long.valueOf(1L))
                    .put("winnerSideEffectOrder",
                        "sex-draw>child-id>child-create>spawn-transition>birth-event")
                    .put("externalAllowance", "0/0/0/0")
                    .put("componentConservationExact", Boolean.TRUE)
                    .put("fixedPointRound", "Java-Math.round");
            case "BR1P-F08-losing-spawn-remains-pending":
                return expected
                    .put("positionClaimCarriesPendingToken", Boolean.TRUE)
                    .put("ordinalPending", Boolean.TRUE)
                    .put("escrowUnchanged", Boolean.TRUE)
                    .put("loserSexDraw", Long.valueOf(0L))
                    .put("loserChildId", Long.valueOf(0L))
                    .put("loserChild", Long.valueOf(0L))
                    .put("loserSpawnTransition", Long.valueOf(0L))
                    .put("loserBirthEvent", Long.valueOf(0L))
                    .put("loserDebitReleaseRefundLoss", "0/0/0/0")
                    .put("attemptsThisStep", Long.valueOf(1L))
                    .put("duplicateClaims", Long.valueOf(0L));
            case "BR1P-F09-partial-litter":
                return expected
                    .put("independentOrdinalStateSurface", Boolean.TRUE)
                    .put("ordinalPartitionExact", Boolean.TRUE)
                    .put("crossOrdinalMutation", Long.valueOf(0L))
                    .put("ordinaryPendingHorizonSteps",
                        Integer.valueOf(FrozenPolicy.PENDING_HORIZON_STEPS))
                    .put("firstFallbackOffset",
                        Integer.valueOf(FrozenPolicy.PENDING_HORIZON_STEPS))
                    .put("fallbackAttemptsPerOrdinalPerStep", Long.valueOf(1L))
                    .put("activeTransactionsPerMother", Long.valueOf(1L))
                    .put("closeRequiresAllTerminal", Boolean.TRUE);
            case "BR1P-F10-maternal-death":
                return expected
                    .put("pregnancyLossProductionSurface", Boolean.TRUE)
                    .put("unreleasedOrdinalLossCount", Long.valueOf(1L))
                    .put("duplicateLossCount", Long.valueOf(0L))
                    .put("committedOrdinalMutation", Long.valueOf(0L))
                    .put("pendingAfterCleanup", Long.valueOf(0L))
                    .put("transactionCloseCount", Long.valueOf(1L))
                    .put("orphanEscrow", Long.valueOf(0L))
                    .put("deathChildSexIdBirthRefundExternal", "0/0/0/0/0/0");
            case "BR1P-F11-all-registered-predators":
                return expected
                    .put("hashBoundRegistryDispatch", frozenPolicyDispatch())
                    .put("registryPredatorCoverageExact", Boolean.TRUE)
                    .put("singleProfilePath", Boolean.TRUE)
                    .put("missingMappingRejected", Boolean.TRUE)
                    .put("duplicateMappingRejected", Boolean.TRUE)
                    .put("unknownMappingRejected", Boolean.TRUE)
                    .put("wrongHashRejected", Boolean.TRUE)
                    .put("classSeedCoordinateCheckpointBranches", Long.valueOf(0L));
            case "BR1P-F12-prey-legacy-adapter":
                return expected
                    .put("preyBehaviorDispatchSurface", Boolean.TRUE)
                    .put("preyAdapters", preyAdapters("LEGACY_GLOBAL"))
                    .put("legacyMateProbabilityLitterExact", Boolean.TRUE)
                    .put("legacySpawnRngTransitionEventResourceExact", Boolean.TRUE)
                    .put("preyPredatorLifecycleStates", Long.valueOf(0L))
                    .put("preyKillCreditLedgers", Long.valueOf(0L));
            case "BR1P-F13-position-kernel-unchanged":
                return expected
                    .put("dueTokenSolePositionOwnerSurface", Boolean.TRUE)
                    .put("onlyDueOrdinalsClaim", Boolean.TRUE)
                    .put("childBeforeCommittedWinner", Long.valueOf(0L))
                    .put("resolveCount", Long.valueOf(1L))
                    .put("commitCount", Long.valueOf(1L))
                    .put("priorityFallbackSaltWaitingDebtExact", Boolean.TRUE)
                    .put("resourceStateInsideResolver", Long.valueOf(0L));
            case "BR1P-F14-fingerprint-complete":
                return expected
                    .put("completeFingerprintSurface", Boolean.TRUE)
                    .put("policySha256", FrozenPolicy.SHA256)
                    .put("components",
                        "policy,lifecycle,credit,escrow,position,rng,id,transition,event,ordinal,registry")
                    .put("omissionFailsClosed", Boolean.TRUE)
                    .put("corruptionFailsClosed", Boolean.TRUE)
                    .put("registrySchemaShared", Boolean.TRUE)
                    .put("preyLegacyProjection", Boolean.TRUE)
                    .put("specialCaseBranches", Long.valueOf(0L));
            default:
                throw new IllegalArgumentException("unknown BR1-P case: " + id);
        }
    }

    private static void requireExactObservation(String id,
                                                Observation expected,
                                                Observation observed)
    {
        if(!expected.keys().equals(observed.keys())) {
            throw new AssertionError("EXPECTED_RED " + id +
                " field=key-set expected=" + expected.keys() +
                " actual=" + observed.keys());
        }
        for(String key : expected.keys()) {
            Object expectedValue = expected.get(key);
            Object actualValue = observed.get(key);
            if(!Objects.equals(expectedValue, actualValue)) {
                throw new AssertionError("EXPECTED_RED " + id +
                    " field=" + key +
                    " expected=" + render(expectedValue) +
                    " actual=" + render(actualValue));
            }
        }
    }

    private static ProbeResult probePredatorAdapters()
    {
        String actual = observedPredatorAdapters();
        return new ProbeResult(actual,
            "SpeciesEcologyFactRegistry.capture predator adapters=" + actual);
    }

    private static ProbeResult probeConceptionLedgerSurface()
    {
        boolean actual = hasMethod(BreedingRandomLedger.class,
            "hasConceptionRoll") &&
            hasMethod(BreedingRandomLedger.class, "getConceptionRollCount");
        BreedingRandomLedger legacy = new BreedingRandomLedger(
            false, 0, false, false, 0);
        return new ProbeResult(Boolean.valueOf(actual),
            "BreedingRandomLedger conception surface=" + actual +
            ",legacyRoll=" + legacy.hasLegacyBreedingRoll());
    }

    private static ProbeResult probeKillCreditLedgerSurface()
    {
        boolean actual = classPresent("savanna.PredatorKillCreditLedger");
        return new ProbeResult(Boolean.valueOf(actual),
            "runtime class savanna.PredatorKillCreditLedger present=" + actual);
    }

    private static ProbeResult probeConceptionLifecycleSurface()
    {
        boolean actual = classPresent("savanna.PredatorConceptionAssessment") &&
            hasLifecycleStateSurface();
        return new ProbeResult(Boolean.valueOf(actual),
            "conception assessment plus lifecycle state surface=" + actual);
    }

    private static ProbeResult probePendingBirthTokenSurface()
    {
        boolean actual = pendingBirthTokenSurface();
        return new ProbeResult(Boolean.valueOf(actual),
            "PendingBirthToken runtime/PositionClaim surface=" + actual);
    }

    private static ProbeResult probeLifecycleStates()
    {
        String actual = observedLifecycleStates();
        return new ProbeResult(actual,
            "SavannahAnimal lifecycle enum constants=" + actual);
    }

    private static ProbeResult probeNewbornReserves()
    {
        String actual = actualNewbornReserves();
        return new ProbeResult(actual,
            "createChild runtime food/hydration/stamina reserves=" + actual);
    }

    private static ProbeResult probePositionClaimPendingToken()
    {
        boolean actual = typeMentions(PositionClaim.class,
            "PendingBirthToken");
        return new ProbeResult(Boolean.valueOf(actual),
            "PositionClaim member type PendingBirthToken=" + actual);
    }

    private static ProbeResult probeIndependentOrdinalSurface()
    {
        boolean actual = classPresent("savanna.PendingBirthToken") &&
            classPresent("savanna.PredatorPregnancyState") &&
            typeMentions(SavannahAnimal.class, "PredatorPregnancyState");
        return new ProbeResult(Boolean.valueOf(actual),
            "production pending token plus per-mother pregnancy state=" + actual);
    }

    private static ProbeResult probePregnancyLossSurface()
    {
        boolean actual = hasSemanticMethod(SavannahAnimal.class,
            "pregnancy", "loss") ||
            hasSemanticMethod(SimulationContext.class,
                "pregnancy", "loss");
        return new ProbeResult(Boolean.valueOf(actual),
            "production maternal pregnancy-loss operation=" + actual);
    }

    private static ProbeResult probeHashBoundRegistryDispatch()
    {
        String actual = observedPredatorAdapters() + "|policy=" +
            observedPolicyHash();
        return new ProbeResult(actual,
            "registry adapters and runtime policy identity=" + actual);
    }

    private static ProbeResult probePreyBehaviorDispatch()
    {
        boolean actual = parameterOrReturnMentions(BreedingSystem.class,
            SpeciesReproductionAdapter.class) ||
            parameterOrReturnMentions(MateFindingBreedingSystem.class,
                SpeciesReproductionAdapter.class) ||
            parameterOrReturnMentions(ActionExecutor.class,
                SpeciesReproductionAdapter.class);
        return new ProbeResult(Boolean.valueOf(actual),
            "production breeding behavior dispatch accepts/returns SpeciesReproductionAdapter=" +
            actual + ",facts=" + observedPreyAdapters());
    }

    private static ProbeResult probeDueTokenPositionOwner()
    {
        boolean actual = pendingBirthTokenSurface() &&
            typeMentions(PositionStepCoordinator.class,
                "PendingBirthToken");
        return new ProbeResult(Boolean.valueOf(actual),
            "due pending token integrated with PositionStepCoordinator=" + actual);
    }

    private static ProbeResult probeCompleteFingerprint()
    {
        boolean actual = classPresent("savanna.PredatorReproductionFingerprint") ||
            hasSemanticMethod(SimulationContext.class,
                "reproduction", "fingerprint");
        return new ProbeResult(Boolean.valueOf(actual),
            "typed policy/lifecycle/credit/escrow fingerprint surface=" + actual);
    }

    private static String predatorAdapters(String adapter)
    {
        List<String> entries = new ArrayList<>();
        for(SpeciesEcologyFactProfile profile :
            SpeciesEcologyFactRegistry.capture().getProfiles()) {
            if(profile.isPredator()) {
                entries.add(profile.getSpeciesKey() + "=" + adapter);
            }
        }
        return String.join(",", entries);
    }

    private static String observedPredatorAdapters()
    {
        List<String> entries = new ArrayList<>();
        for(SpeciesEcologyFactProfile profile :
            SpeciesEcologyFactRegistry.capture().getProfiles()) {
            if(profile.isPredator()) {
                entries.add(profile.getSpeciesKey() + "=" +
                    profile.getReproductionAdapter().name());
            }
        }
        return String.join(",", entries);
    }

    private static String preyAdapters(String adapter)
    {
        List<String> entries = new ArrayList<>();
        for(SpeciesEcologyFactProfile profile :
            SpeciesEcologyFactRegistry.capture().getProfiles()) {
            if(!profile.isPredator()) {
                entries.add(profile.getSpeciesKey() + "=" + adapter);
            }
        }
        return String.join(",", entries);
    }

    private static String observedPreyAdapters()
    {
        List<String> entries = new ArrayList<>();
        for(SpeciesEcologyFactProfile profile :
            SpeciesEcologyFactRegistry.capture().getProfiles()) {
            if(!profile.isPredator()) {
                entries.add(profile.getSpeciesKey() + "=" +
                    profile.getReproductionAdapter().name());
            }
        }
        return String.join(",", entries);
    }

    private static String frozenPolicyDispatch()
    {
        return predatorAdapters("PREDATOR_POLICY") + "|policy=" +
            FrozenPolicy.SHA256;
    }

    private static String frozenFactOnlyDispatch()
    {
        return predatorAdapters("PREDATOR_FACT_ONLY") + "|policy=<absent>";
    }

    private static String frozenNewbornReserveExpectation()
    {
        return "Lion=" + FrozenPolicy.LION_JUVENILE_FOOD + "/" +
            FrozenPolicy.JUVENILE_HYDRATION_MICROS + "/" +
            FrozenPolicy.JUVENILE_STAMINA_MICROS + ";Cheetah=" +
            FrozenPolicy.CHEETAH_JUVENILE_FOOD + "/" +
            FrozenPolicy.JUVENILE_HYDRATION_MICROS + "/" +
            FrozenPolicy.JUVENILE_STAMINA_MICROS;
    }

    private static String fullNewbornReserveExpectation()
    {
        SpeciesProfile lion = SpeciesRegistry.getProfile(SpeciesRegistry.LION);
        SpeciesProfile cheetah = SpeciesRegistry.getProfile(
            SpeciesRegistry.CHEETAH);
        return "Lion=" + lion.getMaxFoodLevel() + "/" +
            Math.round(SavannahAnimal.MAX_HYDRATION *
                FrozenPolicy.MICROS_PER_UNIT) + "/" +
            Math.round(SavannahAnimal.MAX_STAMINA *
                FrozenPolicy.MICROS_PER_UNIT) + ";Cheetah=" +
            cheetah.getMaxFoodLevel() + "/" +
            Math.round(SavannahAnimal.MAX_HYDRATION *
                FrozenPolicy.MICROS_PER_UNIT) + "/" +
            Math.round(SavannahAnimal.MAX_STAMINA *
                FrozenPolicy.MICROS_PER_UNIT);
    }

    private static String actualNewbornReserves()
    {
        ScriptedRandom random = new ScriptedRandom();
        AnimalIdentityAllocator identities = new AnimalIdentityAllocator();
        SavannahAnimal lionParent = new Lion(false,
            new Location(0, 0), random, identities);
        SavannahAnimal cheetahParent = new Cheetah(false,
            new Location(1, 0), random, identities);
        SavannahAnimal lionChild = lionParent.createChild(
            new Location(0, 1));
        SavannahAnimal cheetahChild = cheetahParent.createChild(
            new Location(1, 1));
        return childResource("Lion", lionChild) + ";" +
            childResource("Cheetah", cheetahChild);
    }

    private static String childResource(String species,
                                        SavannahAnimal child)
    {
        return species + "=" + child.getFoodLevel() + "/" +
            Math.round(child.getHydrationPercent() *
                FrozenPolicy.MICROS_PER_UNIT) + "/" +
            Math.round(child.getStaminaPercent() *
                FrozenPolicy.MICROS_PER_UNIT);
    }

    private static boolean pendingBirthTokenSurface()
    {
        return classPresent("savanna.PendingBirthToken") &&
            typeMentions(PositionClaim.class, "PendingBirthToken");
    }

    private static boolean hasLifecycleStateSurface()
    {
        return !"<absent>".equals(observedLifecycleStates());
    }

    private static String observedLifecycleStates()
    {
        for(Field field : SavannahAnimal.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            if(type.isEnum() && hasLifecycleConstants(type)) {
                return lifecycleConstants(type);
            }
        }
        for(Method method : SavannahAnimal.class.getDeclaredMethods()) {
            Class<?> type = method.getReturnType();
            if(type.isEnum() && hasLifecycleConstants(type)) {
                return lifecycleConstants(type);
            }
        }
        return "<absent>";
    }

    private static boolean hasLifecycleConstants(Class<?> type)
    {
        List<String> names = new ArrayList<>();
        for(Object constant : type.getEnumConstants()) {
            names.add(((Enum<?>)constant).name());
        }
        return names.contains("OPEN") && names.contains("GESTATING") &&
            names.contains("POSTPARTUM");
    }

    private static String lifecycleConstants(Class<?> type)
    {
        List<String> names = new ArrayList<>();
        for(Object constant : type.getEnumConstants()) {
            String name = ((Enum<?>)constant).name();
            if(name.equals("OPEN") || name.equals("GESTATING") ||
               name.equals("POSTPARTUM")) {
                names.add(name);
            }
        }
        return String.join(",", names);
    }

    private static String observedPolicyHash()
    {
        String[] candidates = {
            "savanna.PredatorReproductionPolicy",
            "savanna.PredatorReproductionPolicyProfile"
        };
        for(String name : candidates) {
            try {
                Class<?> type = Class.forName(name, false,
                    Br1pExpectedRedTests.class.getClassLoader());
                for(Field field : type.getDeclaredFields()) {
                    if(Modifier.isStatic(field.getModifiers()) &&
                       field.getType() == String.class &&
                       field.getName().toLowerCase(Locale.ROOT)
                           .contains("hash")) {
                        try {
                            if(!field.canAccess(null)) field.setAccessible(true);
                            Object value = field.get(null);
                            if(value instanceof String) return (String)value;
                        }
                        catch(ReflectiveOperationException |
                              RuntimeException ignored) {
                            return "<unreadable>";
                        }
                    }
                }
            }
            catch(ClassNotFoundException | LinkageError ignored) {
                // An absent future policy surface is a typed predecessor fact.
            }
        }
        return "<absent>";
    }

    private static boolean hasMethod(Class<?> type, String methodName)
    {
        for(Method method : type.getDeclaredMethods()) {
            if(method.getName().equals(methodName)) return true;
        }
        return false;
    }

    private static boolean hasSemanticMethod(Class<?> type,
                                             String... fragments)
    {
        for(Method method : type.getDeclaredMethods()) {
            String normalized = method.getName().toLowerCase(Locale.ROOT);
            boolean all = true;
            for(String fragment : fragments) {
                all &= normalized.contains(
                    fragment.toLowerCase(Locale.ROOT));
            }
            if(all) return true;
        }
        return false;
    }

    private static boolean parameterOrReturnMentions(Class<?> owner,
                                                     Class<?> semanticType)
    {
        for(Method method : owner.getDeclaredMethods()) {
            if(method.getReturnType() == semanticType) return true;
            for(Class<?> parameter : method.getParameterTypes()) {
                if(parameter == semanticType) return true;
            }
        }
        return false;
    }

    private static boolean typeMentions(Class<?> owner,
                                        String simpleTypeName)
    {
        for(Field field : owner.getDeclaredFields()) {
            if(field.getType().getSimpleName().equals(simpleTypeName)) {
                return true;
            }
        }
        for(Method method : owner.getDeclaredMethods()) {
            if(method.getReturnType().getSimpleName().equals(simpleTypeName)) {
                return true;
            }
            for(Class<?> parameter : method.getParameterTypes()) {
                if(parameter.getSimpleName().equals(simpleTypeName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean classPresent(String className)
    {
        try {
            Class.forName(className, false,
                Br1pExpectedRedTests.class.getClassLoader());
            return true;
        }
        catch(ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static String render(Object value)
    {
        return String.valueOf(value).replace(' ', '_');
    }

    private enum Mode
    {
        SELF_PASS,
        FAULT,
        BASELINE
    }

    private static final class CaseDefinition
    {
        private final String id;
        private final String anchorField;
        private final Supplier<Object> frozenFaultValue;
        private final Supplier<ProbeResult> predecessorProbe;

        private CaseDefinition(String id, String anchorField,
                               Supplier<Object> frozenFaultValue,
                               Supplier<ProbeResult> predecessorProbe)
        {
            this.id = id;
            this.anchorField = anchorField;
            this.frozenFaultValue = frozenFaultValue;
            this.predecessorProbe = predecessorProbe;
        }
    }

    private static final class ProbeResult
    {
        private final Object actual;
        private final String evidence;

        private ProbeResult(Object actual, String evidence)
        {
            this.actual = actual;
            this.evidence = evidence;
        }
    }

    private static final class Observation
    {
        private final LinkedHashMap<String, Object> values =
            new LinkedHashMap<>();

        private Observation put(String key, Object value)
        {
            values.put(key, value);
            return this;
        }

        private Object get(String key)
        {
            return values.get(key);
        }

        private List<String> keys()
        {
            return new ArrayList<>(values.keySet());
        }

        private int size()
        {
            return values.size();
        }

        private Observation copy()
        {
            Observation copy = new Observation();
            copy.values.putAll(values);
            return copy;
        }
    }

    private static final class ScriptedRandom extends Random
    {
        private ScriptedRandom()
        {
            super(0L);
        }

        @Override
        public int nextInt(int bound)
        {
            if(bound <= 0) throw new IllegalArgumentException("bound");
            return 0;
        }

        @Override
        public double nextDouble()
        {
            return 0.0;
        }
    }

    /** The only block containing approved BR1-P policy constants. */
    private static final class FrozenPolicy
    {
        private static final String SHA256 =
            "e02f067d4700b6327cdfe9b790754cbf1baf9fc3e1dda4db429c1c749b777a4f";
        private static final int KILL_CREDIT_WINDOW_STEPS = 60;
        private static final int CREDIT_DELAY_STEPS = 1;
        private static final int LOCAL_RESOURCE_FLOOR = 34;
        private static final int CONCEPTION_CREDIT_DEBIT = 68;
        private static final int CAPACITY_DENSITY_CEILING_MICROS = 250000;
        private static final int GESTATION_STEPS = 12;
        private static final int POSTPARTUM_STEPS = 12;
        private static final int PENDING_HORIZON_STEPS = 12;
        private static final int LION_JUVENILE_FOOD = 64;
        private static final int CHEETAH_JUVENILE_FOOD = 38;
        private static final int JUVENILE_HYDRATION_MICROS = 50000000;
        private static final int JUVENILE_STAMINA_MICROS = 50000000;
        private static final int MATERNAL_HYDRATION_FLOOR_MICROS = 30000000;
        private static final int MATERNAL_STAMINA_FLOOR_MICROS = 30000000;
        private static final int CONCEPTION_INVESTMENT_STAMINA_MICROS = 1200000;
        private static final long MICROS_PER_UNIT = 1000000L;
    }
}
