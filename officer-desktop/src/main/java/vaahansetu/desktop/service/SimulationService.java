package vaahansetu.desktop.service;

import java.util.List;
import vaahansetu.desktop.model.SegmentKey;
import vaahansetu.desktop.model.SimulationStepResult;

public interface SimulationService {
    SimulationStepResult simulateTwoMinutes(long tripId, List<SegmentKey> segments) throws ServiceException;
    int elapsedMinutes() throws ServiceException;
}
