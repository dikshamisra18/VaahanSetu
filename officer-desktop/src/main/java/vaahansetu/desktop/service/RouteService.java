package vaahansetu.desktop.service;

import java.util.List;
import vaahansetu.desktop.model.Route;
import vaahansetu.desktop.model.RouteStop;

public interface RouteService {
    List<Route> listRoutes() throws ServiceException;
    List<RouteStop> stopsOfRoute(long routeId) throws ServiceException;
}
