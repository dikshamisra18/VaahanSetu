package vaahansetu.desktop.service;

import java.util.List;
import vaahansetu.desktop.model.Bus;

public interface BusService {
    List<Bus> listBuses() throws ServiceException;
}
