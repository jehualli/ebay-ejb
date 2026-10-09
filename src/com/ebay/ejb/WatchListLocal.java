package com.ebay.ejb;

import javax.ejb.EJBLocalObject;
import java.util.List;

public interface WatchListLocal extends EJBLocalObject {
    void add(long itemId);
    List getItems();
    String whoAmI();
    int getCallCount();
}
