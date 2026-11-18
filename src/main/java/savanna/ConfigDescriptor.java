package savanna;

import java.util.Objects;
import java.util.function.Predicate;

/** A typed, named runtime setting with a unit and validation rule. */
public final class ConfigDescriptor<T>
{
    private final String key;
    private final T defaultValue;
    private final String unit;
    private final Predicate<T> validator;

    public ConfigDescriptor(String key, T defaultValue, String unit,
                            Predicate<T> validator)
    {
        if(key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("configuration key is required");
        }
        this.key = key;
        this.defaultValue = Objects.requireNonNull(defaultValue,
            "configuration default is required");
        this.unit = unit == null ? "" : unit;
        this.validator = Objects.requireNonNull(validator,
            "configuration validator is required");
        requireValid(defaultValue);
    }

    public String getKey() { return key; }
    public T getDefaultValue() { return defaultValue; }
    public String getUnit() { return unit; }

    public void requireValid(T value)
    {
        if(value == null || !validator.test(value)) {
            throw new IllegalArgumentException("Invalid " + key + ": " + value);
        }
    }
}
