package de.peter1337.midnight.modules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public class Setting<T> {
    private final String name;
    private T value;
    private final String description;
    private T minValue;
    private T maxValue;
    private final List<T> options;
    private BiConsumer<T, T> onChange;

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
}
