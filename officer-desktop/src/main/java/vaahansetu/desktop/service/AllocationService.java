package vaahansetu.desktop.service;

import vaahansetu.desktop.model.AllocationResult;

public interface AllocationService {
    AllocationResult runAllocation() throws ServiceException;
    void approveAndApply(long allocationRunId) throws ServiceException;
}
