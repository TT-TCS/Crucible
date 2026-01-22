package com.tt_tcs.crucible.drugs;

public enum DrugType {
    weed("weed", 0.1),
    cocaine("cocaine", 0.3),
    meth("meth", 0.4),
    shrooms("shrooms", 0.005),
    fentanyl("fentanyl", 0.15);

    public final String id;
    public final double toleranceRate;

    public static DrugType fromId(String id) {
        for (DrugType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }

    DrugType(String id, double toleranceRate) {
        this.id = id;
        this.toleranceRate = toleranceRate;
    }
}