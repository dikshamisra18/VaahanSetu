package vaahansetu.desktop.service;

import vaahansetu.desktop.model.UserRole;

public interface AuthService {
    boolean login(String username, String password) throws ServiceException;
    String currentDisplayName();
    UserRole currentRole();
}
