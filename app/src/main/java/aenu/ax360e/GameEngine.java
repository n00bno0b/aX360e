package aenu.ax360e;

/**
 * Known game engines with specific compatibility requirements.
 * Based on the documented engine families in game_engine_compatibility.md
 */
public enum GameEngine {
    UNKNOWN("Unknown", "Unknown engine"),

    // Documented engines with known compatibility issues
    UNREAL("Unreal Engine", "Unreal Engine 3/3.5 - needs occlusion query fixes"),
    FROSTBITE("Frostbite", "Frostbite - needs GPU readback/memexport, kernel gaps"),
    MT_FRAMEWORK("MT Framework", "MT Framework - needs GPU readback/memexport"),
    GAMEBRYO("Gamebryo", "Gamebryo - needs kernel gaps, timing/VSync sensitivity"),
    SOURCE("Source Engine", "Source Engine - occlusion queries, GPU readback, CPU instructions"),
    UNITY("Unity", "Unity - CPU instruction coverage issues"),
    ID_TECH("id Tech", "id Tech 5 - timing/VSync sensitivity"),
    LITHTECH("LithTech", "LithTech Jupiter - kernel gaps"),
    EGO("EGO Engine", "EGO Engine - GPU readback/memexport"),

    // Additional common engines
    CRYENGINE("CryEngine", "CryEngine 3"),
    HAVOK("Havok Vision", "Havok Vision Engine"),
    PROPRIETARY("Proprietary", "Custom/proprietary engine");

    private final String displayName;
    private final String description;

    GameEngine(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public static GameEngine fromString(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        for (GameEngine engine : values()) {
            if (engine.name().equalsIgnoreCase(name) ||
                engine.displayName.equalsIgnoreCase(name)) {
                return engine;
            }
        }
        return UNKNOWN;
    }
}
