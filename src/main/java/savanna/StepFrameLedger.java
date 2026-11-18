package savanna;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * One immutable, actor-local S0-S4 causality ledger.
 *
 * <p>The mutable {@link FrameDraft} is package-private and never published. It
 * enforces phase order while the existing R2 position board defers placement;
 * listeners receive exactly one immutable ledger after S4.</p>
 */
public final class StepFrameLedger
{
    private static final Comparator<OrdinalOutcome> OUTCOME_ORDER =
        Comparator.comparingInt(OrdinalOutcome::getLitterOrdinal);
    private static final List<Phase> EXACT_PHASES = List.of(
        Phase.S0_CAPTURE,
        Phase.S1_REPRODUCTION_PROPOSAL,
        Phase.S2_PRIMARY_ACTION,
        Phase.S3_REPRODUCTION_COMMIT,
        Phase.S4_PUBLISH);

    private String ledgerId;
    private final String frameId;
    private final int step;
    private final long actorId;
    private final String actorSpecies;
    private final List<Phase> phases;
    private final AnimalPhysiologyFact frameEntryPhysiology;
    private final ResourceClock resourceClock;
    private final HuntOpportunityFact huntOpportunity;
    private final ReproductionProposal reproductionProposal;
    private final PrimaryAction primaryAction;
    private final ReproductionCommit reproductionCommit;
    private final PositionResolution adultPositionResolution;
    private final List<OrdinalOutcome> ordinalOutcomes;
    private final AnimalPhysiologyFact finalPhysiology;
    private final TerminalReason terminalReason;

    private StepFrameLedger(FrameDraft draft,
                            PositionResolution adultPositionResolution,
                            List<OrdinalOutcome> ordinalOutcomes,
                            AnimalPhysiologyFact finalPhysiology,
                            TerminalReason terminalReason)
    {
        if(draft == null || draft.resourceClock == null ||
           draft.huntOpportunity == null ||
           draft.reproductionProposal == null ||
           draft.primaryAction == null || draft.reproductionCommit == null ||
           finalPhysiology == null || terminalReason == null) {
            throw new IllegalStateException(
                "S0-S4 frame facts must be complete before publication");
        }
        frameId = draft.frameId;
        step = draft.step;
        actorId = draft.actorId;
        actorSpecies = draft.actorSpecies;
        phases = EXACT_PHASES;
        frameEntryPhysiology = draft.frameEntryPhysiology;
        resourceClock = draft.resourceClock;
        huntOpportunity = draft.huntOpportunity;
        reproductionProposal = draft.reproductionProposal;
        primaryAction = draft.primaryAction;
        reproductionCommit = draft.reproductionCommit;
        this.adultPositionResolution = adultPositionResolution;
        List<OrdinalOutcome> copied = ordinalOutcomes == null
            ? new ArrayList<OrdinalOutcome>()
            : new ArrayList<>(ordinalOutcomes);
        // A list of fewer than two outcomes is already sorted; skip the sort machinery.
        if(copied.size() > 1) {
            copied.sort(OUTCOME_ORDER);
        }
        this.ordinalOutcomes = Collections.unmodifiableList(copied);
        this.finalPhysiology = finalPhysiology;
        this.terminalReason = terminalReason;
        if(RuntimeAudit.enabled()) {
            validateJoins();
        }
    }

    public static String frameId(int step, long actorId)
    {
        if(step < 0 || actorId <= 0L) {
            throw new IllegalArgumentException(
                "non-negative step and positive actor ID are required");
        }
        return "step-" + step + "-actor-" + actorId;
    }

    public String getLedgerId()
    {
        if(ledgerId == null) {
            ledgerId = HuntOpportunityFact.sha256(frameId + "|" +
                huntOpportunity.getOpportunityId() + "|" +
                reproductionProposal.getProposalId() + "|" +
                primaryAction.identityText() + "|" +
                reproductionCommit.getDisposition() + "|" +
                ordinalDigest(ordinalOutcomes) + "|" + terminalReason);
        }
        return ledgerId;
    }
    public String getFrameId() { return frameId; }
    public int getStep() { return step; }
    public long getActorId() { return actorId; }
    public String getActorSpecies() { return actorSpecies; }
    public List<Phase> getPhases() { return phases; }
    public AnimalPhysiologyFact getFrameEntryPhysiology()
    {
        return frameEntryPhysiology;
    }
    public ResourceClock getResourceClock() { return resourceClock; }
    public HuntOpportunityFact getHuntOpportunity() { return huntOpportunity; }
    public ReproductionProposal getReproductionProposal()
    {
        return reproductionProposal;
    }
    public PrimaryAction getPrimaryAction() { return primaryAction; }
    public ReproductionCommit getReproductionCommit()
    {
        return reproductionCommit;
    }
    public PositionResolution getAdultPositionResolution()
    {
        return adultPositionResolution;
    }
    public List<OrdinalOutcome> getOrdinalOutcomes()
    {
        return ordinalOutcomes;
    }
    public AnimalPhysiologyFact getFinalPhysiology() { return finalPhysiology; }
    public TerminalReason getTerminalReason() { return terminalReason; }

    private void validateJoins()
    {
        requireFrame(resourceClock.getFrameId(), resourceClock.getActorId(),
            resourceClock.getStep(), "resource clock");
        requireFrame(huntOpportunity.getFrameId(),
            huntOpportunity.getActorId(), huntOpportunity.getStep(),
            "hunt opportunity");
        requireFrame(reproductionProposal.getFrameId(),
            reproductionProposal.getActorId(), reproductionProposal.getStep(),
            "reproduction proposal");
        requireFrame(primaryAction.getFrameId(), primaryAction.getActorId(),
            primaryAction.getStep(), "primary action");
        requireFrame(reproductionCommit.getFrameId(),
            reproductionCommit.getActorId(), reproductionCommit.getStep(),
            "reproduction commit");
        if(reproductionProposal.getResourceClock() != resourceClock &&
           !resourceClock.getClockId().equals(
               reproductionProposal.getResourceClockId())) {
            throw new IllegalStateException(
                "S1/S3 reproduction facts do not join to S0");
        }
        if(reproductionCommit.getProposal() != reproductionProposal &&
           !reproductionProposal.getProposalId().equals(
               reproductionCommit.getProposalId())) {
            throw new IllegalStateException(
                "S1/S3 reproduction facts do not join to S0");
        }
        HuntOpportunityFact resultOpportunity =
            primaryAction.getHuntOpportunityFact();
        if(resultOpportunity != null &&
           !huntOpportunity.hasSameIdentity(resultOpportunity)) {
            throw new IllegalStateException(
                "S2 did not consume the exact S0 HUNT opportunity");
        }
        if(ordinalOutcomes.size() != reproductionProposal.getClaimCount()) {
            throw new IllegalStateException(
                "S4 must resolve every provisional reproduction ordinal");
        }
        for(int index = 0; index < ordinalOutcomes.size(); index++) {
            OrdinalOutcome outcome = ordinalOutcomes.get(index);
            if(outcome.getParentId() != actorId ||
               outcome.getLitterOrdinal() !=
                   reproductionProposal.getLitterOrdinals().get(index)
                       .intValue()) {
                throw new IllegalStateException(
                    "S4 reproduction ordinal does not join to S1");
            }
        }
    }

    private void requireFrame(String actualFrame, long actualActor,
                              int actualStep, String label)
    {
        if(!frameId.equals(actualFrame) || actorId != actualActor ||
           step != actualStep) {
            throw new IllegalStateException(label +
                " does not belong to the published frame");
        }
    }

    private static String ordinalDigest(List<OrdinalOutcome> values)
    {
        StringBuilder result = new StringBuilder();
        for(OrdinalOutcome value : values) {
            result.append(value.parentId).append(':')
                .append(value.litterOrdinal).append(':')
                .append(value.disposition).append(':')
                .append(value.positionReason).append(':')
                .append(value.destination).append('|');
        }
        return result.toString();
    }

    /** Fixed, versioned phase order. */
    public enum Phase
    {
        S0_CAPTURE,
        S1_REPRODUCTION_PROPOSAL,
        S2_PRIMARY_ACTION,
        S3_REPRODUCTION_COMMIT,
        S4_PUBLISH
    }

    public enum ProposalReason
    {
        PROVISIONAL_CLAIMS,
        /** Paid conception decided in S1; the debit happens at commit. */
        CONCEPTION,
        /** Pregnant and not yet due: no conception, no birth claims. */
        PREGNANT,
        NO_MATE,
        PROBABILITY_REJECTED,
        NOT_ELIGIBLE,
        NO_POSITION_CANDIDATE,
        WATER_SUPPRESSED,
        WATER_DEPARTURE,
        ACTOR_TERMINAL,
        NOT_REQUESTED
    }

    public enum CommitDisposition
    {
        SUBMITTED_TO_POSITION_BOARD,
        NO_PROVISIONAL_CLAIMS,
        CANCELLED_PARENT_TERMINAL
    }

    public enum OrdinalDisposition
    {
        COMMITTED_BIRTH,
        LOST_POSITION_RESOLUTION,
        CANCELLED_PARENT_TERMINAL,
        PREGNANCY_LOSS
    }

    public enum TerminalReason
    {
        COMPLETED,
        DEAD_BEFORE_ACTION,
        DEAD_AFTER_ACTION,
        WATER_DEPARTURE
    }

    /** S0 policy-neutral resource clock; same-step kills are never eligible. */
    public static final class ResourceClock
    {
        private String clockId;
        private final String frameId;
        private final int step;
        private final long actorId;
        private final AnimalPhysiologyFact physiology;
        private final int realizedKillCreditThroughStep;

        private ResourceClock(int step, SavannahAnimal actor, String frameId)
        {
            if(actor == null || frameId == null || frameId.isBlank()) {
                throw new IllegalArgumentException(
                    "resource clock actor and frame are required");
            }
            this.frameId = frameId;
            this.step = step;
            actorId = actor.getId();
            physiology = AnimalPhysiologyFact.capture(actor);
            realizedKillCreditThroughStep = step - 1;
        }

        static ResourceClock capture(int step, SavannahAnimal actor,
                                     String frameId)
        {
            return new ResourceClock(step, actor, frameId);
        }

        public String getClockId()
        {
            if(clockId == null) {
                clockId = HuntOpportunityFact.sha256(frameId + "|resource-s0|" +
                    physiology.getFood() + '|' +
                    Double.doubleToLongBits(physiology.getHydration()) + '|' +
                    Double.doubleToLongBits(physiology.getStamina()) + '|' +
                    realizedKillCreditThroughStep);
            }
            return clockId;
        }
        public String getFrameId() { return frameId; }
        public int getStep() { return step; }
        public long getActorId() { return actorId; }
        public AnimalPhysiologyFact getPhysiology() { return physiology; }
        public int getRealizedKillCreditThroughStep()
        {
            return realizedKillCreditThroughStep;
        }
        public boolean mayConsumeRealizedKillFromStep(int killStep)
        {
            return killStep >= 0 && killStep <= realizedKillCreditThroughStep;
        }
    }

    /** Immutable S1 legacy proposal; it owns tokens, never child objects. */
    public static final class ReproductionProposal
    {
        private String proposalId;
        private final String frameId;
        private final int step;
        private final long actorId;
        private final ResourceClock resourceClock;
        private String resourceClockId;
        private final ProposalReason reason;
        private final List<PositionClaim> claims;
        private final List<Integer> litterOrdinals;
        private final LegacyBreedingAssessment legacyAssessment;
        private final AnimalPhysiologyFact before;
        private final AnimalPhysiologyFact after;
        private final int conceptionLitter;

        private ReproductionProposal(String frameId, int step, long actorId,
                                     ResourceClock resourceClock,
                                     ProposalReason reason,
                                     List<PositionClaim> claims,
                                     LegacyBreedingAssessment assessment,
                                     AnimalPhysiologyFact before,
                                     AnimalPhysiologyFact after,
                                     int conceptionLitter)
        {
            if(frameId == null || frameId.isBlank() || actorId <= 0L ||
               resourceClock == null || reason == null || claims == null ||
               before == null || after == null ||
               !samePhysiology(before, after)) {
                throw new IllegalStateException(
                    "S1 proposal must be complete and physiology-pure");
            }
            if((reason == ProposalReason.CONCEPTION) != (conceptionLitter > 0)) {
                throw new IllegalArgumentException(
                    "S1 conception litter and reason disagree");
            }
            this.conceptionLitter = conceptionLitter;
            this.frameId = frameId;
            this.step = step;
            this.actorId = actorId;
            this.resourceClock = resourceClock;
            this.reason = reason;
            this.before = before;
            this.after = after;
            legacyAssessment = assessment;
            List<PositionClaim> copied = new ArrayList<>(claims);
            List<Integer> ordinals = new ArrayList<>();
            for(PositionClaim claim : copied) {
                if(claim == null || !claim.getActor().isSpawn() ||
                   claim.getActor().getParentId() != actorId) {
                    throw new IllegalArgumentException(
                        "S1 reproduction claims must belong to one parent");
                }
                ordinals.add(Integer.valueOf(
                    claim.getActor().getLitterOrdinal()));
            }
            if(ordinals.size() > 1) {
                ordinals.sort(Integer::compareTo);
            }
            if(!allUnique(ordinals) ||
               (copied.isEmpty() && reason == ProposalReason.PROVISIONAL_CLAIMS) ||
               (!copied.isEmpty() && reason != ProposalReason.PROVISIONAL_CLAIMS)) {
                throw new IllegalArgumentException(
                    "S1 reason and provisional ordinals disagree");
            }
            this.claims = Collections.unmodifiableList(copied);
            litterOrdinals = Collections.unmodifiableList(ordinals);
        }

        static ReproductionProposal create(String frameId, int step,
                                           SavannahAnimal actor,
                                           ResourceClock resourceClock,
                                           ProposalReason reason,
                                           List<PositionClaim> claims,
                                           LegacyBreedingAssessment assessment,
                                           AnimalPhysiologyFact before,
                                           AnimalPhysiologyFact after)
        {
            return new ReproductionProposal(frameId, step, actor.getId(),
                resourceClock, reason, claims, assessment, before, after, 0);
        }

        static ReproductionProposal conception(String frameId, int step,
                                               SavannahAnimal actor,
                                               ResourceClock resourceClock,
                                               int litter,
                                               LegacyBreedingAssessment assessment,
                                               AnimalPhysiologyFact before,
                                               AnimalPhysiologyFact after)
        {
            return new ReproductionProposal(frameId, step, actor.getId(),
                resourceClock, ProposalReason.CONCEPTION,
                Collections.<PositionClaim>emptyList(), assessment, before,
                after, litter);
        }

        static ReproductionProposal none(String frameId, int step,
                                         SavannahAnimal actor,
                                         ResourceClock resourceClock,
                                         ProposalReason reason)
        {
            AnimalPhysiologyFact physiology =
                AnimalPhysiologyFact.capture(actor);
            return create(frameId, step, actor, resourceClock, reason,
                Collections.<PositionClaim>emptyList(), null, physiology,
                physiology);
        }

        ResourceClock getResourceClock() { return resourceClock; }

        public String getProposalId()
        {
            if(proposalId == null) {
                proposalId = HuntOpportunityFact.sha256(frameId + "|s1|" +
                    getResourceClockId() + '|' + reason + '|' + claimDigest(claims) +
                    '|' + conceptionLitter);
            }
            return proposalId;
        }

        public String getFrameId() { return frameId; }
        public int getStep() { return step; }
        public long getActorId() { return actorId; }

        public String getResourceClockId()
        {
            if(resourceClockId == null) {
                resourceClockId = resourceClock.getClockId();
            }
            return resourceClockId;
        }

        public ProposalReason getReason() { return reason; }
        public int getClaimCount() { return claims.size(); }
        public int getConceptionLitter() { return conceptionLitter; }
        public List<Integer> getLitterOrdinals() { return litterOrdinals; }
        public LegacyBreedingAssessment getLegacyAssessment()
        {
            return legacyAssessment;
        }
        public AnimalPhysiologyFact getBeforePhysiology() { return before; }
        public AnimalPhysiologyFact getAfterPhysiology() { return after; }
        List<PositionClaim> getPositionClaims()
        {
            return new ArrayList<>(claims);
        }

        private static String claimDigest(List<PositionClaim> values)
        {
            StringBuilder result = new StringBuilder();
            for(PositionClaim value : values) {
                result.append(value.getActor()).append(':')
                    .append(value.getRequested()).append(':')
                    .append(value.getExplicitFallbacks()).append(':')
                    .append(value.getSearchOrigin()).append(':')
                    .append(value.getSearchCandidateBudget()).append('|');
            }
            return result.toString();
        }
    }

    /** Immutable S2 primary-action result. */
    public static final class PrimaryAction
    {
        private final String frameId;
        private final int step;
        private final long actorId;
        private final ActionType requestedAction;
        private final boolean executed;
        private final Location destination;
        private final HuntResult huntResult;

        private PrimaryAction(String frameId, int step, long actorId,
                              ActionType requestedAction, boolean executed,
                              Location destination, HuntResult huntResult)
        {
            if(frameId == null || frameId.isBlank() || actorId <= 0L) {
                throw new IllegalArgumentException(
                    "complete S2 primary identity is required");
            }
            this.frameId = frameId;
            this.step = step;
            this.actorId = actorId;
            this.requestedAction = requestedAction;
            this.executed = executed;
            this.destination = copy(destination);
            this.huntResult = huntResult;
        }

        static PrimaryAction skipped(String frameId, int step,
                                     SavannahAnimal actor,
                                     ActionType requestedAction)
        {
            return new PrimaryAction(frameId, step, actor.getId(),
                requestedAction, false, null, null);
        }

        static PrimaryAction hunt(String frameId, int step,
                                  SavannahAnimal actor, HuntResult result)
        {
            return new PrimaryAction(frameId, step, actor.getId(),
                ActionType.HUNT, true, result == null ? null :
                result.getDestination(), result);
        }

        static PrimaryAction graze(String frameId, int step,
                                   SavannahAnimal actor, Location destination)
        {
            return new PrimaryAction(frameId, step, actor.getId(),
                ActionType.GRAZE, true, destination, null);
        }

        static PrimaryAction nonFood(String frameId, int step,
                                     SavannahAnimal actor,
                                     ActionType requestedAction,
                                     boolean executed, Location destination)
        {
            return new PrimaryAction(frameId, step, actor.getId(),
                requestedAction, executed, destination, null);
        }

        public String getFrameId() { return frameId; }
        public int getStep() { return step; }
        public long getActorId() { return actorId; }
        public ActionType getRequestedAction() { return requestedAction; }
        public boolean isExecuted() { return executed; }
        public Location getDestination() { return copy(destination); }
        public HuntResult getHuntResult() { return huntResult; }
        public HuntOpportunityFact getHuntOpportunityFact()
        {
            return huntResult == null ? null : huntResult.getOpportunityFact();
        }

        private String identityText()
        {
            return requestedAction + "|" + executed + "|" + destination + "|" +
                (huntResult == null ? "NONE" : huntResult.getOutcome());
        }
    }

    /** Immutable S3 submission fact. No physiology or pregnancy mutation occurs. */
    public static final class ReproductionCommit
    {
        private final String frameId;
        private final int step;
        private final long actorId;
        private final ReproductionProposal proposal;
        private String proposalId;
        private final CommitDisposition disposition;

        private ReproductionCommit(ReproductionProposal proposal,
                                   CommitDisposition disposition)
        {
            this.proposal = proposal;
            frameId = proposal.getFrameId();
            step = proposal.getStep();
            actorId = proposal.getActorId();
            this.disposition = disposition;
        }

        ReproductionProposal getProposal() { return proposal; }

        static ReproductionCommit submit(ReproductionProposal proposal,
                                         boolean parentAlive)
        {
            if(proposal == null) {
                throw new IllegalArgumentException(
                    "S3 requires the exact S1 proposal");
            }
            CommitDisposition disposition = !parentAlive
                ? CommitDisposition.CANCELLED_PARENT_TERMINAL
                : proposal.getClaimCount() == 0
                    ? CommitDisposition.NO_PROVISIONAL_CLAIMS
                    : CommitDisposition.SUBMITTED_TO_POSITION_BOARD;
            return new ReproductionCommit(proposal, disposition);
        }

        public String getFrameId() { return frameId; }
        public int getStep() { return step; }
        public long getActorId() { return actorId; }

        public String getProposalId()
        {
            if(proposalId == null) {
                proposalId = proposal != null ? proposal.getProposalId() : "";
            }
            return proposalId;
        }

        public CommitDisposition getDisposition() { return disposition; }
    }

    /** One explicit S4 outcome for every S1 provisional ordinal. */
    public static final class OrdinalOutcome
    {
        private final long parentId;
        private final int litterOrdinal;
        private final OrdinalDisposition disposition;
        private final PositionResolutionReason positionReason;
        private final Location destination;

        private OrdinalOutcome(long parentId, int litterOrdinal,
                               OrdinalDisposition disposition,
                               PositionResolutionReason positionReason,
                               Location destination)
        {
            if(parentId <= 0L || litterOrdinal < 0 || disposition == null) {
                throw new IllegalArgumentException(
                    "complete S4 ordinal identity is required");
            }
            this.parentId = parentId;
            this.litterOrdinal = litterOrdinal;
            this.disposition = disposition;
            this.positionReason = positionReason;
            this.destination = copy(destination);
        }

        static OrdinalOutcome fromResolution(PositionClaim claim,
                                             PositionResolution resolution)
        {
            PositionActorKey actor = claim.getActor();
            if(resolution == null) {
                return new OrdinalOutcome(actor.getParentId(),
                    actor.getLitterOrdinal(),
                    OrdinalDisposition.LOST_POSITION_RESOLUTION,
                    PositionResolutionReason.CLAIM_CANCELLED, null);
            }
            OrdinalDisposition disposition = resolution.isCommitted()
                ? OrdinalDisposition.COMMITTED_BIRTH
                : resolution.getReason() == PositionResolutionReason.CLAIM_CANCELLED
                    ? OrdinalDisposition.CANCELLED_PARENT_TERMINAL
                    : OrdinalDisposition.LOST_POSITION_RESOLUTION;
            return new OrdinalOutcome(actor.getParentId(),
                actor.getLitterOrdinal(), disposition, resolution.getReason(),
                resolution.getDestination());
        }

        /** Pure future-lifecycle fixture; FRAME-1 does not enable pregnancy. */
        public static OrdinalOutcome pregnancyLoss(long parentId,
                                                   int litterOrdinal)
        {
            return new OrdinalOutcome(parentId, litterOrdinal,
                OrdinalDisposition.PREGNANCY_LOSS, null, null);
        }

        public long getParentId() { return parentId; }
        public int getLitterOrdinal() { return litterOrdinal; }
        public OrdinalDisposition getDisposition() { return disposition; }
        public PositionResolutionReason getPositionReason()
        {
            return positionReason;
        }
        public Location getDestination() { return copy(destination); }
    }

    /** Internal ordered draft; it cannot be registered or observed. */
    static final class FrameDraft
    {
        private final int step;
        private final long actorId;
        private final String actorSpecies;
        private final String frameId;
        private final AnimalPhysiologyFact frameEntryPhysiology;
        private ResourceClock resourceClock;
        private HuntOpportunityFact huntOpportunity;
        private ReproductionProposal reproductionProposal;
        private PrimaryAction primaryAction;
        private ReproductionCommit reproductionCommit;
        private int nextPhase;
        private boolean published;

        private FrameDraft(int step, SavannahAnimal actor)
        {
            if(step < 0 || actor == null) {
                throw new IllegalArgumentException(
                    "frame draft step and actor are required");
            }
            this.step = step;
            actorId = actor.getId();
            actorSpecies = actor.getProfile().getName();
            frameId = StepFrameLedger.frameId(step, actorId);
            frameEntryPhysiology = AnimalPhysiologyFact.capture(actor);
        }

        static FrameDraft begin(int step, SavannahAnimal actor)
        {
            return new FrameDraft(step, actor);
        }

        void captureS0(SavannahAnimal actor, Location capturedOrigin,
                       InteractionGeometry geometry, SimulationContext context)
        {
            requirePhase(0);
            resourceClock = ResourceClock.capture(step, actor, frameId);
            huntOpportunity = HuntOpportunityFact.capture(step, actor, frameId,
                capturedOrigin, geometry, context);
            nextPhase = 1;
        }

        void recordS1(ReproductionProposal proposal)
        {
            requirePhase(1);
            reproductionProposal = proposal;
            nextPhase = 2;
        }

        void recordS2(PrimaryAction action)
        {
            requirePhase(2);
            if(action != null && action.getHuntOpportunityFact() != null &&
               !huntOpportunity.hasSameIdentity(
                   action.getHuntOpportunityFact())) {
                throw new IllegalStateException(
                    "S2 attempted to replace the S0 HUNT opportunity");
            }
            primaryAction = action;
            nextPhase = 3;
        }

        void recordS3(ReproductionCommit commit)
        {
            requirePhase(3);
            reproductionCommit = commit;
            nextPhase = 4;
        }

        StepFrameLedger publishS4(SavannahAnimal actor,
                                  PositionResolution adultResolution,
                                  List<OrdinalOutcome> outcomes,
                                  TerminalReason terminalReason)
        {
            requirePhase(4);
            if(published || actor == null || actor.getId() != actorId) {
                throw new IllegalStateException(
                    "one matching immutable ledger may be published once");
            }
            published = true;
            nextPhase = 5;
            return new StepFrameLedger(this, adultResolution, outcomes,
                AnimalPhysiologyFact.capture(actor), terminalReason);
        }

        String getFrameId() { return frameId; }
        ResourceClock getResourceClock() { return resourceClock; }
        HuntOpportunityFact getHuntOpportunity() { return huntOpportunity; }

        private void requirePhase(int expected)
        {
            if(published || nextPhase != expected) {
                throw new IllegalStateException(
                    "frame phase order violation: expected=" + expected +
                    " actual=" + nextPhase);
            }
        }
    }

    static List<OrdinalOutcome> resolveOrdinals(
        ReproductionProposal proposal, PositionPlan plan)
    {
        if(proposal == null || plan == null) {
            return Collections.emptyList();
        }
        List<OrdinalOutcome> outcomes = new ArrayList<>();
        for(PositionClaim claim : proposal.getPositionClaims()) {
            outcomes.add(OrdinalOutcome.fromResolution(claim,
                plan.getResolution(claim.getActor())));
        }
        if(outcomes.size() > 1) {
            outcomes.sort(OUTCOME_ORDER);
        }
        return outcomes;
    }

    private static boolean allUnique(List<Integer> values)
    {
        Integer previous = null;
        for(Integer value : values) {
            if(previous != null && previous.equals(value)) return false;
            previous = value;
        }
        return true;
    }

    private static boolean samePhysiology(AnimalPhysiologyFact first,
                                          AnimalPhysiologyFact second)
    {
        return first.getAge() == second.getAge() &&
            first.getFood() == second.getFood() &&
            Double.doubleToLongBits(first.getHydration()) ==
                Double.doubleToLongBits(second.getHydration()) &&
            Double.doubleToLongBits(first.getStamina()) ==
                Double.doubleToLongBits(second.getStamina()) &&
            Double.doubleToLongBits(first.getSurvivalPressure()) ==
                Double.doubleToLongBits(second.getSurvivalPressure());
    }

    private static Location copy(Location value)
    {
        return value;
    }
}
