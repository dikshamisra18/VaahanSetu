package vaahansetu.desktop.service;

import java.util.List;
import vaahansetu.desktop.model.ScheduledTrip;

public interface TripService {
    List<ScheduledTrip> listTrips() throws ServiceException;
    String currentBusLabel(long tripId) throws ServiceException;
    void refresh() throws ServiceException;
}
