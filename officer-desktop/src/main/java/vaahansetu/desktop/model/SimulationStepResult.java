package vaahansetu.desktop.model;

import java.util.List;

public record SimulationStepResult(int elapsedMinutes, String message,
                                   List<SegmentKey> affectedSegments) {
    public SimulationStepResult {
        affectedSegments = List.copyOf(affectedSegments);
    }
}
