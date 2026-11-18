package savanna;

import java.awt.Color;

/** Shared labels and compact color cues for the v1.7 behavior pipeline. */
public final class BehaviorVisuals
{
    private BehaviorVisuals() {}

    public static Color colorFor(ActionType action)
    {
        if(action == null) return new Color(110, 110, 110, 180);
        switch(action) {
            case DRINK:      return new Color(48, 196, 230, 235);
            case SEEK_WATER: return new Color(52, 121, 228, 235);
            case HUNT:       return new Color(226, 77, 62, 235);
            case GRAZE:      return new Color(100, 190, 84, 235);
            case REST:       return new Color(150, 150, 164, 220);
            case BREED:      return new Color(202, 91, 188, 235);
            case MOVE:       return new Color(246, 225, 101, 235);
            case WAIT:       return new Color(239, 157, 65, 235);
            default:         return new Color(110, 110, 110, 180);
        }
    }

    public static String describe(ActionType action, BehaviorReason reason)
    {
        if(action == null) return "No decision yet / 尚无行为决策";
        String text;
        switch(action) {
            case DRINK:      text = "Drink / 饮水"; break;
            case SEEK_WATER: text = "Seek water / 寻水"; break;
            case HUNT:       text = "Hunt / 捕猎"; break;
            case GRAZE:      text = "Graze / 进食"; break;
            case REST:       text = "Rest / 休息"; break;
            case BREED:      text = "Breed / 繁殖"; break;
            case MOVE:       text = "Move / 移动"; break;
            case WAIT:       text = "Wait / 等待"; break;
            default:         text = action.name(); break;
        }
        return reason == null ? text : text + " — " + reason.name();
    }

    public static String compactSummary(int[] counts)
    {
        if(counts == null) return "none";
        StringBuilder result = new StringBuilder();
        for(ActionType action : ActionType.values()) {
            int count = action.ordinal() < counts.length ? counts[action.ordinal()] : 0;
            if(count <= 0) continue;
            if(result.length() > 0) result.append("  ");
            result.append(shortName(action)).append(' ').append(count);
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    public static String describeEffects(java.util.List<ExecutedEffect> effects)
    {
        if(effects == null || effects.isEmpty()) {
            return "none / 无";
        }
        StringBuilder result = new StringBuilder();
        for(ExecutedEffect effect : effects) {
            if(result.length() > 0) result.append(" → ");
            result.append(effect.name());
        }
        return result.toString();
    }

    private static String shortName(ActionType action)
    {
        switch(action) {
            case DRINK: return "drink";
            case SEEK_WATER: return "water";
            case HUNT: return "hunt";
            case GRAZE: return "graze";
            case REST: return "rest";
            case BREED: return "breed";
            case MOVE: return "move";
            case WAIT: return "wait";
            default: return action.name().toLowerCase();
        }
    }
}
