package vaahansetu.desktop.service;

import java.util.Set;
import vaahansetu.desktop.model.Bus;
import vaahansetu.desktop.model.Route;

public interface PlanningService {
    long activeSessionId() throws ServiceException;
    String activeSessionName() throws ServiceException;
    Set<Long> selectedRouteIds() throws ServiceException;
    Set<Long> selectedBusIds() throws ServiceException;
    void saveSelection(Set<Long> routeIds, Set<Long> busIds) throws ServiceException;
}
