package vaahansetu.desktop.service;

import java.util.List;
import vaahansetu.desktop.model.SegmentDemand;

public interface DemandService {
    List<SegmentDemand> demandForTrip(long tripId) throws ServiceException;
}
