package de.peter1337.midnight.utils;

public class Setting<T> {
    private final String name;
    private T value;
    private final String description;

    public Setting(String name, T defaultValue, String description) {
        this.name = name;
        this.value = defaultValue;
        this.description = description;
    }

    public String getName() { return name; }
    public T getValue() { return value; }
    public void setValue(T value) { this.value = value; }
    public String getDescription() { return description; }
}