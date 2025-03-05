package de.peter1337.midnight.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public class Setting<T> {
    private final String name;
    private T value;
    private final String description;
    private T minValue;
    private T maxValue;
    private final List<T> options;
    private BiConsumer<T, T> onChange;

    // Dependency system
    private Setting<?> parent;
    private Supplier<Boolean> visibilityCondition;

    // Constructor for boolean settings.
    public Setting(String name, T defaultValue, String description) {
        this.name = name;
        this.value = defaultValue;
        this.description = description;
        this.options = new ArrayList<>();
    }

    // Constructor for number settings with range.
    public Setting(String name, T defaultValue, T minValue, T maxValue, String description) {
        this(name, defaultValue, description);
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    // Constructor for enum/dropdown settings with options.
    public Setting(String name, T defaultValue, List<T> options, String description) {
        this(name, defaultValue, description);
        this.options.addAll(options);
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    @SuppressWarnings("unchecked")
    public void setValue(Object newValue) {
        T typedValue;
        try {
            if (value instanceof Boolean && newValue instanceof Boolean) {
                typedValue = (T) newValue;
            } else if (value instanceof Number && newValue instanceof Number) {
                typedValue = (T) convertNumber((Number) newValue);
            } else if (value.getClass().equals(newValue.getClass())) {
                typedValue = (T) newValue;
            } else {
                return;
            }
        } catch (ClassCastException e) {
            return;
        }
        if (isValueValid(typedValue)) {
            T oldValue = this.value;
            this.value = typedValue;
            if (onChange != null) {
                onChange.accept(oldValue, typedValue);
            }
        }
    }

    private Number convertNumber(Number number) {
        if (value instanceof Double) return number.doubleValue();
        if (value instanceof Float) return number.floatValue();
        if (value instanceof Long) return number.longValue();
        if (value instanceof Integer) return number.intValue();
        if (value instanceof Short) return number.shortValue();
        if (value instanceof Byte) return number.byteValue();
        return number;
    }

    private boolean isValueValid(T newValue) {
        if (newValue == null) return false;
        if (minValue != null && maxValue != null && newValue instanceof Number) {
            double val = ((Number) newValue).doubleValue();
            double min = ((Number) minValue).doubleValue();
            double max = ((Number) maxValue).doubleValue();
            return val >= min && val <= max;
        }
        if (!options.isEmpty()) {
            return options.contains(newValue);
        }
        return true;
    }

    public String getDescription() {
        return description;
    }

    public T getMinValue() {
        return minValue;
    }

    public T getMaxValue() {
        return maxValue;
    }

    public List<T> getOptions() {
        return options;
    }

    public void setOnChange(BiConsumer<T, T> callback) {
        this.onChange = callback;
    }

    /**
     * Makes this setting dependent on a parent boolean setting.
     * This setting will only be visible when the parent setting is enabled.
     *
     * @param parent The parent boolean setting
     * @return This setting instance for method chaining
     */
    public Setting<T> dependsOn(Setting<Boolean> parent) {
        this.parent = parent;
        this.visibilityCondition = () -> parent.getValue();
        return this;
    }

    /**
     * Sets a custom visibility condition for this setting.
     * This setting will only be visible when the condition returns true.
     *
     * @param condition A supplier that returns true if the setting should be visible
     * @return This setting instance for method chaining
     */
    public Setting<T> visibleWhen(Supplier<Boolean> condition) {
        this.visibilityCondition = condition;
        return this;
    }

    /**
     * Checks if this setting should be visible based on its dependency conditions.
     *
     * @return true if the setting should be visible, false otherwise
     */
    public boolean isVisible() {
        if (visibilityCondition != null) {
            return visibilityCondition.get();
        }
        return true;
    }

    /**
     * Gets the parent setting that this setting depends on.
     *
     * @return The parent setting, or null if there is no parent
     */
    public Setting<?> getParent() {
        return parent;
    }
}